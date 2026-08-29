package ru.fifth.horror.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.fifth.horror.client.CutscenePlayback;
import ru.fifth.horror.client.LiftTravelOverlay;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method="getFov",at=@At("HEAD"),cancellable=true)
    private void fifth$fov(Camera camera,float tickDelta,boolean changingFov,CallbackInfoReturnable<Double> cir){
        CutscenePlayback.Sample s=CutscenePlayback.sample(tickDelta);
        if(s!=null)cir.setReturnValue(s.fov());
    }

    /**
     * F1 skips InGameHud entirely in vanilla. Rendering at GameRenderer RETURN keeps the lift
     * transition visible anyway and guarantees it is the last full-screen pass over world/HUD/screens.
     */
    @Inject(method="render",at=@At("RETURN"))
    private void fifth$renderLiftAfterHudGate(float tickDelta,long startTime,boolean tick,CallbackInfo ci){
        if(!LiftTravelOverlay.active())return;
        MinecraftClient client=MinecraftClient.getInstance();
        DrawContext context=new DrawContext(client,client.getBufferBuilders().getEntityVertexConsumers());
        LiftTravelOverlay.render(context);
        context.draw();
    }
}
