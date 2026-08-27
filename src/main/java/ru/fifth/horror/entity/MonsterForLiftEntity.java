package ru.fifth.horror.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import ru.fifth.horror.network.FifthNetworking;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Monster For Lift. OFF stands still, SCRIPTED follows authored route points and LOGICAL performs
 * vision/search/chase. Locomotion animation is tied to actual client movement, so pathing can no longer
 * visually slide while the model remains in idle.
 */
public final class MonsterForLiftEntity extends PathAwareEntity implements GeoEntity {
    public enum AiMode { OFF, LOGICAL, SCRIPTED }

    private static final TrackedData<String> CURRENT_ANIMATION = DataTracker.registerData(MonsterForLiftEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> AI_MODE = DataTracker.registerData(MonsterForLiftEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> ANIMATION_START_AGE = DataTracker.registerData(MonsterForLiftEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALKING = RawAnimation.begin().thenLoop("walking");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation LOOK_LEFT = RawAnimation.begin().thenPlay("looking_left");
    private static final RawAnimation LOOK_RIGHT = RawAnimation.begin().thenPlay("looking_right");
    private static final RawAnimation LOOK_BACK = RawAnimation.begin().thenPlay("looking_backward");
    private static final RawAnimation SCREAMER = RawAnimation.begin().thenPlay("mfl_screamer");
    private static final RawAnimation HAND = RawAnimation.begin().thenPlay("mfl_hand");
    private static final List<String> DEBUG_ANIMATIONS = List.of("idle", "walking", "run", "looking_left", "looking_right", "looking_backward", "mfl_hand", "mfl_screamer");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final List<Vec3d> route = new ArrayList<>();

    private int lookVariant;
    private int debugAnimationIndex;
    private int manualAnimationTicks;
    private boolean routeRunning;
    private boolean routeLoop = true;
    private double routeSpeed = .72;
    private int routeIndex;
    private int routeNavIndex = -1;

    private boolean huntEnabled;
    private boolean patrolEnabled;
    private double visionRange = 24.0;
    private double visionAngle = 105.0;
    private double walkSpeed = .72;
    private double runSpeed = 1.18;
    private int searchDurationTicks = 120;
    private int searchTicks;
    private UUID chaseTarget;
    private Vec3d lastKnownTarget;
    private Vec3d lastPathTarget;
    private int acquireCooldown;
    private int chaseRepathCooldown;
    private int screamerCooldown;

    public MonsterForLiftEntity(EntityType<? extends PathAwareEntity> type, World world) { super(type, world); }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(CURRENT_ANIMATION, "idle");
        dataTracker.startTracking(AI_MODE, AiMode.OFF.ordinal());
        dataTracker.startTracking(ANIMATION_START_AGE, 0);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 14f));
        goalSelector.add(4, new LookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (screamerCooldown > 0) screamerCooldown--;
        if (chaseRepathCooldown > 0) chaseRepathCooldown--;

        if (manualAnimationTicks > 0) {
            manualAnimationTicks--;
            if (manualAnimationTicks == 0 && getAiMode() == AiMode.OFF) setCurrentAnimation("idle");
            return;
        }

        switch (getAiMode()) {
            case OFF -> stopAutonomousMovement();
            case SCRIPTED -> tickScripted();
            case LOGICAL -> tickLogical();
        }
    }

    private void tickScripted() {
        if (routeRunning && !route.isEmpty()) {
            tickRoute(routeSpeed);
            setCurrentAnimation(isActuallyMoving() || !getNavigation().isIdle() ? "walking" : "idle");
        } else {
            stopAutonomousMovement();
        }
    }

    private void tickLogical() {
        if (!(getWorld() instanceof ServerWorld world)) return;

        ServerPlayerEntity target = findChaseTarget(world);
        if (huntEnabled) {
            if (target == null && --acquireCooldown <= 0) {
                acquireCooldown = 8;
                target = acquireVisiblePlayer(world);
                if (target != null) {
                    chaseTarget = target.getUuid();
                    chaseRepathCooldown = 0;
                    lastPathTarget = null;
                }
            }

            if (target != null && isValidTarget(target)) {
                if (canSeeTarget(target)) {
                    Vec3d targetPos = target.getPos();
                    lastKnownTarget = targetPos;
                    searchTicks = searchDurationTicks;
                    if (needsRepath(targetPos)) {
                        getNavigation().startMovingTo(target, runSpeed);
                        chaseRepathCooldown = 5;
                        lastPathTarget = targetPos;
                    }
                    setCurrentAnimation("run");
                    if (squaredDistanceTo(target) <= 1.55 * 1.55 && screamerCooldown <= 0) catchPlayer(target);
                    return;
                }

                if (lastKnownTarget != null && searchTicks-- > 0) {
                    if (getPos().squaredDistanceTo(lastKnownTarget) < 1.2) {
                        getNavigation().stop();
                        setCurrentAnimation("idle");
                        return;
                    }
                    if (getNavigation().isIdle() || chaseRepathCooldown <= 0) {
                        getNavigation().startMovingTo(lastKnownTarget.x, lastKnownTarget.y, lastKnownTarget.z, walkSpeed);
                        chaseRepathCooldown = 10;
                    }
                    setCurrentAnimation(isActuallyMoving() || !getNavigation().isIdle() ? "walking" : "idle");
                    return;
                }
                clearChase();
            }
        } else {
            clearChase();
        }

        if (patrolEnabled && routeRunning && !route.isEmpty()) {
            tickRoute(walkSpeed);
            setCurrentAnimation(isActuallyMoving() || !getNavigation().isIdle() ? "walking" : "idle");
        } else {
            stopAutonomousMovement();
        }
    }

    private boolean needsRepath(Vec3d targetPos) {
        return getNavigation().isIdle()
                || chaseRepathCooldown <= 0
                || lastPathTarget == null
                || lastPathTarget.squaredDistanceTo(targetPos) > 2.25;
    }

    private boolean isActuallyMoving() {
        Vec3d v = getVelocity();
        return v.x * v.x + v.z * v.z > 0.00012;
    }

    private void catchPlayer(ServerPlayerEntity player) {
        if (player.isCreative() || player.isSpectator()) return;
        getNavigation().stop();
        screamerCooldown = 100;
        manualAnimationTicks = 16;
        setCurrentAnimation("mfl_screamer");
        triggerAnim("action", "screamer");
        FifthNetworking.sendScreamer(player, 30, 1.15f);
        clearChase();
    }

    public void triggerScreamer(ServerPlayerEntity viewer) {
        getNavigation().stop();
        manualAnimationTicks = 16;
        setCurrentAnimation("mfl_screamer");
        triggerAnim("action", "screamer");
        if (viewer != null) FifthNetworking.sendScreamer(viewer, 30, 1.15f);
    }

    private ServerPlayerEntity acquireVisiblePlayer(ServerWorld world) {
        ServerPlayerEntity best = null;
        double bestSq = visionRange * visionRange;
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (!isValidTarget(p)) continue;
            double d = squaredDistanceTo(p);
            if (d <= bestSq && inVisionCone(p) && canSeeTarget(p)) {
                best = p;
                bestSq = d;
            }
        }
        return best;
    }

