package ru.fifth.horror.vhs;

/** Pure limits/clock policy shared by the VHS recorder, store and TV playback. */
public final class VhsRecordingPolicy {
    public static final int MAX_WIDTH = 256;
    public static final int MAX_HEIGHT = 144;
    public static final int MAX_FPS = 30;
    public static final int MAX_FRAMES = 18_000;
    public static final int MAX_FRAME_BYTES = 512 * 1024;
    public static final long MAX_RECORDING_BYTES = 512L * 1024L * 1024L;

    private VhsRecordingPolicy() {}

    public static int frameIndexForTick(int tick, int fps, int frameCount) {
        if (frameCount <= 0) return 0;
        int safeFps = Math.max(1, Math.min(MAX_FPS, fps));
        long safeTick = Math.max(0, tick);
        long index = safeTick * safeFps / 20L;
        return (int) Math.max(0, Math.min(frameCount - 1L, index));
    }

    public static boolean validMetadata(int width, int height, int fps, int frameCount) {
        return width >= 1 && width <= MAX_WIDTH
                && height >= 1 && height <= MAX_HEIGHT
                && fps >= 1 && fps <= MAX_FPS
                && frameCount >= 1 && frameCount <= MAX_FRAMES;
    }

    public static boolean isComplete(int expectedFrames, int writtenFrames) {
        return expectedFrames > 0 && writtenFrames == expectedFrames;
    }
}
