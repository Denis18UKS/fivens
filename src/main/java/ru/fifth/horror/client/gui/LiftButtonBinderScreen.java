package ru.fifth.horror.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import ru.fifth.horror.lift.LiftBindingFeature;

/** Final, explicit step of lift call-button binding: click one floor and the binding is saved immediately. */
public final class LiftButtonBinderScreen extends HorrorScreen {
    private final Screen parent;
    private final Hand hand;
    private final String liftName;
    private final BlockPos buttonPos;
    private int selected;

    public LiftButtonBinderScreen(Screen parent, Hand hand, int currentFloor, String liftName, BlockPos buttonPos) {
        super(Text.literal("FIVEN / КНОПКА → ЭТАЖ"));
        this.parent = parent;
        this.hand = hand;
        this.selected = Math.max(1, Math.min(9, currentFloor));
        this.liftName = liftName == null || liftName.isBlank() ? "lift" : liftName;
        this.buttonPos = buttonPos;
    }

    @Override
    protected void init() {
        beginHorrorInit();
        int w = contentWidth(420), x = (width - w) / 2, y = safeTop(), gap = 7, bh = 30;
        int cell = (w - gap * 2) / 3;

        for (int floor = 1; floor <= 9; floor++) {
            final int f = floor;
            int col = (floor - 1) % 3, row = (floor - 1) / 3;
            addDrawableChild(HorrorButton.builder(Text.literal(label(f)), button -> {
                selected = f;
                save();
            }).dimensions(x + col * (cell + gap), y + 48 + row * (bh + gap), cell, bh).build());
        }

        addDrawableChild(HorrorButton.builder(Text.literal("Отмена"), button -> client.setScreen(parent))
                .dimensions(x, y + 48 + 3 * (bh + gap) + 8, w, 22).build());
    }

    private String label(int f) {
        return (f == selected ? "§c▶ §r" : "") + f + " этаж";
    }

    private void save() {
        if (buttonPos == null) return;
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(hand == Hand.OFF_HAND ? 1 : 0);
        out.writeBlockPos(buttonPos);
        out.writeVarInt(selected);
        ClientPlayNetworking.send(LiftBindingFeature.BIND_FLOOR, out);
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float d) {
        horrorBackground(c);
        int y = safeTop();
        c.drawCenteredTextWithShadow(textRenderer, "Лифт: " + liftName, width / 2, y + 4, 0xFFD0B9AE);
        c.drawCenteredTextWithShadow(textRenderer,
                "Кнопка: " + (buttonPos == null ? "?" : buttonPos.toShortString()),
                width / 2, y + 17, 0xFFB69F97);
        c.drawCenteredTextWithShadow(textRenderer,
                "Нажми этаж — привязка сохранится сразу", width / 2, y + 30, 0xFFD89090);
        super.render(c, mx, my, d);
    }
}
