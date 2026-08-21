package ru.fifth.horror.client;

import com.google.gson.Gson;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import ru.fifth.horror.cutscene.CutsceneDefinition;

/** Reliable VHS playback overlay that does not depend on inaccessible GameRenderer internals. */
public final class VhsPlayback {
    private static final Gson GSON = new Gson();
    private static boolean active;
    private static int ticks, mode;
    private static BlockPos tvPos;

    private VhsPlayback() {}

    public static void start(CutsceneDefinition scene, int playbackMode, BlockPos tv) {
        stop();
        active = true;
        ticks = 0;
        mode = playbackMode == 1 ? 1 : 0;
        tvPos = tv;
        if (mode == 0 && scene != null) {
            // Deep-copy before applying VHS-only flags so the saved cutscene is never mutated in memory.
            CutsceneDefinition copy = GSON.fromJson(GSON.toJson(scene), CutsceneDefinition.class);
            copy.teleportPlayerAtEnd = false;
            copy.hideHud = true;
            copy.lockInput = true;
            CutscenePlayback.start(copy);
        }
    }

    public static void tick() {
        if (!active) return;
        ticks++;
        if (mode == 0 && !CutscenePlayback.active()) stop();
        if (ticks > 20 * 120) stop();
    }

    public static void stop() {
        if (active && mode == 0 && CutscenePlayback.active()) CutscenePlayback.stop();
        active = false;
        ticks = 0;
        tvPos = null;
    }

    public static void render(DrawContext c) {
        if (!active) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = c.getScaledWindowWidth(), h = c.getScaledWindowHeight();
        if (w <= 0 || h <= 0) return;

        // Dark analogue veil + moving scanlines.
        c.fill(0, 0, w, h, 0x08000000);
        for (int y = ticks % 6; y < h; y += 6) c.fill(0, y, w, y + 1, 0x17000000);

        // Thin rolling tracking line and deterministic drop-outs; visually stable and resource-pack independent.
        int trackingY = (ticks * 3) % h;
        c.fill(0, trackingY, w, Math.min(h, trackingY + 2), 0x20FFFFFF);
        for (int i = 0; i < 5; i++) {
            int seed = ticks * 37 + i * 97;
            int x = Math.floorMod(seed * 13, w);
            int y = Math.floorMod(seed * 7, h);
            int len = Math.min(w - x, 12 + Math.floorMod(seed, 54));
            if (len > 0) c.fill(x, y, x + len, Math.min(h, y + 1), 0x22FFFFFF);
        }

        // Slight black edge crop imitates an old CRT capture and hides edge jitter from camera movement.
        int edge = Math.max(2, Math.min(8, w / 120));
        c.fill(0, 0, edge, h, 0xA0000000);
        c.fill(w - edge, 0, w, h, 0xA0000000);

        if (mode == 1) {
            int bw = Math.max(80, Math.min(420, w - 20));
            int bh = Math.max(60, Math.min(250, h - 20));
            int x = (w - bw) / 2, y = (h - bh) / 2;
            c.fill(x, y, x + bw, y + bh, 0xEE050505);
            c.drawBorder(x, y, bw, bh, 0xFF3D3D3D);
            c.drawCenteredTextWithShadow(mc.textRenderer, "VHS / TV MODE", w / 2, y + 10, 0xFFB8B8B8);
            c.drawCenteredTextWithShadow(mc.textRenderer, "REC", w / 2, y + bh - 20, 0xFF8C2020);
        }
    }
}
