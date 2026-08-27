package ru.fifth.horror.client.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class VideoFramePixelsTest {
    @Test
    void explicitRgbaDecodePreservesChannelsAndRowStride() {
        byte[] rgba = new byte[] {
                (byte) 255, 0, 0, (byte) 255,        // red
                0, (byte) 255, 0, (byte) 255,        // green
                99, 88, 77, 66,                      // row padding (ignored)
                0, 0, (byte) 255, (byte) 255,        // blue
                12, 34, 56, (byte) 255,              // arbitrary RGB
                55, 44, 33, 22                       // row padding (ignored)
        };

        int[] argb = VideoFramePixels.rgbaToArgb(rgba, 2, 2, 12);

        assertArrayEquals(new int[] {
                0xFFFF0000,
                0xFF00FF00,
                0xFF0000FF,
                0xFF0C2238
        }, argb);
    }
}
