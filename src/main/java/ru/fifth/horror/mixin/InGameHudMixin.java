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

/**
 * Keeps ordinary cutscene HUD hiding, but gives lift travel its own final render pass.
 * The tail injection still runs when vanilla HUD elements are hidden with F1.
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void fifth$hide(DrawContext context, float tickDelta, CallbackInfo ci) {
        if (LiftTravelHudPolicy.cancelVanillaHud(CutscenePlayback.hideHud(), LiftTravelOverlay.active())) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void fifth$renderLiftTravelLast(DrawContext context, float tickDelta, CallbackInfo ci) {
        LiftTravelOverlay.render(context);
    }
}
