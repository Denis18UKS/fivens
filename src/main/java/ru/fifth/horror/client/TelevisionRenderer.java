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
    private static final float SCREEN_HALF = 7.0f / 16.0f;
    private static final int SCREEN_LIGHT = 0x00F000F0; // CRT is self-lit; never disappear in a dark horror room.

    public TelevisionRenderer(BlockEntityRendererFactory.Context ctx) { text = ctx.getTextRenderer(); }

    @Override
    public void render(TelevisionBlockEntity be, float delta, MatrixStack ms, VertexConsumerProvider vertices, int light, int overlay) {
        // The VHS session packet and the BlockEntity update packet are independent network messages.
        // Never reject a valid playback session just because BE NBT arrived one frame later.
        VhsPlayback.Session session = VhsPlayback.session(be.getPos());
        if (be.getRecording().isBlank() && be.getStaticTicks() <= 0 && session == null) return;

        // Never draw TV surfaces into the secondary VHS camera itself.
        if (VhsWorldCapture.isCapturing()) return;

        Direction facing = be.getCachedState().contains(TelevisionBlock.FACING)
                ? be.getCachedState().get(TelevisionBlock.FACING) : Direction.NORTH;
        float yaw = switch (facing) { case SOUTH -> 180f; case EAST -> -90f; case WEST -> 90f; default -> 0f; };

        ms.push();
        ms.translate(.5, .5, .5);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        // A tiny offset in front of the physical NORTH/front face avoids z-fighting with the TV texture.
        ms.translate(0, 0, -.5015);

        boolean noise = session != null ? session.staticPhase() : be.getStaticTicks() > 0;
        Identifier captured = session == null ? null : VhsWorldCapture.texture(be.getPos());

        if (!noise && captured != null) drawVideoQuad(ms, vertices, captured, SCREEN_LIGHT);

        // Overlay coordinates are scaled so the full 108px overlay fits inside the same physical 14/16 screen quad.
        float overlayScale = (SCREEN_HALF * 2.0f) / 108.0f;
        ms.scale(overlayScale, -overlayScale, overlayScale);
        Matrix4f matrix = ms.peek().getPositionMatrix();
        int x = -54;
        int y = -43;

        if (noise) {
            drawBlack(matrix, vertices, SCREEN_LIGHT, x, y);
            long seed = (be.getWorld() == null ? 0 : be.getWorld().getTime()) * 31L + be.getPos().asLong();
            Random r = new Random(seed);
            String chars = " ░▒▓";
            for (int row = 0; row < 12; row++) {
                StringBuilder line = new StringBuilder();
                for (int col = 0; col < 18; col++) line.append(chars.charAt(r.nextInt(chars.length())));
                int g = 70 + r.nextInt(120);
                int grey = 0xFF000000 | (g << 16) | (g << 8) | g;
                text.draw(line.toString(), x, y + 4 + row * 7, grey, false, matrix, vertices,
                        TextRenderer.TextLayerType.POLYGON_OFFSET, 0, SCREEN_LIGHT);
            }
            text.draw("NO SIGNAL", -27, -4, 0xFFBFBFBF, false, matrix, vertices,
                    TextRenderer.TextLayerType.POLYGON_OFFSET, 0, SCREEN_LIGHT);
        } else if (session != null) {
            if (captured == null) {
                // If secondary world rendering fails on a driver, playback still remains visibly alive ON THE TV.
                drawBlack(matrix, vertices, SCREEN_LIGHT, x, y);
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
                            TextRenderer.TextLayerType.POLYGON_OFFSET, 0, SCREEN_LIGHT);
                }
            } else {
                for (int row = 0; row < 12; row += 2) {
                    text.draw("──────────────────", x, y + 4 + row * 7, 0x2D000000, false, matrix, vertices,
                            TextRenderer.TextLayerType.POLYGON_OFFSET, 0, SCREEN_LIGHT);
                }
            }

            text.draw("REC", x + 2, y + 2, 0xFFE6E6E6, false, matrix, vertices,
                    TextRenderer.TextLayerType.POLYGON_OFFSET, 0, SCREEN_LIGHT);
            String sub = session.subtitle();
            if (!sub.isBlank()) {
                String clipped = sub.length() > 25 ? sub.substring(0, 25) : sub;
                text.draw(Text.literal(clipped), x + 2, y + 76, 0xFFE0E0E0, false, matrix, vertices,
                        TextRenderer.TextLayerType.POLYGON_OFFSET, 0, SCREEN_LIGHT);
            }
        } else {
            // Server BE says a recording is active even if the VHS session packet was missed: show an explicit state,
            // rather than silently leaving the TV's black texture unchanged.
            drawBlack(matrix, vertices, SCREEN_LIGHT, x, y);
            text.draw("WAITING FOR VHS", x + 4, -4, 0xFFBFBFBF, false, matrix, vertices,
                    TextRenderer.TextLayerType.POLYGON_OFFSET, 0, SCREEN_LIGHT);
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
