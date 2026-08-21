package ru.fifth.horror.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import ru.fifth.horror.FivenExtraContent;
import ru.fifth.horror.client.RuntimeSkinManager;
import ru.fifth.horror.entity.DirectorNpcEntity;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Base64;
import java.util.Deque;

/**
 * 64x64 live PNG editor for Director NPC skins.
 * Pencil/line/eraser changes are pushed into a client preview texture immediately; Save sends one compact PNG to the server.
 */
public final class NpcTextureEditorScreen extends HorrorScreen {
    private static final int TEX = 64;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private enum Tool { PENCIL, LINE, ERASER }

    private final Screen parent;
    private final int entityId;
    private DirectorNpcEntity npc;
    private final int[] pixels = new int[TEX * TEX];
    private int[] original = new int[TEX * TEX];
    private final Deque<int[]> undo = new ArrayDeque<>();

    private TextFieldWidget colorField;
    private Tool tool = Tool.PENCIL;
    private String status = "";
    private boolean drawing;
    private int lineStartX = -1, lineStartY = -1;
    private boolean previewDirty;
    private int canvasX, canvasY, pixelScale;

    public NpcTextureEditorScreen(Screen parent, DirectorNpcEntity npc) {
        super(Text.literal("FIVEN / LIVE PNG TEXTURE EDITOR"));
        this.parent = parent;
        this.npc = npc;
        this.entityId = npc.getId();
        loadTexture();
        original = pixels.clone();
        previewDirty = true;
    }

