package ru.fifth.horror.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/** Full-screen lift transition with red vertically rolling floor numbers and a moving shaft indicator. */
public final class LiftTravelOverlay {
    private static int from, to, total, tick;
    private static boolean active;

    private LiftTravelOverlay() {}

    public static void start(int startFloor, int targetFloor, int ticks) {
        from = startFloor;
        to = targetFloor;
        total = Math.max(1, ticks);
        tick = 0;
        active = true;
    }

    public static void finish(int floor) {
        from = to = floor;
        tick = total;
        active = false;
    }

    public static boolean active() {
        return active;
    }

    public static void tick() {
        if (active && ++tick >= total) active = false;
    }

    /**
     * Final HUD pass. It deliberately resets the GUI transform and inherited scissor so no previous
     * HUD/widget callback can crop or scale the black mask. The world therefore never leaks around it.
     */
    public static void render(DrawContext c) {
        if (!active) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.textRenderer == null) return;

        c.disableScissor();
        c.getMatrices().push();
        c.getMatrices().loadIdentity();
        try {
            int w = c.getScaledWindowWidth();
            int h = c.getScaledWindowHeight();
            c.fill(0, 0, w, h, 0xFF000000);

            float progress = MathHelper.clamp(tick / (float) total, 0, 1);
            float exact = from + (to - from) * progress;
            int base = (int) Math.floor(exact);
            float frac = exact - base;
            int dir = Integer.compare(to, from);
            if (dir == 0) dir = 1;

            String current = Integer.toString(Math.max(1, Math.min(9, base)));
            String next = Integer.toString(Math.max(1, Math.min(9, base + dir)));
            int center = h / 2;
            int travel = 36;
            int yCurrent = (int) (center - frac * travel);
            int yNext = (int) (center + (1 - frac) * travel);
            int red = 0xFFFF2424;

            c.getMatrices().push();
            c.getMatrices().scale(3f, 3f, 1f);
            c.drawCenteredTextWithShadow(client.textRenderer, current, w / 6, yCurrent / 3, red);
            c.drawCenteredTextWithShadow(client.textRenderer, next, w / 6, yNext / 3, red);
            c.getMatrices().pop();

            int trackX = w / 2;
            int top = center + 34;
            int bottom = Math.min(h - 18, top + 58);
            c.fill(trackX - 1, top, trackX + 1, bottom, 0xFF2C0B0D);
            c.fill(trackX - 5, top, trackX - 4, bottom, 0xFF150607);
            c.fill(trackX + 4, top, trackX + 5, bottom, 0xFF150607);

            int span = Math.max(1, bottom - top - 10);
            float phase = (tick % 24) / 24f;
            if (to < from) phase = 1f - phase;
            int markerY = top + 5 + Math.round(span * phase);
            c.fill(trackX - 7, markerY - 3, trackX + 8, markerY + 4, 0xFF8F171A);
            c.fill(trackX - 4, markerY - 1, trackX + 5, markerY + 2, 0xFFFF2B2F);

            String arrow = to >= from ? "▲" : "▼";
            c.drawCenteredTextWithShadow(client.textRenderer, arrow, trackX, bottom + 5, 0xFFB51C20);
        } finally {
            c.getMatrices().pop();
        }
    }
}
