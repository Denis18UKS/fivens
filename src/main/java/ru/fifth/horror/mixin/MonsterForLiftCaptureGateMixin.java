package ru.fifth.horror.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.fifth.horror.entity.MonsterForLiftEntity;

/** Prevents the legacy catchPlayer body from firing outside Adventure mode. */
@Mixin(value = MonsterForLiftEntity.class, remap = false)
public abstract class MonsterForLiftCaptureGateMixin {
    @Inject(method = "catchPlayer", at = @At("HEAD"), cancellable = true, remap = false)
    private void fiven$adventureOnly(ServerPlayerEntity player, CallbackInfo ci) {
        if (player == null || player.interactionManager == null || player.interactionManager.getGameMode() != GameMode.ADVENTURE) {
            ci.cancel();
        }
    }
}
