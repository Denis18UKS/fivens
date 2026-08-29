package ru.fifth.horror.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import ru.fifth.horror.client.video.VideoImportClient;
import ru.fifth.horror.network.FifthNetworking;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Cutscene library with separate authored-MP4 and external real-video VHS workflows. */
public final class CutsceneLibraryScreen extends HorrorScreen {
    public record Info(String id, int frames, int ticks, boolean teleport) {}

    private final Screen parent;
    private TextFieldWidget search;
    private List<Info> all = new ArrayList<>();
    private int page;
    private int rows = 5;
    private String selected = "";
    private String status = "";

    public CutsceneLibraryScreen(Screen parent) {
        super(Text.literal("FIVEN / БИБЛИОТЕКА КАТСЦЕН И VHS"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        beginHorrorInit();
        int w = contentWidth(560), x = (width - w) / 2, y = safeTop(), bh = 20, gap = 6;
        rows = Math.max(2, Math.min(6, (height - y - safeBottom() - 158) / 24));

        search = horrorField(x, y, w, bh, "", 128);
        search.setPlaceholder(Text.literal("Поиск по названию / ID..."));
        search.setChangedListener(value -> { page = 0; refresh(); });

        for (int i = 0; i < rows; i++) {
            final int row = i;
            addDrawableChild(HorrorButton.builder(Text.literal("-"), button -> choose(row))
                    .dimensions(x, y + 28 + i * 24, w, bh).build());
        }

        int yy = y + 28 + rows * 24 + 4;
        int third = Math.max(70, (w - 2 * gap) / 3);
        addDrawableChild(HorrorButton.builder(Text.literal("▶ Проиграть"), b -> play())
                .dimensions(x, yy, third, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("✎ Редактировать"), b -> edit())
                .dimensions(x + third + gap, yy, third, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("📼 Катсцена → видео"), b -> cassette())
                .dimensions(x + 2 * (third + gap), yy, w - 2 * (third + gap), bh).build());

        addDrawableChild(HorrorButton.builder(Text.literal("🎬 Загрузить видео"), b -> importVideo())
                .dimensions(x, yy + 27, w, bh).build());

        addDrawableChild(HorrorButton.builder(Text.literal("‹"), b -> { page = Math.max(0, page - 1); refresh(); })
                .dimensions(x, yy + 54, 46, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Обновить"), b -> request())
                .dimensions(x + 52, yy + 54, w - 104, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("›"), b -> { page++; refresh(); })
                .dimensions(x + w - 46, yy + 54, 46, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"), b -> client.setScreen(parent))
                .dimensions(x, yy + 81, w, bh).build());
        request();
    }

    private List<Info> filtered() {
        String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        return all.stream().filter(info -> q.isBlank() || info.id().toLowerCase(Locale.ROOT).contains(q)).toList();
    }

    private void refresh() {
        if (search == null) return;
        var list = filtered();
        int max = Math.max(0, (list.size() - 1) / Math.max(1, rows));
        page = Math.min(page, max);
        int start = page * rows, idx = 0;
        int top = safeTop() + 28;
        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.widget.ClickableWidget button
                    && button.getY() >= top && button.getY() < top + rows * 24) {
                int pos = start + idx++;
                if (pos < list.size()) {
                    var info = list.get(pos);
                    button.setMessage(Text.literal(info.id() + "  §8| §7" + info.frames() + " кадров §8| §7"
                            + String.format(Locale.ROOT, "%.1fs", info.ticks() / 20.0)));
                    button.active = true;
                } else {
                    button.setMessage(Text.literal("-"));
                    button.active = false;
                }
            }
        }
    }

    private void choose(int row) {
        var list = filtered();
        int pos = page * rows + row;
        if (pos < list.size()) {
            selected = list.get(pos).id();
            status = "Выбрано: " + selected;
        }
    }

    private void request() {
        if (client == null || client.getNetworkHandler() == null) {
            status = "Библиотека доступна после входа в мир.";
            return;
        }
        ClientPlayNetworking.send(FifthNetworking.REQUEST_CUTSCENE_LIBRARY, PacketByteBufs.empty());
        status = "Обновляю библиотеку...";
    }

    public void update(List<Info> rows) {
        all = new ArrayList<>(rows);
        refresh();
        status = "Катсцен: " + all.size();
    }

    private void play() {
        if (selected.isBlank()) return;
        PacketByteBuf out = PacketByteBufs.create();
        out.writeString(selected, 128);
        ClientPlayNetworking.send(FifthNetworking.PLAY_CUTSCENE, out);
        client.setScreen(null);
    }

    private void edit() {
        if (selected.isBlank()) return;
        PacketByteBuf out = PacketByteBufs.create();
        out.writeString(selected, 128);
        ClientPlayNetworking.send(FifthNetworking.REQUEST_CUTSCENE_EDIT, out);
        status = "Загружаю сцену...";
    }

    private void cassette() {
        if (selected.isBlank()) {
            status = "Сначала выбери катсцену.";
            return;
        }
        edit();
        status = "Открываю сцену — нажми «📼 Записать катсцену в видео».";
    }

    private void importVideo() {
        status = "Выбери реальный видеофайл...";
        VideoImportClient.openPickerAndUpload();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        horrorBackground(context);
        if (!status.isBlank()) {
            context.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - safeBottom() - 11, 0xFFD79A9A);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
