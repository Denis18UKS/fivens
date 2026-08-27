package ru.fifth.horror.vhs;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.item.VhsCassetteItem;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Server half of the real stored-frame VHS recorder/playback protocol. */
public final class VhsRecordingFeature implements ModInitializer {
    public static final Identifier RECORD_BEGIN = FifthMod.id("vhs_record_begin");
    public static final Identifier RECORD_FRAME = FifthMod.id("vhs_record_frame");
    public static final Identifier RECORD_FINISH = FifthMod.id("vhs_record_finish");
    public static final Identifier RECORD_ACK = FifthMod.id("vhs_record_ack");
    public static final Identifier PLAYBACK_START = FifthMod.id("vhs_playback_start");
    public static final Identifier FRAME_REQUEST = FifthMod.id("vhs_frame_request");
    public static final Identifier FRAME_DATA = FifthMod.id("vhs_frame_data");
    public static final Identifier PLAYBACK_ERROR = FifthMod.id("vhs_playback_error");

    private static final Map<MinecraftServer, VhsRecordingStore> STORES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<UUID, String> ACTIVE_UPLOAD = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> PLAYBACK_ALLOWED = new ConcurrentHashMap<>();
    private static final Map<UUID, RequestWindow> REQUEST_WINDOWS = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STORES.remove(server);
            ACTIVE_UPLOAD.clear();
            PLAYBACK_ALLOWED.clear();
            REQUEST_WINDOWS.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            ACTIVE_UPLOAD.remove(id);
            PLAYBACK_ALLOWED.remove(id);
            REQUEST_WINDOWS.remove(id);
        });

        ServerPlayNetworking.registerGlobalReceiver(RECORD_BEGIN, (server, player, handler, buf, sender) -> {
            String id = buf.readString(128);
            int width = buf.readVarInt();
            int height = buf.readVarInt();
            int fps = buf.readVarInt();
            int frameCount = buf.readVarInt();
            int durationTicks = buf.readVarInt();
            server.execute(() -> {
                if (!player.hasPermissionLevel(2)) {
                    sendAck(player, "begin", id, false, "Нужны права режиссёра (permission 2).");
                    return;
                }
                String safe = VhsRecordingStore.safeId(id);
                VhsRecordingStore.Metadata metadata = new VhsRecordingStore.Metadata(
                        safe, width, height, fps, frameCount, Math.max(1, durationTicks));
                boolean ok = safe.equals(id) && store(server).beginUpload(metadata);
                if (ok) ACTIVE_UPLOAD.put(player.getUuid(), safe);
                sendAck(player, "begin", safe, ok, ok ? "Запись VHS начата." : "Параметры VHS отклонены.");
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RECORD_FRAME, (server, player, handler, buf, sender) -> {
            String id = buf.readString(128);
            int frameIndex = buf.readVarInt();
            byte[] png;
            try {
                png = buf.readByteArray(VhsRecordingPolicy.MAX_FRAME_BYTES);
            } catch (RuntimeException malformed) {
                return;
            }
            server.execute(() -> {
                String safe = VhsRecordingStore.safeId(id);
                if (!player.hasPermissionLevel(2) || !safe.equals(ACTIVE_UPLOAD.get(player.getUuid()))) return;
                if (!store(server).writeFrame(safe, frameIndex, png)) {
                    sendAck(player, "error", safe, false, "Не удалось сохранить кадр VHS #" + frameIndex + ".");
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RECORD_FINISH, (server, player, handler, buf, sender) -> {
            String id = VhsRecordingStore.safeId(buf.readString(128));
            server.execute(() -> {
                if (!player.hasPermissionLevel(2) || !id.equals(ACTIVE_UPLOAD.get(player.getUuid()))) {
                    sendAck(player, "finish", id, false, "Нет активной записи VHS.");
                    return;
                }
                boolean ok = store(server).finishUpload(id);
                ACTIVE_UPLOAD.remove(player.getUuid());
                if (ok) {
                    ItemStack cassette = new ItemStack(FifthMod.VHS_CASSETTE);
                    VhsCassetteItem.setRecording(cassette, id);
                    player.giveItemStack(cassette);
                }
                sendAck(player, "finish", id, ok,
                        ok ? "VHS записана. Кассета выдана." : "Запись неполная: кассета не создана.");
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(FRAME_REQUEST, (server, player, handler, buf, sender) -> {
            String id = VhsRecordingStore.safeId(buf.readString(128));
            int frameIndex = buf.readVarInt();
            server.execute(() -> sendRequestedFrame(server, player, id, frameIndex));
        });
    }

    public static VhsRecordingStore store(MinecraftServer server) {
        return STORES.computeIfAbsent(server, s -> {
            Path root = s.getSavePath(WorldSavePath.ROOT).resolve("fiven").resolve("vhs");
            return new VhsRecordingStore(root);
        });
    }

    public static void sendPlaybackStart(ServerPlayerEntity player, BlockPos tvPos, VhsRecordingStore.Metadata metadata) {
        if (player == null || tvPos == null || metadata == null) return;
        PLAYBACK_ALLOWED.computeIfAbsent(player.getUuid(), ignored -> ConcurrentHashMap.newKeySet()).add(metadata.id());
        PacketByteBuf out = PacketByteBufs.create();
        out.writeString(metadata.id(), 128);
        out.writeVarInt(metadata.width());
        out.writeVarInt(metadata.height());
        out.writeVarInt(metadata.fps());
        out.writeVarInt(metadata.frameCount());
        out.writeVarInt(metadata.durationTicks());
        out.writeBlockPos(tvPos);
        ServerPlayNetworking.send(player, PLAYBACK_START, out);
    }

    public static void sendPlaybackError(ServerPlayerEntity player, BlockPos tvPos, String message) {
        if (player == null || tvPos == null) return;
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBlockPos(tvPos);
        out.writeString(message == null ? "TAPE READ ERROR" : message, 256);
        ServerPlayNetworking.send(player, PLAYBACK_ERROR, out);
    }

    private static void sendRequestedFrame(MinecraftServer server, ServerPlayerEntity player, String id, int frameIndex) {
        Set<String> allowed = PLAYBACK_ALLOWED.get(player.getUuid());
        if (allowed == null || !allowed.contains(id) || !allowRequest(player.getUuid())) return;
        VhsRecordingStore store = store(server);
        VhsRecordingStore.Metadata metadata = store.metadata(id);
        if (metadata == null || frameIndex < 0 || frameIndex >= metadata.frameCount()) return;
        byte[] png = store.readFrame(id, frameIndex);
        if (png == null) {
            sendPlaybackError(player, BlockPos.ORIGIN, "TAPE READ ERROR");
            return;
        }
        PacketByteBuf out = PacketByteBufs.create();
        out.writeString(id, 128);
        out.writeVarInt(frameIndex);
        out.writeByteArray(png);
        ServerPlayNetworking.send(player, FRAME_DATA, out);
    }

    private static boolean allowRequest(UUID player) {
        long now = System.currentTimeMillis();
        RequestWindow window = REQUEST_WINDOWS.computeIfAbsent(player, ignored -> new RequestWindow(now, 0));
        synchronized (window) {
            if (now - window.startedAt >= 1_000L) {
                window.startedAt = now;
                window.count = 0;
            }
            if (window.count >= 64) return false;
            window.count++;
            return true;
        }
    }

    private static void sendAck(ServerPlayerEntity player, String phase, String id, boolean success, String message) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeString(phase, 16);
        out.writeString(id == null ? "recording" : id, 128);
        out.writeBoolean(success);
        out.writeString(message == null ? "" : message, 256);
        ServerPlayNetworking.send(player, RECORD_ACK, out);
    }

    private static final class RequestWindow {
        private long startedAt;
        private int count;
        private RequestWindow(long startedAt, int count) {
            this.startedAt = startedAt;
            this.count = count;
        }
    }
}
