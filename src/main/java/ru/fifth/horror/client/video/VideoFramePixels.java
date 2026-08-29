package ru.fifth.horror.client.video;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Deterministic pixel conversion for JavaCV's native COLOR/BGR24 output. */
public final class VideoFramePixels {
    private VideoFramePixels() {}

    public static int[] bgrToArgb(byte[] bgr, int width, int height, int strideBytes) {
        if (bgr == null || width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid BGR frame");
        int rowBytes = Math.multiplyExact(width, 3);
        if (strideBytes < rowBytes) throw new IllegalArgumentException("BGR stride is smaller than a row");
        int required = Math.addExact(Math.multiplyExact(height - 1, strideBytes), rowBytes);
        if (bgr.length < required) throw new IllegalArgumentException("BGR buffer is truncated");
        int[] argb = new int[Math.multiplyExact(width, height)];
        int out = 0;
        for (int y = 0; y < height; y++) {
            int row = y * strideBytes;
            for (int x = 0; x < width; x++) {
                int i = row + x * 3;
                int b = bgr[i] & 0xFF, g = bgr[i + 1] & 0xFF, r = bgr[i + 2] & 0xFF;
                argb[out++] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return argb;
    }

    public static int[] rgbaToArgb(byte[] rgba, int width, int height, int strideBytes) {
        if (rgba == null || width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid RGBA frame");
        int rowBytes = Math.multiplyExact(width, 4);
        if (strideBytes < rowBytes) throw new IllegalArgumentException("RGBA stride is smaller than a row");
        int required = Math.addExact(Math.multiplyExact(height - 1, strideBytes), rowBytes);
        if (rgba.length < required) throw new IllegalArgumentException("RGBA buffer is truncated");
        int[] argb = new int[Math.multiplyExact(width, height)];
        int out = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int i = y * strideBytes + x * 4;
            int r = rgba[i] & 0xFF, g = rgba[i + 1] & 0xFF, b = rgba[i + 2] & 0xFF, a = rgba[i + 3] & 0xFF;
            argb[out++] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        return argb;
    }

    public static ByteBuffer argbToRgbaBuffer(int[] argb, int width, int height) {
        if (argb == null || width <= 0 || height <= 0 || argb.length < width * height) throw new IllegalArgumentException("Invalid ARGB frame");
        ByteBuffer rgba = ByteBuffer.allocate(Math.multiplyExact(Math.multiplyExact(width, height), 4));
        for (int i = 0; i < width * height; i++) {
            int color = argb[i];
            rgba.put((byte) ((color >>> 16) & 0xFF)).put((byte) ((color >>> 8) & 0xFF)).put((byte) (color & 0xFF)).put((byte) ((color >>> 24) & 0xFF));
        }
        rgba.flip();
        return rgba;
    }

    public static int[] letterboxNearest(int[] source, int sourceWidth, int sourceHeight, int outputWidth, int outputHeight) {
        if (source == null || sourceWidth <= 0 || sourceHeight <= 0 || outputWidth <= 0 || outputHeight <= 0 || source.length < sourceWidth * sourceHeight) throw new IllegalArgumentException("Invalid video frame dimensions");
        int[] output = new int[Math.multiplyExact(outputWidth, outputHeight)];
        Arrays.fill(output, 0xFF000000);
        double scale = Math.min(outputWidth / (double) sourceWidth, outputHeight / (double) sourceHeight);
        int drawWidth = Math.max(1, (int) Math.round(sourceWidth * scale)), drawHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
        int offsetX = (outputWidth - drawWidth) / 2, offsetY = (outputHeight - drawHeight) / 2;
        for (int y = 0; y < drawHeight; y++) {
            int sourceY = Math.min(sourceHeight - 1, (int) ((long) y * sourceHeight / drawHeight));
            int targetRow = (offsetY + y) * outputWidth + offsetX, sourceRow = sourceY * sourceWidth;
            for (int x = 0; x < drawWidth; x++) output[targetRow + x] = source[sourceRow + Math.min(sourceWidth - 1, (int) ((long) x * sourceWidth / drawWidth))];
        }
        return output;
    }
}
