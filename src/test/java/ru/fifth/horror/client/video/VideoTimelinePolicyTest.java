package ru.fifth.horror.client.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoTimelinePolicyTest {
    @Test
    void playbackTimestampsStartAtZeroEvenWhenFfmpegStartsLaterInFile() {
        long firstTimestamp = 5_000_000L;

        assertEquals(0L, VideoTimelinePolicy.normalizeTimestamp(firstTimestamp, 5_000_000L));
        assertEquals(100_000L, VideoTimelinePolicy.normalizeTimestamp(firstTimestamp, 5_100_000L));
        assertEquals(0L, VideoTimelinePolicy.normalizeTimestamp(firstTimestamp, 4_900_000L));
    }

    @Test
    void decoderCompletionDoesNotEndPlaybackWhileFramesStillNeedPresentation() {
        assertFalse(VideoTimelinePolicy.shouldFinish(true, false, true, true));
        assertFalse(VideoTimelinePolicy.shouldFinish(true, true, false, true));
        assertFalse(VideoTimelinePolicy.shouldFinish(true, true, true, false));
        assertTrue(VideoTimelinePolicy.shouldFinish(true, true, true, true));
        assertFalse(VideoTimelinePolicy.shouldFinish(false, true, true, true));
    }
}
