package ru.fifth.horror.client.video;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class VideoFramePixelsTest {
    @Test
    void explicitRgbaDecodePreservesChannelsAndRowStride() {
        byte[] rgba = new byte[] {
                (byte) 255, 0, 0, (byte) 255,
                0, (byte) 255, 0, (byte) 255,
                99, 88, 77, 66,
                0, 0, (byte) 255, (byte) 255,
                12, 34, 56, (byte) 255,
                55, 44, 33, 22
        };

        int[] argb = VideoFramePixels.rgbaToArgb(rgba, 2, 2, 12);

        assertArrayEquals(new int[] {
                0xFFFF0000,
                0xFF00FF00,
                0xFF0000FF,
                0xFF0C2238
        }, argb);
    }

    @Test
    void argbToRgbaRoundTripPreservesExactPixels() {
        int[] source = {
                0xFFFF0000,
                0x80010203,
                0xFF00FF00,
                0xFF0C2238
        };

        ByteBuffer rgba = VideoFramePixels.argbToRgbaBuffer(source, 2, 2);
        byte[] bytes = new byte[rgba.remaining()];
        rgba.get(bytes);

        assertArrayEquals(new byte[] {
                (byte) 255, 0, 0, (byte) 255,
                1, 2, 3, (byte) 128,
                0, (byte) 255, 0, (byte) 255,
                12, 34, 56, (byte) 255
        }, bytes);
    }
}
