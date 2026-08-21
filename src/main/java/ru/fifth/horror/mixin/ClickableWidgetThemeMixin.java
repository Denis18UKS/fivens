package ru.fifth.horror.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TabButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ToggleButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.fifth.horror.client.gui.HorrorButton;
import ru.fifth.horror.client.gui.HorrorTheme;

/** One and only one overlay pass for ordinary vanilla pressables. */
@Mixin(ClickableWidget.class)
public abstract class ClickableWidgetThemeMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void fifth$skinEveryPressable(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!HorrorTheme.shouldThemeVanillaWidgets()) return;
        ClickableWidget self = (ClickableWidget)(Object)this;
        if (!(self instanceof PressableWidget) || self instanceof HorrorButton) return;
        // These are styled by dedicated mixins. Skipping them prevents the doubled/tripled text seen at GUI scale 3/4.
        if (self instanceof SliderWidget || self instanceof TextFieldWidget || self instanceof TabButtonWidget || self instanceof ToggleButtonWidget) return;
        HorrorTheme.overlayVanillaButton(context, self);
    }
}
