package ru.fifth.horror.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

import java.util.UUID;

/** Persistent physical panel. Its local mask exists for legacy saves; a linked LiftEntity is authoritative. */
public final class LiftPanelEntity extends Entity {
    private UUID liftUuid;
    private int enabledMask = LiftEntity.DEFAULT_OPEN_FLOOR_MASK;

    public LiftPanelEntity(EntityType<? extends LiftPanelEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    public UUID getLiftUuid() { return liftUuid; }
    public void setLiftUuid(UUID value) { liftUuid = value; }

    public boolean enabled(int floor) {
        int f = Math.max(1, Math.min(9, floor));
        return (enabledMask & (1 << (f - 1))) != 0;
    }

    public void setEnabled(int floor, boolean enabled) {
        int f = Math.max(1, Math.min(9, floor));
        int bit = 1 << (f - 1);
        enabledMask = enabled ? (enabledMask | bit) : (enabledMask & ~bit);
    }

    public int getEnabledMask() { return enabledMask; }

    @Override protected void initDataTracker() {}

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        liftUuid = nbt.containsUuid("FivenLiftUuid") ? nbt.getUuid("FivenLiftUuid") : null;
        enabledMask = nbt.contains("FivenEnabledMask") ? nbt.getInt("FivenEnabledMask") : LiftEntity.DEFAULT_OPEN_FLOOR_MASK;
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (liftUuid != null) nbt.putUuid("FivenLiftUuid", liftUuid);
        nbt.putInt("FivenEnabledMask", enabledMask);
    }

    @Override
    public void tick() {
        super.tick();
        setVelocity(0, 0, 0);
    }
}
