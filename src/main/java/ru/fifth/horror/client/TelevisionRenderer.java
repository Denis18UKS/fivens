package ru.fifth.horror.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import ru.fifth.horror.block.TelevisionBlock;
import ru.fifth.horror.block.TelevisionBlockEntity;

import java.util.HashMap;
import java.util.Map;

/** Physical CRT overlay. Tape playback consumes immutable recorded PNG frames only. */
public final class TelevisionRenderer implements BlockEntityRenderer<TelevisionBlockEntity> {
    private static final float SCREEN_HALF = 7.0f / 16.0f;
    /** Keep the CRT plane clearly in front of the vanilla block face to avoid depth fighting. */
    private static final double SCREEN_EPSILON = 0.012;
    private static final int SCREEN_LIGHT = 0x00F000F0;
    private static final Map<Long, Long> DISPATCH_HITS = new HashMap<>();

    public TelevisionRenderer(BlockEntityRendererFactory.Context ignored) {}

    /** Called by the dispatcher mixin as a lightweight render-path heartbeat. */
    public static void noteDispatcherHit(TelevisionBlockEntity be) {
        if (be != null) DISPATCH_HITS.put(be.getPos().asLong(), System.nanoTime());
    }

    @Override
    public void render(TelevisionBlockEntity be, float delta, MatrixStack ms, VertexConsumerProvider vertices, int light, int overlay) {
        renderScreen(be, delta, ms, vertices);
    }

    public static void renderScreen(TelevisionBlockEntity be, float delta, MatrixStack ms, VertexConsumerProvider vertices) {
        if (be == null || VhsWorldCapture.isCapturing()) return;
        BlockPos pos = be.getPos();
        boolean hasSession = VhsRecordedPlayback.hasSession(pos);
        if (be.getRecording().isBlank() && be.getStaticTicks() <= 0 && !hasSession) return;

        TextRenderer text = MinecraftClient.getInstance().textRenderer;
        Direction facing = be.getCachedState().contains(TelevisionBlock.FACING)
                ? be.getCachedState().get(TelevisionBlock.FACING) : Direction.NORTH;

        ms.push();
        orientToPhysicalFront(ms, facing);

        boolean startupStatic = hasSession ? VhsRecordedPlayback.staticPhase(pos) : be.getStaticTicks() > 0;
        String tapeError = hasSession ? VhsRecordedPlayback.error(pos) : "";
        boolean recordingPhase = hasSession && VhsRecordedPlayback.recordingPhase(pos);
        Identifier captured = recordingPhase && tapeError.isBlank() ? VhsRecordedPlayback.texture(pos) : null;

        // Startup snow is now a real texture quad, using exactly the same GPU path as recorded VHS frames.
        if (startupStatic) {
            long tick = be.getWorld() == null ? 0L : be.getWorld().getTime();
            Identifier signal = VhsSignalTexture.texture(pos, tick);
            if (signal != null) drawVideoQuad(ms, vertices, signal, SCREEN_LIGHT);
        } else if (captured != null) {
            drawVideoQuad(ms, vertices, captured, SCREEN_LIGHT);
        }

        float overlayScale = (SCREEN_HALF * 2.0f) / 108.0f;
        ms.scale(overlayScale, -overlayScale, overlayScale);
        Matrix4f matrix = ms.peek().getPositionMatrix();
        int x = -54;
        int y = -43;

        if (startupStatic) {
            // The picture itself is the animated signal texture; text is only a small optional overlay.
            text.draw("VHS TRACKING...", -39, -4, 0xFFF4F4F4, false, matrix, vertices,
                    TextRenderer.TextLayerType.POLYGON_OFFSET, 0, SCREEN_LIGHT);
        } else if (!tapeError.isBlank()) {
            drawBlack(text, matrix, vertices, SCREEN_LIGHT, x, y);
            text.draw("TAPE READ ERROR", -42, -4, 0xFFFFB8B8, false, matrix, vertices,
                    TextRenderer.TextLayerType.POLYGON_OFFSET, 0, SCREEN_LIGHT);
        } else if (recordingPhase) {
            if (captured == null) {
                drawBlack(text, matrix, vertices, SCREEN_LIGHT, x, y);
                text.draw("VIDEO BUFFERING", -39, -4, 0xFFE6E6E6, false, matrix, vertices,
                        TextRenderer.TextLayerType.POLYGON_OFFSET, 0, SCREEN_LIGHT);
            } else {
                for (int row = 0; row < 12; row += 2) {
                    text.draw("──────────────────", x, y + 4 + row * 7, 0x44000000, false, matrix, vertices,
                            TextRenderer.TextLayerType.POLYGON_OFFSET, 0, SCREEN_LIGHT);
                }
            }
            text.draw("PLAY", x + 2, y + 2, 0xFFF2F2F2, false, matrix, vertices,
                    TextRenderer.TextLayerType.POLYGON_OFFSET, 0, SCREEN_LIGHT);
            String label = VhsRecordedPlayback.label(pos);
            if (!label.isBlank()) {
                String clipped = label.length() > 20 ? label.substring(0, 20) : label;
                text.draw(clipped, x + 2, y + 76, 0xFFDDDDDD, false, matrix, vertices,
                        TextRenderer.TextLayerType.POLYGON_OFFSET, 0, SCREEN_LIGHT);
            }
        } else {
            drawBlack(text, matrix, vertices, SCREEN_LIGHT, x, y);
        }
        ms.pop();
    }

    private static void orientToPhysicalFront(MatrixStack ms, Direction facing) {
        switch (facing) {
            case SOUTH -> {
                ms.translate(.5, .5, 1.0 + SCREEN_EPSILON);
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
            }
            case EAST -> {
                ms.translate(1.0 + SCREEN_EPSILON, .5, .5);
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90f));
            }
            case WEST -> {
                ms.translate(-SCREEN_EPSILON, .5, .5);
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90f));
            }
            default -> ms.translate(.5, .5, -SCREEN_EPSILON);
        }
    }

    private static void drawBlack(TextRenderer text, Matrix4f matrix, VertexConsumerProvider vertices, int light, int x, int y) {
        String black = "██████████████████";
        for (int row = 0; row < 13; row++) {
            text.draw(black, x, y + row * 7, 0xFF000000, false, matrix, vertices,
                    TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
        }
    }

    private static void drawVideoQuad(MatrixStack ms, VertexConsumerProvider vertices, Identifier texture, int light) {
        VertexConsumer vc = vertices.getBuffer(RenderLayer.getEntityTranslucent(texture));
        Matrix4f m = ms.peek().getPositionMatrix();
        Matrix3f n = ms.peek().getNormalMatrix();
        float left = -SCREEN_HALF, right = SCREEN_HALF, top = SCREEN_HALF, bottom = -SCREEN_HALF;
        vertex(vc, m, n, left, top, 0, 0, light, -1);
        vertex(vc, m, n, right, top, 1, 0, light, -1);
        vertex(vc, m, n, right, bottom, 1, 1, light, -1);
        vertex(vc, m, n, left, bottom, 0, 1, light, -1);
        vertex(vc, m, n, left, bottom, 0, 1, light, 1);
        vertex(vc, m, n, right, bottom, 1, 1, light, 1);
        vertex(vc, m, n, right, top, 1, 0, light, 1);
        vertex(vc, m, n, left, top, 0, 0, light, 1);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float v, int light, float nz) {
        vc.vertex(m, x, y, 0)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(n, 0, 0, nz)
                .next();
    }
}
