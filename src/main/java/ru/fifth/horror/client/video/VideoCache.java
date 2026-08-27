package ru.fifth.horror.client.video;

import net.minecraft.client.MinecraftClient;
import ru.fifth.horror.video.VideoAssetPolicy;
import ru.fifth.horror.video.VideoAssetStore;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Hash-addressed client cache for downloaded media assets. */
public final class VideoCache {
    private VideoCache() {}

    public static Path root() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("fiven").resolve("video-cache");
    }

    public static Path path(VideoAssetStore.Metadata metadata) {
        return root().resolve(metadata.sha256().toLowerCase(java.util.Locale.ROOT) + "." + metadata.container());
    }

    public static Path partial(VideoAssetStore.Metadata metadata) {
        return root().resolve("." + metadata.sha256().toLowerCase(java.util.Locale.ROOT) + ".part");
    }

    public static boolean valid(VideoAssetStore.Metadata metadata) {
        Path file = path(metadata);
        try {
            return Files.isRegularFile(file)
                    && Files.size(file) == metadata.byteLength()
                    && metadata.sha256().equalsIgnoreCase(VideoAssetPolicy.sha256(file));
        } catch (IOException error) {
            return false;
        }
    }

    public static void resetPartial(VideoAssetStore.Metadata metadata) throws IOException {
        Files.createDirectories(root());
        Files.deleteIfExists(partial(metadata));
        Files.createFile(partial(metadata));
    }

    public static boolean append(VideoAssetStore.Metadata metadata, long offset, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || !VideoAssetPolicy.validChunk(bytes.length)) return false;
        Path file = partial(metadata);
        try {
            if (!Files.isRegularFile(file) || Files.size(file) != offset) return false;
            if (offset + bytes.length > metadata.byteLength()) return false;
            Files.write(file, bytes, StandardOpenOption.APPEND);
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    public static boolean publish(VideoAssetStore.Metadata metadata) {
        Path temp = partial(metadata);
        Path finalPath = path(metadata);
        try {
            if (!Files.isRegularFile(temp) || Files.size(temp) != metadata.byteLength()) return false;
            if (!metadata.sha256().equalsIgnoreCase(VideoAssetPolicy.sha256(temp))) return false;
            Files.createDirectories(root());
            Files.deleteIfExists(finalPath);
            try {
                Files.move(temp, finalPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, finalPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    public static void discardPartial(VideoAssetStore.Metadata metadata) {
        try { Files.deleteIfExists(partial(metadata)); } catch (IOException ignored) {}
    }
}
