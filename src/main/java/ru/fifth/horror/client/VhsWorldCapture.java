package ru.fifth.horror.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import ru.fifth.horror.block.TelevisionBlockEntity;
import ru.fifth.horror.mixin.CameraAccessor;
import ru.fifth.horror.mixin.MinecraftClientFramebufferAccessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Real off-screen world renderer for VHS playback.
 *
 * The important isolation rule is that MinecraftClient.framebuffer itself is temporarily swapped to the VHS
 * framebuffer. WorldRenderer has internal passes that call client.getFramebuffer(); merely binding another OpenGL
 * framebuffer is not enough and caused recorded-camera output to leak into the player's normal world render.
 */
public final class VhsWorldCapture {
    private static final int WIDTH = 256;
    private static final int HEIGHT = 144;
    private static final Map<Long, Capture> CAPTURES = new HashMap<>();
    private static SimpleFramebuffer scratch;
    private static boolean capturing;
    private static int cursor;

    private VhsWorldCapture() {}

    public static boolean isCapturing() { return capturing; }

    public static Identifier texture(BlockPos pos) {
        if (pos == null) return null;
        Capture capture = CAPTURES.get(pos.asLong());
        return capture == null ? null : capture.id;
    }

    /** Called after the player's normal world pass. At most one physical TV frame is captured per call. */
    public static void captureNext(float tickDelta) {
        if (capturing) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || client.worldRenderer == null) return;

        List<Long> active = VhsPlayback.activePositions();
        if (active.isEmpty()) return;
        if (cursor >= active.size()) cursor = 0;

