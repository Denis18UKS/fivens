package ru.fifth.horror.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import ru.fifth.horror.FifthMod;

public final class TelevisionBlockEntity extends BlockEntity {
    private String recording=""; private int staticTicks;
    private int quality=1; // 0 very poor .. 3 cleaner VHS
    private float noise=.65f; private boolean monochrome=true;
    public TelevisionBlockEntity(BlockPos p,BlockState s){super(FifthMod.TELEVISION_BE,p,s);}
    public void start(String id){recording=id==null?"":id;staticTicks=80;markDirty();sync();}
    public String getRecording(){return recording;} public int getStaticTicks(){return staticTicks;} public int getQuality(){return quality;} public float getNoise(){return noise;} public boolean isMonochrome(){return monochrome;}
    public void configure(int quality,float noise,boolean mono){this.quality=Math.max(0,Math.min(3,quality));this.noise=Math.max(0,Math.min(1,noise));monochrome=mono;markDirty();sync();}
    public static void tickClient(net.minecraft.world.World w,BlockPos p,BlockState s,TelevisionBlockEntity be){if(be.staticTicks>0)be.staticTicks--;}

    /**
     * updateListeners alone only guarantees a block-state update. markForUpdate is what actually asks the server
     * chunk manager to send this BlockEntity's update packet/NBT to tracking clients.
     */
    private void sync(){
        if(world==null)return;
        if(world instanceof ServerWorld sw)sw.getChunkManager().markForUpdate(pos);
        world.updateListeners(pos,getCachedState(),getCachedState(),3);
    }

    @Override protected void writeNbt(NbtCompound n){super.writeNbt(n);n.putString("Recording",recording);n.putInt("StaticTicks",staticTicks);n.putInt("Quality",quality);n.putFloat("Noise",noise);n.putBoolean("Mono",monochrome);}
    @Override public void readNbt(NbtCompound n){super.readNbt(n);recording=n.getString("Recording");staticTicks=n.getInt("StaticTicks");quality=n.contains("Quality")?n.getInt("Quality"):1;noise=n.contains("Noise")?n.getFloat("Noise"):.65f;monochrome=!n.contains("Mono")||n.getBoolean("Mono");}
    @Override public NbtCompound toInitialChunkDataNbt(){return createNbt();}@Override public Packet<ClientPlayPacketListener> toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
}
