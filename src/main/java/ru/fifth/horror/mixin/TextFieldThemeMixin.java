package ru.fifth.horror.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.fifth.horror.client.gui.HorrorTheme;

@Mixin(TextFieldWidget.class)
public abstract class TextFieldThemeMixin {
    @Inject(method = "renderButton", at = @At("TAIL"))
    private void fifth$fieldFrame(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!HorrorTheme.shouldThemeVanillaWidgets()) return;
        HorrorTheme.overlayTextField(context, (TextFieldWidget)(Object)this);
    }
}
