package ru.fifth.horror.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import ru.fifth.horror.client.AnimationCatalog;
import ru.fifth.horror.network.FifthNetworking;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Auto-discovered GeckoLib animation catalog. Existing NPCs can preview animations live in the world. */
public class AnimationListScreen extends HorrorScreen {
    private final Screen parent;
    private final Consumer<AnimationCatalog.Entry> select;
    private final int entityId;
    private int page;
    private TextFieldWidget search;
    private List<AnimationCatalog.Entry> filtered = List.of();
    private AnimationCatalog.Entry selectedEntry;

    public AnimationListScreen(Screen parent, Consumer<AnimationCatalog.Entry> select) {
        this(parent, -1, select);
    }

    public AnimationListScreen(Screen parent, int entityId, Consumer<AnimationCatalog.Entry> select) {
        super(Text.literal("ПЯТЫЙ / АНИМАЦИИ GECKOLIB"));
        this.parent = parent;
        this.entityId = entityId;
        this.select = select;
    }

    private boolean livePreview() {
        return entityId >= 0 && client != null && client.world != null && client.getNetworkHandler() != null;
    }

    private int listWidth() {
        if (livePreview()) return Math.max(270, Math.min(520, (int)(width * 0.52f)));
        return contentWidth(540);
    }

    private int listX() {
        return livePreview() ? 12 : (width - listWidth()) / 2;
    }

    @Override
    protected void init() {
        beginHorrorInit();
        int w = listWidth(), x = listX();
        search = horrorField(x, safeTop(), w, 21, search == null ? "" : search.getText(), 128);
        search.setPlaceholder(Text.literal("Поиск по имени, файлу или русскому описанию..."));
        search.setChangedListener(v -> { page = 0; rebuildEntries(); });
        rebuildEntries();
    }

    private void rebuildEntries() {
        String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        List<AnimationCatalog.Entry> out = new ArrayList<>();
        for (var e : AnimationCatalog.INSTANCE.entries()) {
            String hay = (e.name() + " " + e.file() + " " + e.description()).toLowerCase(Locale.ROOT);
            if (q.isBlank() || hay.contains(q)) out.add(e);
        }
        filtered = List.copyOf(out);
        rebuildButtons();
    }

