package ru.fifth.horror.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import ru.fifth.horror.vhs.VhsRecordingFeature;
import ru.fifth.horror.vhs.VhsRecordingPolicy;

import java.util.HashMap;
import java.util.Map;

/** Plays immutable PNG frames recorded during authoring; never renders the live world. */
public final class VhsRecordedPlayback {
    public static final int STATIC_TICKS = 30;
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
        Session old = SESSIONS.put(tvPos.asLong(), new Session(tvPos.toImmutable(), cache, Math.max(1, durationTicks)));
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

    public static void tick() {
        var it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Session session = it.next().getValue();
            session.tick();
            if (session.finished()) {
                session.closeTextureOnly();
                it.remove();
            }
        }
    }

    public static boolean hasSession(BlockPos pos) {
        return pos != null && SESSIONS.containsKey(pos.asLong());
    }

    public static boolean staticPhase(BlockPos pos) {
        Session session = pos == null ? null : SESSIONS.get(pos.asLong());
        return session != null && session.ticks < STATIC_TICKS;
    }

    public static boolean recordingPhase(BlockPos pos) {
        Session session = pos == null ? null : SESSIONS.get(pos.asLong());
        return session != null && session.ticks >= STATIC_TICKS && session.ticks < STATIC_TICKS + session.durationTicks;
    }

    public static String error(BlockPos pos) {
        Session session = pos == null ? null : SESSIONS.get(pos.asLong());
        return session == null ? "" : session.error;
    }

    public static Identifier texture(BlockPos pos) {
        Session session = pos == null ? null : SESSIONS.get(pos.asLong());
        if (session == null || !recordingPhase(pos) || !session.error.isBlank()) return null;
        int localTick = Math.max(0, session.ticks - STATIC_TICKS);
        int frameIndex = VhsRecordingPolicy.frameIndexForTick(localTick, session.cache.fps, session.cache.frameCount);
        return session.install(frameIndex);
    }

    public static String label(BlockPos pos) {
        Session session = pos == null ? null : SESSIONS.get(pos.asLong());
        return session == null ? "" : session.cache.id;
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
        private final int durationTicks;
        private int ticks;
        private int staticPrefetch;
        private String error = "";
        private NativeImageBackedTexture texture;
        private Identifier textureId;
        private int installedFrame = -1;

        private Session(BlockPos tvPos, FrameCache cache, int durationTicks) {
            this.tvPos = tvPos;
            this.cache = cache;
            this.durationTicks = durationTicks;
        }

        private void tick() {
            if (ticks < STATIC_TICKS) {
                request(staticPrefetch++);
            } else if (ticks < STATIC_TICKS + durationTicks && error.isBlank()) {
                int localTick = ticks - STATIC_TICKS;
                int current = VhsRecordingPolicy.frameIndexForTick(localTick, cache.fps, cache.frameCount);
                request(current);
                request(current + 1);
                request(current + 2);
            }
            ticks++;
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
                return textureId;
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

        private boolean finished() {
            return ticks >= STATIC_TICKS + durationTicks + 10;
        }

        private void closeTextureOnly() {
            if (texture != null) {
                try { texture.close(); } catch (Throwable ignored) {}
                texture = null;
                textureId = null;
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
