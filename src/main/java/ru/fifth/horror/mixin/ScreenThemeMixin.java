package ru.fifth.horror.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.fifth.horror.client.gui.HorrorTheme;

/** Fifth background for menus/loading only; in-world screens keep Minecraft's world background. */
@Mixin(Screen.class)
public abstract class ScreenThemeMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void fifth$background(DrawContext context, CallbackInfo ci) {
        if (!HorrorTheme.shouldUseFullMenuBackground()) return;
        MinecraftClient c = MinecraftClient.getInstance();
        HorrorTheme.drawScreenBackground(context, c.getWindow().getScaledWidth(), c.getWindow().getScaledHeight());
        ci.cancel();
    }

    @Inject(method = "renderBackgroundTexture", at = @At("HEAD"), cancellable = true)
    private void fifth$backgroundTexture(DrawContext context, CallbackInfo ci) {
        if (!HorrorTheme.shouldUseFullMenuBackground()) return;
        MinecraftClient c = MinecraftClient.getInstance();
        HorrorTheme.drawScreenBackground(context, c.getWindow().getScaledWidth(), c.getWindow().getScaledHeight());
        ci.cancel();
    }
}