    private void rebuildButtons() {
        String searchValue = search == null ? "" : search.getText();
        clearChildren();
        beginHorrorInit();
        int w = listWidth(), x = listX();
        search = horrorField(x, safeTop(), w, 21, searchValue, 128);
        search.setPlaceholder(Text.literal("Поиск по имени, файлу или русскому описанию..."));
        search.setChangedListener(v -> { page = 0; rebuildEntries(); });

        int top = safeTop() + 31, entryH = 34, gap = 4;
        int controlsH = livePreview() ? 54 : 26;
        int navY = height - safeBottom() - controlsH;
        int available = Math.max(40, navY - top - 5);
        int per = Math.max(2, Math.min(10, available / (entryH + gap)));
        int maxPage = Math.max(0, (filtered.size() - 1) / per);
        page = Math.min(page, maxPage);
        int start = page * per;
        for (int i = start; i < Math.min(start + per, filtered.size()); i++) {
            var e = filtered.get(i);
            int yy = top + (i - start) * (entryH + gap);
            boolean selected = selectedEntry != null && selectedEntry.name().equals(e.name()) && selectedEntry.file().equals(e.file());
            addDrawableChild(new AnimationEntryButton(x, yy, w, entryH, e, selected, () -> choose(e)));
        }

        int side = Math.min(48, Math.max(34, w / 8));
        addDrawableChild(HorrorButton.builder(Text.literal("‹"), b -> { if (page > 0) { page--; rebuildButtons(); } }).dimensions(x, navY, side, 22).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"), b -> client.setScreen(parent)).dimensions(x + side + 6, navY, w - side * 2 - 12, 22).build());
        addDrawableChild(HorrorButton.builder(Text.literal("›"), b -> { if ((page + 1) * per < filtered.size()) { page++; rebuildButtons(); } }).dimensions(x + w - side, navY, side, 22).build());

        if (livePreview()) {
            int by = navY + 28;
            int half = Math.max(70, (w - 6) / 2);
            addDrawableChild(HorrorButton.builder(Text.literal("■ Стоп предпросмотра"), b -> stopPreview()).dimensions(x, by, half, 22).build());
            addDrawableChild(HorrorButton.builder(Text.literal("✓ Использовать выбранную"), b -> applySelected()).dimensions(x + half + 6, by, w - half - 6, 22).build());
        }
    }

    private void choose(AnimationCatalog.Entry e) {
        selectedEntry = e;
        if (livePreview()) {
            sendPreview(e.file().toString(), e.name());
            rebuildButtons();
        } else {
            if (select != null) select.accept(e);
            if (client != null) client.setScreen(parent);
        }
    }

    private void applySelected() {
        if (selectedEntry == null) return;
        if (select != null) select.accept(selectedEntry);
        if (client != null) client.setScreen(parent);
    }

    private void stopPreview() {
        if (livePreview()) sendPreview("", "");
    }

    private void sendPreview(String file, String animation) {
        if (!livePreview()) return;
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(entityId);
        out.writeString(file == null ? "" : file, 512);
        out.writeString(animation == null ? "" : animation, 512);
        ClientPlayNetworking.send(FifthNetworking.PREVIEW_NPC_ANIMATION, out);
    }

    @Override
    public void removed() {
        // Preview is intentionally non-destructive: leaving the catalog returns the NPC to its previous/static state.
        if (entityId >= 0 && MinecraftClient.getInstance().getNetworkHandler() != null) {
            PacketByteBuf out = PacketByteBufs.create();
            out.writeVarInt(entityId);
            out.writeString("", 512);
            out.writeString("", 512);
            ClientPlayNetworking.send(FifthNetworking.PREVIEW_NPC_ANIMATION, out);
        }
        super.removed();
    }

    @Override
    public void render(DrawContext c, int mx, int my, float d) {
        int w = listWidth(), x = listX();
        if (livePreview()) {
            // Keep the actual world visible: animation changes are seen on the NPC in real time.
            c.fill(0, 0, width, height, 0x24000000);
            panel(c, x - 7, 6, w + 14, height - 12);
            c.drawTextWithShadow(textRenderer, title, x + 4, 11, 0xFFE5D7CE);
            c.drawTextWithShadow(textRenderer, "LIVE: клик по анимации сразу проигрывает её на NPC", x + 4, 24, 0xFFFFB0B6);
            if (selectedEntry != null) {
                String info = "▶ " + selectedEntry.name() + " — " + selectedEntry.description();
                c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(info, w - 8), x + 4, height - safeBottom() - 67, 0xFFFFD6A6);
            }
        } else {
            horrorBackground(c);
        }
        c.drawTextWithShadow(textRenderer, "Найдено: " + filtered.size() + " / " + AnimationCatalog.INSTANCE.entries().size(), x, safeTop() + 23, 0xFF9E918B);
        if (!livePreview() && height > 260) c.drawCenteredTextWithShadow(textRenderer, "Новые animation.json автоматически появятся после загрузки ресурсов / F3+T.", width / 2, height - safeBottom() - 38, 0xFF8E837E);
        super.render(c, mx, my, d);
    }

    private static final class AnimationEntryButton extends PressableWidget {
        private final AnimationCatalog.Entry entry;
        private final Runnable action;
        private final boolean selected;
        private AnimationEntryButton(int x, int y, int w, int h, AnimationCatalog.Entry entry, boolean selected, Runnable action) {
            super(x, y, w, h, Text.literal(entry.name()));
            this.entry = entry;
            this.selected = selected;
            this.action = action;
        }
        @Override public void onPress() { if (action != null) action.run(); }
        @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) { appendDefaultNarrations(builder); }
        @Override protected void renderButton(DrawContext c, int mx, int my, float d) {
            int x = getX(), y = getY(); boolean hot = isHovered();
            c.fill(x, y, x + width, y + height, selected ? 0xF02A161A : (hot ? 0xE5201518 : 0xD20D0F12));
            c.fill(x, y, x + (selected ? 4 : 2), y + height, selected ? 0xFFFF9B9F : (hot ? 0xFFD15B65 : 0xFF63343A));
            c.fill(x, y, x + width - 7, y + 1, hot || selected ? 0xFFD99A9D : 0xFF6A494D);
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            String name = (selected ? "▶ " : "") + entry.name();
            if (tr.getWidth(name) > width - 18) name = tr.trimToWidth(name, width - 28) + "…";
            c.drawTextWithShadow(tr, name, x + 8, y + 5, 0xFFE8DDD5);
            String file = entry.file().getNamespace() + ":" + entry.file().getPath();
            String desc = entry.description() + "   [" + file + "]";
            if (tr.getWidth(desc) > width - 18) desc = tr.trimToWidth(desc, width - 28) + "…";
            c.drawTextWithShadow(tr, desc, x + 8, y + 19, 0xFFA69790);
        }
    }
}
