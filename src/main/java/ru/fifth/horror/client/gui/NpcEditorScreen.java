package ru.fifth.horror.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import ru.fifth.horror.client.AnimationCatalog;
import ru.fifth.horror.entity.DirectorNpcEntity;
import ru.fifth.horror.network.FifthNetworking;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Responsive visual NPC control: no script computer required. */
public final class NpcEditorScreen extends HorrorScreen {
    private final Screen parent;
    private final int entityId;
    private DirectorNpcEntity npc;
    private TextFieldWidget search;
    private boolean personal = true;
    private int page;
    private int rows = 6;
    private String selectedAnimation = "";
    private String selectedFile = "";
    private String status = "";

    public NpcEditorScreen(Screen parent, DirectorNpcEntity npc) {
        super(Text.literal("FIVEN / NPC EDITOR"));
        this.parent = parent;
        this.npc = npc;
        this.entityId = npc.getId();
    }

    @Override
    protected void init() {
        beginHorrorInit();
        if (client != null && client.world != null && client.world.getEntityById(entityId) instanceof DirectorNpcEntity current) npc = current;

        int w = contentWidth(560);
        int x = (width - w) / 2;
        int top = safeTop();
        int gap = 6;
        int bh = 20;
        rows = Math.max(2, Math.min(10, (height - top - safeBottom() - 174) / 25));

        String oldSearch = search == null ? "" : search.getText();
        search = horrorField(x, top, w, bh, oldSearch, 96);
        search.setPlaceholder(Text.literal("Поиск анимации..."));
        search.setChangedListener(v -> { page = 0; refresh(); });

        int half = (w - gap) / 2;
        addDrawableChild(HorrorButton.builder(Text.literal("Состояние: " + (npc != null && npc.isAiEnabled() ? "АКТИВЕН" : "СТАТУЯ")), b -> {
            control("toggle_ai", "");
            if (npc != null) status = npc.isAiEnabled() ? "Запрошена статуизация" : "Запрошен запуск NPC";
        }).dimensions(x, top + 27, half, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Маршрут: ▶ / ■"), b -> control("toggle_path", "loop"))
                .dimensions(x + half + gap, top + 27, half, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Анимации: " + (personal ? "ЛИЧНЫЕ" : "ВСЕ")), b -> {
            personal = !personal;
            b.setMessage(Text.literal("Анимации: " + (personal ? "ЛИЧНЫЕ" : "ВСЕ")));
            page = 0;
            refresh();
        }).dimensions(x, top + 54, half, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("■ Стоп анимации"), b -> preview("", ""))
                .dimensions(x + half + gap, top + 54, half, bh).build());

        int listY = top + 82;
        for (int i = 0; i < rows; i++) {
            final int row = i;
            addDrawableChild(HorrorButton.builder(Text.literal("-"), b -> choose(row))
                    .dimensions(x, listY + i * 25, w, bh).compact().build());
        }

        int navY = listY + rows * 25 + 3;
        addDrawableChild(HorrorButton.builder(Text.literal("‹"), b -> { if (page > 0) { page--; refresh(); } })
                .dimensions(x, navY, 50, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("▶ Проиграть выбранную"), b -> {
            if (!selectedAnimation.isBlank()) preview(selectedFile, selectedAnimation);
        }).dimensions(x + 56, navY, w - 112, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("›"), b -> {
            if ((page + 1) * rows < filtered().size()) { page++; refresh(); }
        }).dimensions(x + w - 50, navY, 50, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("🎨 Редактор PNG-текстуры"), b -> {
            if (npc != null) client.setScreen(new NpcTextureEditorScreen(this, npc));
        }).dimensions(x, navY + 27, half, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"), b -> client.setScreen(parent))
                .dimensions(x + half + gap, navY + 27, w - half - gap, bh).build());
        refresh();
    }

    private List<AnimationCatalog.Entry> filtered() {
        List<AnimationCatalog.Entry> out = new ArrayList<>();
        String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        String own = npc == null ? "" : npc.getAnimationResource().toString();
        for (var e : AnimationCatalog.INSTANCE.entries()) {
            if (personal && !e.file().toString().equals(own)) continue;
            String hay = (e.name() + " " + e.description() + " " + e.file()).toLowerCase(Locale.ROOT);
            if (q.isBlank() || hay.contains(q)) out.add(e);
        }
        return out;
    }

    private void refresh() {
        if (search == null) return;
        List<AnimationCatalog.Entry> list = filtered();
        int max = Math.max(0, (list.size() - 1) / Math.max(1, rows));
        page = Math.min(page, max);
        int start = page * rows;
        int idx = 0;
        int listY = safeTop() + 82;
        for (var child : children()) {
            if (child instanceof ClickableWidget b && b.getY() >= listY && b.getY() < listY + rows * 25) {
                int p = start + idx++;
                if (p < list.size()) {
                    var e = list.get(p);
                    String selected = e.name().equals(selectedAnimation) && e.file().toString().equals(selectedFile) ? "§c▶ §r" : "";
                    b.setMessage(Text.literal(selected + e.name() + "  §8— §7" + e.description()));
                    b.active = true;
                } else {
                    b.setMessage(Text.literal("-"));
                    b.active = false;
                }
            }
        }
    }

    private void choose(int row) {
        List<AnimationCatalog.Entry> list = filtered();
        int p = page * rows + row;
        if (p >= list.size()) return;
        var e = list.get(p);
        selectedAnimation = e.name();
        selectedFile = e.file().toString();
        status = "Выбрано: " + selectedAnimation;
        refresh();
    }

    private void preview(String file, String anim) {
        if (client == null || client.getNetworkHandler() == null) return;
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(entityId);
        out.writeString(file, 512);
        out.writeString(anim, 512);
        ClientPlayNetworking.send(FifthNetworking.PREVIEW_NPC_ANIMATION, out);
        status = anim.isBlank() ? "Анимация остановлена." : "Играет: " + anim;
    }

    private void control(String action, String arg) {
        if (client == null || client.getNetworkHandler() == null) return;
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(entityId);
        out.writeString(action, 64);
        out.writeString(arg, 256);
        ClientPlayNetworking.send(FifthNetworking.NPC_CONTROL, out);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float d) {
        horrorBackground(c);
        String heading = npc == null ? "NPC" : npc.getNpcId() + " | точек пути: " + npc.getPathPoints().size();
        c.drawCenteredTextWithShadow(textRenderer, heading, width / 2, Math.max(4, safeTop() - 12), 0xFFD0B9AE);
        if (!status.isBlank()) c.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - safeBottom() - 11, 0xFFD89090);
        super.render(c, mx, my, d);
    }
}
