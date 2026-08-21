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
    public static final int DEFAULT_ENABLED_MASK = 0x1FF & ~(1 << (2 - 1)) & ~(1 << (5 - 1)) & ~(1 << (8 - 1));
    private static final RawAnimation DOORS = RawAnimation.begin().thenPlay("animation_doors");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private String liftId = "lift";
    private int currentFloor = 1;
    private int targetFloor = 1;
    /** Enabled floors. A cleared bit means the panel button is burned and travel is forbidden. */
    private int openFloorMask = DEFAULT_ENABLED_MASK;
    private BlockPos stageOrigin;

    public LiftEntity(EntityType<? extends LiftEntity> type, World world) { super(type, world); setNoGravity(true); }
    public void playDoors() { if (!getWorld().isClient) triggerAnim("main", "doors"); }

    @Override public ActionResult interact(PlayerEntity player, Hand hand) {
        if (!getWorld().isClient) {
            if (player.isSneaking()) player.sendMessage(Text.literal("§8[§cFiven§8] §7Лифт §f"+liftId+" §7| этаж §c"+currentFloor+" §7| сцена слоя от §f"+getStageOrigin().toShortString()), true);
            else playDoors();
        }
        return ActionResult.success(getWorld().isClient);
    }

    public String getLiftId(){return liftId;}
    public void setLiftId(String s){liftId=s==null||s.isBlank()?"lift":s.trim();}
    public int getCurrentFloor(){return currentFloor;}
    public void setCurrentFloor(int f){currentFloor=Math.max(1,Math.min(9,f));}
    public int getTargetFloor(){return targetFloor;}
    public void setTargetFloor(int f){targetFloor=Math.max(1,Math.min(9,f));}
    public int getOpenFloorMask(){return openFloorMask & 0x1FF;}
    public boolean canOpenOnFloor(int floor){if(floor<1||floor>9)return false;int bit=1<<(floor-1);return (openFloorMask&bit)!=0;}
    public void setOpenOnFloor(int floor,boolean open){if(floor<1||floor>9)return;int bit=1<<(floor-1);openFloorMask=open?(openFloorMask|bit):(openFloorMask&~bit);openFloorMask&=0x1FF;}
    public BlockPos getStageOrigin(){return stageOrigin==null?getBlockPos().add(-8,-1,-8):stageOrigin;}
    public void setStageOrigin(BlockPos p){stageOrigin=p==null?null:p.toImmutable();}

    @Override public void tick(){super.tick();setVelocity(0,0,0);}
    @Override protected void initDataTracker(){}
    @Override protected void readCustomDataFromNbt(NbtCompound nbt){
        liftId=nbt.getString("FivenLiftId"); if(liftId.isBlank())liftId="lift";
        currentFloor=Math.max(1,Math.min(9,nbt.getInt("FivenFloor"))); targetFloor=Math.max(1,Math.min(9,nbt.getInt("FivenTargetFloor")));
        openFloorMask=nbt.contains("FivenOpenMask")?(nbt.getInt("FivenOpenMask")&0x1FF):DEFAULT_ENABLED_MASK;
        stageOrigin=nbt.contains("FivenStageOrigin")?BlockPos.fromLong(nbt.getLong("FivenStageOrigin")):null;
    }
    @Override protected void writeCustomDataToNbt(NbtCompound nbt){
        nbt.putString("FivenLiftId",liftId);nbt.putInt("FivenFloor",currentFloor);nbt.putInt("FivenTargetFloor",targetFloor);nbt.putInt("FivenOpenMask",openFloorMask&0x1FF);
        if(stageOrigin!=null)nbt.putLong("FivenStageOrigin",stageOrigin.asLong());
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){controllers.add(new AnimationController<>(this,"main",0,state-> PlayState.STOP).triggerableAnim("doors",DOORS));}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
}
