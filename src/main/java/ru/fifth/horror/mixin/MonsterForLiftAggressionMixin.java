package ru.fifth.horror.mixin;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.fifth.horror.entity.MflAggressionManager;
import ru.fifth.horror.entity.MonsterForLiftEntity;

/** Counts direct Adventure-mode player hits without changing normal damage handling. */
@Mixin(value = MonsterForLiftEntity.class, remap = false)
public abstract class MonsterForLiftAggressionMixin {
    @Inject(method = "damage", at = @At("HEAD"), remap = false)
    private void fiven$recordHit(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        MonsterForLiftEntity mfl = (MonsterForLiftEntity) (Object) this;
        if (source != null && source.getAttacker() instanceof PlayerEntity player) {
            MflAggressionManager.recordHit(mfl, player);
        }
    }
}
