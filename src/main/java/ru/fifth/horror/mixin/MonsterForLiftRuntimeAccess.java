package ru.fifth.horror.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import ru.fifth.horror.entity.MonsterForLiftEntity;

/** Narrow server-side access used by the authored MFL runtime/test controller. */
@Mixin(value = MonsterForLiftEntity.class, remap = false)
public interface MonsterForLiftRuntimeAccess {
    @Accessor("manualAnimationTicks")
    int fiven$getManualAnimationTicks();

    @Accessor("manualAnimationTicks")
    void fiven$setManualAnimationTicks(int ticks);

    @Invoker("setCurrentAnimation")
    void fiven$setCurrentAnimation(String animation);

    @Invoker("canSeeTarget")
    boolean fiven$canSeeTarget(ServerPlayerEntity player);
}
