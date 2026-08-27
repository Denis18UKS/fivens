package ru.fifth.horror.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import ru.fifth.horror.network.FifthNetworking;

/**
 * Simplified layer authoring screen.
 * Normal layers use only "object/set + state"; lift floors use only "lift id + floor".
 * Legacy metadata remains supported by StructureLayerManager, but raw build/group/floorSet fields are no longer
 * exposed during the normal workflow.
 */
public class BuildLayerScreen extends HorrorScreen {
    private final Screen parent;
    private final BlockPos a, b;

    private TextFieldWidget objectField;
    private TextFieldWidget stateField;
    private TextFieldWidget liftIdField;

    private boolean floorMode;
    private boolean advanced;
    private boolean defaultActive = true;
    private boolean restore = true;
    private int floor = 1;

    private String objectName = "scene";
    private String stateName = "default";
    private String liftId = "lift";
    private String status = "";

    public BuildLayerScreen(Screen parent, ItemStack stack) {
        super(Text.literal("FIVEN / СЛОИ И ЭТАЖИ"));
        this.parent = parent;
        this.a = stack.hasNbt() && stack.getNbt().contains("FifthPosA")
                ? BlockPos.fromLong(stack.getNbt().getLong("FifthPosA")) : null;
        this.b = stack.hasNbt() && stack.getNbt().contains("FifthPosB")
                ? BlockPos.fromLong(stack.getNbt().getLong("FifthPosB")) : null;
    }

