package ru.fifth.horror.client.video;

import java.util.Arrays;

/**
 * Deterministic pixel conversion for FFmpeg's explicitly requested RGBA8 output.
 * Keeping this independent from AWT removes platform-dependent channel/raster conversions
 * before pixels are uploaded into Minecraft's NativeImage.
 */
public final class VideoFramePixels {
    private VideoFramePixels() {}

    public static int[] rgbaToArgb(byte[] rgba, int width, int height, int strideBytes) {
        if (rgba == null || width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid RGBA frame");
        int rowBytes = Math.multiplyExact(width, 4);
        if (strideBytes < rowBytes) throw new IllegalArgumentException("RGBA stride is smaller than a row");
        int required = Math.addExact(Math.multiplyExact(height - 1, strideBytes), rowBytes);
        if (rgba.length < required) throw new IllegalArgumentException("RGBA buffer is truncated");

        int[] argb = new int[Math.multiplyExact(width, height)];
        int out = 0;
        for (int y = 0; y < height; y++) {
            int row = y * strideBytes;
            for (int x = 0; x < width; x++) {
                int i = row + x * 4;
                int r = rgba[i] & 0xFF;
                int g = rgba[i + 1] & 0xFF;
                int b = rgba[i + 2] & 0xFF;
                int a = rgba[i + 3] & 0xFF;
                argb[out++] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return argb;
    }

    /** Letterboxes an ARGB frame into the CRT's fixed output size without changing channel order. */
    public static int[] letterboxNearest(int[] source, int sourceWidth, int sourceHeight, int outputWidth, int outputHeight) {
        if (source == null || sourceWidth <= 0 || sourceHeight <= 0 || outputWidth <= 0 || outputHeight <= 0
                || source.length < sourceWidth * sourceHeight) {
            throw new IllegalArgumentException("Invalid video frame dimensions");
        }

        int[] output = new int[Math.multiplyExact(outputWidth, outputHeight)];
        Arrays.fill(output, 0xFF000000);

        double scale = Math.min(outputWidth / (double) sourceWidth, outputHeight / (double) sourceHeight);
        int drawWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int drawHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
        int offsetX = (outputWidth - drawWidth) / 2;
        int offsetY = (outputHeight - drawHeight) / 2;

        for (int y = 0; y < drawHeight; y++) {
            int sourceY = Math.min(sourceHeight - 1, (int) ((long) y * sourceHeight / drawHeight));
            int targetRow = (offsetY + y) * outputWidth + offsetX;
            int sourceRow = sourceY * sourceWidth;
            for (int x = 0; x < drawWidth; x++) {
                int sourceX = Math.min(sourceWidth - 1, (int) ((long) x * sourceWidth / drawWidth));
                output[targetRow + x] = source[sourceRow + sourceX];
            }
        }
        return output;
    }
}
