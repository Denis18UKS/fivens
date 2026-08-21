package ru.fifth.horror.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.lift.LiftManager;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Locale;

/** Persistent state and GeckoLib animation controller for the physical lift block. */
public final class LiftBlockEntity extends BlockEntity implements GeoBlockEntity {
    public static final int DEFAULT_OPEN_FLOOR_MASK = 0x1FF & ~(1 << 1) & ~(1 << 4) & ~(1 << 7); // 2,5,8: doors blocked
    private static final RawAnimation CLOSED = RawAnimation.begin().thenLoop("doors_closed");
    private static final RawAnimation OPEN = RawAnimation.begin().thenLoop("doors_open");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private String liftId = "lift";
    private int currentFloor = 1;
    private int targetFloor = 1;
    private int openFloorMask = DEFAULT_OPEN_FLOOR_MASK;
    private BlockPos stageOrigin;
    private int autoCloseTicks;

    public LiftBlockEntity(BlockPos pos, BlockState state) {
        super(FifthMod.LIFT_BE, pos, state);
    }

    public static void tickServer(World world, BlockPos pos, BlockState state, LiftBlockEntity lift) {
        if (world.isClient) return;
        LiftManager.register(lift);
        if (lift.autoCloseTicks > 0 && --lift.autoCloseTicks == 0) lift.closeDoors();
    }

    public boolean isDoorOpen() {
        return getCachedState().contains(LiftBlock.OPEN) && getCachedState().get(LiftBlock.OPEN);
    }

    public void openDoors(int holdTicks) {
        if (!(world instanceof ServerWorld sw)) return;
        if (!getCachedState().get(LiftBlock.OPEN)) {
            sw.setBlockState(pos, getCachedState().with(LiftBlock.OPEN, true), Block.NOTIFY_ALL);
            sw.playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_IRON_DOOR_OPEN, net.minecraft.sound.SoundCategory.BLOCKS, 0.85f, 0.82f);
        }
        autoCloseTicks = Math.max(0, holdTicks);
        dirtyAndSync();
    }

    public void closeDoors() {
        if (!(world instanceof ServerWorld sw)) return;
        if (getCachedState().get(LiftBlock.OPEN)) {
            sw.setBlockState(pos, getCachedState().with(LiftBlock.OPEN, false), Block.NOTIFY_ALL);
            sw.playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_IRON_DOOR_CLOSE, net.minecraft.sound.SoundCategory.BLOCKS, 0.85f, 0.82f);
        }
        autoCloseTicks = 0;
        dirtyAndSync();
    }

    public String getLiftId() { return liftId; }
    public void setLiftId(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        liftId = normalized.isBlank() ? "lift" : normalized;
        dirtyAndSync();
    }
    public int getCurrentFloor() { return currentFloor; }
    public void setCurrentFloor(int floor) { currentFloor = clampFloor(floor); dirtyAndSync(); }
    public int getTargetFloor() { return targetFloor; }
    public void setTargetFloor(int floor) { targetFloor = clampFloor(floor); dirtyAndSync(); }
    public int getOpenFloorMask() { return openFloorMask; }
    public void setOpenFloorMask(int mask) { openFloorMask = mask & 0x1FF; dirtyAndSync(); }
    public boolean canOpenOnFloor(int floor) { return (openFloorMask & (1 << (clampFloor(floor) - 1))) != 0; }
    public void setOpenOnFloor(int floor, boolean open) {
        int bit = 1 << (clampFloor(floor) - 1);
        openFloorMask = open ? openFloorMask | bit : openFloorMask & ~bit;
        dirtyAndSync();
    }
    public BlockPos getStageOrigin() { return stageOrigin == null ? pos.add(-8, -1, -8) : stageOrigin; }
    public void setStageOrigin(BlockPos origin) { stageOrigin = origin == null ? null : origin.toImmutable(); dirtyAndSync(); }

    private static int clampFloor(int floor) { return Math.max(1, Math.min(9, floor)); }

    private void dirtyAndSync() {
        markDirty();
        if (world != null && !world.isClient) world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putString("FivenLiftId", liftId);
        nbt.putInt("FivenFloor", currentFloor);
        nbt.putInt("FivenTargetFloor", targetFloor);
        nbt.putInt("FivenOpenMask", openFloorMask);
        nbt.putInt("FivenAutoClose", autoCloseTicks);
        if (stageOrigin != null) nbt.putLong("FivenStageOrigin", stageOrigin.asLong());
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        setLiftIdWithoutSync(nbt.getString("FivenLiftId"));
        currentFloor = clampFloor(nbt.contains("FivenFloor") ? nbt.getInt("FivenFloor") : 1);
        targetFloor = clampFloor(nbt.contains("FivenTargetFloor") ? nbt.getInt("FivenTargetFloor") : currentFloor);
        openFloorMask = nbt.contains("FivenOpenMask") ? nbt.getInt("FivenOpenMask") & 0x1FF : DEFAULT_OPEN_FLOOR_MASK;
        autoCloseTicks = Math.max(0, nbt.getInt("FivenAutoClose"));
        stageOrigin = nbt.contains("FivenStageOrigin") ? BlockPos.fromLong(nbt.getLong("FivenStageOrigin")) : null;
    }

    private void setLiftIdWithoutSync(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        liftId = normalized.isBlank() ? "lift" : normalized;
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }

    @Override
    public NbtCompound toInitialChunkDataNbt() { return createNbt(); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "doors", 2, state -> state.setAndContinue(isDoorOpen() ? OPEN : CLOSED)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
