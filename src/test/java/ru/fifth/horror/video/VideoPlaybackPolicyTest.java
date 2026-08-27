package ru.fifth.horror.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VideoPlaybackPolicyTest {
    @Test
    void playbackWaitsForMediaBeforeStaticAndVideo() {
        assertEquals(VideoPlaybackPolicy.State.PREPARING,
                VideoPlaybackPolicy.next(VideoPlaybackPolicy.State.PREPARING, false, 0, 1000, 1000));
        assertEquals(VideoPlaybackPolicy.State.STATIC,
                VideoPlaybackPolicy.next(VideoPlaybackPolicy.State.PREPARING, true, 0, 1000, 1000));
        assertEquals(VideoPlaybackPolicy.State.STATIC,
                VideoPlaybackPolicy.next(VideoPlaybackPolicy.State.STATIC, true, 29, 1000, 1000));
        assertEquals(VideoPlaybackPolicy.State.PLAYING,
                VideoPlaybackPolicy.next(VideoPlaybackPolicy.State.STATIC, true, 30, 1000, 1000));
        assertEquals(VideoPlaybackPolicy.State.PLAYING,
                VideoPlaybackPolicy.next(VideoPlaybackPolicy.State.PLAYING, true, 30, 999, 1000));
        assertEquals(VideoPlaybackPolicy.State.ENDED,
                VideoPlaybackPolicy.next(VideoPlaybackPolicy.State.PLAYING, true, 30, 1000, 1000));
    }

    @Test
    void explicitErrorIsTerminalUntilSessionReset() {
        assertEquals(VideoPlaybackPolicy.State.ERROR,
                VideoPlaybackPolicy.next(VideoPlaybackPolicy.State.ERROR, true, 30, 10, 1000));
        assertEquals(VideoPlaybackPolicy.STATIC_TICKS, 30);
    }
}