    @Override
    protected void init() {
        beginHorrorInit();
        int w = contentWidth(520), x = (width - w) / 2, y = safeTop(), gap = 6, bh = 21;

        addDrawableChild(HorrorButton.builder(Text.literal(floorMode
                        ? "Режим: ЭТАЖ ЛИФТА"
                        : "Режим: ОБЫЧНЫЙ СЛОЙ"), button -> {
            floorMode = !floorMode;
            status = floorMode
                    ? "Этаж привязывается только по ID лифта и номеру."
                    : "Обычный слой: выбери объект/набор и его состояние.";
            clearAndInit();
        }).dimensions(x, y, w, bh).build());

        int contentY = y + 31;
        if (floorMode) initFloorMode(x, contentY, w, gap, bh);
        else initNormalMode(x, contentY, w, gap, bh);

        int bottomY = Math.min(height - safeBottom() - 50, floorMode ? contentY + 122 : contentY + 62);
        addDrawableChild(HorrorButton.builder(Text.literal(advanced ? "Расширенные настройки: ПОКАЗАНЫ" : "Расширенные настройки: скрыты"), button -> {
            advanced = !advanced;
            clearAndInit();
        }).dimensions(x, bottomY, w, bh).build());

        if (advanced && !floorMode) {
            int half = (w - gap) / 2;
            addDrawableChild(HorrorButton.builder(Text.literal("По умолчанию: " + (defaultActive ? "ДА" : "НЕТ")), button -> {
                defaultActive = !defaultActive;
                button.setMessage(Text.literal("По умолчанию: " + (defaultActive ? "ДА" : "НЕТ")));
            }).dimensions(x, bottomY + 28, half, bh).build());
            addDrawableChild(HorrorButton.builder(Text.literal("Восстанавливать при загрузке: " + (restore ? "ДА" : "НЕТ")), button -> {
                restore = !restore;
                button.setMessage(Text.literal("Восстанавливать при загрузке: " + (restore ? "ДА" : "НЕТ")));
            }).dimensions(x + half + gap, bottomY + 28, w - half - gap, bh).build());
        }

        int actionY = bottomY + (advanced && !floorMode ? 57 : 29);
        int half = (w - gap) / 2;
        addDrawableChild(HorrorButton.builder(Text.literal(floorMode ? "Сохранить этаж" : "Сохранить слой"), button -> capture())
                .dimensions(x, actionY, half, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Активировать / проверить"), button -> activate())
                .dimensions(x + half + gap, actionY, w - half - gap, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"), button -> client.setScreen(parent))
                .dimensions(x, actionY + 28, w, bh).build());
    }

    private void initNormalMode(int x, int y, int w, int gap, int bh) {
        int half = (w - gap) / 2;
        objectField = horrorField(x, y, half, bh, objectName, 128);
        objectField.setPlaceholder(Text.literal("например: corridor"));
        objectField.setChangedListener(v -> objectName = v);
        stateField = horrorField(x + half + gap, y, w - half - gap, bh, stateName, 128);
        stateField.setPlaceholder(Text.literal("например: intact"));
        stateField.setChangedListener(v -> stateName = v);
    }

    private void initFloorMode(int x, int y, int w, int gap, int bh) {
        liftIdField = horrorField(x, y, w, bh, liftId, 64);
        liftIdField.setPlaceholder(Text.literal("ID лифта, например lift"));
        liftIdField.setChangedListener(v -> liftId = v);

        int cell = (w - gap * 2) / 3;
        int gridY = y + 31;
        for (int i = 1; i <= 9; i++) {
            final int f = i;
            int col = (i - 1) % 3;
            int row = (i - 1) / 3;
            addDrawableChild(HorrorButton.builder(Text.literal((floor == f ? "§c▶ §r" : "") + "Этаж " + f), button -> {
                floor = f;
                clearAndInit();
            }).dimensions(x + col * (cell + gap), gridY + row * 28, cell, bh).build());
        }
    }

    private void capture() {
        if (a == null || b == null) {
            status = "Сначала выбери область: ПКМ = A, Shift+ПКМ = B.";
            return;
        }
        if (client == null || client.getNetworkHandler() == null) {
            status = "Слои сохраняются только внутри мира.";
            return;
        }

        String build;
        String variant;
        String group;
        String floorSet;
        int floorNumber;
        boolean def;
        boolean restoreOnLoad;

        if (floorMode) {
            if (liftId.isBlank()) {
                status = "Укажи ID лифта. Он должен совпадать с ID в Lift Editor.";
                return;
            }
            build = "lift_" + liftId;
            variant = "floor_" + floor;
            group = "floors";
            floorSet = liftId;
            floorNumber = floor;
            // Never restore every authored floor on world load; the lift chooses exactly one floor at runtime.
            def = false;
            restoreOnLoad = false;
        } else {
            if (objectName.isBlank() || stateName.isBlank()) {
                status = "Заполни «Объект / набор» и «Состояние».";
                return;
            }
            build = objectName;
            variant = stateName;
            group = objectName;
            floorSet = "default";
            floorNumber = 0;
            def = defaultActive;
            restoreOnLoad = restore;
        }

        PacketByteBuf out = PacketByteBufs.create();
        out.writeString(build, 128);
        out.writeString(variant, 128);
        out.writeString(group, 128);
        out.writeBoolean(def);
        out.writeBoolean(restoreOnLoad);
        out.writeString(floorSet, 64);
        out.writeVarInt(floorNumber);
        out.writeBlockPos(a);
        out.writeBlockPos(b);
        ClientPlayNetworking.send(FifthNetworking.STRUCTURE_CAPTURE, out);

        status = floorMode
                ? "Сохранено: лифт «" + liftId + "» → этаж " + floor + "."
                : "Сохранено: «" + objectName + "» → состояние «" + stateName + "».";
    }

    private void activate() {
        if (client == null || client.getNetworkHandler() == null) {
            status = "Активация доступна только внутри мира.";
            return;
        }

        String build = floorMode ? "lift_" + liftId : objectName;
        String variant = floorMode ? "floor_" + floor : stateName;
        if (build.isBlank() || variant.isBlank()) {
            status = "Сначала укажи слой/этаж.";
            return;
        }

        PacketByteBuf out = PacketByteBufs.create();
        out.writeString(build, 128);
        out.writeString(variant, 128);
        ClientPlayNetworking.send(FifthNetworking.STRUCTURE_ACTIVATE, out);
        status = floorMode
                ? "Проверяю этаж " + floor + " для лифта «" + liftId + "»."
                : "Активирую «" + objectName + " / " + stateName + "».";
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        horrorBackground(c);
        int w = contentWidth(520), x = (width - w) / 2, y = safeTop();

        if (floorMode) {
            c.drawTextWithShadow(textRenderer, "ID лифта — тот же, что указан в Lift Editor", x, y + 22, 0xFFB69F97);
            c.drawTextWithShadow(textRenderer, "Выбери номер этажа. Остальные ID мод создаёт сам.", x, y + 53, 0xFF9F918B);
        } else {
            c.drawTextWithShadow(textRenderer, "Объект / набор", x, y + 22, 0xFFB69F97);
            c.drawTextWithShadow(textRenderer, "Состояние", x + (w - 6) / 2 + 6, y + 22, 0xFFB69F97);
        }

        String apos = a == null ? "не выбрана" : a.toShortString();
        String bpos = b == null ? "не выбрана" : b.toShortString();
        c.drawCenteredTextWithShadow(textRenderer, "Область: A " + apos + "  →  B " + bpos,
                width / 2, height - safeBottom() - 27, 0xFFB7A49B);
        if (!status.isBlank()) {
            c.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - safeBottom() - 12, 0xFFD5A9A2);
        }
        super.render(c, mx, my, delta);
    }
}
