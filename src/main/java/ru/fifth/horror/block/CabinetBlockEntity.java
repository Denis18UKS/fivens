package ru.fifth.horror.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import ru.fifth.horror.cabinet.CabinetFeature;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public final class CabinetBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation DOOR_SEQUENCE = RawAnimation.begin().thenPlay("animation_doors_pcase");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID occupant;
    private int soundCloseTicks;

    public CabinetBlockEntity(BlockPos pos, BlockState state) { super(CabinetFeature.CABINET_BE, pos, state); }

    @Nullable public UUID getOccupant() { return occupant; }
    public boolean isOccupied() { return occupant != null; }

    public void setOccupant(@Nullable UUID uuid) {
        occupant = uuid;
        markDirtyAndSync();
    }

    public void playDoorSequence(ServerPlayerEntity source) {
        triggerAnim("door", "open_close");
        soundCloseTicks = 225;
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.playSound(null, pos, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.9f, 0.85f);
        }
    }

    public static void tickServer(net.minecraft.world.World world, BlockPos pos, BlockState state, CabinetBlockEntity cabinet) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        if (cabinet.occupant != null) CabinetFeature.registerLoaded(cabinet);
        if (cabinet.soundCloseTicks > 0 && --cabinet.soundCloseTicks == 0) {
            serverWorld.playSound(null, pos, SoundEvents.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 0.9f, 0.9f);
        }
        if (cabinet.occupant != null) {
            ServerPlayerEntity player = serverWorld.getServer().getPlayerManager().getPlayer(cabinet.occupant);
            if (player == null || !player.isAlive() || player.getServerWorld() != serverWorld) {
                CabinetFeature.forceRelease(cabinet, player);
            } else {
                CabinetFeature.keepPlayerInside(cabinet, player);
            }
        }
    }

    private void markDirtyAndSync() {
        markDirty();
        if (world instanceof ServerWorld serverWorld) serverWorld.getChunkManager().markForUpdate(pos);
    }

    @Override public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (occupant != null) nbt.putUuid("FivenCabinetOccupant", occupant);
        nbt.putInt("FivenCabinetCloseSound", soundCloseTicks);
    }

    @Override public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        occupant = nbt.containsUuid("FivenCabinetOccupant") ? nbt.getUuid("FivenCabinetOccupant") : null;
        soundCloseTicks = Math.max(0, nbt.getInt("FivenCabinetCloseSound"));
    }

    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override public NbtCompound toInitialChunkDataNbt() { return createNbt(); }

    @Override public void markRemoved() {
        CabinetFeature.unregister(this);
        super.markRemoved();
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "door", 0, state -> PlayState.STOP)
                .triggerableAnim("open_close", DOOR_SEQUENCE));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
