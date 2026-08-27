package ru.fifth.horror.client.video;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import ru.fifth.horror.video.VideoAssetPolicy;
import ru.fifth.horror.video.VideoAssetStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads trustworthy media metadata from a completed local file before it is uploaded. */
public final class VideoInspector {
    private VideoInspector() {}

    public static VideoAssetStore.Metadata inspect(Path file, String requestedId, VideoAssetStore.Origin origin) throws Exception {
        if (file == null || !Files.isRegularFile(file)) throw new IOException("Видео-файл не найден.");
        if (!VideoAssetPolicy.allowedExtension(file.getFileName().toString())) throw new IOException("Неподдерживаемый формат видео.");
        long size = Files.size(file);
        if (!VideoAssetPolicy.validDeclaredSize(size)) throw new IOException("Видео должно быть от 1 байта до 512 MiB.");
        if (!VideoNativeRuntime.available()) throw new IOException(VideoNativeRuntime.failureMessage());

        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(file.toFile());
        try {
            grabber.start();
            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();
            long duration = grabber.getLengthInTime();
            if (width <= 0 || height <= 0 || duration <= 0) {
                throw new IOException("FFmpeg не нашёл декодируемый видеопоток.");
            }
            int channels = Math.max(0, grabber.getAudioChannels());
            int sampleRate = channels > 0 ? Math.max(0, grabber.getSampleRate()) : 0;
            String fileName = file.getFileName().toString();
            return new VideoAssetStore.Metadata(
                    VideoAssetPolicy.safeId(requestedId),
                    fileName,
                    VideoAssetPolicy.extension(fileName),
                    width,
                    height,
                    duration,
                    channels > 0,
                    channels,
                    sampleRate,
                    size,
                    VideoAssetPolicy.sha256(file),
                    origin);
        } finally {
            try { grabber.stop(); } catch (Throwable ignored) {}
            try { grabber.release(); } catch (Throwable ignored) {}
        }
    }
}
