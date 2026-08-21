package ru.fifth.horror.block;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.cutscene.CutsceneManager;
import ru.fifth.horror.item.VhsCassetteItem;
import ru.fifth.horror.network.FifthNetworking;

/** VHS drive: 1.5s physical insert -> verify -> TV playback OR malfunction -> 1.5s eject. */
public final class CassetteDriveBlockEntity extends BlockEntity {
    public static final int INSERT_TICKS = 30;
    public static final int FAIL_TICKS = 46;
    public static final int EJECT_TICKS = 30;
    private ItemStack cassette = ItemStack.EMPTY;
    private int timer;
    private int phase; // 0 idle/loaded, 1 inserting, 2 malfunction, 3 ejecting
    private boolean reject;
    private BlockPos tvPos;

    public CassetteDriveBlockEntity(BlockPos p, BlockState s) { super(FifthMod.CASSETTE_DRIVE_BE, p, s); }
    public boolean hasCassette() { return !cassette.isEmpty(); }
    public ItemStack getCassette() { return cassette; }
    public int getTimer() { return timer; }
    public int getPhase() { return phase; }
    public BlockPos getTvPos() { return tvPos; }
    public void linkTv(BlockPos p) { tvPos = p == null ? null : p.toImmutable(); markDirty(); sync(); }
    /** Kept for old saves/UI; VHS is now always rendered on the linked world-TV surface. */
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
        if (world instanceof ServerWorld sw) sw.playSound(null, pos, SoundEvents.BLOCK_DISPENSER_DISPENSE, SoundCategory.BLOCKS, .75f, .65f);
    }

    public ItemStack ejectNow() {
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
            boolean invalid = be.reject || rec.isBlank() || CutsceneManager.load(sw.getServer(), rec) == null || !be.hasValidTv(sw);
            if (invalid) {
                be.reject = true;
                be.phase = 2;
                be.timer = FAIL_TICKS;
                sw.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.BLOCKS, .75f, .42f);
                be.sync();
                return;
            }
            be.phase = 0;
            be.startPlayback(sw, rec);
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

    private void startPlayback(ServerWorld sw, String rec) {
        if (tvPos == null || !(sw.getBlockEntity(tvPos) instanceof TelevisionBlockEntity tv)) return;
        String json = CutsceneManager.json(sw.getServer(), rec);
        tv.start(rec);
        Box box = new Box(tvPos).expand(48);
        for (ServerPlayerEntity p : sw.getEntitiesByClass(ServerPlayerEntity.class, box, x -> true)) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(json, 1_000_000);
            buf.writeVarInt(1);
            buf.writeBlockPos(tvPos);
            ServerPlayNetworking.send(p, FifthNetworking.VHS_PLAYBACK, buf);
        }
        sw.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, .7f, .7f);
        markDirty();
    }

    private void sync() { if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3); }

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
