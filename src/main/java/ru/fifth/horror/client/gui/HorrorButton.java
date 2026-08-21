package ru.fifth.horror.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/** Non-vanilla button used by every Fifth screen. */
public class HorrorButton extends PressableWidget {
    private final Consumer<HorrorButton> pressAction;
    private final boolean compact;

    public HorrorButton(int x, int y, int width, int height, Text message, Consumer<HorrorButton> pressAction) {
        this(x, y, width, height, message, pressAction, false);
    }

    public HorrorButton(int x, int y, int width, int height, Text message, Consumer<HorrorButton> pressAction, boolean compact) {
        super(x, y, width, height, message);
        this.pressAction = pressAction;
        this.compact = compact;
    }

    public static Builder builder(Text message, Consumer<HorrorButton> action) { return new Builder(message, action); }

    @Override public void onPress() { if (active && pressAction != null) pressAction.accept(this); }
    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) { appendDefaultNarrations(builder); }

    @Override
    protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), x2 = x + width, y2 = y + height;
        boolean hot = isHovered() && active;

        int fill = active ? (hot ? 0xE5201518 : 0xD40D0F12) : 0xC10A0B0D;
        int top = active ? (hot ? 0xFFE2A0A2 : 0xFF765156) : 0xFF342A2D;
        int edge = hot ? 0xFFD4656D : 0xFF5B3237;
        int text = active ? (hot ? 0xFFFFEEE5 : 0xFFE1D4CC) : 0xFF6C6460;

        // Main-menu-style flat plaque, intentionally asymmetric and worn.
        context.fill(x, y, x2, y2, fill);
        context.fill(x, y, x2 - 6, y + 1, top);
        context.fill(x, y2 - 1, x2, y2, 0xFF281D20);
        context.fill(x, y, x + 2, y2, edge);
        context.fill(x2 - 1, y + 4, x2, y2 - 3, 0x804B2B30);
        if (hot) {
            context.fill(x + 4, y + 4, x + 7, y2 - 4, 0xFF8D2D38);
            context.fill(x + 9, y2 - 4, x2 - 9, y2 - 3, 0x454D292E);
        } else {
            context.fill(x + 8, y + 4, x + 22, y + 5, 0x274C3638);
        }

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        String raw = getMessage().getString();
        Text msg = getMessage();
        int max = Math.max(12, width - 20);
        if (tr.getWidth(raw) > max) msg = Text.literal(tr.trimToWidth(raw, max - 6) + "…");
        int tx = compact ? x + 8 : x + Math.max(8, (width - tr.getWidth(msg)) / 2);
        context.drawTextWithShadow(tr, msg, tx, y + (height - 8) / 2, text);
    }

    public static final class Builder {
        private final Text message;
        private final Consumer<HorrorButton> action;
        private int x, y, width = 150, height = 20;
        private boolean compact;
        private Builder(Text message, Consumer<HorrorButton> action) { this.message = message; this.action = action; }
        public Builder dimensions(int x, int y, int width, int height) { this.x=x; this.y=y; this.width=width; this.height=height; return this; }
        public Builder compact() { compact = true; return this; }
        public HorrorButton build() { return new HorrorButton(x,y,width,height,message,action,compact); }
    }
}
