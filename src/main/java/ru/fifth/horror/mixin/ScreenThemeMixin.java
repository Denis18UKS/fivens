package ru.fifth.horror.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ru.fifth.horror.client.gui.HorrorTheme;


/**
 * Заменяет стандартный фон Minecraft на фон "Пятого"
 * только там, где HorrorTheme разрешает его использовать.
 *
 * В игровых GUI вроде инвентаря/чата фон мира не должен
 * перекрываться нашим fullscreen-фоном.
 */
@Mixin(Screen.class)
public abstract class ScreenThemeMixin {

    /**
     * Screen#renderBackground(DrawContext)
     */
    @Inject(
            method = "renderBackground(Lnet/minecraft/client/gui/DrawContext;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fifth$background(
            DrawContext context,
            CallbackInfo ci
    ) {

        if (!HorrorTheme.shouldUseFullMenuBackground()) {
            return;
        }

        MinecraftClient client =
                MinecraftClient.getInstance();

        HorrorTheme.drawScreenBackground(
                context,
                client.getWindow().getScaledWidth(),
                client.getWindow().getScaledHeight()
        );

        /*
         * Не даём vanilla Screen после нашего фона
         * нарисовать свой стандартный dirt/options background.
         */
        ci.cancel();
    }


    /**
     * Screen#renderBackgroundTexture(DrawContext)
     */
    @Inject(
            method = "renderBackgroundTexture(Lnet/minecraft/client/gui/DrawContext;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fifth$backgroundTexture(
            DrawContext context,
            CallbackInfo ci
    ) {

        if (!HorrorTheme.shouldUseFullMenuBackground()) {
            return;
        }

        MinecraftClient client =
                MinecraftClient.getInstance();

        HorrorTheme.drawScreenBackground(
                context,
                client.getWindow().getScaledWidth(),
                client.getWindow().getScaledHeight()
        );

        ci.cancel();
    }
}