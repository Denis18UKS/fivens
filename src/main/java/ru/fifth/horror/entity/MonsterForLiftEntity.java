package ru.fifth.horror.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

/** MFL = monster_for_lift. Supports run state, custom model animations and creative animation cycling. */
public final class MonsterForLiftEntity extends PathAwareEntity implements GeoEntity {
    private static final TrackedData<String> CURRENT_ANIMATION = DataTracker.registerData(MonsterForLiftEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALKING = RawAnimation.begin().thenLoop("walking");
    private static final RawAnimation RUNNING = RawAnimation.begin().thenLoop("running");
    private static final RawAnimation LOOK_LEFT = RawAnimation.begin().thenPlay("looking_left");
    private static final RawAnimation LOOK_RIGHT = RawAnimation.begin().thenPlay("looking_right");
    private static final RawAnimation LOOK_BACK = RawAnimation.begin().thenPlay("looking_backward");
    private static final List<String> DEBUG_ANIMATIONS = List.of("idle", "walking", "running", "looking_left", "looking_right", "looking_backward");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final List<Vec3d> route = new ArrayList<>();
    private int lookVariant;
    private int debugAnimationIndex;
    private int manualAnimationTicks;
    private boolean routeRunning;
    private boolean routeLoop = true;
    private double routeSpeed = .72;
    private int routeIndex;

    public MonsterForLiftEntity(EntityType<? extends PathAwareEntity> type, World world) { super(type, world); }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(CURRENT_ANIMATION, "idle");
    }

    @Override
    protected void initGoals() {
        goalSelector.add(2, new WanderAroundFarGoal(this, .72, 18));
        goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 14f));
        goalSelector.add(4, new LookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (routeRunning && !route.isEmpty()) tickRoute();

        if (manualAnimationTicks > 0) {
            manualAnimationTicks--;
            return;
        }
        double speed = getVelocity().horizontalLength();
        setCurrentAnimation(speed > .15 ? "running" : speed > .015 ? "walking" : "idle");
    }

    private void tickRoute() {
        routeIndex = Math.max(0, Math.min(routeIndex, route.size() - 1));
        Vec3d p = route.get(routeIndex);
        if (squaredDistanceTo(p) < .8) {
            routeIndex++;
            if (routeIndex >= route.size()) {
                if (routeLoop) routeIndex = 0;
                else { routeRunning = false; return; }
            }
            p = route.get(routeIndex);
        }
        getNavigation().startMovingTo(p.x, p.y, p.z, Math.max(.15, Math.min(2.5, routeSpeed)));
    }

    public void addRoutePoint(Vec3d p) { route.add(p); }
    public void clearRoute() { route.clear(); routeIndex = 0; routeRunning = false; getNavigation().stop(); }
    public List<Vec3d> getRoute() { return List.copyOf(route); }
    public boolean isRouteRunning() { return routeRunning; }
    public void startRoute(boolean loop, double speed) { routeLoop = loop; routeSpeed = speed; routeIndex = 0; routeRunning = !route.isEmpty(); }
    public void stopRoute() { routeRunning = false; getNavigation().stop(); }

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

    /** Preview a known or custom animation name from the MFL animation resource. */
    public void preview(String name) {
        if (getWorld().isClient) return;
        String normalized = name == null ? "" : name.trim();
        if (normalized.isBlank()) {
            manualAnimationTicks = 0;
            setCurrentAnimation("idle");
            return;
        }
        switch (normalized) {
            case "idle" -> {
                manualAnimationTicks = 80;
                setCurrentAnimation("idle");
                triggerAnim("main", "idle_debug");
            }
            case "walking", "walk" -> {
                manualAnimationTicks = 80;
                setCurrentAnimation("walking");
                triggerAnim("main", "walk_debug");
            }
            case "running", "run" -> {
                manualAnimationTicks = 80;
                setCurrentAnimation("running");
                triggerAnim("main", "run_debug");
            }
            case "looking_left" -> {
                manualAnimationTicks = 100;
                setCurrentAnimation("looking_left");
                triggerAnim("main", "look_left");
            }
            case "looking_right" -> {
                manualAnimationTicks = 100;
                setCurrentAnimation("looking_right");
                triggerAnim("main", "look_right");
            }
            case "looking_backward" -> {
                manualAnimationTicks = 100;
                setCurrentAnimation("looking_backward");
                triggerAnim("main", "look_back");
            }
            default -> {
                // Custom names are rendered dynamically by the controller below.
                manualAnimationTicks = 120;
                setCurrentAnimation(normalized);
            }
        }
    }

    public String getCurrentAnimation() { return dataTracker.get(CURRENT_ANIMATION); }
    private void setCurrentAnimation(String animation) {
        String value = animation == null || animation.isBlank() ? "idle" : animation;
        if (!value.equals(dataTracker.get(CURRENT_ANIMATION))) dataTracker.set(CURRENT_ANIMATION, value);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            String animation = getCurrentAnimation();
            if (animation == null || animation.isBlank() || animation.startsWith("looking_")) {
                return state.setAndContinue(IDLE);
            }
            if ("idle".equals(animation)) return state.setAndContinue(IDLE);
            if ("walking".equals(animation) || "walk".equals(animation)) return state.setAndContinue(WALKING);
            if ("running".equals(animation) || "run".equals(animation)) return state.setAndContinue(RUNNING);
            return state.setAndContinue(RawAnimation.begin().thenLoop(animation));
        }).triggerableAnim("idle_debug", IDLE)
                .triggerableAnim("walk_debug", WALKING)
                .triggerableAnim("run_debug", RUNNING)
                .triggerableAnim("look_left", LOOK_LEFT)
                .triggerableAnim("look_right", LOOK_RIGHT)
                .triggerableAnim("look_back", LOOK_BACK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public void writeCustomDataToNbt(NbtCompound n) {
        super.writeCustomDataToNbt(n);
        n.putInt("FivenMflLook", lookVariant);
        n.putInt("FivenMflDebug", debugAnimationIndex);
        n.putString("FivenMflAnimation", getCurrentAnimation());
        n.putBoolean("FivenMflRouteRunning", routeRunning);
        n.putBoolean("FivenMflRouteLoop", routeLoop);
        n.putDouble("FivenMflRouteSpeed", routeSpeed);
        NbtList list = new NbtList();
        for (Vec3d p : route) {
            NbtCompound c = new NbtCompound();
            c.putDouble("x", p.x); c.putDouble("y", p.y); c.putDouble("z", p.z);
            list.add(c);
        }
        n.put("FivenMflRoute", list);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound n) {
        super.readCustomDataFromNbt(n);
        lookVariant = n.getInt("FivenMflLook");
        debugAnimationIndex = n.getInt("FivenMflDebug");
        if (n.contains("FivenMflAnimation")) dataTracker.set(CURRENT_ANIMATION, n.getString("FivenMflAnimation"));
        manualAnimationTicks = 0;
        routeRunning = n.getBoolean("FivenMflRouteRunning");
        routeLoop = !n.contains("FivenMflRouteLoop") || n.getBoolean("FivenMflRouteLoop");
        routeSpeed = n.contains("FivenMflRouteSpeed") ? n.getDouble("FivenMflRouteSpeed") : .72;
        route.clear();
        NbtList list = n.getList("FivenMflRoute", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            route.add(new Vec3d(c.getDouble("x"), c.getDouble("y"), c.getDouble("z")));
        }
    }
}
