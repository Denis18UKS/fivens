package ru.fifth.horror.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Comparator;

/** Animated lift button/panel. Clicking it also opens the nearest Fiven lift. */
public final class LiftButtonEntity extends Entity implements GeoEntity {
    private static final RawAnimation CLICK = RawAnimation.begin().thenPlay("click_on_btn");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public LiftButtonEntity(EntityType<? extends LiftButtonEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (!getWorld().isClient) {
            triggerAnim("main", "click");
            getWorld().playSound(null, getBlockPos(), SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON, SoundCategory.BLOCKS, 0.8f, 0.95f);
            Box area = getBoundingBox().expand(12.0);
            getWorld().getEntitiesByClass(LiftEntity.class, area, e -> e.isAlive()).stream()
                    .min(Comparator.comparingDouble(this::squaredDistanceTo))
                    .ifPresent(LiftEntity::playDoors);
        }
        return ActionResult.success(getWorld().isClient);
    }

    @Override public void tick() { super.tick(); setVelocity(0, 0, 0); }
    @Override protected void initDataTracker() {}
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> PlayState.STOP)
                .triggerableAnim("click", CLICK));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
