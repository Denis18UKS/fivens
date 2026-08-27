package ru.fifth.horror.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.fifth.horror.entity.MflDeathSequenceManager;
import ru.fifth.horror.entity.MonsterForLiftEntity;

/** Replaces the old 16-tick catch pulse with the authoritative MFL death sequence. */
@Mixin(value = MonsterForLiftEntity.class, remap = false)
public abstract class MonsterForLiftDeathMixin {
    @Inject(method = "catchPlayer", at = @At("HEAD"), cancellable = true, remap = false)
    private void fiven$startDeathSequence(ServerPlayerEntity player, CallbackInfo ci) {
        MonsterForLiftEntity mfl = (MonsterForLiftEntity) (Object) this;
        if (MflDeathSequenceManager.begin(mfl, player)) ci.cancel();
    }
}
