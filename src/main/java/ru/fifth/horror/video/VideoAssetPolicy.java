package ru.fifth.horror.video;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/** Pure validation and hashing rules for real-video assets. */
public final class VideoAssetPolicy {
    public static final long MAX_ASSET_BYTES = 512L * 1024L * 1024L;
    public static final int CHUNK_BYTES = 64 * 1024;
    private static final Set<String> EXTENSIONS = Set.of("mp4", "mov", "mkv", "webm", "avi", "m4v");

    private VideoAssetPolicy() {}

    public static String safeId(String value) {
        if (value == null) return "recording";
        String safe = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        safe = safe.replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        return safe.isBlank() ? "recording" : safe;
    }

    public static boolean allowedExtension(String fileName) {
        if (fileName == null) return false;
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return false;
        return EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    public static String extension(String fileName) {
        if (!allowedExtension(fileName)) return "";
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean validDeclaredSize(long bytes) {
        return bytes > 0 && bytes <= MAX_ASSET_BYTES;
    }

    public static boolean validChunk(int bytes) {
        return bytes > 0 && bytes <= CHUNK_BYTES;
    }

    public static long nextOffset(long offset, int bytes) {
        if (offset < 0 || !validChunk(bytes)) throw new IllegalArgumentException("invalid video chunk");
        return Math.addExact(offset, bytes);
    }

    public static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path)) {
                byte[] buffer = new byte[CHUNK_BYTES];
                int read;
                while ((read = in.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
