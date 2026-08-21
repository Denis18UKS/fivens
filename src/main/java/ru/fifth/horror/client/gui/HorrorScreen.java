package ru.fifth.horror.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.fifth.horror.FifthMod;

import java.util.ArrayList;
import java.util.List;

/** Shared visual language for every Fifth editor screen. */
public abstract class HorrorScreen extends Screen {
    protected static final Identifier MENU_BG = FifthMod.id("textures/gui/title.png");
    private static final int BG_W = 1672;
    private static final int BG_H = 941;
    private final List<ClickableWidget> framedWidgets = new ArrayList<>();

    protected HorrorScreen(Text title) { super(title); }

    @Override public boolean shouldPause() { return false; }

    protected int contentWidth(int preferred) {
        return Math.max(220, Math.min(preferred, width - 28));
    }

    protected int contentLeft(int preferred) {
        int w = contentWidth(preferred);
        return (width - w) / 2;
    }

    protected void beginHorrorInit() { framedWidgets.clear(); }

    protected int safeTop() { return Math.max(34, Math.min(48, height / 8)); }
    protected int safeBottom() { return Math.max(28, Math.min(42, height / 8)); }

    protected <T extends ClickableWidget> T frameWidget(T widget) {
        framedWidgets.add(widget);
        return widget;
    }

    protected TextFieldWidget horrorField(int x, int y, int w, int h, String value, int max) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, h, Text.empty());
        f.setMaxLength(max);
        f.setText(value == null ? "" : value);
        f.setDrawsBackground(false);
        f.setEditableColor(0xE8DDD5);
        f.setUneditableColor(0x786F6A);
        frameWidget(f);
        addDrawableChild(f);
        return f;
    }

    protected void horrorBackground(DrawContext c) {
        // Reuse the main-menu art everywhere, but bury it under a dirty, low-contrast horror veil.
        c.drawTexture(MENU_BG, 0, 0, width, height, 0, 0, BG_W, BG_H, BG_W, BG_H);
        c.fillGradient(0, 0, width, height, 0xA40A0B0E, 0xD40A0809);
        c.fill(0, 0, width, 2, 0xD071232B);
        c.fill(0, 30, width, 31, 0x704E1C22);
        for (int i = 0; i < 13; i++) {
            int y = (i * 47 + (int)(System.currentTimeMillis() / 95L)) % Math.max(1, height);
            c.fill(0, y, width, y + 1, 0x0BFFFFFF);
        }
        c.drawTextWithShadow(textRenderer, title, Math.max(10, (width - textRenderer.getWidth(title)) / 2), 11, 0xFFE5D7CE);
    }

    protected void panel(DrawContext c, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        c.fill(x, y, x + w, y + h, 0xC00A0C0F);
        c.fill(x, y, x + w, y + 1, 0xC06F4245);
        c.fill(x, y + h - 1, x + w, y + h, 0xA02A1D20);
        c.fill(x, y, x + 1, y + h, 0x704C3033);
        c.fill(x + w - 1, y, x + w, y + h, 0x704C3033);
        // tiny asymmetrical scratches keep panels from looking like vanilla widgets
        if (w > 80 && h > 20) {
            c.fill(x + 9, y + 5, x + Math.min(w - 8, 41), y + 6, 0x244D3537);
            c.fill(x + w - 48, y + h - 6, x + w - 12, y + h - 5, 0x173F2A2C);
        }
    }

    protected void sectionLabel(DrawContext c, String text, int x, int y) {
        c.drawTextWithShadow(textRenderer, text, x, y, 0xFFAF9E95);
        int lineX = x + textRenderer.getWidth(text) + 8;
        if (lineX < width - 14) c.fill(lineX, y + 4, width - 14, y + 5, 0x3F7A4549);
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        // Draw our field/editor frames before vanilla text glyphs so even text inputs belong to the same theme.
        for (ClickableWidget w : framedWidgets) {
            int x = w.getX(), y = w.getY(), ww = w.getWidth(), hh = w.getHeight();
            c.fill(x - 1, y - 1, x + ww + 1, y + hh + 1, 0xB0613A3D);
            c.fill(x, y, x + ww, y + hh, 0xE30A0C0F);
            if (w.isFocused()) {
                c.fill(x, y, x + ww, y + 1, 0xFFE0A7A8);
                c.fill(x, y, x + 2, y + hh, 0xFF8D343B);
            }
        }
        super.render(c, mouseX, mouseY, delta);
    }
}
