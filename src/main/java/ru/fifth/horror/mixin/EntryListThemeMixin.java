package ru.fifth.horror.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EntryListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.fifth.horror.client.gui.HorrorTheme;

@Mixin(EntryListWidget.class)
public abstract class EntryListThemeMixin {
    @Shadow protected int left;
    @Shadow protected int right;
    @Shadow protected int top;
    @Shadow protected int bottom;

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void fifth$listBackground(DrawContext context, CallbackInfo ci) {
        if (!HorrorTheme.shouldThemeVanillaWidgets()) return;
        HorrorTheme.drawListBackground(context, left, top, right, bottom);
        ci.cancel();
    }

    @Inject(method = "renderEntry", at = @At("HEAD"))
    private void fifth$entryCard(DrawContext context, int mouseX, int mouseY, float delta, int index, int x, int y, int entryWidth, int entryHeight, CallbackInfo ci) {
        if (!HorrorTheme.shouldThemeVanillaWidgets()) return;
        boolean hot = mouseX >= x && mouseX < x + entryWidth && mouseY >= y && mouseY < y + entryHeight;
        context.fill(x + 1, y, x + entryWidth - 1, y + entryHeight - 1, hot ? 0x8C2D181D : 0x68090B0E);
        context.fill(x + 1, y, x + 3, y + entryHeight - 1, hot ? 0xB9B64A56 : 0x725A3339);
        context.fill(x + 5, y + entryHeight - 2, x + entryWidth - 6, y + entryHeight - 1, 0x303E292D);
    }
}