    private boolean isValidTarget(ServerPlayerEntity p) {
        return p != null && p.isAlive() && !p.isCreative() && !p.isSpectator();
    }

    private boolean canSeeTarget(ServerPlayerEntity p) {
        return !MflHidingManager.isHidden(p) && canSee(p);
    }

    private boolean inVisionCone(ServerPlayerEntity p) {
        Vec3d to = p.getEyePos().subtract(getEyePos());
        if (to.lengthSquared() < 0.0001) return true;
        double yaw = Math.toRadians(getYaw());
        Vec3d forward = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3d flat = new Vec3d(to.x, 0, to.z);
        if (flat.lengthSquared() < 0.0001) return true;
        double dot = forward.normalize().dotProduct(flat.normalize());
        return dot >= Math.cos(Math.toRadians(Math.max(10, Math.min(360, visionAngle)) * .5));
    }

    private ServerPlayerEntity findChaseTarget(ServerWorld world) {
        if (chaseTarget == null) return null;
        for (ServerPlayerEntity p : world.getPlayers()) if (p.getUuid().equals(chaseTarget)) return p;
        return null;
    }

    private void clearChase() {
        chaseTarget = null;
        lastKnownTarget = null;
        lastPathTarget = null;
        searchTicks = 0;
        chaseRepathCooldown = 0;
    }

    private void stopAutonomousMovement() {
        if (!getNavigation().isIdle()) getNavigation().stop();
        routeNavIndex = -1;
        setCurrentAnimation("idle");
    }

    private void tickRoute(double speed) {
        routeIndex = Math.max(0, Math.min(routeIndex, route.size() - 1));
        Vec3d p = route.get(routeIndex);
        if (getPos().squaredDistanceTo(p) < .8) {
            routeIndex++;
            routeNavIndex = -1;
            if (routeIndex >= route.size()) {
                if (routeLoop) routeIndex = 0;
                else { routeRunning = false; getNavigation().stop(); return; }
            }
            p = route.get(routeIndex);
        }
        if (routeNavIndex != routeIndex || getNavigation().isIdle() || age % 15 == 0) {
            getNavigation().startMovingTo(p.x, p.y, p.z, Math.max(.15, Math.min(2.5, speed)));
            routeNavIndex = routeIndex;
        }
    }

