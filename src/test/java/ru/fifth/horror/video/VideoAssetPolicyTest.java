package ru.fifth.horror.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VideoAssetPolicyTest {
    @Test
    void safeIdsAndSupportedExtensionsAreDeterministic() {
        assertEquals("intro_scene", VideoAssetPolicy.safeId(" Intro Scene "));
        assertEquals("recording", VideoAssetPolicy.safeId("***"));
        assertTrue(VideoAssetPolicy.allowedExtension("movie.MP4"));
        assertTrue(VideoAssetPolicy.allowedExtension("clip.webm"));
        assertTrue(VideoAssetPolicy.allowedExtension("shot.mkv"));
        assertFalse(VideoAssetPolicy.allowedExtension("frame.png"));
        assertFalse(VideoAssetPolicy.allowedExtension("movie.exe"));
    }

    @Test
    void assetAndChunkLimitsMatchProtocol() {
        assertTrue(VideoAssetPolicy.validDeclaredSize(1));
        assertTrue(VideoAssetPolicy.validDeclaredSize(VideoAssetPolicy.MAX_ASSET_BYTES));
        assertFalse(VideoAssetPolicy.validDeclaredSize(0));
        assertFalse(VideoAssetPolicy.validDeclaredSize(VideoAssetPolicy.MAX_ASSET_BYTES + 1));
        assertTrue(VideoAssetPolicy.validChunk(VideoAssetPolicy.CHUNK_BYTES));
        assertFalse(VideoAssetPolicy.validChunk(VideoAssetPolicy.CHUNK_BYTES + 1));
        assertEquals(65_536L, VideoAssetPolicy.nextOffset(0, 65_536));
    }
}
