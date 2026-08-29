package ru.fifth.horror.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.fifth.horror.client.CutscenePlayback;
import ru.fifth.horror.client.LiftTravelHudPolicy;
import ru.fifth.horror.client.LiftTravelOverlay;
import ru.fifth.horror.client.ScreamerOverlay;

/** Keeps authored lift/cutscene suppression while forcing the screamer above F1. */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void fifth$hide(DrawContext context, float tickDelta, CallbackInfo ci) {
        if (ScreamerOverlay.active()) {
            ScreamerOverlay.render(context);
            ci.cancel();
            return;
        }
        if (LiftTravelHudPolicy.cancelVanillaHud(CutscenePlayback.hideHud(), LiftTravelOverlay.active())) {
            ci.cancel();
        }
    }
}
