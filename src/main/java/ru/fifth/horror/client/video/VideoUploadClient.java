package ru.fifth.horror.client.video;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import ru.fifth.horror.video.VideoAssetPolicy;
import ru.fifth.horror.video.VideoAssetStore;
import ru.fifth.horror.video.VideoFeature;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

/** Sequential 64-KiB uploader shared by cutscene MP4 recording and external video import. */
public final class VideoUploadClient {
    private static Upload active;

    private VideoUploadClient() {}

    public static boolean start(Path file, VideoAssetStore.Metadata metadata) {
        if (active != null) {
            message("§eУже загружается другое видео.");
            return false;
        }
        if (file == null || metadata == null || !Files.isRegularFile(file)) {
            message("§cНе найден готовый видеофайл.");
            return false;
        }
        active = new Upload(file, metadata, metadata.id(), 1);
        sendBegin(active);
        message("§7MP4/видео готово, запрашиваю загрузку на сервер...");
        return true;
    }

    public static void handleStatus(String phase, String id, boolean success, String serverMessage) {
        Upload upload = active;
        if (upload == null || !upload.id.equals(id)) return;
        if ("begin".equals(phase)) {
            if (!success) {
                if (serverMessage != null && serverMessage.contains("VIDEO ID EXISTS") && upload.suffixAttempt < 100) {
                    String nextId = VideoAssetPolicy.safeId(upload.baseId + "_" + (upload.suffixAttempt + 1));
                    active = upload.withId(nextId, upload.suffixAttempt + 1);
                    sendBegin(active);
                    message("§eID занят, пробую §f" + nextId);
                    return;
                }
                fail(serverMessage);
                return;
            }
            upload.ready = true;
            message("§7Загрузка видео на сервер: §f0%");
            return;
        }
        if ("error".equals(phase)) {
            fail(serverMessage);
            return;
        }
        if ("finish".equals(phase)) {
            String finished = upload.id;
            active = null;
            message((success ? "§a" : "§c") + (serverMessage == null ? "" : serverMessage)
                    + (success ? " §7[§f" + finished + "§7]" : ""));
        }
    }

    public static void tick() {
        Upload upload = active;
        MinecraftClient client = MinecraftClient.getInstance();
        if (upload == null) return;
        if (client.world == null || client.player == null) {
            active = null;
            return;
        }
        if (!upload.ready || upload.finishing) return;
        try {
            long size = upload.metadata.byteLength();
            if (upload.offset >= size) {
                upload.finishing = true;
                PacketByteBuf out = PacketByteBufs.create();
                out.writeString(upload.id, 128);
                ClientPlayNetworking.send(VideoFeature.UPLOAD_FINISH, out);
                message("§7Проверка SHA-256 и создание VHS...");
                return;
            }
            int length = (int) Math.min(VideoAssetPolicy.CHUNK_BYTES, size - upload.offset);
            byte[] bytes = new byte[length];
            try (RandomAccessFile input = new RandomAccessFile(upload.file.toFile(), "r")) {
                input.seek(upload.offset);
                input.readFully(bytes);
            }
            PacketByteBuf out = PacketByteBufs.create();
            out.writeString(upload.id, 128);
            out.writeLong(upload.offset);
            out.writeByteArray(bytes);
            ClientPlayNetworking.send(VideoFeature.UPLOAD_CHUNK, out);
            upload.offset += bytes.length;
            int percent = (int) Math.min(100L, upload.offset * 100L / Math.max(1L, size));
            if (percent >= upload.nextReportedPercent) {
                message("§7Загрузка видео: §f" + percent + "%");
                upload.nextReportedPercent = Math.min(100, ((percent / 10) + 1) * 10);
            }
        } catch (Exception error) {
            fail("Не удалось прочитать локальное видео: " + error.getMessage());
        }
    }

    public static boolean active() { return active != null; }

    private static void sendBegin(Upload upload) {
        PacketByteBuf out = PacketByteBufs.create();
        VideoFeature.writeMetadata(out, metadataWithId(upload.metadata, upload.id));
        ClientPlayNetworking.send(VideoFeature.UPLOAD_BEGIN, out);
    }

    private static VideoAssetStore.Metadata metadataWithId(VideoAssetStore.Metadata source, String id) {
        return new VideoAssetStore.Metadata(id, source.fileName(), source.container(), source.width(), source.height(),
                source.durationMicros(), source.hasAudio(), source.audioChannels(), source.audioSampleRate(),
                source.byteLength(), source.sha256(), source.origin());
    }

    private static void fail(String text) {
        active = null;
        message("§c" + (text == null || text.isBlank() ? "Ошибка загрузки видео." : text));
    }

    private static void message(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) client.player.sendMessage(Text.literal(text), true);
    }

    private static final class Upload {
        final Path file;
        final VideoAssetStore.Metadata metadata;
        final String baseId;
        final String id;
        final int suffixAttempt;
        long offset;
        int nextReportedPercent = 10;
        boolean ready;
        boolean finishing;

        Upload(Path file, VideoAssetStore.Metadata metadata, String id, int suffixAttempt) {
            this.file = file;
            this.metadata = metadata;
            this.baseId = metadata.id();
            this.id = id;
            this.suffixAttempt = suffixAttempt;
        }

        Upload withId(String nextId, int nextAttempt) {
            return new Upload(file, metadata, nextId, nextAttempt);
        }
    }
}
