package ru.fifth.horror.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.item.VhsCassetteItem;
import ru.fifth.horror.vhs.VhsRecordingFeature;
import ru.fifth.horror.vhs.VhsRecordingStore;

/** VHS drive: insert -> validate immutable PNG frame recording -> arm linked TV for manual browsing. */
public final class CassetteDriveBlockEntity extends BlockEntity {
    public static final int INSERT_TICKS = 30;
    public static final int FAIL_TICKS = 46;
    public static final int EJECT_TICKS = 30;
    private ItemStack cassette = ItemStack.EMPTY;
    private int timer;
    private int phase;
    private boolean reject;
    private BlockPos tvPos;

    public CassetteDriveBlockEntity(BlockPos p, BlockState s) { super(FifthMod.CASSETTE_DRIVE_BE, p, s); }
    public boolean hasCassette() { return !cassette.isEmpty(); }
    public ItemStack getCassette() { return cassette; }
    public int getTimer() { return timer; }
    public int getPhase() { return phase; }
    public BlockPos getTvPos() { return tvPos; }
    public void linkTv(BlockPos p) { tvPos = p == null ? null : p.toImmutable(); markDirty(); sync(); }
    public void setPlaybackMode(int ignored) { markDirty(); sync(); }

    public void insert(ItemStack stack) {
        if (hasCassette()) return;
        cassette = stack.copy();
        cassette.setCount(1);
        timer = INSERT_TICKS;
        phase = 1;
        reject = VhsCassetteItem.recording(cassette).isBlank();
        markDirty();
        sync();
        if (world instanceof ServerWorld sw) {
            sw.playSound(null, pos, SoundEvents.BLOCK_DISPENSER_DISPENSE, SoundCategory.BLOCKS, .75f, .65f);
        }
    }

    public ItemStack ejectNow() {
        if (world instanceof ServerWorld sw && tvPos != null && sw.getBlockEntity(tvPos) instanceof TelevisionBlockEntity tv) {
            tv.stop();
        }
        ItemStack out = cassette;
        cassette = ItemStack.EMPTY;
        timer = 0;
        phase = 0;
        reject = false;
        markDirty();
        sync();
        return out;
    }

    public static void tickClient(World world, BlockPos pos, BlockState state, CassetteDriveBlockEntity be) {
        if (be.timer > 0) be.timer--;
    }

    public static void tick(World world, BlockPos pos, BlockState state, CassetteDriveBlockEntity be) {
        if (!(world instanceof ServerWorld sw) || be.phase == 0) return;
        if (be.timer > 0) { be.timer--; return; }

        if (be.phase == 1) {
            String rec = VhsCassetteItem.recording(be.cassette);
            boolean invalid = be.reject || rec.isBlank()
                    || !VhsRecordingFeature.store(sw.getServer()).isComplete(rec)
                    || !be.hasValidTv(sw);
            if (invalid) {
                be.reject = true;
                be.phase = 2;
                be.timer = FAIL_TICKS;
                sw.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.BLOCKS, .75f, .42f);
                be.sync();
                return;
            }
            be.phase = 0;
            be.armTelevision(sw, rec);
            be.sync();
            return;
        }

        if (be.phase == 2) {
            be.phase = 3;
            be.timer = EJECT_TICKS;
            sw.playSound(null, pos, SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.BLOCKS, .9f, .55f);
            be.sync();
            return;
        }

        if (be.phase == 3) {
            ItemStack out = be.ejectNow();
            if (!out.isEmpty()) sw.spawnEntity(new ItemEntity(sw, pos.getX() + .5, pos.getY() + .7, pos.getZ() + .45, out));
        }
    }

    private boolean hasValidTv(ServerWorld sw) {
        return tvPos != null && sw.getBlockEntity(tvPos) instanceof TelevisionBlockEntity;
    }

    private void armTelevision(ServerWorld sw, String rec) {
        if (tvPos == null || !(sw.getBlockEntity(tvPos) instanceof TelevisionBlockEntity tv)) return;
        VhsRecordingStore.Metadata metadata = VhsRecordingFeature.store(sw.getServer()).metadata(rec);
        if (metadata == null) {
            reject = true;
            phase = 2;
            timer = FAIL_TICKS;
            sync();
            return;
        }

        tv.start(rec);
        sw.playSound(null, tvPos, SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS, .72f, .62f);
        sw.playSound(null, tvPos, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.BLOCKS, .32f, .48f);
        markDirty();
        sync();
    }

    private void sync() {
        if (world == null) return;
        if (world instanceof ServerWorld sw) sw.getChunkManager().markForUpdate(pos);
        world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    @Override public NbtCompound toInitialChunkDataNbt() { return createNbt(); }
    @Override public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> toUpdatePacket() { return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this); }
    @Override protected void writeNbt(NbtCompound n) {
        super.writeNbt(n);
        if (!cassette.isEmpty()) n.put("Cassette", cassette.writeNbt(new NbtCompound()));
        n.putInt("Timer", timer);
        n.putInt("Phase", phase);
        n.putBoolean("Reject", reject);
        if (tvPos != null) n.putLong("TvPos", tvPos.asLong());
        n.putInt("PlaybackMode", 1);
    }
    @Override public void readNbt(NbtCompound n) {
        super.readNbt(n);
        cassette = n.contains("Cassette") ? ItemStack.fromNbt(n.getCompound("Cassette")) : ItemStack.EMPTY;
        timer = n.getInt("Timer");
        phase = n.getInt("Phase");
        reject = n.getBoolean("Reject");
        tvPos = n.contains("TvPos") ? BlockPos.fromLong(n.getLong("TvPos")) : null;
    }
}
