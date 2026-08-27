package ru.fifth.horror.client;

import com.google.gson.Gson;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.Text;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;
import ru.fifth.horror.client.video.VideoInspector;
import ru.fifth.horror.client.video.VideoNativeRuntime;
import ru.fifth.horror.client.video.VideoUploadClient;
import ru.fifth.horror.cutscene.CutsceneDefinition;
import ru.fifth.horror.video.VideoAssetPolicy;
import ru.fifth.horror.video.VideoAssetStore;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Authoring-only recorder that turns a saved Fiven camera cutscene into one real MP4 file. */
public final class VhsRecorderClient {
    public static final int WIDTH = 640;
    public static final int HEIGHT = 360;
    public static final int FPS = 30;
    private static final Gson GSON = new Gson();

    private static CutsceneDefinition scene;
    private static String recordingId = "";
    private static int durationTicks;
    private static int frameCount;
    private static int frameIndex;
    private static FFmpegFrameRecorder recorder;
    private static Java2DFrameConverter converter;
    private static Path output;
    private static boolean recording;

    private VhsRecorderClient() {}

    public static boolean start(CutsceneDefinition source) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (source == null || source.keyframes.isEmpty() || client.player == null || client.world == null) {
            message("§cНельзя записать видео: нет мира или ключевых кадров.");
            return false;
        }
        if (recording || VideoUploadClient.active()) {
            message("§eЗапись/загрузка видео уже выполняется.");
            return false;
        }
        if (!VideoNativeRuntime.available()) {
            message("§c" + VideoNativeRuntime.failureMessage());
            return false;
        }

        CutsceneDefinition copy = GSON.fromJson(GSON.toJson(source), CutsceneDefinition.class);
        int total = 0;
        for (CutsceneDefinition.Keyframe keyframe : copy.keyframes) total += Math.max(1, keyframe.durationTicks);
        long frames = Math.max(1L, (total * (long) FPS + 19L) / 20L);
        if (frames > 108_000L) {
            message("§cКатсцена слишком длинная для одной VHS-записи.");
            return false;
        }

