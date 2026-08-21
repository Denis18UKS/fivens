package ru.fifth.horror.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.fifth.horror.FifthMod;

public class FifthTitleScreen extends Screen {
    private static final Identifier BG = FifthMod.id("textures/gui/title.png");
    private static final int IMAGE_W = 1672;
    private static final int IMAGE_H = 941;

    // Regions are in source-image coordinates. The background itself already contains the visible buttons.
    private static final Rect PLAY = new Rect(88, 279, 397, 345);
    private static final Rect CONTINUE = new Rect(88, 355, 397, 423);
    private static final Rect CHAPTERS = new Rect(88, 434, 397, 500);
    private static final Rect SETTINGS = new Rect(88, 510, 397, 577);
    private static final Rect CREDITS = new Rect(88, 588, 397, 655);
    private static final Rect EXIT = new Rect(88, 665, 397, 733);
    private static final Rect HIDDEN_STUDIO = new Rect(1431, 478, 1471, 535);

    private Rect hovered;

    public FifthTitleScreen() { super(Text.literal("Пятый")); }

    @Override protected void init() {
        // Intentionally no vanilla widgets here. Click regions are taken directly from the painted background.
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || client == null) return super.mouseClicked(mouseX, mouseY, button);
        double ix = mouseX * IMAGE_W / Math.max(1.0, width);
        double iy = mouseY * IMAGE_H / Math.max(1.0, height);

        if (PLAY.contains(ix, iy)) { client.setScreen(new PlayMenuScreen(this)); return true; }
        if (CONTINUE.contains(ix, iy)) { client.setScreen(new SelectWorldScreen(this)); return true; }
        if (CHAPTERS.contains(ix, iy)) { client.setScreen(new ChaptersScreen(this)); return true; }
        if (SETTINGS.contains(ix, iy)) { client.setScreen(new OptionsScreen(this, client.options)); return true; }
        if (CREDITS.contains(ix, iy)) { client.setScreen(new CreditsScreen(this)); return true; }
        if (EXIT.contains(ix, iy)) { client.scheduleStop(); return true; }

        // Secret director access: the violet round call button on the paranormal elevator.
        if (HIDDEN_STUDIO.contains(ix, iy)) { client.setScreen(new StudioScreen(this)); return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void render(DrawContext c, int mx, int my, float delta) {
        c.drawTexture(BG, 0, 0, width, height, 0, 0, IMAGE_W, IMAGE_H, IMAGE_W, IMAGE_H);
        c.fillGradient(0, 0, width, height, 0x08000000, 0x25000000);

        double ix = mx * IMAGE_W / Math.max(1.0, width);
        double iy = my * IMAGE_H / Math.max(1.0, height);
        hovered = visibleRegionAt(ix, iy);
        if (hovered != null) drawHover(c, hovered);
        super.render(c, mx, my, delta);
    }

    private Rect visibleRegionAt(double x, double y) {
        if (PLAY.contains(x,y)) return PLAY;
        if (CONTINUE.contains(x,y)) return CONTINUE;
        if (CHAPTERS.contains(x,y)) return CHAPTERS;
        if (SETTINGS.contains(x,y)) return SETTINGS;
        if (CREDITS.contains(x,y)) return CREDITS;
        if (EXIT.contains(x,y)) return EXIT;
        return null; // Secret studio hotspot intentionally has no hover highlight.
    }

    private void drawHover(DrawContext c, Rect r) {
        int x1 = (int)Math.round(r.x1 * width / (double)IMAGE_W);
        int y1 = (int)Math.round(r.y1 * height / (double)IMAGE_H);
        int x2 = (int)Math.round(r.x2 * width / (double)IMAGE_W);
        int y2 = (int)Math.round(r.y2 * height / (double)IMAGE_H);
        c.fill(x1, y1, x2, y2, 0x14000000);
        c.fill(x1, y1, x2, y1 + 1, 0xB0B9AAA0);
        c.fill(x1, y2 - 1, x2, y2, 0x703B2828);
        c.fill(x1, y1, x1 + 1, y2, 0x806C5050);
        c.fill(x2 - 1, y1, x2, y2, 0x806C5050);
    }

    private record Rect(int x1, int y1, int x2, int y2) {
        boolean contains(double x, double y) { return x >= x1 && x <= x2 && y >= y1 && y <= y2; }
    }
}