    @Override
    protected void init() {
        beginHorrorInit();
        if (client != null && client.world != null && client.world.getEntityById(entityId) instanceof DirectorNpcEntity current) npc = current;

        int top = safeTop();
        int availableH = Math.max(128, height - top - safeBottom() - 8);
        int availableW = Math.max(128, width - 250);
        pixelScale = Math.max(1, Math.min(6, Math.min(availableH / TEX, availableW / TEX)));
        int canvas = TEX * pixelScale;
        canvasX = Math.max(12, (width - (canvas + 218)) / 2);
        canvasY = top;

        int panelX = canvasX + canvas + 14;
        int panelW = Math.max(180, Math.min(204, width - panelX - 12));
        int bh = 20;

        colorField = horrorField(panelX, top, panelW, bh, "#D03030", 9);
        colorField.setChangedListener(v -> {
            if (parseColor(false) != null) status = "Цвет: " + colorField.getText().trim().toUpperCase();
        });

        int half = (panelW - 6) / 2;
        addDrawableChild(HorrorButton.builder(Text.literal("Карандаш"), b -> selectTool(Tool.PENCIL))
                .dimensions(panelX, top + 28, half, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Линия"), b -> selectTool(Tool.LINE))
                .dimensions(panelX + half + 6, top + 28, panelW - half - 6, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Ластик"), b -> selectTool(Tool.ERASER))
                .dimensions(panelX, top + 55, half, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("↶ Отмена"), b -> undo())
                .dimensions(panelX + half + 6, top + 55, panelW - half - 6, bh).build());

        addDrawableChild(HorrorButton.builder(Text.literal("Чёрный"), b -> setColor("#111111"))
                .dimensions(panelX, top + 82, half, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Белый"), b -> setColor("#F2F2F2"))
                .dimensions(panelX + half + 6, top + 82, panelW - half - 6, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Красный"), b -> setColor("#B51E2E"))
                .dimensions(panelX, top + 109, half, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Серый"), b -> setColor("#777777"))
                .dimensions(panelX + half + 6, top + 109, panelW - half - 6, bh).build());

        int actionsY = top + 217;
        if (availableH < 305) actionsY = top + 140;
        addDrawableChild(HorrorButton.builder(Text.literal("Сохранить в NPC"), b -> saveToNpc())
                .dimensions(panelX, actionsY, panelW, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Экспортировать PNG"), b -> exportPng())
                .dimensions(panelX, actionsY + 27, panelW, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Откатить к исходной"), b -> resetOriginal())
                .dimensions(panelX, actionsY + 54, panelW, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"), b -> close())
                .dimensions(panelX, actionsY + 81, panelW, bh).build());
    }

    private void selectTool(Tool value) {
        tool = value;
        status = switch (value) {
            case PENCIL -> "Инструмент: карандаш";
            case LINE -> "Инструмент: линия — зажми и отпусти ЛКМ";
            case ERASER -> "Инструмент: ластик";
        };
    }

    private void setColor(String value) {
        colorField.setText(value);
        tool = Tool.PENCIL;
        status = "Цвет: " + value;
    }

    private void loadTexture() {
        Arrays.fill(pixels, 0x00000000);
        if (npc == null) return;
        try {
            BufferedImage image = null;
            String base64 = npc.getSkinBase64();
            if (base64 != null && !base64.isBlank()) {
                image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
            }
            if (image == null && client != null) {
                var optional = client.getResourceManager().getResource(npc.getTextureResource());
                if (optional.isPresent()) {
                    try (InputStream in = optional.get().getInputStream()) {
                        image = ImageIO.read(in);
                    }
                }
            }
            if (image == null) return;
            if (image.getWidth() != TEX || image.getHeight() != TEX) {
                BufferedImage scaled = new BufferedImage(TEX, TEX, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(image, 0, 0, TEX, TEX, null);
                g.dispose();
                image = scaled;
            }
            image.getRGB(0, 0, TEX, TEX, pixels, 0, TEX);
        } catch (Exception e) {
            status = "Не удалось открыть исходную PNG — создан прозрачный холст.";
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int[] p = canvasPixel(mouseX, mouseY);
        if (p != null) {
            if (button == 1) {
                int argb = pixels[p[1] * TEX + p[0]];
                colorField.setText(String.format("#%06X", argb & 0xFFFFFF));
                status = "Цвет взят с пикселя " + p[0] + "," + p[1];
                return true;
            }
            if (button == 0) {
                pushUndo();
                drawing = true;
                if (tool == Tool.LINE) {
                    lineStartX = p[0]; lineStartY = p[1];
                    status = "Начало линии: " + p[0] + "," + p[1];
                } else {
                    paint(p[0], p[1]);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (drawing && button == 0 && tool != Tool.LINE) {
            int[] p = canvasPixel(mouseX, mouseY);
            if (p != null) paint(p[0], p[1]);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (drawing && button == 0) {
            if (tool == Tool.LINE && lineStartX >= 0) {
                int[] p = canvasPixel(mouseX, mouseY);
                if (p != null) drawLine(lineStartX, lineStartY, p[0], p[1]);
                lineStartX = lineStartY = -1;
            }
            drawing = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int[] canvasPixel(double mouseX, double mouseY) {
        int size = TEX * pixelScale;
        if (mouseX < canvasX || mouseY < canvasY || mouseX >= canvasX + size || mouseY >= canvasY + size) return null;
        return new int[]{Math.min(63, (int)((mouseX - canvasX) / pixelScale)), Math.min(63, (int)((mouseY - canvasY) / pixelScale))};
    }

    private void paint(int x, int y) {
        Integer color = tool == Tool.ERASER ? 0x00000000 : parseColor(true);
        if (color == null) return;
        pixels[y * TEX + x] = color;
        previewDirty = true;
    }

    private void drawLine(int x0, int y0, int x1, int y1) {
        Integer color = parseColor(true);
        if (color == null) return;
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            pixels[y0 * TEX + x0] = color;
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
        previewDirty = true;
    }

    private Integer parseColor(boolean report) {
        try {
            String raw = colorField == null ? "#FFFFFF" : colorField.getText().trim();
            if (raw.startsWith("#")) raw = raw.substring(1);
            if (raw.length() == 8) return (int) Long.parseLong(raw, 16);
            if (raw.length() != 6) throw new NumberFormatException();
            return 0xFF000000 | Integer.parseInt(raw, 16);
        } catch (Exception e) {
            if (report) status = "Цвет: используй #RRGGBB или AARRGGBB";
            return null;
        }
    }

    private void pushUndo() {
        undo.push(pixels.clone());
        while (undo.size() > 20) undo.removeLast();
    }

    private void undo() {
        if (undo.isEmpty()) { status = "История изменений пуста."; return; }
        int[] previous = undo.pop();
        System.arraycopy(previous, 0, pixels, 0, pixels.length);
        previewDirty = true;
        status = "Последнее действие отменено.";
    }

    private void resetOriginal() {
        pushUndo();
        System.arraycopy(original, 0, pixels, 0, pixels.length);
        previewDirty = true;
        status = "Исходная текстура восстановлена локально.";
    }

    private BufferedImage toImage() {
        BufferedImage image = new BufferedImage(TEX, TEX, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, TEX, TEX, pixels, 0, TEX);
        return image;
    }

    private String toBase64() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(toImage(), "PNG", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private void saveToNpc() {
        if (client == null || client.getNetworkHandler() == null || npc == null) return;
        try {
            String base64 = toBase64();
            if (base64.length() > 65535) { status = "PNG получилась слишком большой для сетевой синхронизации."; return; }
            npc.setSkinBase64(base64);
            PacketByteBuf out = PacketByteBufs.create();
            out.writeVarInt(entityId);
            out.writeString(base64, 65535);
            ClientPlayNetworking.send(FivenExtraContent.SAVE_NPC_TEXTURE, out);
            original = pixels.clone();
            RuntimeSkinManager.clearPreview(npc.getUuid());
            status = "Сохранено в NPC и синхронизировано с сервером.";
        } catch (Exception e) {
            status = "Не удалось сохранить PNG: " + e.getClass().getSimpleName();
        }
    }

    private void exportPng() {
        if (client == null) return;
        try {
            Path dir = client.runDirectory.toPath().resolve("fiven").resolve("exports").resolve("skins");
            Files.createDirectories(dir);
            String id = npc == null ? "npc" : npc.getNpcId().replaceAll("[^a-zA-Z0-9_\\-]", "_");
            Path file = dir.resolve(id + "_" + FILE_TIME.format(LocalDateTime.now()) + ".png");
            ImageIO.write(toImage(), "PNG", file.toFile());
            status = "PNG экспортирована: " + file.toAbsolutePath();
        } catch (Exception e) {
            status = "Ошибка экспорта PNG: " + e.getMessage();
        }
    }

    private void updatePreview() {
        if (!previewDirty || npc == null) return;
        RuntimeSkinManager.setPreview(npc.getUuid(), pixels);
        previewDirty = false;
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        updatePreview();
        horrorBackground(c);
        int size = TEX * pixelScale;
        panel(c, canvasX - 3, canvasY - 3, size + 6, size + 6);

        for (int y = 0; y < TEX; y++) {
            for (int x = 0; x < TEX; x++) {
                int px = canvasX + x * pixelScale;
                int py = canvasY + y * pixelScale;
                int color = pixels[y * TEX + x];
                if (((color >>> 24) & 255) == 0) {
                    int checker = ((x + y) & 1) == 0 ? 0xFF343434 : 0xFF202020;
                    c.fill(px, py, px + pixelScale, py + pixelScale, checker);
                } else {
                    c.fill(px, py, px + pixelScale, py + pixelScale, color);
                }
            }
        }
        if (pixelScale >= 3) {
            for (int i = 0; i <= 64; i += 8) {
                c.fill(canvasX + i * pixelScale, canvasY, canvasX + i * pixelScale + 1, canvasY + size, 0x553A171B);
                c.fill(canvasX, canvasY + i * pixelScale, canvasX + size, canvasY + i * pixelScale + 1, 0x553A171B);
            }
        }

        int panelX = canvasX + size + 14;
        int panelW = Math.max(180, Math.min(204, width - panelX - 12));
        int previewBottom = Math.min(height - safeBottom() - 105, safeTop() + 210);
        if (npc != null && previewBottom > safeTop() + 115) {
            panel(c, panelX, safeTop() + 136, panelW, previewBottom - safeTop() - 136);
            try {
                int entityX = panelX + panelW / 2;
                int entityY = previewBottom - 6;
                int entityScale = Math.max(26, Math.min(54, previewBottom - safeTop() - 150));
                InventoryScreen.drawEntity(c, entityX, entityY, entityScale, entityX - mx, entityY - 45 - my, npc);
            } catch (Exception ignored) {}
        }

        c.drawTextWithShadow(textRenderer, "64×64 PNG • ЛКМ рисует • ПКМ берёт цвет", canvasX, canvasY - 12, 0xFFCDBDB5);
        c.drawTextWithShadow(textRenderer, "Инструмент: " + tool.name(), panelX, safeTop() + 126, 0xFFB69F97);
        if (!status.isBlank()) c.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - safeBottom() + 5, 0xFFD89090);
        super.render(c, mx, my, delta);
    }

    @Override
    public void close() {
        if (npc != null) RuntimeSkinManager.clearPreview(npc.getUuid());
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void removed() {
        if (npc != null) RuntimeSkinManager.clearPreview(npc.getUuid());
        super.removed();
    }
}
