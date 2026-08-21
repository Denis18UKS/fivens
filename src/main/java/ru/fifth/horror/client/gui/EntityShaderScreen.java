package ru.fifth.horror.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import ru.fifth.horror.network.FifthNetworking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Searchable, responsive entity list + live shader binding settings. */
public final class EntityShaderScreen extends HorrorScreen {
    private final Screen parent;
    private TextFieldWidget search, hex, ox, oy, oz, intensity;
    private final List<Entity> entities = new ArrayList<>();
    private UUID selected;
    private String type = "dark", status = "";
    private int page;
    private int rows = 6;

    public EntityShaderScreen(Screen parent) {
        super(Text.literal("FIVEN / ШЕЙДЕРЫ СУЩНОСТЕЙ"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        beginHorrorInit();
        int w = contentWidth(600), x = (width - w) / 2, y = safeTop(), g = 6, bh = 20;
        rows = Math.max(2, Math.min(9, (height - y - safeBottom() - 168) / 24));

        String oldSearch = search == null ? "" : search.getText();
        search = horrorField(x, y, w, bh, oldSearch, 128);
        search.setPlaceholder(Text.literal("Поиск сущности..."));
        search.setChangedListener(s -> { page = 0; reload(); refresh(); });

        addDrawableChild(HorrorButton.builder(Text.literal(typeText()), b -> {
            type = switch (type) { case "dark" -> "eyes"; case "eyes" -> "off"; default -> "dark"; };
            b.setMessage(Text.literal(typeText()));
        }).dimensions(x, y + 28, w, bh).build());

        int listY = y + 56;
        for (int i = 0; i < rows; i++) {
            final int row = i;
            addDrawableChild(HorrorButton.builder(Text.literal("-"), b -> choose(row))
                    .dimensions(x, listY + i * 24, w, bh).compact().build());
        }

        int fieldsY = listY + rows * 24 + 4;
        int col = Math.max(40, (w - g * 4) / 5);
        hex = horrorField(x, fieldsY, col, bh, "#FF2020", 10);
        ox = horrorField(x + col + g, fieldsY, col, bh, "0", 12);
        oy = horrorField(x + (col + g) * 2, fieldsY, col, bh, "0", 12);
        oz = horrorField(x + (col + g) * 3, fieldsY, col, bh, "0", 12);
        intensity = horrorField(x + (col + g) * 4, fieldsY, w - (col + g) * 4, bh, "1.0", 8);

        int navY = fieldsY + 28;
        addDrawableChild(HorrorButton.builder(Text.literal("‹"), b -> { if (page > 0) { page--; refresh(); } })
                .dimensions(x, navY, 48, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Привязать / обновить эффект"), b -> save())
                .dimensions(x + 54, navY, w - 108, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("›"), b -> {
            if ((page + 1) * rows < entities.size()) { page++; refresh(); }
        }).dimensions(x + w - 48, navY, 48, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"), b -> client.setScreen(parent))
                .dimensions(x, navY + 27, w, bh).build());

        reload();
        refresh();
    }

    private String typeText() {
        return switch (type) {
            case "dark" -> "Эффект: ТЁМНЫЕ СГУСТКИ (shader)";
            case "eyes" -> "Эффект: СВЕТЯЩИЕСЯ ГЛАЗА (shader)";
            default -> "Эффект: ВЫКЛЮЧИТЬ";
        };
    }

    private void reload() {
        entities.clear();
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null || c.player == null) return;
        Box box = c.player.getBoundingBox().expand(128);
        entities.add(c.player);
        entities.addAll(c.world.getOtherEntities(c.player, box, e -> !e.isRemoved()));
        String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        entities.removeIf(e -> !q.isBlank() && !(e.getName().getString() + " " + e.getType() + " " + e.getUuidAsString())
                .toLowerCase(Locale.ROOT).contains(q));
        entities.sort(Comparator.comparing(e -> e.getName().getString(), String.CASE_INSENSITIVE_ORDER));
    }

    private void refresh() {
        int max = Math.max(0, (entities.size() - 1) / Math.max(1, rows));
        page = Math.min(page, max);
        int start = page * rows, idx = 0;
        int listY = safeTop() + 56;
        for (var child : children()) {
            if (child instanceof ClickableWidget b && b.getY() >= listY && b.getY() < listY + rows * 24) {
                int p = start + idx++;
                if (p < entities.size()) {
                    Entity e = entities.get(p);
                    boolean sel = e.getUuid().equals(selected);
                    b.setMessage(Text.literal((sel ? "§c▶ §r" : "") + e.getName().getString() + " §8[§7" + e.getType() + "§8]"));
                    b.active = true;
                } else {
                    b.setMessage(Text.literal("-"));
                    b.active = false;
                }
            }
        }
    }

    private void choose(int row) {
        int p = page * rows + row;
        if (p < entities.size()) {
            selected = entities.get(p).getUuid();
            status = "Выбрано: " + entities.get(p).getName().getString();
            refresh();
        }
    }

    private void save() {
        if (selected == null) { status = "Сначала выбери сущность."; return; }
        try {
            String h = hex.getText().trim().replace("#", "");
            int rgb = Integer.parseInt(h, 16) & 0xFFFFFF;
            int argb = 0xFF000000 | rgb;
            double x = Double.parseDouble(ox.getText()), y = Double.parseDouble(oy.getText()), z = Double.parseDouble(oz.getText());
            float in = Math.max(.05f, Math.min(4f, Float.parseFloat(intensity.getText())));
            PacketByteBuf b = PacketByteBufs.create();
            b.writeString(selected.toString(), 64);
            b.writeString(type, 16);
            b.writeInt(argb);
            b.writeDouble(x); b.writeDouble(y); b.writeDouble(z); b.writeFloat(in);
            ClientPlayNetworking.send(FifthNetworking.ENTITY_EFFECT_SAVE, b);
            status = "Эффект сохранён.";
        } catch (Exception e) {
            status = "Проверь HEX, смещение и интенсивность.";
        }
    }

    @Override
    public void render(DrawContext c, int mx, int my, float d) {
        horrorBackground(c);
        int w = contentWidth(600), x = (width - w) / 2;
        int fieldsY = safeTop() + 56 + rows * 24 + 4;
        int col = Math.max(40, (w - 24) / 5);
        c.drawTextWithShadow(textRenderer, "Цвет", x, fieldsY - 10, 0xFFB69F97);
        c.drawTextWithShadow(textRenderer, "X", x + col + 6, fieldsY - 10, 0xFFB69F97);
        c.drawTextWithShadow(textRenderer, "Y", x + (col + 6) * 2, fieldsY - 10, 0xFFB69F97);
        c.drawTextWithShadow(textRenderer, "Z", x + (col + 6) * 3, fieldsY - 10, 0xFFB69F97);
        c.drawTextWithShadow(textRenderer, "Сила", x + (col + 6) * 4, fieldsY - 10, 0xFFB69F97);
        if (!status.isBlank()) c.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - safeBottom() - 11, 0xFFD99090);
        super.render(c, mx, my, d);
    }
}
