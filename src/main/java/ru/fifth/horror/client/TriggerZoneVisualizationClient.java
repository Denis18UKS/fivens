package ru.fifth.horror.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ru.fifth.horror.trigger.TriggerZoneVisualizationServer;

import java.util.ArrayList;
import java.util.List;

/** Client-only read-only visualization of trigger zones selected by this director. */
public final class TriggerZoneVisualizationClient {
    private static volatile List<Row> rows = List.of();

    private TriggerZoneVisualizationClient() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(TriggerZoneVisualizationServer.PAYLOAD, (client, handler, buf, sender) -> {
            boolean clear = buf.readBoolean();
            if (clear) {
                client.execute(TriggerZoneVisualizationClient::clear);
                return;
            }

            int count = buf.readVarInt();
            if (count < 0 || count > 4096) {
                client.execute(TriggerZoneVisualizationClient::clear);
                return;
            }
            List<Row> next = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                next.add(new Row(
                        buf.readString(128), buf.readString(256),
                        buf.readInt(), buf.readInt(), buf.readInt(),
                        buf.readInt(), buf.readInt(), buf.readInt(),
                        buf.readString(16), buf.readBoolean(),
                        buf.readVarInt(), buf.readVarInt()
                ));
            }
            client.execute(() -> rows = List.copyOf(next));
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> render(context.matrixStack(), context.camera().getPos()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    public static void clear() {
        rows = List.of();
    }

    private static void render(MatrixStack matrices, Vec3d camera) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (matrices == null || client.world == null || client.player == null || rows.isEmpty()) return;
        String world = client.world.getRegistryKey().getValue().toString();

        for (Row row : rows) {
            if (!world.equals(row.world)) continue;
            drawZone(matrices, camera, row);
        }
    }

    private static void drawZone(MatrixStack matrices, Vec3d camera, Row row) {
        float r = row.enabled ? 0.15f : 0.55f;
        float g = row.enabled ? 0.85f : 0.55f;
        float b = row.enabled ? 1.00f : 0.55f;

        double x0 = row.minX;
        double y0 = row.minY;
        double z0 = row.minZ;
        double x1 = row.maxX + 1.0;
        double y1 = row.maxY + 1.0;
        double z1 = row.maxZ + 1.0;

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        face(buffer, matrix, x0,y0,z0, x1,y0,z0, x1,y1,z0, x0,y1,z0, r,g,b,.10f);
        face(buffer, matrix, x0,y0,z1, x0,y1,z1, x1,y1,z1, x1,y0,z1, r,g,b,.10f);
        face(buffer, matrix, x0,y0,z0, x0,y1,z0, x0,y1,z1, x0,y0,z1, r,g,b,.10f);
        face(buffer, matrix, x1,y0,z0, x1,y0,z1, x1,y1,z1, x1,y1,z0, r,g,b,.10f);
        face(buffer, matrix, x0,y0,z0, x0,y0,z1, x1,y0,z1, x1,y0,z0, r,g,b,.10f);
        face(buffer, matrix, x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1, r,g,b,.10f);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.lineWidth(2.5f);
        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        edge(buffer,matrix,x0,y0,z0,x1,y0,z0,r,g,b,.95f); edge(buffer,matrix,x1,y0,z0,x1,y0,z1,r,g,b,.95f);
        edge(buffer,matrix,x1,y0,z1,x0,y0,z1,r,g,b,.95f); edge(buffer,matrix,x0,y0,z1,x0,y0,z0,r,g,b,.95f);
        edge(buffer,matrix,x0,y1,z0,x1,y1,z0,r,g,b,.95f); edge(buffer,matrix,x1,y1,z0,x1,y1,z1,r,g,b,.95f);
        edge(buffer,matrix,x1,y1,z1,x0,y1,z1,r,g,b,.95f); edge(buffer,matrix,x0,y1,z1,x0,y1,z0,r,g,b,.95f);
        edge(buffer,matrix,x0,y0,z0,x0,y1,z0,r,g,b,.95f); edge(buffer,matrix,x1,y0,z0,x1,y1,z0,r,g,b,.95f);
        edge(buffer,matrix,x1,y0,z1,x1,y1,z1,r,g,b,.95f); edge(buffer,matrix,x0,y0,z1,x0,y1,z1,r,g,b,.95f);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        matrices.pop();

        drawLabel(matrices, camera, row, (x0 + x1) * .5, y1 + .20, (z0 + z1) * .5);
    }

    private static void drawLabel(MatrixStack matrices, Vec3d camera, Row row, double x, double y, double z) {
        MinecraftClient client = MinecraftClient.getInstance();
        String text = row.id + " | " + row.mode + " | " + row.currentPlayers + "/" + row.minPlayers + (row.enabled ? "" : " | OFF");
        float scale = .025f;

        matrices.push();
        matrices.translate(x - camera.x, y - camera.y, z - camera.z);
        matrices.multiply(client.getEntityRenderDispatcher().getRotation());
        matrices.scale(-scale, -scale, scale);

        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        float left = -client.textRenderer.getWidth(text) / 2.0f;
        client.textRenderer.draw(text, left, 0, 0xFFFFFFFF, false,
                matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.SEE_THROUGH,
                0x66000000, 15728880);
        consumers.draw();
        matrices.pop();
    }

    private static void face(BufferBuilder b, Matrix4f m,
                             double ax,double ay,double az,double bx,double by,double bz,
                             double cx,double cy,double cz,double dx,double dy,double dz,
                             float r,float g,float bl,float a) {
        vertex(b,m,ax,ay,az,r,g,bl,a); vertex(b,m,bx,by,bz,r,g,bl,a);
        vertex(b,m,cx,cy,cz,r,g,bl,a); vertex(b,m,dx,dy,dz,r,g,bl,a);
    }

    private static void edge(BufferBuilder b, Matrix4f m,
                             double ax,double ay,double az,double bx,double by,double bz,
                             float r,float g,float bl,float a) {
        vertex(b,m,ax,ay,az,r,g,bl,a); vertex(b,m,bx,by,bz,r,g,bl,a);
    }

    private static void vertex(BufferBuilder b, Matrix4f m, double x,double y,double z, float r,float g,float bl,float a) {
        b.vertex(m, (float)x, (float)y, (float)z).color(r,g,bl,a).next();
    }

    private record Row(String id, String world,
                       int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                       String mode, boolean enabled, int currentPlayers, int minPlayers) {}
}
