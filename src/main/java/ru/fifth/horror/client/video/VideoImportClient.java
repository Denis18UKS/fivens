package ru.fifth.horror.client.video;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.PointerBuffer;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import ru.fifth.horror.video.VideoAssetPolicy;
import ru.fifth.horror.video.VideoAssetStore;

import java.nio.file.Path;

/** Native file-picker import of real external media; original bytes/audio are preserved. */
public final class VideoImportClient {
    private VideoImportClient() {}

    public static void openPickerAndUpload() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            message("§cЗагрузка видео доступна только внутри мира.");
            return;
        }
        if (VideoUploadClient.active()) {
            message("§eСначала дождитесь окончания текущей загрузки.");
            return;
        }
        if (!VideoNativeRuntime.available()) {
            message("§c" + VideoNativeRuntime.failureMessage());
            return;
        }

        String selected = TinyFileDialogs.tinyfd_openFileDialog(
                "Fiven — загрузить реальное видео",
                "",
                (PointerBuffer) null,
                "Видео: MP4 / MOV / MKV / WEBM / AVI / M4V",
                false);
        if (selected == null || selected.isBlank()) return;

        Path file = Path.of(selected);
        if (!VideoAssetPolicy.allowedExtension(file.getFileName().toString())) {
            message("§cНужен .mp4, .mov, .mkv, .webm, .avi или .m4v.");
            return;
        }

        try {
            String name = file.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            VideoAssetStore.Metadata metadata = VideoInspector.inspect(file, base, VideoAssetStore.Origin.IMPORTED_FILE);
            message("§7Видео проверено FFmpeg: §f" + metadata.width() + "×" + metadata.height()
                    + " §8| §7" + String.format(java.util.Locale.ROOT, "%.2fs", metadata.durationMicros() / 1_000_000.0)
                    + (metadata.hasAudio() ? " §8| §aсо звуком" : " §8| §eбез звука"));
            VideoUploadClient.start(file, metadata);
        } catch (Throwable error) {
            message("§cНе удалось импортировать видео: " + concise(error));
        }
    }

    private static String concise(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank() ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static void message(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) client.player.sendMessage(Text.literal(text), true);
    }
}
