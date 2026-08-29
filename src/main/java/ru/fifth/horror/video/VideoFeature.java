package ru.fifth.horror.video;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
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

/** Server transport and director commands for real encoded video assets. Never loads native FFmpeg. */
public final class VideoFeature implements ModInitializer {
    public static final Identifier UPLOAD_BEGIN = FifthMod.id("video_upload_begin");
    public static final Identifier UPLOAD_CHUNK = FifthMod.id("video_upload_chunk");
    public static final Identifier UPLOAD_FINISH = FifthMod.id("video_upload_finish");
    public static final Identifier UPLOAD_STATUS = FifthMod.id("video_upload_status");
    public static final Identifier PLAYBACK_START = FifthMod.id("video_playback_start");
    public static final Identifier CACHE_STATUS = FifthMod.id("video_cache_status");
    public static final Identifier CHUNK_REQUEST = FifthMod.id("video_chunk_request");
    public static final Identifier CHUNK_DATA = FifthMod.id("video_chunk_data");
    public static final Identifier PLAYBACK_ERROR = FifthMod.id("video_playback_error");

    private static final Map<MinecraftServer, VideoAssetStore> STORES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<UUID, String> ACTIVE_UPLOAD = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> DOWNLOAD_ALLOWED = new ConcurrentHashMap<>();
    private static final Map<UUID, RequestWindow> REQUEST_WINDOWS = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STORES.remove(server);
            ACTIVE_UPLOAD.clear();
            DOWNLOAD_ALLOWED.clear();
            REQUEST_WINDOWS.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID player = handler.player.getUuid();
            String active = ACTIVE_UPLOAD.remove(player);
            if (active != null) store(server).abortUpload(active);
            DOWNLOAD_ALLOWED.remove(player);
            REQUEST_WINDOWS.remove(player);
        });

        ServerPlayNetworking.registerGlobalReceiver(UPLOAD_BEGIN, (server, player, handler, buf, responseSender) -> {
            VideoAssetStore.Metadata metadata;
            try { metadata = readMetadata(buf); } catch (RuntimeException badPacket) { return; }
            server.execute(() -> beginUpload(server, player, metadata));
        });
        ServerPlayNetworking.registerGlobalReceiver(UPLOAD_CHUNK, (server, player, handler, buf, responseSender) -> {
            String id = VideoAssetPolicy.safeId(buf.readString(128));
            long offset = buf.readLong();
            byte[] bytes;
            try { bytes = buf.readByteArray(VideoAssetPolicy.CHUNK_BYTES); } catch (RuntimeException badPacket) { return; }
            server.execute(() -> writeUploadChunk(server, player, id, offset, bytes));
        });
        ServerPlayNetworking.registerGlobalReceiver(UPLOAD_FINISH, (server, player, handler, buf, responseSender) -> {
            String id = VideoAssetPolicy.safeId(buf.readString(128));
            server.execute(() -> finishUpload(server, player, id));
        });
        ServerPlayNetworking.registerGlobalReceiver(CACHE_STATUS, (server, player, handler, buf, responseSender) -> {
            String id = VideoAssetPolicy.safeId(buf.readString(128));
            String sha = buf.readString(64);
            boolean present = buf.readBoolean();
            server.execute(() -> {
                VideoAssetStore.Metadata metadata = store(server).metadata(id);
                if (metadata != null && metadata.sha256().equalsIgnoreCase(sha) && present) {
                    DOWNLOAD_ALLOWED.computeIfAbsent(player.getUuid(), ignored -> ConcurrentHashMap.newKeySet()).add(id);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(CHUNK_REQUEST, (server, player, handler, buf, responseSender) -> {
            String id = VideoAssetPolicy.safeId(buf.readString(128));
            long offset = buf.readLong();
            BlockPos tvPos = buf.readBlockPos();
            server.execute(() -> sendChunk(server, player, id, offset, tvPos));
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("fiven")
                        .then(CommandManager.literal("video")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.literal("list").executes(ctx -> list(ctx.getSource())))
                                .then(CommandManager.literal("info")
                                        .then(CommandManager.argument("id", StringArgumentType.word())
                                                .executes(ctx -> info(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                                .then(CommandManager.literal("cassette")
                                        .then(CommandManager.argument("id", StringArgumentType.word())
                                                .executes(ctx -> cassette(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                                .then(CommandManager.literal("delete")
                                        .then(CommandManager.argument("id", StringArgumentType.word())
                                                .executes(ctx -> delete(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))))));
    }

    public static VideoAssetStore store(MinecraftServer server) {
        return STORES.computeIfAbsent(server, value -> {
            Path fiven = value.getSavePath(WorldSavePath.ROOT).resolve("fiven");
            return new VideoAssetStore(fiven.resolve("videos"), fiven.resolve("vhs"));
        });
    }

    public static void sendPlaybackStart(ServerPlayerEntity player, BlockPos tvPos, VideoAssetStore.Metadata metadata) {
        if (player == null || tvPos == null || metadata == null) return;
        DOWNLOAD_ALLOWED.computeIfAbsent(player.getUuid(), ignored -> ConcurrentHashMap.newKeySet()).add(metadata.id());
        PacketByteBuf out = PacketByteBufs.create();
        writeMetadata(out, metadata);
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

    public static void writeMetadata(PacketByteBuf out, VideoAssetStore.Metadata metadata) {
        out.writeString(metadata.id(), 128);
        out.writeString(metadata.fileName(), 256);
        out.writeString(metadata.container(), 16);
        out.writeVarInt(metadata.width());
        out.writeVarInt(metadata.height());
        out.writeLong(metadata.durationMicros());
        out.writeBoolean(metadata.hasAudio());
        out.writeVarInt(metadata.audioChannels());
        out.writeVarInt(metadata.audioSampleRate());
        out.writeLong(metadata.byteLength());
        out.writeString(metadata.sha256(), 64);
        out.writeEnumConstant(metadata.origin());
    }

    public static VideoAssetStore.Metadata readMetadata(PacketByteBuf in) {
        return new VideoAssetStore.Metadata(
                VideoAssetPolicy.safeId(in.readString(128)),
                in.readString(256),
                in.readString(16),
                in.readVarInt(),
                in.readVarInt(),
                in.readLong(),
                in.readBoolean(),
                in.readVarInt(),
                in.readVarInt(),
                in.readLong(),
                in.readString(64),
                in.readEnumConstant(VideoAssetStore.Origin.class));
    }

    private static void beginUpload(MinecraftServer server, ServerPlayerEntity player, VideoAssetStore.Metadata metadata) {
        if (!player.hasPermissionLevel(2)) {
            sendStatus(player, "begin", metadata.id(), false, "Нужны права режиссёра (permission 2).");
            return;
        }
        String previous = ACTIVE_UPLOAD.remove(player.getUuid());
        if (previous != null) store(server).abortUpload(previous);
        boolean ok = store(server).beginUpload(metadata);
        if (ok) ACTIVE_UPLOAD.put(player.getUuid(), metadata.id());
        String message = ok ? "UPLOAD READY" : (store(server).metadata(metadata.id()) != null ? "VIDEO ID EXISTS" : "VIDEO UPLOAD REJECTED");
        sendStatus(player, "begin", metadata.id(), ok, message);
    }

    private static void writeUploadChunk(MinecraftServer server, ServerPlayerEntity player, String id, long offset, byte[] bytes) {
        if (!player.hasPermissionLevel(2) || !id.equals(ACTIVE_UPLOAD.get(player.getUuid()))) return;
        if (!store(server).writeChunk(id, offset, bytes)) {
            store(server).abortUpload(id);
            ACTIVE_UPLOAD.remove(player.getUuid());
            sendStatus(player, "error", id, false, "VIDEO CHUNK REJECTED @ " + offset);
        }
    }

    private static void finishUpload(MinecraftServer server, ServerPlayerEntity player, String id) {
        if (!player.hasPermissionLevel(2) || !id.equals(ACTIVE_UPLOAD.get(player.getUuid()))) {
            sendStatus(player, "finish", id, false, "Нет активной загрузки видео.");
            return;
        }
        boolean ok = store(server).finishUpload(id);
        ACTIVE_UPLOAD.remove(player.getUuid());
        if (ok) {
            ItemStack cassette = new ItemStack(FifthMod.VHS_CASSETTE);
            VhsCassetteItem.setRecording(cassette, id);
            player.giveItemStack(cassette);
        } else {
            store(server).abortUpload(id);
        }
        sendStatus(player, "finish", id, ok, ok ? "VHS создана: " + id : "Видео не прошло проверку длины/SHA-256.");
    }

    private static void sendChunk(MinecraftServer server, ServerPlayerEntity player, String id, long offset, BlockPos tvPos) {
        Set<String> allowed = DOWNLOAD_ALLOWED.get(player.getUuid());
        VideoAssetStore.Metadata metadata = store(server).metadata(id);
        if (allowed == null || !allowed.contains(id) || metadata == null || !allowRequest(player.getUuid())) return;
        if (offset < 0 || offset >= metadata.byteLength()) {
            sendPlaybackError(player, tvPos, "TAPE READ ERROR: bad video offset");
            return;
        }
        byte[] bytes = store(server).readChunk(id, offset);
        if (bytes == null || bytes.length == 0) {
            sendPlaybackError(player, tvPos, "TAPE READ ERROR: media read failed");
            return;
        }
        PacketByteBuf out = PacketByteBufs.create();
        out.writeString(id, 128);
        out.writeLong(offset);
        out.writeByteArray(bytes);
        out.writeBoolean(offset + bytes.length >= metadata.byteLength());
        ServerPlayNetworking.send(player, CHUNK_DATA, out);
    }

    private static boolean allowRequest(UUID player) {
        long now = System.currentTimeMillis();
        RequestWindow window = REQUEST_WINDOWS.computeIfAbsent(player, ignored -> new RequestWindow(now));
        synchronized (window) {
            if (now - window.startedAt >= 1_000L) {
                window.startedAt = now;
                window.count = 0;
            }
            if (window.count >= 128) return false;
            window.count++;
            return true;
        }
    }

    private static void sendStatus(ServerPlayerEntity player, String phase, String id, boolean success, String message) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeString(phase, 16);
        out.writeString(id == null ? "video" : id, 128);
        out.writeBoolean(success);
        out.writeString(message == null ? "" : message, 256);
        ServerPlayNetworking.send(player, UPLOAD_STATUS, out);
    }

    private static int list(ServerCommandSource source) {
        var entries = store(source.getServer()).list();
        if (entries.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§8[§cFiven/Video§8] §7Видео пока нет."), false);
            return 1;
        }
        source.sendFeedback(() -> Text.literal("§8[§cFiven/Video§8] §7Видео: §f" + entries.stream().map(VideoAssetStore.Metadata::id).toList()), false);
        return entries.size();
    }

    private static int info(ServerCommandSource source, String rawId) {
        String id = VideoAssetPolicy.safeId(rawId);
        VideoAssetStore.Metadata metadata = store(source.getServer()).metadata(id);
        if (metadata == null) {
            source.sendError(Text.literal(store(source.getServer()).isLegacyOnly(id)
                    ? "LEGACY VHS: перезапишите кассету" : "Видео не найдено: " + id));
            return 0;
        }
        String text = String.format(java.util.Locale.ROOT,
                "§8[§cFiven/Video§8] §f%s §7| %dx%d | %.2fs | audio=%s | %.2f MiB | sha=%s",
                metadata.id(), metadata.width(), metadata.height(), metadata.durationMicros() / 1_000_000.0,
                metadata.hasAudio() ? "yes" : "no", metadata.byteLength() / 1048576.0,
                metadata.sha256().substring(0, Math.min(12, metadata.sha256().length())));
        source.sendFeedback(() -> Text.literal(text), false);
        return 1;
    }

    private static int cassette(ServerCommandSource source, String rawId) {
        String id = VideoAssetPolicy.safeId(rawId);
        if (!store(source.getServer()).isComplete(id)) {
            source.sendError(Text.literal(store(source.getServer()).isLegacyOnly(id)
                    ? "LEGACY VHS: перезапишите кассету" : "Готовое видео не найдено: " + id));
            return 0;
        }
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            ItemStack cassette = new ItemStack(FifthMod.VHS_CASSETTE);
            VhsCassetteItem.setRecording(cassette, id);
            player.giveItemStack(cassette);
            source.sendFeedback(() -> Text.literal("§8[§cFiven/Video§8] §aКассета выдана: §f" + id), false);
            return 1;
        } catch (Exception error) {
            source.sendError(Text.literal("Команду cassette нужно выполнять игроком."));
            return 0;
        }
    }

    private static int delete(ServerCommandSource source, String rawId) {
        String id = VideoAssetPolicy.safeId(rawId);
        boolean ok = store(source.getServer()).delete(id);
        if (!ok) {
            source.sendError(Text.literal("Видео не найдено: " + id));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("§8[§cFiven/Video§8] §aУдалено: §f" + id), false);
        return 1;
    }

    private static final class RequestWindow {
        long startedAt;
        int count;
        RequestWindow(long startedAt) { this.startedAt = startedAt; }
    }
}
