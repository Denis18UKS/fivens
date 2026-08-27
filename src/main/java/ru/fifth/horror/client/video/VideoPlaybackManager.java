package ru.fifth.horror.client.video;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import ru.fifth.horror.video.VideoAssetPolicy;
import ru.fifth.horror.video.VideoAssetStore;
import ru.fifth.horror.video.VideoFeature;
import ru.fifth.horror.video.VideoPlaybackPolicy;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Coordinates local cache/download and one FFmpeg playback session per physical TV. */
public final class VideoPlaybackManager {
    private static final Map<Long, VideoPlayerSession> SESSIONS = new HashMap<>();
    private static final Map<String, Download> DOWNLOADS = new HashMap<>();

    private VideoPlaybackManager() {}

    public static void start(VideoAssetStore.Metadata metadata, BlockPos tvPos) {
        if (metadata == null || tvPos == null) return;
        VideoPlayerSession previous = SESSIONS.put(tvPos.asLong(), new VideoPlayerSession(metadata, tvPos));
        if (previous != null) previous.close();
        VideoPlayerSession session = SESSIONS.get(tvPos.asLong());

        if (VideoCache.valid(metadata)) {
            sendCacheStatus(metadata, true);
            session.mediaReady(VideoCache.path(metadata));
            return;
        }

        sendCacheStatus(metadata, false);
        Download existing = DOWNLOADS.get(metadata.id());
        if (existing != null && existing.matches(metadata)) return;
        if (existing != null) {
            VideoCache.discardPartial(existing.metadata);
            DOWNLOADS.remove(metadata.id());
        }
        try {
            VideoCache.resetPartial(metadata);
            Download download = new Download(metadata, tvPos.toImmutable());
            DOWNLOADS.put(metadata.id(), download);
            request(download);
        } catch (Exception error) {
            failSessions(metadata.id(), "TAPE READ ERROR: local cache unavailable");
        }
    }

    public static void receiveChunk(String rawId, long offset, byte[] bytes, boolean eof) {
        String id = VideoAssetPolicy.safeId(rawId);
        Download download = DOWNLOADS.get(id);
        if (download == null || bytes == null || offset != download.offset) return;
        if (!VideoCache.append(download.metadata, offset, bytes)) {
            DOWNLOADS.remove(id);
            VideoCache.discardPartial(download.metadata);
            failSessions(id, "TAPE READ ERROR: cache write failed");
            return;
        }
        download.offset += bytes.length;
        if (eof) {
            DOWNLOADS.remove(id);
            if (download.offset != download.metadata.byteLength() || !VideoCache.publish(download.metadata)) {
                VideoCache.discardPartial(download.metadata);
                failSessions(id, "TAPE READ ERROR: video SHA-256 mismatch");
                return;
            }
            Path cached = VideoCache.path(download.metadata);
            for (VideoPlayerSession session : SESSIONS.values()) {
                if (session.metadata().id().equals(id) && session.state() == VideoPlaybackPolicy.State.PREPARING) {
                    session.mediaReady(cached);
                }
            }
        } else {
            request(download);
        }
    }

    public static void error(BlockPos tvPos, String message) {
        if (tvPos == null || BlockPos.ORIGIN.equals(tvPos)) {
            for (VideoPlayerSession session : SESSIONS.values()) session.fail(message);
            return;
        }
        VideoPlayerSession session = SESSIONS.get(tvPos.asLong());
        if (session != null) session.fail(message);
    }

    public static void tick() {
        for (VideoPlayerSession session : SESSIONS.values()) session.tick();
    }

    public static boolean hasSession(BlockPos pos) {
        return pos != null && SESSIONS.containsKey(pos.asLong());
    }

    public static boolean staticPhase(BlockPos pos) {
        VideoPlayerSession session = session(pos);
        return session != null && session.staticPhase();
    }

    public static boolean playing(BlockPos pos) {
        VideoPlayerSession session = session(pos);
        return session != null && session.playing();
    }

    public static Identifier texture(BlockPos pos) {
        VideoPlayerSession session = session(pos);
        return session == null ? null : session.texture();
    }

    public static String error(BlockPos pos) {
        VideoPlayerSession session = session(pos);
        return session == null ? "" : session.error();
    }

    public static String label(BlockPos pos) {
        VideoPlayerSession session = session(pos);
        return session == null ? "" : session.metadata().id();
    }

    public static VideoPlaybackPolicy.State state(BlockPos pos) {
        VideoPlayerSession session = session(pos);
        return session == null ? VideoPlaybackPolicy.State.ENDED : session.state();
    }

    public static void clear() {
        for (VideoPlayerSession session : SESSIONS.values()) session.close();
        SESSIONS.clear();
        for (Download download : DOWNLOADS.values()) VideoCache.discardPartial(download.metadata);
        DOWNLOADS.clear();
    }

    private static VideoPlayerSession session(BlockPos pos) {
        return pos == null ? null : SESSIONS.get(pos.asLong());
    }

    private static void sendCacheStatus(VideoAssetStore.Metadata metadata, boolean present) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeString(metadata.id(), 128);
        out.writeString(metadata.sha256(), 64);
        out.writeBoolean(present);
        ClientPlayNetworking.send(VideoFeature.CACHE_STATUS, out);
    }

    private static void request(Download download) {
        if (download.offset < 0 || download.offset >= download.metadata.byteLength()) return;
        PacketByteBuf out = PacketByteBufs.create();
        out.writeString(download.metadata.id(), 128);
        out.writeLong(download.offset);
        out.writeBlockPos(download.tvPos);
        ClientPlayNetworking.send(VideoFeature.CHUNK_REQUEST, out);
    }

    private static void failSessions(String id, String message) {
        for (VideoPlayerSession session : SESSIONS.values()) {
            if (session.metadata().id().equals(id)) session.fail(message);
        }
    }

    private static final class Download {
        final VideoAssetStore.Metadata metadata;
        final BlockPos tvPos;
        long offset;

        Download(VideoAssetStore.Metadata metadata, BlockPos tvPos) {
            this.metadata = metadata;
            this.tvPos = tvPos;
        }

        boolean matches(VideoAssetStore.Metadata other) {
            return other != null
                    && metadata.id().equals(other.id())
                    && metadata.sha256().equalsIgnoreCase(other.sha256())
                    && metadata.byteLength() == other.byteLength();
        }
    }
}
