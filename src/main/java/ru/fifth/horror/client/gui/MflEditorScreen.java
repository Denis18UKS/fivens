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
import ru.fifth.horror.entity.MonsterForLiftEntity;
import ru.fifth.horror.network.FifthNetworking;

import java.util.List;
import java.util.Locale;

/** Responsive MFL editor using every animation discovered in monster_for_lift.animation.json. */
public final class MflEditorScreen extends HorrorScreen {
    private static final String MFL_ANIMATION_FILE = "fiven:animations/monster_for_lift.animation.json";
    private final Screen parent;
    private final int id;
    private TextFieldWidget search;
    private String status = "";
    private String selected = "";
    private int page;
    private int rows = 5;

    public MflEditorScreen(Screen parent, MonsterForLiftEntity mfl) {
        super(Text.literal("FIVEN / MFL EDITOR"));
        this.parent = parent;
        this.id = mfl.getId();
    }

    @Override
    protected void init() {
        beginHorrorInit();
        int w = contentWidth(520), x = (width - w) / 2, y = safeTop(), bh = 21, gap = 6;
        rows = Math.max(2, Math.min(8, (height - y - safeBottom() - 200) / 25));

        int third = (w - gap * 2) / 3;
        addDrawableChild(HorrorButton.builder(Text.literal("▶ / ■ Маршрут"), b -> send("toggle_route"))
                .dimensions(x, y, third, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Очистить маршрут"), b -> send("clear_route"))
                .dimensions(x + third + gap, y, third, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("ИИ / Охота"), b -> openAi())
                .dimensions(x + (third + gap) * 2, y, w - (third + gap) * 2, bh).build());

        String oldSearch = search == null ? "" : search.getText();
        search = horrorField(x, y + 28, w, bh, oldSearch, 96);
        search.setPlaceholder(Text.literal("Поиск личной анимации MFL..."));
        search.setChangedListener(v -> { page = 0; refresh(); });

        int listY = y + 57;
        for (int i = 0; i < rows; i++) {
            final int row = i;
            addDrawableChild(HorrorButton.builder(Text.literal("-"), b -> choose(row))
                    .dimensions(x, listY + i * 25, w, bh).compact().build());
        }

        int navY = listY + rows * 25 + 3;
        addDrawableChild(HorrorButton.builder(Text.literal("‹"), b -> { if (page > 0) { page--; refresh(); } })
                .dimensions(x, navY, 48, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("▶ Проиграть выбранную"), b -> { if (!selected.isBlank()) anim(selected); })
                .dimensions(x + 54, navY, w - 108, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("›"), b -> { if ((page + 1) * rows < filtered().size()) { page++; refresh(); } })
                .dimensions(x + w - 48, navY, 48, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("■ Стоп / idle"), b -> anim("idle"))
                .dimensions(x, navY + 28, (w-gap)/2, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Тест скримера"), b -> send("screamer"))
                .dimensions(x+(w-gap)/2+gap, navY + 28, (w-gap)/2, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"), b -> client.setScreen(parent))
                .dimensions(x, navY + 56, w, bh).build());
        refresh();
    }

    private void openAi(){
        if(client!=null&&client.world!=null&&client.world.getEntityById(id) instanceof MonsterForLiftEntity mfl){client.setScreen(new MflAiScreen(this,mfl));}
        else status="MFL больше не найден.";
    }

    private List<AnimationCatalog.Entry> filtered() {
        String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        return AnimationCatalog.INSTANCE.entries().stream()
                .filter(e -> MFL_ANIMATION_FILE.equals(e.file().toString()))
                .filter(e -> q.isBlank() || (e.name() + " " + e.description()).toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }

    private void refresh() {
        if (search == null) return;
        List<AnimationCatalog.Entry> list = filtered();
        int max = Math.max(0, (list.size() - 1) / Math.max(1, rows));
        page = Math.min(page, max);
        int start = page * rows, idx = 0;
        int listY = safeTop() + 57;
        for (var child : children()) {
            if (child instanceof ClickableWidget b && b.getY() >= listY && b.getY() < listY + rows * 25) {
                int p = start + idx++;
                if (p < list.size()) {
                    var e = list.get(p);
                    b.setMessage(Text.literal((e.name().equals(selected) ? "§c▶ §r" : "") + e.name() + " §8— §7" + e.description()));
                    b.active = true;
                } else {
                    b.setMessage(Text.literal("-"));
                    b.active = false;
                }
            }
        }
        if (list.isEmpty()) status = "В monster_for_lift.animation.json не найдено анимаций. Обнови ресурсы F3+T.";
    }

    private void choose(int row) {
        List<AnimationCatalog.Entry> list = filtered();
        int p = page * rows + row;
        if (p >= list.size()) return;
        selected = list.get(p).name();
        status = "Выбрано: " + selected;
        refresh();
    }

    private void send(String action) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeVarInt(id);
        b.writeString(action, 64);
        ClientPlayNetworking.send(FifthNetworking.MFL_CONTROL, b);
        status = "Команда MFL: " + action;
    }

    private void anim(String animation) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeVarInt(id);
        b.writeString("animation", 64);
        b.writeString(animation, 128);
        ClientPlayNetworking.send(FifthNetworking.MFL_CONTROL, b);
        status = "Проигрывается: " + animation;
    }

    @Override
    public void render(DrawContext c, int mx, int my, float d) {
        horrorBackground(c);
        c.drawCenteredTextWithShadow(textRenderer, "Анимации берутся автоматически из monster_for_lift.animation.json", width / 2,
                Math.max(22, safeTop() - 12), 0xFFD0B9AE);
        if (!status.isBlank()) c.drawCenteredTextWithShadow(textRenderer, status, width / 2,
                height - safeBottom() - 11, 0xFFD89090);
        super.render(c, mx, my, d);
    }
}
