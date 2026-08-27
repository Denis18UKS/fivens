package ru.fifth.horror.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

/** Shared animated analogue-static texture used by every CRT during cassette signal lock. */
public final class VhsSignalTexture {
    private static final int WIDTH = 256;
    private static final int HEIGHT = 144;
    private static NativeImageBackedTexture texture;
    private static Identifier id;
    private static long lastFrame = Long.MIN_VALUE;

    private VhsSignalTexture() {}

    public static Identifier texture(BlockPos pos, long worldTick) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return null;
        ensure(client);
        if (texture == null || id == null) return null;

        // 10 visual updates/sec is enough for VHS snow and avoids uploading a texture every render frame.
        long frame = worldTick / 2L;
        if (frame != lastFrame) {
            lastFrame = frame;
            NativeImage image = texture.getImage();
            if (image != null) {
                fill(image, pos == null ? 0L : pos.asLong(), frame);
                texture.upload();
            }
        }
        return id;
    }

    private static void ensure(MinecraftClient client) {
        if (texture != null) return;
        NativeImage image = new NativeImage(WIDTH, HEIGHT, false);
        fill(image, 0L, 0L);
        texture = new NativeImageBackedTexture(image);
        id = client.getTextureManager().registerDynamicTexture("fiven_vhs_signal", texture);
        texture.upload();
    }

    private static void fill(NativeImage image, long posSeed, long frame) {
        Random random = new Random(posSeed * 31L + frame * 1_000_003L);
        int tearY = random.nextInt(HEIGHT);
        int tearH = 2 + random.nextInt(7);
        int brightBandY = random.nextInt(HEIGHT);

        for (int y = 0; y < HEIGHT; y++) {
            boolean tear = y >= tearY && y < tearY + tearH;
            boolean brightBand = Math.abs(y - brightBandY) <= 1;
            int lineBias = random.nextInt(41) - 20;
            for (int x = 0; x < WIDTH; x++) {
                int grain = 42 + random.nextInt(190);
                if (((x + (int) frame * 7) & 15) == 0) grain += 18;
                if (tear) grain = 70 + random.nextInt(150);
                if (brightBand) grain += 45;
                grain = Math.max(18, Math.min(245, grain + lineBias));
                image.setColor(x, y, 0xFF000000 | (grain << 16) | (grain << 8) | grain);
            }
        }
    }

    public static void clear() {
        if (texture != null) {
            try { texture.close(); } catch (Throwable ignored) {}
        }
        texture = null;
        id = null;
        lastFrame = Long.MIN_VALUE;
    }
}
