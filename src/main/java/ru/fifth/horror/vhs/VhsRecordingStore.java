package ru.fifth.horror.vhs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Locale;

/**
 * Filesystem-backed, self-contained VHS recording store.
 * Uploads live in a temporary directory and become playable only after every declared frame exists.
 */
public final class VhsRecordingStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path root;

    public VhsRecordingStore(Path root) {
        this.root = root;
    }

    public synchronized boolean beginUpload(Metadata metadata) {
        if (metadata == null || !safeId(metadata.id()).equals(metadata.id())
                || !VhsRecordingPolicy.validMetadata(metadata.width(), metadata.height(), metadata.fps(), metadata.frameCount())) {
            return false;
        }
        try {
            Files.createDirectories(root);
            Path tmp = uploadDir(metadata.id());
            deleteTree(tmp);
            Files.createDirectories(tmp);
            Files.writeString(tmp.resolve("metadata.json"), GSON.toJson(metadata), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public synchronized boolean writeFrame(String id, int frameIndex, byte[] png) {
        id = safeId(id);
        if (png == null || png.length == 0 || png.length > VhsRecordingPolicy.MAX_FRAME_BYTES) return false;
        Metadata metadata = readMetadata(uploadDir(id));
        if (metadata == null || frameIndex < 0 || frameIndex >= metadata.frameCount()) return false;
        Path file = uploadDir(id).resolve(frameName(frameIndex));
        if (Files.exists(file)) return false;
        try {
            if (currentUploadBytes(id) + png.length > VhsRecordingPolicy.MAX_RECORDING_BYTES) return false;
            Files.write(file, png, StandardOpenOption.CREATE_NEW);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public synchronized boolean finishUpload(String id) {
        id = safeId(id);
        Path tmp = uploadDir(id);
        Metadata metadata = readMetadata(tmp);
        if (metadata == null) return false;
        for (int i = 0; i < metadata.frameCount(); i++) {
            if (!Files.isRegularFile(tmp.resolve(frameName(i)))) return false;
        }
        try {
            Path dst = recordingDir(id);
            deleteTree(dst);
            try {
                Files.move(tmp, dst, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, dst);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public synchronized boolean isComplete(String id) {
        id = safeId(id);
        Path dir = recordingDir(id);
        Metadata metadata = readMetadata(dir);
        if (metadata == null || !VhsRecordingPolicy.validMetadata(metadata.width(), metadata.height(), metadata.fps(), metadata.frameCount())) return false;
        int found = 0;
        for (int i = 0; i < metadata.frameCount(); i++) {
            if (!Files.isRegularFile(dir.resolve(frameName(i)))) return false;
            found++;
        }
        return VhsRecordingPolicy.isComplete(metadata.frameCount(), found);
    }

    public synchronized Metadata metadata(String id) {
        id = safeId(id);
        Metadata value = readMetadata(recordingDir(id));
        return value != null && isComplete(id) ? value : null;
    }

    public synchronized byte[] readFrame(String id, int frameIndex) {
        Metadata metadata = metadata(id);
        if (metadata == null || frameIndex < 0 || frameIndex >= metadata.frameCount()) return null;
        try {
            byte[] bytes = Files.readAllBytes(recordingDir(safeId(id)).resolve(frameName(frameIndex)));
            return bytes.length > 0 && bytes.length <= VhsRecordingPolicy.MAX_FRAME_BYTES ? bytes : null;
        } catch (IOException e) {
            return null;
        }
    }

    public synchronized void abortUpload(String id) {
        try { deleteTree(uploadDir(safeId(id))); } catch (IOException ignored) {}
    }

    private long currentUploadBytes(String id) throws IOException {
        Path dir = uploadDir(id);
        if (!Files.isDirectory(dir)) return 0L;
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().startsWith("frame_")).mapToLong(p -> {
                try { return Files.size(p); } catch (IOException e) { return 0L; }
            }).sum();
        }
    }

    private Metadata readMetadata(Path dir) {
        try {
            Path file = dir.resolve("metadata.json");
            if (!Files.isRegularFile(file)) return null;
            Metadata metadata = GSON.fromJson(Files.readString(file), Metadata.class);
            if (metadata == null || !safeId(metadata.id()).equals(metadata.id())) return null;
            return metadata;
        } catch (Exception e) {
            return null;
        }
    }

    private Path uploadDir(String id) { return root.resolve(".upload-" + safeId(id)); }
    private Path recordingDir(String id) { return root.resolve(safeId(id)); }
    private static String frameName(int index) { return String.format(Locale.ROOT, "frame_%05d.png", index); }

    public static String safeId(String value) {
        if (value == null) return "recording";
        String s = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        return s.isBlank() ? "recording" : s;
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    public record Metadata(String id, int width, int height, int fps, int frameCount, int durationTicks) {}
}
