package ru.fifth.horror.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TabButtonWidget;
import net.minecraft.client.gui.widget.ToggleButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.fifth.horror.client.gui.HorrorTheme;

/** Tabs/toggles need their own pass because they are not ordinary PressableWidget subclasses. */
@Mixin({TabButtonWidget.class, ToggleButtonWidget.class})
public abstract class SpecialWidgetThemeMixin {
    @Inject(method = "renderButton", at = @At("TAIL"))
    private void fifth$special(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!HorrorTheme.shouldThemeVanillaWidgets()) return;
        HorrorTheme.overlayVanillaButton(context, (ClickableWidget)(Object)this);
    }
}
