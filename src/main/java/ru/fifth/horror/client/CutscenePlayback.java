package ru.fifth.horror.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import ru.fifth.horror.cutscene.CutsceneDefinition;
import ru.fifth.horror.network.FifthNetworking;

public final class CutscenePlayback {
    private static CutsceneDefinition scene;
    private static int tick;
    private static int lastSubtitleIndex = -1;
    private static boolean previousHudHidden;
    private static boolean hudStateCaptured;

    private CutscenePlayback() {}

    public static void start(CutsceneDefinition value) {
        if (scene != null) stop();
        scene = value;
        tick = 0;
        lastSubtitleIndex = -1;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null) {
            previousHudHidden = client.options.hudHidden;
            hudStateCaptured = true;
            if (value != null && value.hideHud) client.options.hudHidden = true;
        }
    }

    public static void stop() {
        restoreHud();
        scene = null;
        tick = 0;
        lastSubtitleIndex = -1;
    }

    private static void finish() {
        CutsceneDefinition finished = scene;
        MinecraftClient client = MinecraftClient.getInstance();
        restoreHud();
        scene = null;
        tick = 0;
        lastSubtitleIndex = -1;
        if (finished != null && finished.teleportPlayerAtEnd && finished.id != null && !finished.id.isBlank()
                && client.getNetworkHandler() != null) {
            PacketByteBuf out = PacketByteBufs.create();
            out.writeString(finished.id, 128);
            ClientPlayNetworking.send(FifthNetworking.CUTSCENE_END_TELEPORT, out);
        }
    }

    private static void restoreHud() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (hudStateCaptured && client.options != null) client.options.hudHidden = previousHudHidden;
        hudStateCaptured = false;
    }

    public static boolean active() {
        return scene != null && scene.keyframes != null && !scene.keyframes.isEmpty();
    }

    public static boolean hideHud() {
        return active() && scene.hideHud;
    }

    public static boolean lockInput() {
        return active() && scene.lockInput;
    }

    public static void tick() {
        if (!active()) return;
        Sample sample = sample(0);
        if (sample != null && sample.index != lastSubtitleIndex) {
            lastSubtitleIndex = sample.index;
            String text = scene.keyframes.get(sample.index).subtitle;
            if (text != null && !text.isBlank() && MinecraftClient.getInstance().inGameHud != null) {
                MinecraftClient.getInstance().inGameHud.setOverlayMessage(Text.literal(text), false);
            }
        }
        tick++;
        if (tick >= totalTicks()) finish();
    }

    public static Sample sample(float delta) {
        if (!active()) return null;
        if (scene.keyframes.size() == 1) {
            var k = scene.keyframes.get(0);
            return new Sample(k.x, k.y, k.z, k.yaw, k.pitch, k.fov, 0);
        }

        int cursor = 0;
        for (int i = 0; i < scene.keyframes.size() - 1; i++) {
            CutsceneDefinition.Keyframe a = scene.keyframes.get(i);
            CutsceneDefinition.Keyframe b = scene.keyframes.get(i + 1);
            int duration = Math.max(1, a.durationTicks);
            if (tick < cursor + duration) {
                float t = MathHelper.clamp((tick + delta - cursor) / (float) duration, 0, 1);
                t = t * t * (3 - 2 * t);
                return new Sample(
                        MathHelper.lerp(t, a.x, b.x),
                        MathHelper.lerp(t, a.y, b.y),
                        MathHelper.lerp(t, a.z, b.z),
                        lerpAngle(t, a.yaw, b.yaw),
                        MathHelper.lerp(t, a.pitch, b.pitch),
                        MathHelper.lerp(t, a.fov, b.fov),
                        i);
            }
            cursor += duration;
        }

        var last = scene.keyframes.get(scene.keyframes.size() - 1);
        return new Sample(last.x, last.y, last.z, last.yaw, last.pitch, last.fov, scene.keyframes.size() - 1);
    }

    private static float lerpAngle(float t, float a, float b) {
        return a + MathHelper.wrapDegrees(b - a) * t;
    }

    private static int totalTicks() {
        int total = 0;
        for (CutsceneDefinition.Keyframe keyframe : scene.keyframes) total += Math.max(1, keyframe.durationTicks);
        return Math.max(1, total);
    }

    public record Sample(double x, double y, double z, float yaw, float pitch, double fov, int index) {}
}
