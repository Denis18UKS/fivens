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

/** Draws VHS content only on the physical black screen area of the television model. */
public final class TelevisionRenderer implements BlockEntityRenderer<TelevisionBlockEntity> {
    private final TextRenderer text;

    public TelevisionRenderer(BlockEntityRendererFactory.Context ctx) { text = ctx.getTextRenderer(); }

    @Override
    public void render(TelevisionBlockEntity be, float delta, MatrixStack ms, VertexConsumerProvider vertices, int light, int overlay) {
        if (be.getRecording().isBlank() && be.getStaticTicks() <= 0) return;
        // The off-screen VHS world pass renders the base TV block but not the TV's own video surface.
        if (VhsWorldCapture.isCapturing()) return;

        Direction facing = be.getCachedState().contains(TelevisionBlock.FACING)
                ? be.getCachedState().get(TelevisionBlock.FACING) : Direction.NORTH;
        float yaw = switch (facing) { case SOUTH -> 180f; case EAST -> -90f; case WEST -> 90f; default -> 0f; };

        ms.push();
        ms.translate(.5, .73, .5);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        // Local north face. Tiny offset prevents z-fighting with the black screen polygon.
        ms.translate(0, 0, -.506);

        VhsPlayback.Session session = VhsPlayback.session(be.getPos());
        boolean noise = be.getStaticTicks() > 0 || (session != null && session.staticPhase());
        Identifier captured = session == null ? null : VhsWorldCapture.texture(be.getPos());

        if (!noise && captured != null) {
            drawVideoQuad(ms, vertices, captured, light);
        }

        // Text/VHS overlays are intentionally rendered in the same old low-resolution screen space.
        ms.scale(.0062f, -.0062f, .0062f);
        Matrix4f matrix = ms.peek().getPositionMatrix();
        int x = -54, y = -36;

        if (noise) {
            drawBlack(matrix, vertices, light, x, y);
            long seed = (be.getWorld() == null ? 0 : be.getWorld().getTime()) * 31L + be.getPos().asLong();
            Random r = new Random(seed);
            String chars = " ░▒▓";
            for (int row = 0; row < 10; row++) {
                StringBuilder line = new StringBuilder();
                for (int col = 0; col < 18; col++) line.append(chars.charAt(r.nextInt(chars.length())));
                int g = 70 + r.nextInt(120);
                int grey = 0xFF000000 | (g << 16) | (g << 8) | g;
                text.draw(line.toString(), x, y + 3 + row * 6, grey, false, matrix, vertices,
                        TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
            }
            text.draw("NO SIGNAL", -27, y + 29, 0xFFBFBFBF, false, matrix, vertices,
                    TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
        } else if (session != null) {
            if (captured == null) {
                // GPU/driver-safe fallback: timeline visualization instead of crashing the client.
                drawBlack(matrix, vertices, light, x, y);
                VhsPlayback.Sample s = session.sample();
                float p = session.progress();
                for (int row = 0; row < 9; row++) {
                    StringBuilder line = new StringBuilder();
                    for (int col = 0; col < 18; col++) {
                        double wave = Math.sin((col * .72) + (row * .41) + (p * 22) + (s.yaw() * .025) + s.x() * .03 + s.z() * .02);
                        line.append(wave > .55 ? '▓' : wave > 0 ? '▒' : wave > -.55 ? '░' : ' ');
                    }
                    int g = Math.max(55, Math.min(205, (int) (120 + Math.sin(row + p * 17) * 55)));
                    int color = 0xFF000000 | (g << 16) | (g << 8) | g;
                    text.draw(line.toString(), x, y + 3 + row * 6, color, false, matrix, vertices,
                            TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
                }
            } else {
                // Thin scanlines over the captured camera image.
                for (int row = 0; row < 10; row += 2) {
                    text.draw("──────────────────", x, y + 3 + row * 6, 0x2D000000, false, matrix, vertices,
                            TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
                }
            }

            text.draw("REC", x + 2, y + 2, 0xFFE6E6E6, false, matrix, vertices,
                    TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
            String sub = session.subtitle();
            if (!sub.isBlank()) {
                String clipped = sub.length() > 25 ? sub.substring(0, 25) : sub;
                text.draw(Text.literal(clipped), x + 2, y + 60, 0xFFE0E0E0, false, matrix, vertices,
                        TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
            }
        } else {
            drawBlack(matrix, vertices, light, x, y);
            text.draw("REC  " + be.getRecording(), x + 3, y + 30, 0xFFBFBFBF, false, matrix, vertices,
                    TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
        }
        ms.pop();
    }

    private void drawBlack(Matrix4f matrix, VertexConsumerProvider vertices, int light, int x, int y) {
        String black = "██████████████████";
        for (int row = 0; row < 12; row++) {
            text.draw(black, x, y + row * 6, 0xFF000000, false, matrix, vertices,
                    TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
        }
    }

    private static void drawVideoQuad(MatrixStack ms, VertexConsumerProvider vertices, Identifier texture, int light) {
        VertexConsumer vc = vertices.getBuffer(RenderLayer.getEntityTranslucent(texture));
        Matrix4f m = ms.peek().getPositionMatrix();
        Matrix3f n = ms.peek().getNormalMatrix();
        float left = -.335f, right = .335f, top = .225f, bottom = -.225f;
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
