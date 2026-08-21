package ru.fifth.horror.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import ru.fifth.horror.cutscene.CutsceneDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side timeline cache for VHS sessions attached to physical televisions.
 * VHS never hijacks the player's fullscreen renderer; TelevisionRenderer samples this timeline and VhsWorldCapture
 * renders the recorded camera into a small off-screen framebuffer.
 */
public final class VhsPlayback {
    public static final int STATIC_TICKS = 80;
    private static final Map<Long, Session> TV = new HashMap<>();
    private VhsPlayback() {}

    public static void start(CutsceneDefinition scene, int ignoredMode, BlockPos tvPos) {
        if (scene == null || tvPos == null) return;
        long key = tvPos.asLong();
        VhsWorldCapture.release(key);
        TV.put(key, new Session(copy(scene)));
    }

    public static void tick() {
        var it = TV.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (!entry.getValue().tick()) {
                VhsWorldCapture.release(entry.getKey());
                it.remove();
            }
        }
    }

    /** Kept for the HUD callback. Deliberately draws nothing: VHS belongs to the TV surface. */
    public static void render(DrawContext ignored) {}

    public static Session session(BlockPos pos) { return pos == null ? null : TV.get(pos.asLong()); }
    public static Session session(long pos) { return TV.get(pos); }

    /** Stable snapshot so the off-screen renderer can iterate while sessions may expire on the next client tick. */
    public static List<Long> activePositions() { return new ArrayList<>(TV.keySet()); }

    private static CutsceneDefinition copy(CutsceneDefinition scene) {
        CutsceneDefinition out = new CutsceneDefinition();
        out.id = scene.id;
        out.hideHud = false;
        out.lockInput = false;
        out.teleportPlayerAtEnd = false;
        for (CutsceneDefinition.Keyframe src : scene.keyframes) {
            CutsceneDefinition.Keyframe k = new CutsceneDefinition.Keyframe(src.x, src.y, src.z, src.yaw, src.pitch, src.fov, Math.max(1, src.durationTicks));
            k.subtitle = src.subtitle;
            k.event = src.event;
            out.keyframes.add(k);
        }
        return out;
    }

    public static final class Session {
        private final CutsceneDefinition scene;
        private final int total;
        private int ticks;

        private Session(CutsceneDefinition scene) {
            this.scene = scene;
            int t = 0;
            for (var k : scene.keyframes) t += Math.max(1, k.durationTicks);
            this.total = Math.max(1, t);
        }

        private boolean tick() { ticks++; return ticks < STATIC_TICKS + total + 40; }
        public int ticks() { return ticks; }
        public boolean staticPhase() { return ticks < STATIC_TICKS; }
        public boolean recordingPhase() { return ticks >= STATIC_TICKS && ticks < STATIC_TICKS + total; }
        public float progress() { return Math.max(0, Math.min(1, (ticks - STATIC_TICKS) / (float) total)); }
        public String id() { return scene.id == null ? "recording" : scene.id; }

        public String subtitle() {
            if (scene.keyframes.isEmpty() || ticks < STATIC_TICKS) return "";
            int local = ticks - STATIC_TICKS, acc = 0;
            for (var k : scene.keyframes) {
                acc += Math.max(1, k.durationTicks);
                if (local < acc) return k.subtitle == null ? "" : k.subtitle;
            }
            return "";
        }

        public Sample sample() {
            if (scene.keyframes.isEmpty()) return new Sample(0, 0, 0, 0, 0, 70);
            int local = Math.max(0, ticks - STATIC_TICKS), acc = 0;
            for (int i = 0; i < scene.keyframes.size(); i++) {
                var a = scene.keyframes.get(i);
                int dur = Math.max(1, a.durationTicks);
                if (local < acc + dur) {
                    var b = i + 1 < scene.keyframes.size() ? scene.keyframes.get(i + 1) : a;
                    float q = Math.max(0, Math.min(1, (local - acc) / (float) dur));
                    return new Sample(lerp(a.x, b.x, q), lerp(a.y, b.y, q), lerp(a.z, b.z, q),
                            lerpAngle(a.yaw, b.yaw, q), lerpFloat(a.pitch, b.pitch, q), lerp(a.fov, b.fov, q));
                }
                acc += dur;
            }
            var k = scene.keyframes.get(scene.keyframes.size() - 1);
            return new Sample(k.x, k.y, k.z, k.yaw, k.pitch, k.fov);
        }

        private static double lerp(double a, double b, float q) { return a + (b - a) * q; }
        private static float lerpFloat(float a, float b, float q) { return a + (b - a) * q; }
        private static float lerpAngle(float a, float b, float q) {
            float d = (b - a) % 360f;
            if (d > 180) d -= 360;
            if (d < -180) d += 360;
            return a + d * q;
        }
    }

    public record Sample(double x, double y, double z, float yaw, float pitch, double fov) {}
}
