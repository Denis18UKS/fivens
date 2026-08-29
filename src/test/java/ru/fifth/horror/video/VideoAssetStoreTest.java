package ru.fifth.horror.video;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class VideoAssetStoreTest {
    @TempDir Path temp;

    @Test
    void requiresSequentialChunksAndPublishesOnlyAfterVerifiedHash() throws Exception {
        Path videos = temp.resolve("videos");
        Path legacy = temp.resolve("vhs");
        VideoAssetStore store = new VideoAssetStore(videos, legacy);
        byte[] media = "not-a-real-codec-stream-but-valid-store-bytes".getBytes();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(media));
        var metadata = new VideoAssetStore.Metadata(
                "intro", "intro.mp4", "mp4", 640, 360, 2_000_000L,
                false, 0, 0, media.length, hash, VideoAssetStore.Origin.CUTSCENE_RECORDING);

        assertTrue(store.beginUpload(metadata));
        assertFalse(store.writeChunk("intro", 1, media));
        assertTrue(store.writeChunk("intro", 0, media));
        assertFalse(store.isComplete("intro"));
        assertTrue(store.finishUpload("intro"));
        assertTrue(store.isComplete("intro"));
        assertArrayEquals(media, Files.readAllBytes(store.mediaPath("intro")));
    }

    @Test
    void rejectsBadHashAndDetectsLegacyOnlyIds() throws Exception {
        Path videos = temp.resolve("videos");
        Path legacy = temp.resolve("vhs");
        VideoAssetStore store = new VideoAssetStore(videos, legacy);
        byte[] media = "broken".getBytes();
        var metadata = new VideoAssetStore.Metadata(
                "bad", "bad.mp4", "mp4", 320, 180, 1_000_000L,
                false, 0, 0, media.length, "0".repeat(64), VideoAssetStore.Origin.IMPORTED_FILE);

        assertTrue(store.beginUpload(metadata));
        assertTrue(store.writeChunk("bad", 0, media));
        assertFalse(store.finishUpload("bad"));
        assertFalse(store.isComplete("bad"));

        Files.createDirectories(legacy.resolve("old_tape"));
        Files.writeString(legacy.resolve("old_tape").resolve("metadata.json"), "{}");
        assertTrue(store.isLegacyOnly("old_tape"));
        assertFalse(store.isLegacyOnly("missing"));
    }
}
