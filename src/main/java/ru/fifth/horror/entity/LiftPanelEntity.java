package ru.fifth.horror.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.UUID;

/** 2D lift panel. The linked lift is the source of truth after panel state has been migrated once. */
public final class LiftPanelEntity extends Entity {
    private UUID liftUuid;
    private int enabledMask = LiftEntity.DEFAULT_ENABLED_MASK;
    private boolean syncPanelMaskToLift;

    public LiftPanelEntity(EntityType<? extends LiftPanelEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    public UUID getLiftUuid(){return liftUuid;}

    public void setLiftUuid(UUID uuid){
        liftUuid=uuid;
        syncPanelMaskToLift=true;
        applyPanelMaskToLift();
    }

    public boolean enabled(int floor){
        if(floor<1||floor>9)return false;
        LiftEntity lift=linkedLift();
        if(lift!=null&&!syncPanelMaskToLift)return lift.canOpenOnFloor(floor);
        return (enabledMask&(1<<(floor-1)))!=0;
    }

    public void setEnabled(int floor,boolean enabled){
        if(floor<1||floor>9)return;
        int bit=1<<(floor-1);
        enabledMask=enabled?(enabledMask|bit):(enabledMask&~bit);
        enabledMask&=0x1FF;
        syncPanelMaskToLift=false;
        LiftEntity lift=linkedLift();
        if(lift!=null)lift.setOpenOnFloor(floor,enabled);
    }

    public int getEnabledMask(){
        LiftEntity lift=linkedLift();
        return lift==null||syncPanelMaskToLift?(enabledMask&0x1FF):lift.getOpenFloorMask();
    }

    private LiftEntity linkedLift(){
        if(liftUuid==null||!(getWorld() instanceof ServerWorld sw))return null;
        return sw.getEntity(liftUuid) instanceof LiftEntity lift?lift:null;
    }

    private void applyPanelMaskToLift(){
        LiftEntity lift=linkedLift();
        if(lift==null)return;
        for(int floor=1;floor<=9;floor++)lift.setOpenOnFloor(floor,(enabledMask&(1<<(floor-1)))!=0);
        syncPanelMaskToLift=false;
    }

    @Override protected void initDataTracker(){}

    @Override protected void readCustomDataFromNbt(NbtCompound nbt){
        liftUuid=nbt.containsUuid("FivenLiftUuid")?nbt.getUuid("FivenLiftUuid"):null;
        enabledMask=nbt.contains("FivenEnabledMask")?(nbt.getInt("FivenEnabledMask")&0x1FF):LiftEntity.DEFAULT_ENABLED_MASK;
        // Existing worlds may have an old lift mask (all 9 floors) and a panel with burned floors.
        syncPanelMaskToLift=liftUuid!=null;
    }

    @Override protected void writeCustomDataToNbt(NbtCompound nbt){
        if(liftUuid!=null)nbt.putUuid("FivenLiftUuid",liftUuid);
        nbt.putInt("FivenEnabledMask",getEnabledMask());
    }

    @Override public void tick(){
        super.tick();
        setVelocity(0,0,0);
        if(!getWorld().isClient&&syncPanelMaskToLift)applyPanelMaskToLift();
    }
}
