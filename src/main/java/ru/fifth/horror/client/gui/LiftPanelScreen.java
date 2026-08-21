package ru.fifth.horror.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import ru.fifth.horror.network.FifthNetworking;

/** Responsive 3x3 floor panel for the physical LiftPanelBlock. */
public final class LiftPanelScreen extends HorrorScreen {
    private final Screen parent;
    private final BlockPos panelPos;
    private int mask;
    private final boolean edit;
    private String status = "";

    public LiftPanelScreen(Screen parent, BlockPos panelPos, int mask, boolean edit) {
        super(Text.literal(edit ? "FIVEN / РЕДАКТОР ПАНЕЛИ" : "ЛИФТ"));
        this.parent = parent;
        this.panelPos = panelPos;
        this.mask = mask;
        this.edit = edit;
    }

    @Override
    protected void init() {
        beginHorrorInit();
        int maxPanel = Math.min(contentWidth(480), Math.max(180, height - safeTop() - safeBottom() - (edit ? 92 : 58)));
        int gap = Math.max(4, Math.min(8, maxPanel / 45));
        int size = Math.max(34, Math.min(72, (maxPanel - gap * 2) / 3));
        int total = size * 3 + gap * 2;
        int x = (width - total) / 2;
        int y = Math.max(safeTop() + 24, (height - total) / 2 - (edit ? 18 : 0));
        int maxY = height - safeBottom() - total - (edit ? 54 : 30);
        y = Math.max(safeTop() + 24, Math.min(y, maxY));

        for (int f = 1; f <= 9; f++) {
            final int floor = f;
            int col = (f - 1) % 3;
            int row = (f - 1) / 3;
            boolean doorsAllowed = (mask & (1 << (f - 1))) != 0;
            String label = doorsAllowed ? Integer.toString(f) : "§4" + f + " ✕";
            addDrawableChild(HorrorButton.builder(Text.literal(label), button -> {
                if (edit) {
                    boolean nowAllowed = (mask & (1 << (floor - 1))) == 0;
                    if (nowAllowed) mask |= 1 << (floor - 1); else mask &= ~(1 << (floor - 1));
                    send("door", floor, nowAllowed ? 1 : 0);
                    clearAndInit();
                } else {
                    send("press", floor, 0);
                    if (client != null) client.setScreen(null);
                }
            }).dimensions(x + col * (size + gap), y + row * (size + gap), size, size).build());
        }

        int yy = y + total + 8;
        if (edit) {
            addDrawableChild(HorrorButton.builder(Text.literal("Готово"), b -> client.setScreen(parent)).dimensions(x, yy, total, 22).build());
        } else {
            addDrawableChild(HorrorButton.builder(Text.literal("Закрыть"), b -> client.setScreen(parent)).dimensions(x, yy, total, 22).build());
        }
    }

    private void send(String action, int floor, int value) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(panelPos);
        buf.writeString(action, 32);
        buf.writeVarInt(floor);
        buf.writeVarInt(value);
        ClientPlayNetworking.send(FifthNetworking.LIFT_PANEL_CONTROL, buf);
        status = action + " " + floor;
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        horrorBackground(c);
        c.drawCenteredTextWithShadow(textRenderer,
                edit ? "Красный ✕: лифт приедет, но двери не откроются" : "Выбери этаж",
                width / 2, safeTop() + 6, 0xFFD0B9AE);
        if (edit && !status.isBlank() && height >= 220) {
            c.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - safeBottom() - 11, 0xFFD99090);
        }
        super.render(c, mx, my, delta);
    }
}
