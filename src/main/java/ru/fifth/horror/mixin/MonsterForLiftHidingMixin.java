package ru.fifth.horror.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.fifth.horror.cabinet.CabinetFeature;
import ru.fifth.horror.entity.MflHidingManager;
import ru.fifth.horror.entity.MonsterForLiftEntity;

/** Makes authored hiding zones and occupied player cabinets invisible to MFL target acquisition. */
@Mixin(MonsterForLiftEntity.class)
public abstract class MonsterForLiftHidingMixin {
    @Inject(method = "isValidTarget", at = @At("HEAD"), cancellable = true, remap = false)
    private void fiven$ignoreHiddenPlayers(ServerPlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        if (MflHidingManager.isHidden(player) || CabinetFeature.isHidden(player)) cir.setReturnValue(false);
    }
}
