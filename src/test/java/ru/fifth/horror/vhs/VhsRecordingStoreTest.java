package ru.fifth.horror.vhs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VhsRecordingStoreTest {
    @TempDir Path temp;

    @Test
    void incompleteUploadIsNotPlayableAndFinishMakesEveryFrameReadable() throws Exception {
        VhsRecordingStore store = new VhsRecordingStore(temp);
        VhsRecordingStore.Metadata meta = new VhsRecordingStore.Metadata("hallway", 256, 144, 15, 2, 3);

        assertTrue(store.beginUpload(meta));
        assertTrue(store.writeFrame("hallway", 0, new byte[]{1,2,3}));
        assertFalse(store.isComplete("hallway"));
        assertFalse(store.finishUpload("hallway"));

        assertTrue(store.writeFrame("hallway", 1, new byte[]{4,5,6}));
        assertTrue(store.finishUpload("hallway"));
        assertTrue(store.isComplete("hallway"));
        assertArrayEquals(new byte[]{1,2,3}, store.readFrame("hallway", 0));
        assertArrayEquals(new byte[]{4,5,6}, store.readFrame("hallway", 1));
        assertEquals(2, store.metadata("hallway").frameCount());
    }

    @Test
    void rejectsDuplicateOutOfRangeAndOversizedFrames() throws Exception {
        VhsRecordingStore store = new VhsRecordingStore(temp);
        assertTrue(store.beginUpload(new VhsRecordingStore.Metadata("x", 256, 144, 15, 1, 1)));
        assertFalse(store.writeFrame("x", 1, new byte[]{1}));
        assertFalse(store.writeFrame("x", 0, new byte[VhsRecordingPolicy.MAX_FRAME_BYTES + 1]));
        assertTrue(store.writeFrame("x", 0, new byte[]{7}));
        assertFalse(store.writeFrame("x", 0, new byte[]{8}));
    }
}
