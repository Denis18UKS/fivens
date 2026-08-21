package ru.fifth.horror.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.fifth.horror.client.CutscenePlayback;

/** Blocks gameplay actions while a cutscene owns player input. */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method="handleInputEvents", at=@At("HEAD"), cancellable=true)
    private void fifth$lockCutsceneActions(CallbackInfo ci) {
        if (CutscenePlayback.lockInput()) ci.cancel();
    }
}
