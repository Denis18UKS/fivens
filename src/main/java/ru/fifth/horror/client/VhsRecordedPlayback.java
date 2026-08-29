package ru.fifth.horror.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import ru.fifth.horror.vhs.VhsFrameNavigationPolicy;
import ru.fifth.horror.vhs.VhsRecordingFeature;
import ru.fifth.horror.vhs.VhsRecordingPolicy;

import java.util.HashMap;
import java.util.Map;

/** Client cache/texture bridge for immutable PNG VHS frames. Navigation is manual, never timed like video. */
public final class VhsRecordedPlayback {
    private static final Map<Long, Session> SESSIONS = new HashMap<>();
    private static final Map<String, FrameCache> CACHE = new HashMap<>();

    private VhsRecordedPlayback() {}

    public static void start(String id, int width, int height, int fps, int frameCount, int durationTicks, BlockPos tvPos) {
        if (tvPos == null || !VhsRecordingPolicy.validMetadata(width, height, fps, frameCount)) return;
        String recordingId = id == null ? "recording" : id;
        FrameCache cache = CACHE.computeIfAbsent(recordingId,
                ignored -> new FrameCache(recordingId, width, height, fps, frameCount));
        if (!cache.matches(width, height, fps, frameCount)) {
            cache.close();
            cache = new FrameCache(recordingId, width, height, fps, frameCount);
            CACHE.put(recordingId, cache);
        }
        Session old = SESSIONS.put(tvPos.asLong(), new Session(tvPos.toImmutable(), cache));
        if (old != null) old.closeTextureOnly();
        Session session = SESSIONS.get(tvPos.asLong());
        if (session != null) session.prefetch();
    }

    /** One-frame test card for /fiven tv test. */
    public static void startDiagnostic(BlockPos tvPos) {
        if (tvPos == null) return;
        String id = "__tv_diagnostic__";
        FrameCache cache = CACHE.computeIfAbsent(id, ignored -> new FrameCache(id, 256, 144, 1, 1));
        if (!cache.matches(256, 144, 1, 1)) {
            cache.close();
            cache = new FrameCache(id, 256, 144, 1, 1);
            CACHE.put(id, cache);
        }
        if (cache.frames[0] == null) {
            try (NativeImage image = new NativeImage(256, 144, false)) {
                for (int y = 0; y < 144; y++) {
                    for (int x = 0; x < 256; x++) {
                        boolean border = x < 5 || x >= 251 || y < 5 || y >= 139;
                        boolean cross = Math.abs(x - 128) < 2 || Math.abs(y - 72) < 2;
                        boolean checker = (((x / 16) + (y / 16)) & 1) == 0;
                        int grey = checker ? 0xD8 : 0x38;
                        if (border) image.setColor(x, y, 0xFFFFFFFF);
                        else if (cross) image.setColor(x, y, 0xFF2020FF);
                        else image.setColor(x, y, 0xFF000000 | (grey << 16) | (grey << 8) | grey);
                    }
                }
                cache.frames[0] = image.getBytes();
                cache.requested[0] = true;
            } catch (Exception error) {
                return;
            }
        }
        Session old = SESSIONS.put(tvPos.asLong(), new Session(tvPos.toImmutable(), cache));
        if (old != null) old.closeTextureOnly();
    }

    public static void receiveFrame(String id, int frameIndex, byte[] png) {
        FrameCache cache = CACHE.get(id);
        if (cache == null || png == null || png.length == 0 || frameIndex < 0 || frameIndex >= cache.frameCount) return;
        cache.frames[frameIndex] = png;
    }

    public static void error(BlockPos pos, String message) {
        String text = message == null || message.isBlank() ? "TAPE READ ERROR" : message;
        if (pos == null || BlockPos.ORIGIN.equals(pos)) {
            for (Session session : SESSIONS.values()) session.error = text;
            return;
        }
        Session session = SESSIONS.get(pos.asLong());
        if (session != null) session.error = text;
    }

    /** Keeps only the current frame and immediate neighbours warm. There is no playback clock. */
    public static void tick() {
        for (Session session : SESSIONS.values()) session.prefetch();
    }

    public static boolean hasSession(BlockPos pos) {
        return pos != null && SESSIONS.containsKey(pos.asLong());
    }

    public static boolean staticPhase(BlockPos pos) {
        return false;
    }

    public static boolean recordingPhase(BlockPos pos) {
        Session session = pos == null ? null : SESSIONS.get(pos.asLong());
        return session != null && session.error.isBlank();
    }

    public static String error(BlockPos pos) {
        Session session = pos == null ? null : SESSIONS.get(pos.asLong());
        return session == null ? "" : session.error;
    }

    public static Identifier texture(BlockPos pos) {
        Session session = pos == null ? null : SESSIONS.get(pos.asLong());
        if (session == null || !session.error.isBlank()) return null;
        return session.install(session.currentFrame);
    }

