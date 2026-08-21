package ru.fifth.horror.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import ru.fifth.horror.block.LiftBlockEntity;
import ru.fifth.horror.network.FifthNetworking;

/** Responsive editor for a physical lift block. */
public final class LiftEditorScreen extends HorrorScreen {
    private final Screen parent;
    private final BlockPos liftPos;
    private final String initialLiftId;
    private int floor;
    private int openMask;
    private BlockPos stage;
    private boolean relocateStage;
    private TextFieldWidget idField, xField, yField, zField;
    private String status = "";

    public LiftEditorScreen(Screen parent, BlockPos liftPos, String liftId, int floor, int openMask, BlockPos stage) {
        super(Text.literal("FIVEN / РЕДАКТОР ЛИФТА"));
        this.parent = parent;
        this.liftPos = liftPos;
        this.floor = Math.max(1, Math.min(9, floor));
        this.openMask = openMask;
        this.relocateStage = stage != null && !LiftBlockEntity.NO_STAGE_ORIGIN.equals(stage);
        this.stage = relocateStage ? stage : liftPos;
        this.initialLiftId = liftId == null || liftId.isBlank() ? "lift" : liftId;
    }

    @Override
    protected void init() {
        beginHorrorInit();
        int w = contentWidth(540), x = (width - w) / 2, y = safeTop(), gap = 6, bh = 20;
        boolean compact = height < 360 || w < 380;
        int idW = Math.max(120, (int)(w * .66));
        idField = horrorField(x, y, idW, bh, initialLiftId, 64);
        addDrawableChild(HorrorButton.builder(Text.literal("Этаж: " + floor), b -> {
            floor = floor % 9 + 1;
            b.setMessage(Text.literal("Этаж: " + floor));
        }).dimensions(x + idW + gap, y, w - idW - gap, bh).build());

        int fw = (w - gap * 2) / 3;
        xField = horrorField(x, y + 28, fw, bh, Integer.toString(stage.getX()), 12);
        yField = horrorField(x + fw + gap, y + 28, fw, bh, Integer.toString(stage.getY()), 12);
        zField = horrorField(x + (fw + gap) * 2, y + 28, fw, bh, Integer.toString(stage.getZ()), 12);

        addDrawableChild(HorrorButton.builder(stageModeText(), b -> {
            relocateStage = !relocateStage;
            b.setMessage(stageModeText());
            setStageFieldsActive(relocateStage);
            status = relocateStage
                    ? "Этажи будут переноситься в указанную общую область."
                    : "Безопасный режим: каждый этаж восстанавливается там, где был сохранён.";
        }).dimensions(x, y + 55, w, bh).build());

        int gridTop = y + 83;
        int bw = (w - gap * 2) / 3;
        int rowStep = compact ? 22 : 25;
        for (int i = 1; i <= 9; i++) {
            final int f = i;
            int col = (i - 1) % 3, row = (i - 1) / 3;
            addDrawableChild(HorrorButton.builder(doorText(f), b -> {
                openMask ^= 1 << (f - 1);
                b.setMessage(doorText(f));
            }).dimensions(x + col * (bw + gap), gridTop + row * rowStep, bw, bh).build());
        }

        int yy = gridTop + rowStep * 3 + 4;
        addDrawableChild(HorrorButton.builder(Text.literal("Общая область: min-угол = позиция игрока"), b -> {
            if (client != null && client.player != null) {
                BlockPos p = client.player.getBlockPos();
                xField.setText(Integer.toString(p.getX()));
                yField.setText(Integer.toString(p.getY()));
                zField.setText(Integer.toString(p.getZ()));
                relocateStage = true;
                setStageFieldsActive(true);
                status = "Общий min-угол восстановления: " + p.toShortString();
                init(client, width, height);
            }
        }).dimensions(x, yy, w, bh).build());
        yy += 26;

        int half = (w - gap) / 2;
        addDrawableChild(HorrorButton.builder(Text.literal("Тип: ОБЫЧНЫЙ"), b -> setCurse(false))
                .dimensions(x, yy, half, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Тип: ПРОКЛЯТЫЙ"), b -> setCurse(true))
                .dimensions(x + half + gap, yy, w - half - gap, bh).build());
        yy += 26;

        addDrawableChild(HorrorButton.builder(Text.literal("Сохранить"), b -> save()).dimensions(x, yy, half, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"), b -> client.setScreen(parent)).dimensions(x + half + gap, yy, w - half - gap, bh).build());
        setStageFieldsActive(relocateStage);
    }

    private void setStageFieldsActive(boolean active) {
        if (xField != null) xField.active = active;
        if (yField != null) yField.active = active;
        if (zField != null) zField.active = active;
    }

    private Text stageModeText() {
        return Text.literal(relocateStage
                ? "Область этажей: ОБЩАЯ ТОЧКА (перенос включён)"
                : "Область этажей: ИСХОДНОЕ МЕСТО СЛОЯ (безопасно)");
    }

    private Text doorText(int f) {
        return Text.literal("Этаж " + f + ": " + (((openMask >> (f - 1)) & 1) != 0 ? "двери ОТКР." : "двери ЗАКР."));
    }

    private void setCurse(boolean cursed) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBlockPos(liftPos);
        out.writeBoolean(cursed);
        ClientPlayNetworking.send(FifthNetworking.LIFT_CURSE_CONFIG, out);
        status = cursed ? "Лифт помечен как ПРОКЛЯТЫЙ" : "Лифт помечен как ОБЫЧНЫЙ";
    }

    private void save() {
        try {
            if (relocateStage) {
                stage = new BlockPos(Integer.parseInt(xField.getText()), Integer.parseInt(yField.getText()), Integer.parseInt(zField.getText()));
            }
            String liftId = idField.getText().trim();
            if (liftId.isBlank()) { status = "ID лифта не может быть пустым"; return; }
            PacketByteBuf out = PacketByteBufs.create();
            out.writeBlockPos(liftPos);
            out.writeString(liftId, 64);
            out.writeVarInt(floor);
            out.writeVarInt(openMask);
            out.writeBlockPos(relocateStage ? stage : LiftBlockEntity.NO_STAGE_ORIGIN);
            ClientPlayNetworking.send(FifthNetworking.SAVE_LIFT_CONFIG, out);
            status = relocateStage
                    ? "Сохранено. Все этажи будут вставляться от min-угла " + stage.toShortString()
                    : "Сохранено. Этажи будут восстанавливаться в исходных координатах своих слоёв.";
        } catch (NumberFormatException e) {
            status = "Координаты должны быть целыми числами";
        }
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        horrorBackground(c);
        int w = contentWidth(540), x = (width - w) / 2;
        c.drawTextWithShadow(textRenderer, "ID лифта / набора этажей", x, safeTop() - 10, 0xFFB69F97);
        if (height >= 220) {
            int fw = (w - 12) / 3;
            c.drawTextWithShadow(textRenderer, "X min-угла", x, safeTop() + 19, 0xFFB69F97);
            c.drawTextWithShadow(textRenderer, "Y min-угла", x + fw + 6, safeTop() + 19, 0xFFB69F97);
            c.drawTextWithShadow(textRenderer, "Z min-угла", x + (fw + 6) * 2, safeTop() + 19, 0xFFB69F97);
        }
        if (!status.isBlank()) c.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - safeBottom() - 11, 0xFFD99090);
        super.render(c, mx, my, delta);
    }
}
