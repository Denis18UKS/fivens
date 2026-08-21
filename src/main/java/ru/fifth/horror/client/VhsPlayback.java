package ru.fifth.horror.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.cutscene.CutsceneDefinition;
import ru.fifth.horror.mixin.GameRendererAccessor;

/** Client-side VHS playback and screen-space distortion overlay. */
public final class VhsPlayback {
    private static boolean active;
    private static int ticks;
    private static int mode;
    private static BlockPos tvPos;
    private static boolean postProcessorLoaded;

    private VhsPlayback() {}

    public static void start(CutsceneDefinition scene, int playbackMode, BlockPos tv) {
        stop();
        active = true;
        ticks = 0;
        mode = playbackMode == 1 ? 1 : 0;
        tvPos = tv;

        if (mode == 0 && scene != null) {
            CutsceneDefinition copy = new CutsceneDefinition();
            copy.id = scene.id;
            copy.teleportPlayerAtEnd = false;
            copy.hideHud = true;
            copy.lockInput = true;
            copy.keyframes.addAll(scene.keyframes);
            CutscenePlayback.start(copy);
        }

        MinecraftClient client = MinecraftClient.getInstance();
        try {
            ((GameRendererAccessor) client.gameRenderer).fiven$loadPostProcessor(FifthMod.id("shaders/post/vhs.json"));
            postProcessorLoaded = true;
        } catch (Exception ignored) {
            // The screen overlay below is a graceful fallback if another mod blocks post-processing.
            postProcessorLoaded = false;
        }
    }

    public static void tick() {
        if (!active) return;
        ticks++;
        if (mode == 0 && !CutscenePlayback.active()) stop();
        if (ticks > 20 * 120) stop();
    }

    public static void stop() {
        if (!active && !postProcessorLoaded) return;
        active = false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (postProcessorLoaded) {
            try {
                client.gameRenderer.disablePostProcessor();
            } catch (Exception ignored) {}
        }
        postProcessorLoaded = false;
    }

    public static void render(DrawContext c) {
        if (!active) return;
        int w = c.getScaledWindowWidth();
        int h = c.getScaledWindowHeight();

        // Scanlines + travelling tracking tear. Kept even when the post chain works to make VHS readable at low GUI scales.
        for (int y = ticks % 7; y < h; y += 7) c.fill(0, y, w, y + 1, 0x18000000);
        int tearY = Math.floorMod(ticks * 3, Math.max(1, h));
        c.fill(0, tearY, w, Math.min(h, tearY + 2), 0x18FFFFFF);
        int noiseX = Math.floorMod(ticks * 37, Math.max(1, w));
        c.fill(noiseX, 0, Math.min(w, noiseX + 2), h, 0x10FFFFFF);

        if (mode == 1) {
            int bw = Math.min(420, w - 20);
            int bh = Math.min(250, h - 20);
            int x = (w - bw) / 2;
            int y = (h - bh) / 2;
            c.fill(x, y, x + bw, y + bh, 0xEE050505);
            c.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, "VHS / TV MODE", w / 2, y + 10, 0xFFB8B8B8);
            c.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, "REC", w / 2, y + bh - 20, 0xFF8C2020);
            if (tvPos != null) {
                c.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                        tvPos.getX() + ", " + tvPos.getY() + ", " + tvPos.getZ(), w / 2, y + bh - 34, 0xFF6F6F6F);
            }
        }
    }
}
