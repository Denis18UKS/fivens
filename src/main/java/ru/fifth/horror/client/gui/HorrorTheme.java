package ru.fifth.horror.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.fifth.horror.FifthMod;

/** Shared visual skin. Vanilla gameplay screens are deliberately left alone. */
public final class HorrorTheme {
    public static final Identifier BACKGROUND = FifthMod.id("textures/gui/title.png");
    public static final int BG_W = 1672;
    public static final int BG_H = 941;

    private HorrorTheme() {}

    /**
     * The photographic Fifth background belongs to menu/loading screens only.
     * Once a world exists, vanilla must keep rendering the actual world behind inventory/chat/etc.
     */
    public static boolean shouldUseFullMenuBackground() {
        MinecraftClient c = MinecraftClient.getInstance();
        return c.world == null;
    }

    /**
     * Theme vanilla menu widgets, but never repaint gameplay/container UI.
     * Fifth's own screens already draw themselves and therefore do not need a second overlay pass.
     */
    public static boolean shouldThemeVanillaWidgets() {
        MinecraftClient c = MinecraftClient.getInstance();
        Screen screen = c.currentScreen;
        if (screen == null) return false;
        if (screen instanceof HorrorScreen) return false;
        if (screen instanceof HandledScreen<?>) return false;
        if (screen instanceof ChatScreen) return false;
        return true;
    }

    public static void drawScreenBackground(DrawContext c, int width, int height) {
        c.drawTexture(BACKGROUND, 0, 0, width, height, 0, 0, BG_W, BG_H, BG_W, BG_H);
        c.fillGradient(0, 0, width, height, 0xB40A0B0E, 0xE0090709);
        c.fill(0, 0, width, 2, 0xD0762730);
        c.fill(0, height - 2, width, height, 0xC0201619);
        long t = System.currentTimeMillis() / 110L;
        int count = Math.max(7, Math.min(18, height / 26));
        for (int i = 0; i < count; i++) {
            int y = (int)((i * 53L + t) % Math.max(1, height));
            c.fill(0, y, width, y + 1, 0x0DFFFFFF);
        }
    }

    /** Adds one horror plaque over a vanilla button without replacing its click logic. */
    public static void overlayVanillaButton(DrawContext c, ClickableWidget w) {
        if (!shouldThemeVanillaWidgets()) return;
        int x = w.getX(), y = w.getY(), ww = w.getWidth(), hh = w.getHeight();
        if (ww <= 0 || hh <= 0) return;
        boolean hot = w.isHovered() && w.active;
        int fill = w.active ? (hot ? 0xFF34191E : 0xFF0B0D10) : 0xFF0A0B0D;
        int top = w.active ? (hot ? 0xFFE39A9F : 0xFF775056) : 0xFF382D30;
        int edge = hot ? 0xFFD45C68 : 0xFF61363B;
        c.fill(x, y, x + ww, y + hh, fill);
        c.fill(x, y, x + ww - 5, y + 1, top);
        c.fill(x, y + hh - 1, x + ww, y + hh, 0xE024191C);
        c.fill(x, y, x + 2, y + hh, edge);
        c.fill(x + ww - 1, y + 3, x + ww, y + hh - 2, 0xB04A2930);
        if (hot && ww > 14 && hh > 8) {
            c.fill(x + 5, y + 4, x + 7, y + hh - 4, 0xFF96333F);
            c.fill(x + 10, y + hh - 4, x + ww - 8, y + hh - 3, 0x55492A30);
        }

        Text msg = w.getMessage();
        if (msg != null && !msg.getString().isBlank()) {
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            String raw = msg.getString();
            Text shown = msg;
            int max = Math.max(10, ww - 18);
            if (tr.getWidth(raw) > max) shown = Text.literal(tr.trimToWidth(raw, Math.max(6, max - 6)) + "…");
            int tx = x + Math.max(7, (ww - tr.getWidth(shown)) / 2);
            int ty = y + Math.max(1, (hh - 8) / 2);
            c.drawTextWithShadow(tr, shown, tx, ty, w.active ? (hot ? 0xFFFFEEE6 : 0xFFE2D5CD) : 0xFF746A66);
        }
    }

    public static void overlayTextField(DrawContext c, ClickableWidget w) {
        if (!shouldThemeVanillaWidgets()) return;
        int x = w.getX(), y = w.getY(), ww = w.getWidth(), hh = w.getHeight();
        if (ww <= 0 || hh <= 0) return;
        int edge = w.isFocused() ? 0xFFE2A0A4 : (w.isHovered() ? 0xFF9D4A52 : 0xFF654046);
        c.fill(x, y, x + ww, y + 1, edge);
        c.fill(x, y + hh - 1, x + ww, y + hh, 0xFF2D2023);
        c.fill(x, y, x + 1, y + hh, edge);
        c.fill(x + ww - 1, y, x + ww, y + hh, 0xFF4A3034);
    }

    public static void drawListBackground(DrawContext c, int left, int top, int right, int bottom) {
        if (!shouldThemeVanillaWidgets()) return;
        if (right <= left || bottom <= top) return;
        c.fill(left, top, right, bottom, 0xD5090B0E);
        c.fill(left, top, right, top + 1, 0xB0714047);
        c.fill(left, bottom - 1, right, bottom, 0xB02A1D20);
        c.fill(left, top, left + 1, bottom, 0x8052343A);
        c.fill(right - 1, top, right, bottom, 0x8052343A);
    }
}
