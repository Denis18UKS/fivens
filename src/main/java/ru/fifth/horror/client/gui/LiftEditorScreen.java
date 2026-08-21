package ru.fifth.horror.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import ru.fifth.horror.network.FifthNetworking;

/** Visual lift configuration: lift id, current floor, stage origin and per-floor door permissions. */
public final class LiftEditorScreen extends HorrorScreen {
    private final Screen parent;
    private final int entityId;
    private int floor;
    private int openMask;
    private BlockPos stage;
    private TextFieldWidget idField, xField, yField, zField;
    private String status = "";

    public LiftEditorScreen(Screen parent, int entityId, String liftId, int floor, int openMask, BlockPos stage) {
        super(Text.literal("FIVEN / РЕДАКТОР ЛИФТА"));
        this.parent = parent;
        this.entityId = entityId;
        this.floor = Math.max(1, Math.min(9, floor));
        this.openMask = openMask;
        this.stage = stage;
        this.initialLiftId = liftId == null || liftId.isBlank() ? "lift" : liftId;
    }

    private final String initialLiftId;

    @Override
    protected void init() {
        beginHorrorInit();
        int w = contentWidth(540), x = (width - w) / 2, y = safeTop(), gap = 6, bh = 20;
        int idW = (int) (w * .68);
        idField = horrorField(x, y, idW, bh, initialLiftId, 64);
        addDrawableChild(HorrorButton.builder(Text.literal("Этаж: " + floor), b -> {
            floor = floor % 9 + 1;
            b.setMessage(Text.literal("Этаж: " + floor));
        }).dimensions(x + idW + gap, y, w - idW - gap, bh).build());

        int fw = (w - gap * 2) / 3;
        xField = horrorField(x, y + 30, fw, bh, Integer.toString(stage.getX()), 12);
        yField = horrorField(x + fw + gap, y + 30, fw, bh, Integer.toString(stage.getY()), 12);
        zField = horrorField(x + (fw + gap) * 2, y + 30, fw, bh, Integer.toString(stage.getZ()), 12);

        int bw = (w - gap * 2) / 3;
        for (int i = 1; i <= 9; i++) {
            final int f = i;
            int col = (i - 1) % 3, row = (i - 1) / 3;
            addDrawableChild(HorrorButton.builder(doorText(f), b -> {
                openMask ^= 1 << (f - 1);
                b.setMessage(doorText(f));
            }).dimensions(x + col * (bw + gap), y + 62 + row * 26, bw, bh).build());
        }

        addDrawableChild(HorrorButton.builder(Text.literal("Взять точку сцены из позиции игрока"), b -> {
            if (client != null && client.player != null) {
                BlockPos p = client.player.getBlockPos();
                xField.setText(Integer.toString(p.getX()));
                yField.setText(Integer.toString(p.getY()));
                zField.setText(Integer.toString(p.getZ()));
                status = "Точка восстановления = позиция игрока";
            }
        }).dimensions(x, y + 148, w, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Сохранить настройки лифта"), b -> save()).dimensions(x, y + 176, w, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"), b -> client.setScreen(parent)).dimensions(x, y + 204, w, bh).build());
    }

    private Text doorText(int f) {
        return Text.literal("Этаж " + f + ": двери " + (((openMask >> (f - 1)) & 1) != 0 ? "ОТКР." : "ЗАКР."));
    }

    private void save() {
        try {
            stage = new BlockPos(Integer.parseInt(xField.getText()), Integer.parseInt(yField.getText()), Integer.parseInt(zField.getText()));
            String liftId = idField.getText().trim();
            if (liftId.isBlank()) {
                status = "ID лифта не может быть пустым";
                return;
            }
            PacketByteBuf out = PacketByteBufs.create();
            out.writeVarInt(entityId);
            out.writeString(liftId, 64);
            out.writeVarInt(floor);
            out.writeVarInt(openMask);
            out.writeBlockPos(stage);
            ClientPlayNetworking.send(FifthNetworking.SAVE_LIFT_CONFIG, out);
            status = "Сохранено. Для слоёв используй ID: " + liftId;
        } catch (NumberFormatException e) {
            status = "Координаты должны быть целыми числами";
        }
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        horrorBackground(c);
        int w = contentWidth(540), x = (width - w) / 2;
        c.drawTextWithShadow(textRenderer, "ID лифта / набора этажей", x, safeTop() - 10, 0xFFB69F97);
        c.drawTextWithShadow(textRenderer, "X сцены", x, safeTop() + 20, 0xFFB69F97);
        c.drawTextWithShadow(textRenderer, "Y сцены", x + (w + 6) / 3, safeTop() + 20, 0xFFB69F97);
        c.drawTextWithShadow(textRenderer, "Z сцены", x + ((w + 6) / 3) * 2, safeTop() + 20, 0xFFB69F97);
        if (!status.isBlank()) {
            c.drawCenteredTextWithShadow(textRenderer, status, width / 2,
                    Math.min(height - safeBottom() - 12, safeTop() + 232), 0xFFD99090);
        }
        super.render(c, mx, my, delta);
    }
}