        for (int checked = 0; checked < active.size(); checked++) {
            long key = active.get((cursor + checked) % active.size());
            VhsPlayback.Session session = VhsPlayback.session(key);
            if (session == null || !session.recordingPhase()) continue;
            Capture capture = CAPTURES.computeIfAbsent(key, ignored -> new Capture());
            if (capture.lastSessionTick == session.ticks()) continue;

            // A broken GPU/driver path must degrade to TelevisionRenderer's in-screen fallback, never corrupt gameplay.
            if (capture.failures >= 5 && session.ticks() % 20 != 0) {
                capture.lastSessionTick = session.ticks();
                continue;
            }

            cursor = (cursor + checked + 1) % Math.max(1, active.size());
            render(client, key, session, tickDelta, capture);
            return;
        }
    }

    private static void render(MinecraftClient client, long key, VhsPlayback.Session session, float tickDelta, Capture capture) {
        ensureFramebuffer();
        if (scratch == null) return;

        MinecraftClientFramebufferAccessor framebufferAccess = (MinecraftClientFramebufferAccessor) (Object) client;
        Framebuffer originalFramebuffer = framebufferAccess.fiven$getFramebuffer();
        Matrix4f oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter oldSorter = RenderSystem.getVertexSorting();
        MatrixStack modelView = RenderSystem.getModelViewStack();
        boolean pushedModelView = false;
        boolean lightmap = false;
        boolean framebufferSwapped = false;
        capturing = true;

        try {
            VhsPlayback.Sample sample = session.sample();
            Camera camera = new Camera();
            camera.update(client.world, client.player, false, false, tickDelta);
            ((CameraAccessor) (Object) camera).fiven$setPos(sample.x(), sample.y(), sample.z());
            ((CameraAccessor) (Object) camera).fiven$setRotation(sample.yaw(), sample.pitch());

            double fov = Math.max(20.0, Math.min(140.0, sample.fov()));
            Matrix4f projection = client.gameRenderer.getBasicProjectionMatrix(fov);
            MatrixStack matrices = new MatrixStack();
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0f));

            // Critical: make every renderer path that asks MinecraftClient for its framebuffer receive scratch.
            framebufferAccess.fiven$setFramebuffer(scratch);
            framebufferSwapped = true;
            scratch.setClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            scratch.beginWrite(true);
            scratch.clear(false);
            RenderSystem.setProjectionMatrix(projection, VertexSorter.BY_DISTANCE);

            modelView.push();
            pushedModelView = true;
            modelView.loadIdentity();
            modelView.multiplyPositionMatrix(matrices.peek().getPositionMatrix());
            RenderSystem.applyModelViewMatrix();

            client.gameRenderer.getLightmapTextureManager().enable();
            lightmap = true;
            client.worldRenderer.render(
                    matrices,
                    tickDelta,
                    System.nanoTime() + 1_000_000_000L,
                    false,
                    camera,
                    client.gameRenderer,
                    client.gameRenderer.getLightmapTextureManager(),
                    projection
            );
            client.gameRenderer.getLightmapTextureManager().disable();
            lightmap = false;
            scratch.endWrite();

            NativeImage image = readFramebuffer();
            if (image == null) {
                capture.lastSessionTick = session.ticks();
                capture.failures++;
                return;
            }
            applyVhs(image, client, key, session.ticks());
            installTexture(client, key, capture, image);
            capture.lastSessionTick = session.ticks();
            capture.failures = 0;
        } catch (Throwable error) {
            capture.lastSessionTick = session.ticks();
            capture.failures++;
        } finally {
            if (lightmap) {
                try { client.gameRenderer.getLightmapTextureManager().disable(); } catch (Throwable ignored) {}
            }
            if (pushedModelView) {
                try { modelView.pop(); RenderSystem.applyModelViewMatrix(); } catch (Throwable ignored) {}
            }
            try { RenderSystem.setProjectionMatrix(oldProjection, oldSorter); } catch (Throwable ignored) {}

            // Restore the player's target before any later HUD/world pass can run.
            if (framebufferSwapped) {
                try { framebufferAccess.fiven$setFramebuffer(originalFramebuffer); } catch (Throwable ignored) {}
            }
            try { originalFramebuffer.beginWrite(true); } catch (Throwable ignored) {}
            capturing = false;
        }
    }

    private static void ensureFramebuffer() {
        if (scratch == null) {
            scratch = new SimpleFramebuffer(WIDTH, HEIGHT, true, false);
            scratch.setTexFilter(9729); // GL_LINEAR: deliberately soft VHS upscale on the CRT surface.
            return;
        }
        if (scratch.textureWidth != WIDTH || scratch.textureHeight != HEIGHT) scratch.resize(WIDTH, HEIGHT, false);
    }

    private static NativeImage readFramebuffer() {
        try {
            scratch.beginRead();
            RenderSystem.bindTexture(scratch.getColorAttachment());
            NativeImage image = new NativeImage(WIDTH, HEIGHT, false);
            image.loadFromTextureImage(0, false);
            image.mirrorVertically();
            scratch.endRead();
            return image;
        } catch (Throwable error) {
            try { scratch.endRead(); } catch (Throwable ignored) {}
            return null;
        }
    }

    private static void applyVhs(NativeImage image, MinecraftClient client, long key, int sessionTick) {
        int quality = 1;
        float noise = .65f;
        boolean mono = true;
        if (client.world != null && client.world.getBlockEntity(BlockPos.fromLong(key)) instanceof TelevisionBlockEntity tv) {
            quality = tv.getQuality();
            noise = tv.getNoise();
            mono = tv.isMonochrome();
        }

        float qualityFactor = switch (quality) {
            case 0 -> 1.0f;
            case 1 -> .72f;
            case 2 -> .42f;
            default -> .18f;
        };
        int noiseAmplitude = Math.round(34f * Math.max(0, Math.min(1, noise)) * qualityFactor);
        Random random = new Random(key * 31L + sessionTick * 1_000_003L);

        for (int y = 0; y < HEIGHT; y++) {
            float scan = (y & 1) == 0 ? .90f : 1.0f;
            int lineNoise = noiseAmplitude <= 0 ? 0 : random.nextInt(noiseAmplitude * 2 + 1) - noiseAmplitude;
            for (int x = 0; x < WIDTH; x++) {
                int abgr = image.getColor(x, y);
                int a = (abgr >>> 24) & 255;
                int b = (abgr >>> 16) & 255;
                int g = (abgr >>> 8) & 255;
                int r = abgr & 255;

                if (mono) {
                    int grey = (r * 30 + g * 59 + b * 11) / 100;
                    r = g = b = grey;
                }
                int grain = noiseAmplitude <= 0 ? 0 : random.nextInt(noiseAmplitude + 1) - noiseAmplitude / 2;
                r = clamp(Math.round(r * scan) + lineNoise + grain);
                g = clamp(Math.round(g * scan) + lineNoise + grain);
                b = clamp(Math.round(b * scan) + lineNoise + grain);
                image.setColor(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
    }

    private static void installTexture(MinecraftClient client, long key, Capture capture, NativeImage image) {
        if (capture.texture == null) {
            capture.texture = new NativeImageBackedTexture(image);
            capture.id = client.getTextureManager().registerDynamicTexture("fiven_vhs_" + Long.toUnsignedString(key), capture.texture);
        } else {
            capture.texture.setImage(image);
        }
        capture.texture.upload();
    }

    private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }

    public static void release(long key) {
        Capture capture = CAPTURES.remove(key);
        if (capture != null && capture.texture != null) {
            try { capture.texture.close(); } catch (Throwable ignored) {}
        }
    }

    public static void clear() {
        for (long key : List.copyOf(CAPTURES.keySet())) release(key);
        if (scratch != null) {
            try { scratch.delete(); } catch (Throwable ignored) {}
            scratch = null;
        }
    }

    private static final class Capture {
        private NativeImageBackedTexture texture;
        private Identifier id;
        private int lastSessionTick = Integer.MIN_VALUE;
        private int failures;
    }
}
