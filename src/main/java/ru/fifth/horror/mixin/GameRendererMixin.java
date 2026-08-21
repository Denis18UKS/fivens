package ru.fifth.horror.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.fifth.horror.client.CutscenePlayback;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method="getFov",at=@At("HEAD"),cancellable=true)
    private void fifth$fov(Camera camera,float tickDelta,boolean changingFov,CallbackInfoReturnable<Double> cir){CutscenePlayback.Sample s=CutscenePlayback.sample(tickDelta);if(s!=null)cir.setReturnValue(s.fov());}
}