        try {
            scene = copy;
            recordingId = VideoAssetPolicy.safeId(copy.id);
            durationTicks = Math.max(1, total);
            frameCount = (int) frames;
            frameIndex = 0;

            Path tempDir = client.runDirectory.toPath().resolve("fiven").resolve("video-temp");
            Files.createDirectories(tempDir);
            output = tempDir.resolve(recordingId + "-" + System.currentTimeMillis() + ".mp4");

            recorder = new FFmpegFrameRecorder(output.toFile(), WIDTH, HEIGHT, 0);
            recorder.setFormat("mp4");
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
            recorder.setFrameRate(FPS);
            recorder.setVideoBitrate(2_500_000);
            recorder.setGopSize(FPS * 2);
            recorder.start();
            converter = new Java2DFrameConverter();
            recording = true;
            client.setScreen(null);
            message("§aЗаписываю MP4... §f0% §7(640×360 / 30 FPS)");
            message("§8Эта запись катсцены содержит видео без общего Minecraft-аудиомикса.");
            return true;
        } catch (Throwable error) {
            closeRecorder();
            resetState();
            message("§cНе удалось запустить MP4-кодировщик: " + concise(error));
            return false;
        }
    }

    /** Called after the normal world frame. One callback encodes one media frame with an explicit timestamp. */
    public static void captureNext(float tickDelta) {
        if (!recording || scene == null || recorder == null) return;
        if (frameIndex >= frameCount) {
            finishRecording();
            return;
        }

        double mediaTick = frameIndex * 20.0 / FPS;
        VhsPlayback.Sample camera = sample(scene, mediaTick);
        NativeImage nativeImage = VhsWorldCapture.captureFrame(camera, tickDelta);
        if (nativeImage == null) {
            fail("Не удалось отрисовать кадр MP4 #" + frameIndex + ".");
            return;
        }

        try (nativeImage) {
            BufferedImage buffered = toBufferedImage(nativeImage);
            recorder.setTimestamp(Math.round(frameIndex * 1_000_000.0 / FPS));
            recorder.record(converter.convert(buffered));
            frameIndex++;
            int percent = Math.min(100, Math.round(frameIndex * 100f / frameCount));
            if (frameIndex == frameCount || frameIndex == 1 || percent % 10 == 0) {
                message("§7Записываю MP4... §f" + percent + "% §8(" + frameIndex + "/" + frameCount + ")");
            }
            if (frameIndex >= frameCount) finishRecording();
        } catch (Throwable error) {
            fail("Ошибка кодирования MP4: " + concise(error));
        }
    }

    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (recording && (client.world == null || client.player == null)) fail("Запись MP4 прервана: выход из мира.");
    }

    public static boolean active() { return recording; }

    private static void finishRecording() {
        if (!recording) return;
        recording = false;
        Path completed = output;
        String id = recordingId;
        try {
            closeRecorder();
            message("§7MP4 готов, проверяю медиапоток...");
            VideoAssetStore.Metadata metadata = VideoInspector.inspect(completed, id, VideoAssetStore.Origin.CUTSCENE_RECORDING);
            resetState();
            if (!VideoUploadClient.start(completed, metadata)) {
                message("§cНе удалось начать загрузку готового MP4.");
            } else {
                message("§7MP4 готов, загружаю на сервер...");
            }
        } catch (Throwable error) {
            closeRecorder();
            resetState();
            message("§cГотовый MP4 не прошёл проверку FFmpeg: " + concise(error));
        }
    }

    private static BufferedImage toBufferedImage(NativeImage image) {
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int abgr = image.getColor(x, y);
                int a = (abgr >>> 24) & 0xFF;
                int b = (abgr >>> 16) & 0xFF;
                int g = (abgr >>> 8) & 0xFF;
                int r = abgr & 0xFF;
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    private static void fail(String text) {
        recording = false;
        closeRecorder();
        resetState();
        message("§c" + text);
    }

    private static void closeRecorder() {
        if (recorder != null) {
            try { recorder.stop(); } catch (Throwable ignored) {}
            try { recorder.release(); } catch (Throwable ignored) {}
            recorder = null;
        }
        if (converter != null) {
            try { converter.close(); } catch (Throwable ignored) {}
            converter = null;
        }
    }

    private static void resetState() {
        scene = null;
        recordingId = "";
        durationTicks = 0;
        frameCount = 0;
        frameIndex = 0;
        output = null;
        recording = false;
    }

    private static void message(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) client.player.sendMessage(Text.literal(text), true);
    }

    private static String concise(Throwable error) {
        String value = error.getMessage();
        return value == null || value.isBlank() ? error.getClass().getSimpleName() : value;
    }

    private static VhsPlayback.Sample sample(CutsceneDefinition source, double localTick) {
        if (source.keyframes.isEmpty()) return new VhsPlayback.Sample(0, 0, 0, 0, 0, 70);
        double acc = 0;
        for (int i = 0; i < source.keyframes.size(); i++) {
            CutsceneDefinition.Keyframe a = source.keyframes.get(i);
            double duration = Math.max(1, a.durationTicks);
            if (localTick < acc + duration) {
                CutsceneDefinition.Keyframe b = i + 1 < source.keyframes.size() ? source.keyframes.get(i + 1) : a;
                float q = (float) Math.max(0.0, Math.min(1.0, (localTick - acc) / duration));
                return new VhsPlayback.Sample(
                        lerp(a.x, b.x, q), lerp(a.y, b.y, q), lerp(a.z, b.z, q),
                        lerpAngle(a.yaw, b.yaw, q), lerpFloat(a.pitch, b.pitch, q), lerp(a.fov, b.fov, q));
            }
            acc += duration;
        }
        CutsceneDefinition.Keyframe last = source.keyframes.get(source.keyframes.size() - 1);
        return new VhsPlayback.Sample(last.x, last.y, last.z, last.yaw, last.pitch, last.fov);
    }

    private static double lerp(double a, double b, float q) { return a + (b - a) * q; }
    private static float lerpFloat(float a, float b, float q) { return a + (b - a) * q; }
    private static float lerpAngle(float a, float b, float q) {
        float d = (b - a) % 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return a + d * q;
    }
}