    public static void step(BlockPos pos, int delta) {
        Session session = pos == null ? null : SESSIONS.get(pos.asLong());
        if (session == null || session.cache.frameCount <= 0) return;
        session.currentFrame = VhsFrameNavigationPolicy.move(session.currentFrame, delta, session.cache.frameCount);
        session.prefetch();
    }

    public static int currentFrame(BlockPos pos) {
        Session session = pos == null ? null : SESSIONS.get(pos.asLong());
        return session == null ? 0 : session.currentFrame;
    }

    public static int frameCount(BlockPos pos) {
        Session session = pos == null ? null : SESSIONS.get(pos.asLong());
        return session == null ? 0 : session.cache.frameCount;
    }

    public static int width(BlockPos pos) {
        Session session = pos == null ? null : SESSIONS.get(pos.asLong());
        return session == null ? 0 : session.cache.width;
    }

    public static int height(BlockPos pos) {
        Session session = pos == null ? null : SESSIONS.get(pos.asLong());
        return session == null ? 0 : session.cache.height;
    }

    public static boolean frameReady(BlockPos pos) {
        Session session = pos == null ? null : SESSIONS.get(pos.asLong());
        return session != null && session.currentFrame >= 0 && session.currentFrame < session.cache.frameCount
                && session.cache.frames[session.currentFrame] != null;
    }

    public static String label(BlockPos pos) {
        Session session = pos == null ? null : SESSIONS.get(pos.asLong());
        return session == null ? "" : session.cache.id;
    }

    public static void closeSession(BlockPos pos) {
        if (pos == null) return;
        Session session = SESSIONS.remove(pos.asLong());
        if (session != null) session.closeTextureOnly();
    }

    public static void clear() {
        for (Session session : SESSIONS.values()) session.closeTextureOnly();
        SESSIONS.clear();
        for (FrameCache cache : CACHE.values()) cache.close();
        CACHE.clear();
    }

    private static final class Session {
        private final BlockPos tvPos;
        private final FrameCache cache;
        private int currentFrame;
        private String error = "";
        private NativeImageBackedTexture texture;
        private Identifier textureId;
        private int installedFrame = -1;

        private Session(BlockPos tvPos, FrameCache cache) {
            this.tvPos = tvPos;
            this.cache = cache;
        }

        private void prefetch() {
            request(currentFrame);
            request(currentFrame - 1);
            request(currentFrame + 1);
        }

        private void request(int index) {
            if (index < 0 || index >= cache.frameCount || cache.frames[index] != null || cache.requested[index]) return;
            cache.requested[index] = true;
            PacketByteBuf out = PacketByteBufs.create();
            out.writeString(cache.id, 128);
            out.writeVarInt(index);
            out.writeBlockPos(tvPos);
            ClientPlayNetworking.send(VhsRecordingFeature.FRAME_REQUEST, out);
        }

        private Identifier install(int frameIndex) {
            if (frameIndex < 0 || frameIndex >= cache.frameCount) return null;
            byte[] png = cache.frames[frameIndex];
            if (png == null) {
                request(frameIndex);
                return installedFrame >= 0 ? textureId : null;
            }
            if (frameIndex == installedFrame && textureId != null) return textureId;
            try (NativeImage decoded = NativeImage.read(png)) {
                if (decoded.getWidth() != cache.width || decoded.getHeight() != cache.height) {
                    error = "TAPE READ ERROR";
                    return null;
                }
                if (texture == null) {
                    texture = new NativeImageBackedTexture(cache.width, cache.height, false);
                    textureId = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture(
                            "fiven_recorded_vhs_" + Long.toUnsignedString(tvPos.asLong()), texture);
                }
                NativeImage target = texture.getImage();
                if (target == null) {
                    error = "TAPE READ ERROR";
                    return null;
                }
                target.copyFrom(decoded);
                texture.upload();
                installedFrame = frameIndex;
                return textureId;
            } catch (Exception decodeError) {
                error = "TAPE READ ERROR";
                return null;
            }
        }

        private void closeTextureOnly() {
            if (texture != null) {
                try { texture.close(); } catch (Throwable ignored) {}
                texture = null;
                textureId = null;
                installedFrame = -1;
            }
        }
    }

    private static final class FrameCache {
        private final String id;
        private final int width;
        private final int height;
        private final int fps;
        private final int frameCount;
        private final byte[][] frames;
        private final boolean[] requested;

        private FrameCache(String id, int width, int height, int fps, int frameCount) {
            this.id = id;
            this.width = width;
            this.height = height;
            this.fps = fps;
            this.frameCount = frameCount;
            this.frames = new byte[frameCount][];
            this.requested = new boolean[frameCount];
        }

        private boolean matches(int width, int height, int fps, int frameCount) {
            return this.width == width && this.height == height && this.fps == fps && this.frameCount == frameCount;
        }

        private void close() {
            for (int i = 0; i < frames.length; i++) frames[i] = null;
        }
    }
}
