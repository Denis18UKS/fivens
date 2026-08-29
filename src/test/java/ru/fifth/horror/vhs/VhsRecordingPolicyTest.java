package ru.fifth.horror.vhs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VhsRecordingPolicyTest {
    @Test
    void mapsMinecraftTicksToStoredFifteenFpsFrames() {
        assertEquals(0, VhsRecordingPolicy.frameIndexForTick(0, 15, 100));
        assertEquals(0, VhsRecordingPolicy.frameIndexForTick(1, 15, 100));
        assertEquals(1, VhsRecordingPolicy.frameIndexForTick(2, 15, 100));
        assertEquals(15, VhsRecordingPolicy.frameIndexForTick(20, 15, 100));
        assertEquals(99, VhsRecordingPolicy.frameIndexForTick(10_000, 15, 100));
    }

    @Test
    void validatesBoundedSelfContainedRecordingMetadata() {
        assertTrue(VhsRecordingPolicy.validMetadata(256, 144, 15, 1));
        assertTrue(VhsRecordingPolicy.validMetadata(256, 144, 30, 18_000));
        assertFalse(VhsRecordingPolicy.validMetadata(0, 144, 15, 1));
        assertFalse(VhsRecordingPolicy.validMetadata(257, 144, 15, 1));
        assertFalse(VhsRecordingPolicy.validMetadata(256, 145, 15, 1));
        assertFalse(VhsRecordingPolicy.validMetadata(256, 144, 31, 1));
        assertFalse(VhsRecordingPolicy.validMetadata(256, 144, 15, 18_001));
    }

    @Test
    void recordingIsCompleteOnlyWhenEveryFrameExists() {
        assertFalse(VhsRecordingPolicy.isComplete(4, 3));
        assertTrue(VhsRecordingPolicy.isComplete(4, 4));
        assertFalse(VhsRecordingPolicy.isComplete(0, 0));
    }
}
