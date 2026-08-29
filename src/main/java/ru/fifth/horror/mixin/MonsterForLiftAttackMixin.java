package ru.fifth.horror.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.fifth.horror.entity.MonsterForLiftEntity;

/** MFL is a scripted/non-physical target: players cannot damage or attack it. */
@Mixin(Entity.class)
public abstract class MonsterForLiftAttackMixin {
    @Inject(method = "isAttackable", at = @At("HEAD"), cancellable = true)
    private void fiven$disableMflAttack(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof MonsterForLiftEntity) cir.setReturnValue(false);
    }

    @Inject(method = "handleAttack", at = @At("HEAD"), cancellable = true)
    private void fiven$consumeMflAttack(Entity attacker, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof MonsterForLiftEntity && attacker instanceof PlayerEntity) cir.setReturnValue(true);
    }
}