    public void addRoutePoint(Vec3d p) { route.add(p); routeNavIndex = -1; }
    public void clearRoute() { route.clear(); routeIndex = 0; routeNavIndex = -1; routeRunning = false; getNavigation().stop(); }
    public List<Vec3d> getRoute() { return List.copyOf(route); }
    public boolean isRouteRunning() { return routeRunning; }
    public void startRoute(boolean loop, double speed) { routeLoop = loop; routeSpeed = speed; routeIndex = 0; routeNavIndex = -1; routeRunning = !route.isEmpty(); }
    public void stopRoute() { routeRunning = false; routeNavIndex = -1; getNavigation().stop(); }

    public AiMode getAiMode() { return AiMode.values()[Math.max(0, Math.min(AiMode.values().length - 1, dataTracker.get(AI_MODE)))]; }
    public void setAiMode(AiMode mode) { dataTracker.set(AI_MODE, (mode == null ? AiMode.OFF : mode).ordinal()); if (mode == AiMode.OFF) stopAutonomousMovement(); }
    public boolean isHuntEnabled() { return huntEnabled; }
    public void setHuntEnabled(boolean value) { huntEnabled = value; if (!value) clearChase(); }
    public boolean isPatrolEnabled() { return patrolEnabled; }
    public void setPatrolEnabled(boolean value) { patrolEnabled = value; }
    public double getVisionRange() { return visionRange; }
    public void setVisionRange(double value) { visionRange = Math.max(2, Math.min(96, value)); }
    public double getVisionAngle() { return visionAngle; }
    public void setVisionAngle(double value) { visionAngle = Math.max(10, Math.min(360, value)); }
    public double getWalkSpeed() { return walkSpeed; }
    public void setWalkSpeed(double value) { walkSpeed = Math.max(.1, Math.min(2.5, value)); }
    public double getRunSpeed() { return runSpeed; }
    public void setRunSpeed(double value) { runSpeed = Math.max(.1, Math.min(3.5, value)); }
    public int getSearchDurationTicks() { return searchDurationTicks; }
    public void setSearchDurationTicks(int value) { searchDurationTicks = Math.max(0, Math.min(1200, value)); }
    public int getAnimationStartAge() { return dataTracker.get(ANIMATION_START_AGE); }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (player.getAbilities().creativeMode) {
            if (!getWorld().isClient) {
                String next = DEBUG_ANIMATIONS.get(debugAnimationIndex++ % DEBUG_ANIMATIONS.size());
                preview(next);
                player.sendMessage(Text.literal("§8[§cFiven§8] §7MFL animation: §c" + next), true);
            }
            return ActionResult.success(getWorld().isClient);
        }
        if (player.isSneaking()) {
            if (!getWorld().isClient) {
                String trigger = switch (lookVariant++ % 3) {
                    case 0 -> "looking_left";
                    case 1 -> "looking_right";
                    default -> "looking_backward";
                };
                preview(trigger);
            }
            return ActionResult.success(getWorld().isClient);
        }
        return super.interactMob(player, hand);
    }

    public void preview(String name) {
        if (getWorld().isClient) return;
        String a = name == null ? "" : name.trim();
        getNavigation().stop();
        if (a.isBlank()) { manualAnimationTicks = 0; setCurrentAnimation("idle"); return; }
        switch (a) {
            case "idle" -> { manualAnimationTicks = 80; setCurrentAnimation("idle"); }
            case "walking", "walk" -> { manualAnimationTicks = 80; setCurrentAnimation("walking"); }
            case "running", "run" -> { manualAnimationTicks = 80; setCurrentAnimation("run"); }
            case "looking_left" -> { manualAnimationTicks = 100; setCurrentAnimation(a); triggerAnim("action", "look_left"); }
            case "looking_right" -> { manualAnimationTicks = 100; setCurrentAnimation(a); triggerAnim("action", "look_right"); }
            case "looking_backward" -> { manualAnimationTicks = 100; setCurrentAnimation(a); triggerAnim("action", "look_back"); }
            case "mfl_screamer" -> { manualAnimationTicks = 16; setCurrentAnimation(a); triggerAnim("action", "screamer"); }
            case "mfl_hand" -> { manualAnimationTicks = 50; setCurrentAnimation(a); triggerAnim("action", "hand"); }
            default -> { manualAnimationTicks = 120; setCurrentAnimation(a); }
        }
    }

    public String getCurrentAnimation() { return dataTracker.get(CURRENT_ANIMATION); }
    private void setCurrentAnimation(String animation) {
        String value = animation == null || animation.isBlank() ? "idle" : animation;
        if (!value.equals(dataTracker.get(CURRENT_ANIMATION))) {
            dataTracker.set(CURRENT_ANIMATION, value);
            dataTracker.set(ANIMATION_START_AGE, age);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 1, state -> {
            String a = getCurrentAnimation();
            boolean action = a != null && (a.startsWith("looking_") || "mfl_screamer".equals(a) || "mfl_hand".equals(a));
            Vec3d velocity = getVelocity();
            boolean physicallyMoving = state.isMoving() || velocity.x * velocity.x + velocity.z * velocity.z > 0.00012;
            boolean wantsRun = "run".equals(a) || "running".equals(a);

            // Physical motion wins over a stale tracked idle state. This is the anti-sliding path.
            if (!action && physicallyMoving) {
                if (wantsRun) {
                    state.getController().setAnimationSpeed(2.75);
                    return state.setAndContinue(RUN);
                }
                state.getController().setAnimationSpeed(1.8);
                return state.setAndContinue(WALKING);
            }

            state.getController().setAnimationSpeed(1.0);
            if (a == null || a.isBlank() || action || "idle".equals(a)) return state.setAndContinue(IDLE);
            if ("walking".equals(a) || "walk".equals(a)) {
                state.getController().setAnimationSpeed(1.8);
                return state.setAndContinue(WALKING);
            }
            if (wantsRun) {
                state.getController().setAnimationSpeed(2.75);
                return state.setAndContinue(RUN);
            }
            return state.setAndContinue(RawAnimation.begin().thenLoop(a));
        }));

        controllers.add(new AnimationController<>(this, "action", 1, state -> PlayState.STOP)
                .triggerableAnim("look_left", LOOK_LEFT)
                .triggerableAnim("look_right", LOOK_RIGHT)
                .triggerableAnim("look_back", LOOK_BACK)
                .triggerableAnim("screamer", SCREAMER)
                .triggerableAnim("hand", HAND));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public void writeCustomDataToNbt(NbtCompound n) {
        super.writeCustomDataToNbt(n);
        n.putInt("FivenMflLook", lookVariant);
        n.putInt("FivenMflDebug", debugAnimationIndex);
        n.putString("FivenMflAnimation", getCurrentAnimation());
        n.putInt("FivenMflAiMode", getAiMode().ordinal());
        n.putBoolean("FivenMflHunt", huntEnabled);
        n.putBoolean("FivenMflPatrol", patrolEnabled);
        n.putDouble("FivenMflVisionRange", visionRange);
        n.putDouble("FivenMflVisionAngle", visionAngle);
        n.putDouble("FivenMflWalkSpeed", walkSpeed);
        n.putDouble("FivenMflRunSpeed", runSpeed);
        n.putInt("FivenMflSearchTicks", searchDurationTicks);
        n.putBoolean("FivenMflRouteRunning", routeRunning);
        n.putBoolean("FivenMflRouteLoop", routeLoop);
        n.putDouble("FivenMflRouteSpeed", routeSpeed);
        NbtList list = new NbtList();
        for (Vec3d p : route) {
            NbtCompound c = new NbtCompound(); c.putDouble("x", p.x); c.putDouble("y", p.y); c.putDouble("z", p.z); list.add(c);
        }
        n.put("FivenMflRoute", list);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound n) {
        super.readCustomDataFromNbt(n);
        lookVariant = n.getInt("FivenMflLook");
        debugAnimationIndex = n.getInt("FivenMflDebug");
        if (n.contains("FivenMflAnimation")) dataTracker.set(CURRENT_ANIMATION, n.getString("FivenMflAnimation"));
        dataTracker.set(AI_MODE, Math.max(0, Math.min(AiMode.values().length - 1, n.getInt("FivenMflAiMode"))));
        huntEnabled = n.getBoolean("FivenMflHunt");
        patrolEnabled = n.getBoolean("FivenMflPatrol");
        visionRange = n.contains("FivenMflVisionRange") ? n.getDouble("FivenMflVisionRange") : 24;
        visionAngle = n.contains("FivenMflVisionAngle") ? n.getDouble("FivenMflVisionAngle") : 105;
        walkSpeed = n.contains("FivenMflWalkSpeed") ? n.getDouble("FivenMflWalkSpeed") : .72;
        runSpeed = n.contains("FivenMflRunSpeed") ? n.getDouble("FivenMflRunSpeed") : 1.18;
        searchDurationTicks = n.contains("FivenMflSearchTicks") ? n.getInt("FivenMflSearchTicks") : 120;
        manualAnimationTicks = 0;
        routeRunning = n.getBoolean("FivenMflRouteRunning");
        routeLoop = !n.contains("FivenMflRouteLoop") || n.getBoolean("FivenMflRouteLoop");
        routeSpeed = n.contains("FivenMflRouteSpeed") ? n.getDouble("FivenMflRouteSpeed") : .72;
        route.clear();
        routeNavIndex = -1;
        chaseRepathCooldown = 0;
        lastPathTarget = null;
        NbtList list = n.getList("FivenMflRoute", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i); route.add(new Vec3d(c.getDouble("x"), c.getDouble("y"), c.getDouble("z")));
        }
    }
}
