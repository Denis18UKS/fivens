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

/**
 * Simplified physical lift editor. Floor-layer binding is now simply "same lift ID + floor number".
 * Relocation remains available under an explicit advanced section and defaults to safe source coordinates.
 */
public final class LiftEditorScreen extends HorrorScreen {
    private final Screen parent;
    private final BlockPos liftPos;
    private final String initialLiftId;
    private int floor;
    private int openMask;
    private BlockPos stage;
    private boolean relocateStage;
    private boolean advanced;
    private TextFieldWidget idField;
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
        int w = contentWidth(540), x = (width - w) / 2, y = safeTop(), gap = 6, bh = 21;
        int idW = Math.max(150, (int) (w * .68));
        String currentId = idField == null ? initialLiftId : idField.getText();
        idField = horrorField(x, y, idW, bh, currentId, 64);
        idField.setPlaceholder(Text.literal("ID лифта, например lift"));

        addDrawableChild(HorrorButton.builder(Text.literal("Текущий этаж: " + floor), button -> {
            floor = floor % 9 + 1;
            button.setMessage(Text.literal("Текущий этаж: " + floor));
        }).dimensions(x + idW + gap, y, w - idW - gap, bh).build());

        int gridTop = y + 54;
        int cell = (w - gap * 2) / 3;
        for (int i = 1; i <= 9; i++) {
            final int f = i;
            int col = (i - 1) % 3, row = (i - 1) / 3;
            addDrawableChild(HorrorButton.builder(doorText(f), button -> {
                openMask ^= 1 << (f - 1);
                button.setMessage(doorText(f));
            }).dimensions(x + col * (cell + gap), gridTop + row * 27, cell, bh).build());
        }

        int yy = gridTop + 85;
        addDrawableChild(HorrorButton.builder(Text.literal(advanced ? "Расширенные этажи: ПОКАЗАНЫ" : "Расширенные этажи: скрыты"), button -> {
            advanced = !advanced;
            clearAndInit();
        }).dimensions(x, yy, w, bh).build());
        yy += 28;

        if (advanced) {
            addDrawableChild(HorrorButton.builder(Text.literal(relocateStage
                            ? "Перенос всех этажей в одну область: ДА"
                            : "Перенос всех этажей в одну область: НЕТ (безопасно)"), button -> {
                relocateStage = !relocateStage;
                if (relocateStage && client != null && client.player != null && (stage == null || stage.equals(liftPos))) {
                    stage = client.player.getBlockPos();
                }
                button.setMessage(Text.literal(relocateStage
                        ? "Перенос всех этажей в одну область: ДА"
                        : "Перенос всех этажей в одну область: НЕТ (безопасно)"));
                status = relocateStage
                        ? "Общая область включена. Точка: " + stage.toShortString()
                        : "Безопасно: каждый этаж вернётся туда, где был сохранён.";
            }).dimensions(x, yy, w, bh).build());
            yy += 28;

            addDrawableChild(HorrorButton.builder(Text.literal("Поставить общую точку там, где я стою"), button -> {
                if (client != null && client.player != null) {
                    stage = client.player.getBlockPos();
                    relocateStage = true;
                    status = "Общая min-точка этажей: " + stage.toShortString();
                }
            }).dimensions(x, yy, w, bh).build());
            yy += 28;
        }

        int half = (w - gap) / 2;
        addDrawableChild(HorrorButton.builder(Text.literal("Тип: ОБЫЧНЫЙ"), button -> setCurse(false))
                .dimensions(x, yy, half, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Тип: ПРОКЛЯТЫЙ"), button -> setCurse(true))
                .dimensions(x + half + gap, yy, w - half - gap, bh).build());
        yy += 28;

        addDrawableChild(HorrorButton.builder(Text.literal("Сохранить лифт"), button -> save())
                .dimensions(x, yy, half, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"), button -> client.setScreen(parent))
                .dimensions(x + half + gap, yy, w - half - gap, bh).build());
    }

    private Text doorText(int f) {
        return Text.literal("Этаж " + f + ": " + (((openMask >> (f - 1)) & 1) != 0 ? "двери ДА" : "двери НЕТ"));
    }

    private void setCurse(boolean cursed) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBlockPos(liftPos);
        out.writeBoolean(cursed);
        ClientPlayNetworking.send(FifthNetworking.LIFT_CURSE_CONFIG, out);
        status = cursed ? "Лифт помечен как ПРОКЛЯТЫЙ" : "Лифт помечен как ОБЫЧНЫЙ";
    }

    private void save() {
        String liftId = idField.getText().trim();
        if (liftId.isBlank()) {
            status = "ID лифта не может быть пустым.";
            return;
        }
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBlockPos(liftPos);
        out.writeString(liftId, 64);
        out.writeVarInt(floor);
        out.writeVarInt(openMask);
        out.writeBlockPos(relocateStage ? stage : LiftBlockEntity.NO_STAGE_ORIGIN);
        ClientPlayNetworking.send(FifthNetworking.SAVE_LIFT_CONFIG, out);
        status = "Сохранено. Этажи привязываются как «" + liftId + " → 1..9».";
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        horrorBackground(c);
        int w = contentWidth(540), x = (width - w) / 2, y = safeTop();
        c.drawTextWithShadow(textRenderer, "ID лифта", x, y - 10, 0xFFB69F97);
        c.drawCenteredTextWithShadow(textRenderer,
                "Чтобы привязать слой этажа: Слои и этажи → ЭТАЖ ЛИФТА → этот же ID → номер 1–9",
                width / 2, y + 31, 0xFFD0B9AE);
        if (advanced && relocateStage) {
            c.drawCenteredTextWithShadow(textRenderer, "Общая точка вставки: " + stage.toShortString(),
                    width / 2, Math.min(height - 42, y + 173), 0xFFB69F97);
        }
        if (!status.isBlank()) {
            c.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - safeBottom() - 11, 0xFFD99090);
        }
        super.render(c, mx, my, delta);
    }
}
