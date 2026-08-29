package ru.fifth.horror.client;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import ru.fifth.horror.cutscene.CutsceneDefinition;
import ru.fifth.horror.vhs.VhsRecordingFeature;
import ru.fifth.horror.vhs.VhsRecordingPolicy;
import ru.fifth.horror.vhs.VhsRecordingStore;

/** Explicit authoring-only recorder. Captures the saved camera timeline as immutable PNG frames. */
public final class VhsRecorderClient {
    public static final int WIDTH = 256;
    public static final int HEIGHT = 144;
    public static final int FPS = 15;
    private static final Gson GSON = new Gson();

    private static CutsceneDefinition scene;
    private static String recordingId = "";
    private static int durationTicks;
    private static int frameCount;
    private static int frameIndex;
    private static int elapsedTicks;
    private static boolean waitingBegin;
    private static boolean recording;
    private static boolean waitingFinish;

    private VhsRecorderClient() {}

    public static boolean start(CutsceneDefinition source) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (source == null || source.keyframes.isEmpty() || client.player == null || client.world == null) {
            message("§cНельзя записать VHS: нет мира или ключевых кадров.");
            return false;
        }
        if (waitingBegin || recording || waitingFinish) {
            message("§eЗапись VHS уже выполняется.");
            return false;
        }

        CutsceneDefinition copy = GSON.fromJson(GSON.toJson(source), CutsceneDefinition.class);
        int total = 0;
        for (CutsceneDefinition.Keyframe keyframe : copy.keyframes) total += Math.max(1, keyframe.durationTicks);
        long frames = Math.max(1L, (total * (long) FPS + 19L) / 20L);
        if (frames > VhsRecordingPolicy.MAX_FRAMES) {
            message("§cVHS слишком длинная: максимум " + VhsRecordingPolicy.MAX_FRAMES + " кадров.");
            return false;
        }

        scene = copy;
        recordingId = VhsRecordingStore.safeId(copy.id);
        durationTicks = Math.max(1, total);
        frameCount = (int) frames;
        frameIndex = 0;
        elapsedTicks = 0;
        waitingBegin = true;
        recording = false;
        waitingFinish = false;

        PacketByteBuf out = PacketByteBufs.create();
        out.writeString(recordingId, 128);
        out.writeVarInt(WIDTH);
        out.writeVarInt(HEIGHT);
        out.writeVarInt(FPS);
        out.writeVarInt(frameCount);
        out.writeVarInt(durationTicks);
        ClientPlayNetworking.send(VhsRecordingFeature.RECORD_BEGIN, out);
        client.setScreen(null);
        message("§7Подготовка покадровой VHS §f" + recordingId + "§7...");
        return true;
    }

    public static void handleAck(String phase, String id, boolean success, String serverMessage) {
        if ("begin".equals(phase)) {
            if (!waitingBegin || !recordingId.equals(id)) return;
            waitingBegin = false;
            if (!success) {
                reset();
                message("§c" + serverMessage);
                return;
            }
            elapsedTicks = 0;
            recording = true;
            message("§aПокадровая VHS началась: §f" + recordingId + " §7(" + frameCount + " кадров / 15 FPS)");
            return;
        }
        if ("error".equals(phase)) {
            if (!recordingId.equals(id)) return;
            reset();
            message("§c" + serverMessage);
            return;
        }
        if ("finish".equals(phase)) {
            if (!waitingFinish || !recordingId.equals(id)) return;
            String finishedId = recordingId;
            reset();
            message((success ? "§a" : "§c") + serverMessage + (success ? " §7[§f" + finishedId + "§7]" : ""));
        }
    }

    /** Called from HudRenderCallback after the normal world pass; captures only when this 15 FPS sample is due. */
    public static void captureNext(float tickDelta) {
        if (!recording || scene == null) return;
        if (frameIndex >= frameCount) {
            finish();
            return;
        }

        int sampleTick = (int) Math.min(durationTicks - 1L, frameIndex * 20L / FPS);
        if (sampleTick > elapsedTicks) return;

        VhsPlayback.Sample sample = sample(scene, sampleTick);
        NativeImage image = VhsWorldCapture.captureFrame(sample, tickDelta);
        if (image == null) {
            failLocal("Не удалось отрисовать кадр VHS #" + frameIndex + ". Смотри latest.log [Fiven/VHS].");
            return;
        }

        try (image) {
            byte[] png = image.getBytes();
            if (png.length <= 0 || png.length > VhsRecordingPolicy.MAX_FRAME_BYTES) {
                failLocal("Кадр VHS #" + frameIndex + " слишком большой: " + png.length + " байт.");
                return;
            }
            PacketByteBuf out = PacketByteBufs.create();
            out.writeString(recordingId, 128);
            out.writeVarInt(frameIndex);
            out.writeByteArray(png);
            ClientPlayNetworking.send(VhsRecordingFeature.RECORD_FRAME, out);
        } catch (Exception error) {
            failLocal("Не удалось закодировать кадр VHS #" + frameIndex + ".");
            return;
        }

        frameIndex++;
        if (frameIndex == frameCount || frameIndex % Math.max(1, frameCount / 10) == 0) {
            int percent = Math.min(100, Math.round(frameIndex * 100f / frameCount));
            message("§7Запись VHS: §f" + percent + "% §8(" + frameIndex + "/" + frameCount + ")");
        }
        if (frameIndex >= frameCount) finish();
    }

    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if ((waitingBegin || recording || waitingFinish) && (client.world == null || client.player == null)) {
            reset();
            return;
        }
        if (recording && elapsedTicks < durationTicks) elapsedTicks++;
    }

    public static boolean active() {
        return waitingBegin || recording || waitingFinish;
    }

    private static void finish() {
        if (!recording) return;
        recording = false;
        waitingFinish = true;
        PacketByteBuf out = PacketByteBufs.create();
        out.writeString(recordingId, 128);
        ClientPlayNetworking.send(VhsRecordingFeature.RECORD_FINISH, out);
        message("§7Финализация покадровой VHS...");
    }

    private static void failLocal(String text) {
        reset();
        message("§c" + text);
    }

    private static void reset() {
        scene = null;
        recordingId = "";
        durationTicks = 0;
        frameCount = 0;
        frameIndex = 0;
        elapsedTicks = 0;
        waitingBegin = false;
        recording = false;
        waitingFinish = false;
    }

    private static void message(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) client.player.sendMessage(Text.literal(text), true);
    }

    private static VhsPlayback.Sample sample(CutsceneDefinition scene, int localTick) {
        if (scene.keyframes.isEmpty()) return new VhsPlayback.Sample(0, 0, 0, 0, 0, 70);
        int acc = 0;
        for (int i = 0; i < scene.keyframes.size(); i++) {
            CutsceneDefinition.Keyframe a = scene.keyframes.get(i);
            int dur = Math.max(1, a.durationTicks);
            if (localTick < acc + dur) {
                CutsceneDefinition.Keyframe b = i + 1 < scene.keyframes.size() ? scene.keyframes.get(i + 1) : a;
                float q = Math.max(0f, Math.min(1f, (localTick - acc) / (float) dur));
                return new VhsPlayback.Sample(
                        lerp(a.x, b.x, q), lerp(a.y, b.y, q), lerp(a.z, b.z, q),
                        lerpAngle(a.yaw, b.yaw, q), lerpFloat(a.pitch, b.pitch, q), lerp(a.fov, b.fov, q));
            }
            acc += dur;
        }
        CutsceneDefinition.Keyframe last = scene.keyframes.get(scene.keyframes.size() - 1);
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
