package ru.fifth.horror.client.video;

/** Pure playback-timeline rules kept separate from Minecraft/FFmpeg runtime code for deterministic tests. */
public final class VideoTimelinePolicy {
    private VideoTimelinePolicy() {}

    public static long normalizeTimestamp(long firstVideoTimestampMicros, long rawTimestampMicros) {
        if (firstVideoTimestampMicros < 0L) firstVideoTimestampMicros = 0L;
        if (rawTimestampMicros <= firstVideoTimestampMicros) return 0L;
        return rawTimestampMicros - firstVideoTimestampMicros;
    }

    public static boolean shouldFinish(boolean decoderDone, boolean queueEmpty, boolean pendingFrameEmpty, boolean framePresented) {
        return decoderDone && queueEmpty && pendingFrameEmpty && framePresented;
    }
}
