package ru.fifth.horror.entity;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import ru.fifth.horror.block.ScriptComputerBlockEntity;
import net.minecraft.world.World;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.script.FifthScriptEngine;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DirectorNpcEntity extends PathAwareEntity implements GeoEntity {
    private static final Gson GSON = new Gson();
    private static final TrackedData<String> NPC_ID = DataTracker.registerData(DirectorNpcEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<String> MODEL = DataTracker.registerData(DirectorNpcEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<String> TEXTURE = DataTracker.registerData(DirectorNpcEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<String> SKIN_BASE64 = DataTracker.registerData(DirectorNpcEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<String> ANIMATION_FILE = DataTracker.registerData(DirectorNpcEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<String> CURRENT_ANIMATION = DataTracker.registerData(DirectorNpcEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Boolean> AI_ENABLED = DataTracker.registerData(DirectorNpcEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> SCALE = DataTracker.registerData(DirectorNpcEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final List<Vec3d> route = new ArrayList<>();
    private String aiScript = "";
    private boolean routeRunning;
    private boolean routeLoop;
    private double routeSpeed = 0.25;
    private int routeIndex;
    private int lastNavigationTarget = -1;
    private Vec3d directTarget;
    private double directSpeed = 0.25;
    private int navigationRetryTicks;
    private BlockPos linkedComputer;

    public DirectorNpcEntity(EntityType<? extends PathAwareEntity> type, World world) { super(type, world); }

    @Override
    protected void initGoals() {
        // Deliberately empty: an unprogrammed NPC is a statue.
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(NPC_ID, "npc_" + UUID.randomUUID().toString().substring(0, 8));
        dataTracker.startTracking(MODEL, "fiven:geo/npc_default.geo.json");
        dataTracker.startTracking(TEXTURE, "fiven:textures/entity/npc_default.png");
        dataTracker.startTracking(SKIN_BASE64, "");
        dataTracker.startTracking(ANIMATION_FILE, "fiven:animations/npc_default.animation.json");
        dataTracker.startTracking(CURRENT_ANIMATION, "");
        dataTracker.startTracking(AI_ENABLED, false);
        dataTracker.startTracking(SCALE, 1.0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (!isAiEnabled()) {
            getNavigation().stop();
            setVelocity(Vec3d.ZERO);
            return;
        }
        if (linkedComputer != null && age % 20 == 0 && getWorld().getBlockEntity(linkedComputer) instanceof ScriptComputerBlockEntity computer) {
            String linkedScript = computer.getScriptName();
            if (linkedScript != null && !linkedScript.isBlank()) aiScript = linkedScript;
        }
        if (!aiScript.isBlank() && age % 5 == 0) FifthScriptEngine.runNpcTick(this, aiScript);
        if (directTarget != null) tickDirectMove();
        else tickRoute();
    }

    private void tickRoute() {
        if (!routeRunning || route.isEmpty()) return;
        routeIndex = Math.max(0, Math.min(routeIndex, route.size() - 1));
        Vec3d p = route.get(routeIndex);
        if (squaredDistanceTo(p) < 0.75) {
            routeIndex++;
            lastNavigationTarget = -1;
            if (routeIndex >= route.size()) {
                if (routeLoop) routeIndex = 0;
                else {
                    routeRunning = false;
                    getNavigation().stop();
                    clearMovementAnimation();
                    return;
                }
            }
            p = route.get(routeIndex);
        }
        driveNavigation(p, routeSpeed, routeIndex);
    }

    private void tickDirectMove() {
        Vec3d p = directTarget;
        if (p == null) return;
        if (squaredDistanceTo(p) < 0.65) {
            directTarget = null;
            getNavigation().stop();
            lastNavigationTarget = -1;
            clearMovementAnimation();
            return;
        }
        driveNavigation(p, directSpeed, Integer.MIN_VALUE);
    }

    /**
     * Drives vanilla path navigation, but does not rebuild the path every tick.
     * Script speeds are authored as familiar 0.25/0.4 values; navigation expects a multiplier,
     * therefore 0.25 is mapped to normal walking speed (~1.0 multiplier).
     */
    private void driveNavigation(Vec3d target, double authoredSpeed, int targetKey) {
        double navigationSpeed = Math.max(0.12, Math.min(2.5, authoredSpeed * 4.0));
        boolean newTarget = targetKey != lastNavigationTarget;
        boolean retry = getNavigation().isIdle() && (++navigationRetryTicks >= 8);
        if (newTarget || retry) {
            getNavigation().startMovingTo(target.x, target.y, target.z, navigationSpeed);
            getMoveControl().moveTo(target.x, target.y, target.z, navigationSpeed);
            lastNavigationTarget = targetKey;
            navigationRetryTicks = 0;
        }
        if (getCurrentAnimation().isBlank()) setCurrentAnimation(authoredSpeed >= 0.38 ? "animation.npc.run" : "animation.npc.walk");
    }

    private void clearMovementAnimation() {
        String a = getCurrentAnimation();
        if ("animation.npc.walk".equals(a) || "animation.npc.run".equals(a)) setCurrentAnimation("");
    }

    public void applyTemplateJson(String json) {
        try {
            JsonObject o = GSON.fromJson(json, JsonObject.class);
            if (o.has("id")) setNpcId(o.get("id").getAsString());
            if (o.has("name")) setCustomName(Text.literal(o.get("name").getAsString()));
            setCustomNameVisible(o.has("showName") && o.get("showName").getAsBoolean());
            if (o.has("model")) dataTracker.set(MODEL, o.get("model").getAsString());
            if (o.has("texture")) dataTracker.set(TEXTURE, o.get("texture").getAsString());
            if (o.has("skinBase64")) dataTracker.set(SKIN_BASE64, o.get("skinBase64").getAsString());
            if (o.has("animationFile")) dataTracker.set(ANIMATION_FILE, o.get("animationFile").getAsString());
            if (o.has("scale")) dataTracker.set(SCALE, Math.max(0.1f, Math.min(8f, o.get("scale").getAsFloat())));
            if (o.has("aiScript")) aiScript = o.get("aiScript").getAsString();
            if (o.has("aiEnabled")) setAiEnabled(o.get("aiEnabled").getAsBoolean());
        } catch (Exception ignored) {}
    }

    public String toTemplateJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", getNpcId());
        o.addProperty("name", getCustomName() == null ? getNpcId() : getCustomName().getString());
        o.addProperty("showName", isCustomNameVisible());
        o.addProperty("model", getModelResource().toString());
        o.addProperty("texture", getTextureResource().toString());
        o.addProperty("skinBase64", getSkinBase64());
        o.addProperty("animationFile", getAnimationResource().toString());
        o.addProperty("scale", getNpcScale());
        o.addProperty("aiScript", aiScript);
        o.addProperty("aiEnabled", isAiEnabled());
        return GSON.toJson(o);
    }

    public void addPathPoint(Vec3d p) { route.add(p); }
    public void clearPath() { route.clear(); routeIndex = 0; routeRunning = false; lastNavigationTarget = -1; getNavigation().stop(); clearMovementAnimation(); }
    public List<Vec3d> getPathPoints() { return List.copyOf(route); }
    public void followPath(boolean loop, double speed) {
        routeLoop = loop;
        routeSpeed = Math.max(0.02, Math.min(2.0, speed));
        // onNpcTick() may call followPath every few ticks. Do not restart at point #1 every time.
        directTarget = null;
        if (!routeRunning) {
            routeIndex = 0;
            lastNavigationTarget = -1;
            routeRunning = !route.isEmpty();
        }
    }
    public void stopPath() { routeRunning = false; lastNavigationTarget = -1; getNavigation().stop(); clearMovementAnimation(); }
    public boolean isRouteRunning() { return routeRunning; }
    public void moveToTarget(double x, double y, double z, double speed) {
        setAiEnabled(true);
        routeRunning = false;
        directTarget = new Vec3d(x, y, z);
        directSpeed = Math.max(0.02, Math.min(2.0, speed));
        lastNavigationTarget = -1;
        navigationRetryTicks = 0;
    }
    public void stopAllMovement() {
        routeRunning = false;
        directTarget = null;
        lastNavigationTarget = -1;
        getNavigation().stop();
        setVelocity(Vec3d.ZERO);
        clearMovementAnimation();
    }
    public String getNpcId() { return dataTracker.get(NPC_ID); }
    public void setNpcId(String id) { dataTracker.set(NPC_ID, id == null || id.isBlank() ? "npc" : id.trim()); }
    public boolean isAiEnabled() { return dataTracker.get(AI_ENABLED); }
    public void setAiEnabled(boolean enabled) { dataTracker.set(AI_ENABLED, enabled); if (!enabled) stopAllMovement(); }
    public String getCurrentAnimation() { return dataTracker.get(CURRENT_ANIMATION); }
    public void setCurrentAnimation(String animation) { dataTracker.set(CURRENT_ANIMATION, animation == null ? "" : animation); }
    public float getNpcScale() { return dataTracker.get(SCALE); }
    public String getSkinBase64() { return dataTracker.get(SKIN_BASE64); }
    public void setSkinBase64(String value) { dataTracker.set(SKIN_BASE64, value == null ? "" : value); }
    public String getAiScript() { return aiScript; }
    public void setAiScript(String script) { aiScript = script == null ? "" : script; }
    public void linkComputer(BlockPos pos) { linkedComputer = pos == null ? null : pos.toImmutable(); }
    public BlockPos getLinkedComputer() { return linkedComputer; }
    public Identifier getModelResource() { return safeId(dataTracker.get(MODEL), FifthMod.id("geo/npc_default.geo.json")); }
    public Identifier getTextureResource() { return safeId(dataTracker.get(TEXTURE), FifthMod.id("textures/entity/npc_default.png")); }
    public Identifier getAnimationResource() { return safeId(dataTracker.get(ANIMATION_FILE), FifthMod.id("animations/npc_default.animation.json")); }
    public void setAnimationResource(String value) {
        Identifier parsed = Identifier.tryParse(value);
        if (parsed != null) dataTracker.set(ANIMATION_FILE, parsed.toString());
    }
    private Identifier safeId(String value, Identifier fallback) { Identifier id = Identifier.tryParse(value); return id == null ? fallback : id; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("FifthNpcId", getNpcId()); nbt.putString("FifthModel", dataTracker.get(MODEL)); nbt.putString("FifthTexture", dataTracker.get(TEXTURE));
        nbt.putString("FifthSkinBase64", getSkinBase64()); nbt.putString("FifthAnimFile", dataTracker.get(ANIMATION_FILE)); nbt.putString("FifthAnimation", getCurrentAnimation());
        nbt.putBoolean("FifthAi", isAiEnabled()); nbt.putFloat("FifthScale", getNpcScale()); nbt.putString("FifthAiScript", aiScript);
        if (linkedComputer != null) nbt.putLong("FifthLinkedComputer", linkedComputer.asLong());
        NbtList list = new NbtList();
        for (Vec3d p : route) { NbtCompound c = new NbtCompound(); c.putDouble("x", p.x); c.putDouble("y", p.y); c.putDouble("z", p.z); list.add(c); }
        nbt.put("FifthPath", list);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("FifthNpcId")) dataTracker.set(NPC_ID, nbt.getString("FifthNpcId"));
        if (nbt.contains("FifthModel")) dataTracker.set(MODEL, nbt.getString("FifthModel"));
        if (nbt.contains("FifthTexture")) dataTracker.set(TEXTURE, nbt.getString("FifthTexture"));
        if (nbt.contains("FifthSkinBase64")) dataTracker.set(SKIN_BASE64, nbt.getString("FifthSkinBase64"));
        if (nbt.contains("FifthAnimFile")) dataTracker.set(ANIMATION_FILE, nbt.getString("FifthAnimFile"));
        if (nbt.contains("FifthAnimation")) dataTracker.set(CURRENT_ANIMATION, nbt.getString("FifthAnimation"));
        if (nbt.contains("FifthAi")) dataTracker.set(AI_ENABLED, nbt.getBoolean("FifthAi"));
        if (nbt.contains("FifthScale")) dataTracker.set(SCALE, nbt.getFloat("FifthScale"));
        aiScript = nbt.getString("FifthAiScript");
        linkedComputer = nbt.contains("FifthLinkedComputer") ? BlockPos.fromLong(nbt.getLong("FifthLinkedComputer")) : null;
        route.clear();
        NbtList list = nbt.getList("FifthPath", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) { NbtCompound c = list.getCompound(i); route.add(new Vec3d(c.getDouble("x"), c.getDouble("y"), c.getDouble("z"))); }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "director", 3, state -> {
            String anim = getCurrentAnimation();
            if (anim == null || anim.isBlank()) return software.bernie.geckolib.core.object.PlayState.STOP;
            return state.setAndContinue(RawAnimation.begin().thenLoop(anim));
        }));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
