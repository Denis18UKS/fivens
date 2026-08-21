package ru.fifth.horror.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import ru.fifth.horror.block.TelevisionBlock;
import ru.fifth.horror.block.TelevisionBlockEntity;

import java.util.Random;

/** Draws VHS content only inside the black 14x14-pixel screen area of the physical television. */
public final class TelevisionRenderer implements BlockEntityRenderer<TelevisionBlockEntity> {
    private final TextRenderer text;

    /* The north-face TV texture uses a 16x16 region; its black screen is pixels 1..14 on both axes. */
    private static final float SCREEN_HALF = 7.0f / 16.0f; // 14/16 block-wide screen, centered at 0.5/0.5

    public TelevisionRenderer(BlockEntityRendererFactory.Context ctx) { text = ctx.getTextRenderer(); }

    @Override
    public void render(TelevisionBlockEntity be, float delta, MatrixStack ms, VertexConsumerProvider vertices, int light, int overlay) {
        if (be.getRecording().isBlank() && be.getStaticTicks() <= 0) return;
        // Never draw a TV surface while the secondary VHS camera is capturing the world.
        if (VhsWorldCapture.isCapturing()) return;

        Direction facing = be.getCachedState().contains(TelevisionBlock.FACING)
                ? be.getCachedState().get(TelevisionBlock.FACING) : Direction.NORTH;
        float yaw = switch (facing) { case SOUTH -> 180f; case EAST -> -90f; case WEST -> 90f; default -> 0f; };

        ms.push();
        // Screen is centered on the front face of the 1x1 TV model, not at the old y=.73 offset.
        ms.translate(.5, .5, .5);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        // Place the quad a hair in front of the local NORTH face to avoid z-fighting.
        ms.translate(0, 0, -.5015);

        VhsPlayback.Session session = VhsPlayback.session(be.getPos());
        boolean noise = be.getStaticTicks() > 0 || (session != null && session.staticPhase());
        Identifier captured = session == null ? null : VhsWorldCapture.texture(be.getPos());

        if (!noise && captured != null) drawVideoQuad(ms, vertices, captured, light);

        // Overlay coordinates are scaled so the full 108px text width fits inside the same physical screen quad.
        float overlayScale = (SCREEN_HALF * 2.0f) / 108.0f;
        ms.scale(overlayScale, -overlayScale, overlayScale);
        Matrix4f matrix = ms.peek().getPositionMatrix();
        int x = -54;
        int y = -43;

        if (noise) {
            drawBlack(matrix, vertices, light, x, y);
            long seed = (be.getWorld() == null ? 0 : be.getWorld().getTime()) * 31L + be.getPos().asLong();
            Random r = new Random(seed);
            String chars = " ░▒▓";
            for (int row = 0; row < 12; row++) {
                StringBuilder line = new StringBuilder();
                for (int col = 0; col < 18; col++) line.append(chars.charAt(r.nextInt(chars.length())));
                int g = 70 + r.nextInt(120);
                int grey = 0xFF000000 | (g << 16) | (g << 8) | g;
                text.draw(line.toString(), x, y + 4 + row * 7, grey, false, matrix, vertices,
                        TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
            }
            text.draw("NO SIGNAL", -27, -4, 0xFFBFBFBF, false, matrix, vertices,
                    TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
        } else if (session != null) {
            if (captured == null) {
                // GPU/driver-safe fallback remains strictly on the television surface.
                drawBlack(matrix, vertices, light, x, y);
                VhsPlayback.Sample s = session.sample();
                float p = session.progress();
                for (int row = 0; row < 11; row++) {
                    StringBuilder line = new StringBuilder();
                    for (int col = 0; col < 18; col++) {
                        double wave = Math.sin((col * .72) + (row * .41) + (p * 22) + (s.yaw() * .025) + s.x() * .03 + s.z() * .02);
                        line.append(wave > .55 ? '▓' : wave > 0 ? '▒' : wave > -.55 ? '░' : ' ');
                    }
                    int g = Math.max(55, Math.min(205, (int) (120 + Math.sin(row + p * 17) * 55)));
                    int color = 0xFF000000 | (g << 16) | (g << 8) | g;
                    text.draw(line.toString(), x, y + 4 + row * 7, color, false, matrix, vertices,
                            TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
                }
            } else {
                for (int row = 0; row < 12; row += 2) {
                    text.draw("──────────────────", x, y + 4 + row * 7, 0x2D000000, false, matrix, vertices,
                            TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
                }
            }

            text.draw("REC", x + 2, y + 2, 0xFFE6E6E6, false, matrix, vertices,
                    TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
            String sub = session.subtitle();
            if (!sub.isBlank()) {
                String clipped = sub.length() > 25 ? sub.substring(0, 25) : sub;
                text.draw(Text.literal(clipped), x + 2, y + 76, 0xFFE0E0E0, false, matrix, vertices,
                        TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
            }
        } else {
            drawBlack(matrix, vertices, light, x, y);
            text.draw("REC  " + be.getRecording(), x + 3, -4, 0xFFBFBFBF, false, matrix, vertices,
                    TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
        }
        ms.pop();
    }

    private void drawBlack(Matrix4f matrix, VertexConsumerProvider vertices, int light, int x, int y) {
        String black = "██████████████████";
        for (int row = 0; row < 13; row++) {
            text.draw(black, x, y + row * 7, 0xFF000000, false, matrix, vertices,
                    TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
        }
    }

    /** Exact 14/16 x 14/16 quad matching the black square in television.png. */
    private static void drawVideoQuad(MatrixStack ms, VertexConsumerProvider vertices, Identifier texture, int light) {
        VertexConsumer vc = vertices.getBuffer(RenderLayer.getEntityTranslucent(texture));
        Matrix4f m = ms.peek().getPositionMatrix();
        Matrix3f n = ms.peek().getNormalMatrix();
        float left = -SCREEN_HALF, right = SCREEN_HALF, top = SCREEN_HALF, bottom = -SCREEN_HALF;
        vertex(vc, m, n, left, top, 0, 0, light);
        vertex(vc, m, n, right, top, 1, 0, light);
        vertex(vc, m, n, right, bottom, 1, 1, light);
        vertex(vc, m, n, left, bottom, 0, 1, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float v, int light) {
        vc.vertex(m, x, y, 0)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(n, 0, 0, -1)
                .next();
    }
}
