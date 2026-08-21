package ru.fifth.horror.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.lift.LiftManager;

/** Persistent link between the physical floor panel block and a physical lift block. */
public final class LiftPanelBlockEntity extends BlockEntity {
    private String liftWorld = "";
    private BlockPos liftPos;
    private int enabledMask = LiftBlockEntity.DEFAULT_OPEN_FLOOR_MASK;

    public LiftPanelBlockEntity(BlockPos pos, BlockState state) { super(FifthMod.LIFT_PANEL_BE, pos, state); }

    public void setLiftReference(String worldId, BlockPos pos) {
        this.liftWorld = worldId == null ? "" : worldId;
        this.liftPos = pos == null ? null : pos.toImmutable();
        dirtyAndSync();
    }

    public String getLiftWorld() { return liftWorld; }
    public BlockPos getLiftPos() { return liftPos; }

    @Nullable
    public LiftBlockEntity resolveLift(MinecraftServer server) {
        return liftPos == null ? null : LiftManager.findLift(server, liftWorld, liftPos);
    }

    public int getEnabledMask() { return enabledMask; }
    public void setEnabledMask(int mask) { enabledMask = mask & 0x1FF; dirtyAndSync(); }
    public void setEnabled(int floor, boolean enabled) {
        int f = Math.max(1, Math.min(9, floor));
        int bit = 1 << (f - 1);
        enabledMask = enabled ? enabledMask | bit : enabledMask & ~bit;
        dirtyAndSync();
    }

    private void dirtyAndSync() {
        markDirty();
        if (world != null && !world.isClient) world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putString("FivenLiftWorld", liftWorld);
        if (liftPos != null) nbt.putLong("FivenLiftPos", liftPos.asLong());
        nbt.putInt("FivenEnabledMask", enabledMask);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        liftWorld = nbt.getString("FivenLiftWorld");
        liftPos = nbt.contains("FivenLiftPos") ? BlockPos.fromLong(nbt.getLong("FivenLiftPos")) : null;
        enabledMask = nbt.contains("FivenEnabledMask") ? nbt.getInt("FivenEnabledMask") & 0x1FF : LiftBlockEntity.DEFAULT_OPEN_FLOOR_MASK;
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }

    @Override
    public NbtCompound toInitialChunkDataNbt() { return createNbt(); }
}
