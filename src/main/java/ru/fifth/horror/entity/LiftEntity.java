package ru.fifth.horror.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/** GeckoLib lift prop + persistent 1..9 floor configuration. */
public final class LiftEntity extends Entity implements GeoEntity {
    private static final RawAnimation DOORS = RawAnimation.begin().thenPlay("animation_doors");
    /** 1..9, with 2 / 5 / 8 blocked by default. */
    public static final int DEFAULT_OPEN_FLOOR_MASK = 0x1FF & ~(1 << 1) & ~(1 << 4) & ~(1 << 7);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private String liftId = "lift";
    private int currentFloor = 1;
    private int targetFloor = 1;
    private int openFloorMask = DEFAULT_OPEN_FLOOR_MASK;
    private BlockPos stageOrigin;

    public LiftEntity(EntityType<? extends LiftEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    public void playDoors() {
        if (!getWorld().isClient) triggerAnim("main", "doors");
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (!getWorld().isClient) {
            if (player.isSneaking()) {
                player.sendMessage(Text.literal("§8[§cFiven§8] §7Лифт §f" + liftId + " §7| этаж §c" + currentFloor + " §7| сцена слоя от §f" + getStageOrigin().toShortString()), true);
            } else if (canOpenOnFloor(currentFloor)) {
                playDoors();
            } else {
                player.sendMessage(Text.literal("§8[§cFiven§8] §7Двери на этаже §c" + currentFloor + " §7заблокированы."), true);
            }
        }
        return ActionResult.success(getWorld().isClient);
    }

    public String getLiftId() {
        return liftId;
    }

    public void setLiftId(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]", "_");
        liftId = normalized.isBlank() ? "lift" : normalized;
    }

    public int getCurrentFloor() {
        return currentFloor;
    }

    public void setCurrentFloor(int floor) {
        currentFloor = Math.max(1, Math.min(9, floor));
    }

    public int getTargetFloor() {
        return targetFloor;
    }

    public void setTargetFloor(int floor) {
        targetFloor = Math.max(1, Math.min(9, floor));
    }

    public int getOpenFloorMask() {
        return openFloorMask;
    }

    public boolean canOpenOnFloor(int floor) {
        int bit = 1 << (Math.max(1, Math.min(9, floor)) - 1);
        return (openFloorMask & bit) != 0;
    }

    public void setOpenOnFloor(int floor, boolean open) {
        int bit = 1 << (Math.max(1, Math.min(9, floor)) - 1);
        openFloorMask = open ? (openFloorMask | bit) : (openFloorMask & ~bit);
    }

    public BlockPos getStageOrigin() {
        return stageOrigin == null ? getBlockPos().add(-8, -1, -8) : stageOrigin;
    }

    public void setStageOrigin(BlockPos pos) {
        stageOrigin = pos == null ? null : pos.toImmutable();
    }

    @Override
    public void tick() {
        super.tick();
        setVelocity(0, 0, 0);
    }

    @Override
    protected void initDataTracker() {}

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        setLiftId(nbt.getString("FivenLiftId"));
        currentFloor = Math.max(1, Math.min(9, nbt.getInt("FivenFloor")));
        targetFloor = Math.max(1, Math.min(9, nbt.getInt("FivenTargetFloor")));
        openFloorMask = nbt.contains("FivenOpenMask") ? nbt.getInt("FivenOpenMask") : DEFAULT_OPEN_FLOOR_MASK;
        stageOrigin = nbt.contains("FivenStageOrigin") ? BlockPos.fromLong(nbt.getLong("FivenStageOrigin")) : null;
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putString("FivenLiftId", liftId);
        nbt.putInt("FivenFloor", currentFloor);
        nbt.putInt("FivenTargetFloor", targetFloor);
        nbt.putInt("FivenOpenMask", openFloorMask);
        if (stageOrigin != null) nbt.putLong("FivenStageOrigin", stageOrigin.asLong());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> PlayState.STOP)
                .triggerableAnim("doors", DOORS));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
