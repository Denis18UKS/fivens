package ru.fifth.horror.video;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Server-side real-video asset store. This class never touches JavaCV/FFmpeg natives. */
public final class VideoAssetStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum Origin { CUTSCENE_RECORDING, IMPORTED_FILE }

    public record Metadata(
            String id,
            String fileName,
            String container,
            int width,
            int height,
            long durationMicros,
            boolean hasAudio,
            int audioChannels,
            int audioSampleRate,
            long byteLength,
            String sha256,
            Origin origin
    ) {}

    private final Path videosRoot;
    private final Path legacyRoot;

    public VideoAssetStore(Path videosRoot, Path legacyRoot) {
        this.videosRoot = videosRoot;
        this.legacyRoot = legacyRoot;
    }

    public synchronized boolean beginUpload(Metadata metadata) {
        if (!validMetadata(metadata)) return false;
        String id = metadata.id();
        if (Files.exists(assetDir(id))) return false;
        Path temp = uploadDir(id);
        try {
            Files.createDirectories(videosRoot);
            deleteTree(temp);
            Files.createDirectories(temp);
            Files.writeString(temp.resolve("metadata.json"), GSON.toJson(metadata),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.createFile(uploadMediaPath(metadata));
            return true;
        } catch (IOException error) {
            try { deleteTree(temp); } catch (IOException ignored) {}
            return false;
        }
    }

    public synchronized boolean writeChunk(String rawId, long offset, byte[] bytes) {
        String id = VideoAssetPolicy.safeId(rawId);
        if (bytes == null || !VideoAssetPolicy.validChunk(bytes.length) || offset < 0) return false;
        Metadata metadata = readMetadata(uploadDir(id));
        if (metadata == null || !metadata.id().equals(id)) return false;
        Path file = uploadMediaPath(metadata);
        try {
            long current = Files.size(file);
            if (current != offset) return false;
            long next = Math.addExact(offset, bytes.length);
            if (next > metadata.byteLength()) return false;
            Files.write(file, bytes, StandardOpenOption.APPEND);
            return true;
        } catch (IOException | ArithmeticException error) {
            return false;
        }
    }

    public synchronized boolean finishUpload(String rawId) {
        String id = VideoAssetPolicy.safeId(rawId);
        Path temp = uploadDir(id);
        Metadata metadata = readMetadata(temp);
        if (metadata == null || !metadata.id().equals(id)) return false;
        Path partial = uploadMediaPath(metadata);
        try {
            if (!Files.isRegularFile(partial) || Files.size(partial) != metadata.byteLength()) return false;
            if (!metadata.sha256().equalsIgnoreCase(VideoAssetPolicy.sha256(partial))) return false;
            Path publishedMedia = temp.resolve(mediaName(metadata));
            Files.move(partial, publishedMedia, StandardCopyOption.REPLACE_EXISTING);
            Path destination = assetDir(id);
            if (Files.exists(destination)) return false;
            try {
                Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, destination);
            }
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    public synchronized void abortUpload(String rawId) {
        try { deleteTree(uploadDir(VideoAssetPolicy.safeId(rawId))); } catch (IOException ignored) {}
    }

    public synchronized boolean isComplete(String rawId) {
        Metadata metadata = metadata(rawId);
        if (metadata == null) return false;
        Path media = mediaPath(metadata);
        try {
            return Files.isRegularFile(media)
                    && Files.size(media) == metadata.byteLength()
                    && metadata.sha256().equalsIgnoreCase(VideoAssetPolicy.sha256(media));
        } catch (IOException error) {
            return false;
        }
    }

    public synchronized Metadata metadata(String rawId) {
        String id = VideoAssetPolicy.safeId(rawId);
        Metadata metadata = readMetadata(assetDir(id));
        return metadata != null && metadata.id().equals(id) && validMetadata(metadata) ? metadata : null;
    }

    public synchronized Path mediaPath(String rawId) {
        Metadata metadata = metadata(rawId);
        return metadata == null ? assetDir(VideoAssetPolicy.safeId(rawId)).resolve("media.bin") : mediaPath(metadata);
    }

    public synchronized byte[] readChunk(String rawId, long offset) {
        Metadata metadata = metadata(rawId);
        if (metadata == null || offset < 0 || offset >= metadata.byteLength()) return null;
        Path file = mediaPath(metadata);
        int length = (int) Math.min(VideoAssetPolicy.CHUNK_BYTES, metadata.byteLength() - offset);
        byte[] out = new byte[length];
        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
            input.seek(offset);
            input.readFully(out);
            return out;
        } catch (IOException error) {
            return null;
        }
    }

    public synchronized boolean isLegacyOnly(String rawId) {
        String id = VideoAssetPolicy.safeId(rawId);
        return metadata(id) == null && legacyRoot != null && Files.isDirectory(legacyRoot.resolve(id));
    }

    public synchronized List<Metadata> list() {
        List<Metadata> result = new ArrayList<>();
        if (!Files.isDirectory(videosRoot)) return result;
        try (var stream = Files.list(videosRoot)) {
            for (Path path : stream.filter(Files::isDirectory).sorted().toList()) {
                if (path.getFileName().toString().startsWith(".upload-")) continue;
                Metadata metadata = readMetadata(path);
                if (metadata != null && validMetadata(metadata)) result.add(metadata);
            }
        } catch (IOException ignored) {}
        return result;
    }

    public synchronized boolean delete(String rawId) {
        try {
            Path dir = assetDir(VideoAssetPolicy.safeId(rawId));
            if (!Files.exists(dir)) return false;
            deleteTree(dir);
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    private boolean validMetadata(Metadata metadata) {
        if (metadata == null || metadata.origin() == null) return false;
        String id = VideoAssetPolicy.safeId(metadata.id());
        if (!id.equals(metadata.id())) return false;
        if (!VideoAssetPolicy.allowedExtension(metadata.fileName())) return false;
        String extension = VideoAssetPolicy.extension(metadata.fileName());
        if (metadata.container() == null || !extension.equals(metadata.container().toLowerCase(Locale.ROOT))) return false;
        if (metadata.width() <= 0 || metadata.height() <= 0 || metadata.width() > 8192 || metadata.height() > 8192) return false;
        if (metadata.durationMicros() <= 0 || !VideoAssetPolicy.validDeclaredSize(metadata.byteLength())) return false;
        if (metadata.sha256() == null || !metadata.sha256().matches("(?i)[0-9a-f]{64}")) return false;
        if (metadata.audioChannels() < 0 || metadata.audioChannels() > 8 || metadata.audioSampleRate() < 0 || metadata.audioSampleRate() > 384000) return false;
        return metadata.hasAudio() || (metadata.audioChannels() == 0 && metadata.audioSampleRate() == 0);
    }

    private Metadata readMetadata(Path directory) {
        try {
            Path file = directory.resolve("metadata.json");
            if (!Files.isRegularFile(file)) return null;
            return GSON.fromJson(Files.readString(file), Metadata.class);
        } catch (Exception error) {
            return null;
        }
    }

    private Path uploadDir(String id) { return videosRoot.resolve(".upload-" + id); }
    private Path assetDir(String id) { return videosRoot.resolve(id); }
    private Path uploadMediaPath(Metadata metadata) { return uploadDir(metadata.id()).resolve(mediaName(metadata) + ".part"); }
    private Path mediaPath(Metadata metadata) { return assetDir(metadata.id()).resolve(mediaName(metadata)); }
    private static String mediaName(Metadata metadata) { return "media." + VideoAssetPolicy.extension(metadata.fileName()); }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
