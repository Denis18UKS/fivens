package ru.fifth.horror.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.fifth.horror.mixin.CameraAccessor;
import ru.fifth.horror.mixin.MinecraftClientFramebufferAccessor;
import ru.fifth.horror.mixin.WorldRendererVhsAccessor;

/** Off-screen camera helper used only while a director explicitly records a real MP4 VHS. */
public final class VhsWorldCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("Fiven/Video");
    public static final int WIDTH = 640;
    public static final int HEIGHT = 360;
    private static SimpleFramebuffer scratch;
    private static boolean capturing;

    private VhsWorldCapture() {}

    public static boolean isCapturing() { return capturing; }

    /** Legacy playback hook remains a no-op: normal tape playback never re-renders the live world. */
    public static void captureNext(float ignoredTickDelta) {}
    public static Identifier texture(BlockPos ignored) { return null; }

    /** Renders one authored camera sample into an isolated 640x360 framebuffer. */
    public static NativeImage captureFrame(VhsPlayback.Sample sample, float tickDelta) {
        if (capturing || sample == null) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || client.worldRenderer == null) return null;
        ensureFramebuffer();
        if (scratch == null) return null;

        MinecraftClientFramebufferAccessor framebufferAccess = (MinecraftClientFramebufferAccessor) (Object) client;
        Framebuffer originalFramebuffer = framebufferAccess.fiven$getFramebuffer();
        Matrix4f oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter oldSorter = RenderSystem.getVertexSorting();
        MatrixStack modelView = RenderSystem.getModelViewStack();
        boolean pushedModelView = false;
        boolean lightmap = false;
        boolean framebufferSwapped = false;
        boolean scratchWriting = false;
        capturing = true;

        try {
            Camera camera = new Camera();
            camera.update(client.world, client.player, false, false, tickDelta);
            ((CameraAccessor) (Object) camera).fiven$setPos(sample.x(), sample.y(), sample.z());
            ((CameraAccessor) (Object) camera).fiven$setRotation(sample.yaw(), sample.pitch());

            double fov = Math.max(20.0, Math.min(140.0, sample.fov()));
            Matrix4f projection = client.gameRenderer.getBasicProjectionMatrix(fov);
            MatrixStack matrices = new MatrixStack();
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0f));

            client.worldRenderer.setupFrustum(matrices, camera.getPos(), projection);
            WorldRendererVhsAccessor worldAccess = (WorldRendererVhsAccessor) (Object) client.worldRenderer;
            Frustum vhsFrustum = worldAccess.fiven$getFrustum();
            if (vhsFrustum != null) worldAccess.fiven$setupTerrain(camera, vhsFrustum, false, client.player.isSpectator());

            framebufferAccess.fiven$setFramebuffer(scratch);
            framebufferSwapped = true;
            scratch.setClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            scratch.beginWrite(true);
            scratchWriting = true;
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
            scratchWriting = false;
            return readFramebuffer();
        } catch (Throwable error) {
            LOGGER.warn("MP4 authoring frame capture failed.", error);
            return null;
        } finally {
            if (scratchWriting) try { scratch.endWrite(); } catch (Throwable ignored) {}
            if (lightmap) try { client.gameRenderer.getLightmapTextureManager().disable(); } catch (Throwable ignored) {}
            if (pushedModelView) try { modelView.pop(); RenderSystem.applyModelViewMatrix(); } catch (Throwable ignored) {}
            try { RenderSystem.setProjectionMatrix(oldProjection, oldSorter); } catch (Throwable ignored) {}
            if (framebufferSwapped) try { framebufferAccess.fiven$setFramebuffer(originalFramebuffer); } catch (Throwable ignored) {}
            try { originalFramebuffer.beginWrite(true); } catch (Throwable ignored) {}
            capturing = false;
        }
    }

    private static void ensureFramebuffer() {
        if (scratch == null) {
            scratch = new SimpleFramebuffer(WIDTH, HEIGHT, true, false);
            scratch.setTexFilter(9729);
        } else if (scratch.textureWidth != WIDTH || scratch.textureHeight != HEIGHT) {
            scratch.resize(WIDTH, HEIGHT, false);
        }
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
            LOGGER.warn("Could not read MP4 authoring framebuffer.", error);
            return null;
        }
    }

    public static void release(long ignored) {}

    public static void clear() {
        if (scratch != null) {
            try { scratch.delete(); } catch (Throwable ignored) {}
            scratch = null;
        }
    }
}
