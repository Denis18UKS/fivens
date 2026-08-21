package ru.fifth.horror.client.gui;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import ru.fifth.horror.block.LiftBlockEntity;
import ru.fifth.horror.block.LiftPanelBlockEntity;
import ru.fifth.horror.block.TelevisionBlockEntity;
import ru.fifth.horror.entity.DirectorNpcEntity;
import ru.fifth.horror.entity.MonsterForLiftEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Generic target picker used when a GUI tool is right-clicked in the air.
 * Clicking a target directly still opens the editor immediately, while RMB in-hand always opens this screen.
 */
public final class ToolTargetScreen extends HorrorScreen {
    public enum Mode {
        NPC("Выбери NPC"),
        MFL("Выбери MFL"),
        ANIMATION_CONDITION("Выбери сущность"),
        LIFT("Выбери лифт"),
        LIFT_PANEL("Выбери панель лифта"),
        TV("Выбери телевизор");

        private final String title;
        Mode(String title) { this.title = title; }
    }

    private record Target(String label, String searchable, Runnable open) {}

    private final Screen parent;
    private final Mode mode;
    private final List<Target> targets = new ArrayList<>();
    private TextFieldWidget search;
    private int page;
    private int rows = 6;
    private String status = "";

    public ToolTargetScreen(Screen parent, Mode mode) {
        super(Text.literal("FIVEN / " + mode.title.toUpperCase(Locale.ROOT)));
        this.parent = parent;
        this.mode = mode;
    }

    @Override
    protected void init() {
        beginHorrorInit();
        int w = contentWidth(560);
        int x = (width - w) / 2;
        int top = safeTop();
        int bh = 21;
        rows = Math.max(2, Math.min(10, (height - top - safeBottom() - 92) / 26));

        String oldSearch = search == null ? "" : search.getText();
        search = horrorField(x, top, w, bh, oldSearch, 128);
        search.setPlaceholder(Text.literal("Поиск по имени, ID, типу или координатам..."));
        search.setChangedListener(v -> { page = 0; refreshButtons(); });

        int listY = top + 29;
        for (int i = 0; i < rows; i++) {
            final int row = i;
            addDrawableChild(HorrorButton.builder(Text.literal("-"), b -> choose(row))
                    .dimensions(x, listY + i * 26, w, bh).compact().build());
        }

        int navY = listY + rows * 26 + 2;
        addDrawableChild(HorrorButton.builder(Text.literal("‹"), b -> {
            if (page > 0) { page--; refreshButtons(); }
        }).dimensions(x, navY, 46, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Обновить список"), b -> {
            reloadTargets();
            refreshButtons();
        }).dimensions(x + 52, navY, w - 104, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("›"), b -> {
            int count = filtered().size();
            if ((page + 1) * rows < count) { page++; refreshButtons(); }
        }).dimensions(x + w - 46, navY, 46, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"), b -> client.setScreen(parent))
                .dimensions(x, navY + 27, w, bh).build());

        reloadTargets();
        refreshButtons();
    }

    private void reloadTargets() {
        targets.clear();
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null || c.player == null) {
            status = "Список доступен только внутри мира.";
            return;
        }

        switch (mode) {
            case NPC -> scanNpcTargets(c);
            case MFL -> scanMflTargets(c);
            case ANIMATION_CONDITION -> scanEntityTargets(c);
            case LIFT, LIFT_PANEL, TV -> scanBlockTargets(c);
        }

        targets.sort(Comparator.comparing(Target::label, String.CASE_INSENSITIVE_ORDER));
        page = Math.min(page, Math.max(0, (targets.size() - 1) / Math.max(1, rows)));
        status = targets.isEmpty() ? emptyMessage() : "Найдено: " + targets.size();
    }

    private void scanNpcTargets(MinecraftClient c) {
        Box box = c.player.getBoundingBox().expand(96.0);
        for (Entity e : c.world.getOtherEntities(c.player, box, e -> e instanceof DirectorNpcEntity && !e.isRemoved())) {
            DirectorNpcEntity npc = (DirectorNpcEntity) e;
            String name = npc.getCustomName() == null ? npc.getNpcId() : npc.getCustomName().getString();
            String label = name + " §8[§7" + npc.getNpcId() + "§8] §7• " + shortPos(npc.getBlockPos());
            targets.add(new Target(label, name + " " + npc.getNpcId() + " npc " + npc.getUuidAsString(),
                    () -> MinecraftClient.getInstance().setScreen(new NpcEditorScreen(this, npc))));
        }
    }

    private void scanMflTargets(MinecraftClient c) {
        Box box = c.player.getBoundingBox().expand(96.0);
        for (Entity e : c.world.getOtherEntities(c.player, box, e -> e instanceof MonsterForLiftEntity && !e.isRemoved())) {
            MonsterForLiftEntity mfl = (MonsterForLiftEntity) e;
            String label = mfl.getName().getString() + " §8[§7MFL§8] §7• " + shortPos(mfl.getBlockPos());
            targets.add(new Target(label, label + " " + mfl.getUuidAsString(),
                    () -> MinecraftClient.getInstance().setScreen(new MflEditorScreen(this, mfl))));
        }
    }

    private void scanEntityTargets(MinecraftClient c) {
        Entity self = c.player;
        targets.add(entityTarget(self, "§e[ВЫ] §r"));
        Box box = c.player.getBoundingBox().expand(96.0);
        for (Entity e : c.world.getOtherEntities(c.player, box, e -> !e.isRemoved())) {
            targets.add(entityTarget(e, ""));
        }
    }

    private Target entityTarget(Entity entity, String prefix) {
        String type = entity.getType().toString();
        String label = prefix + entity.getName().getString() + " §8[§7" + type + "§8] §7• " + shortPos(entity.getBlockPos());
        return new Target(label, label + " " + entity.getUuidAsString(),
                () -> MinecraftClient.getInstance().setScreen(new AnimationConditionScreen(this, entity)));
    }

    private void scanBlockTargets(MinecraftClient c) {
        BlockPos center = c.player.getBlockPos();
        int radius = 20;
        int minY = Math.max(c.world.getBottomY(), center.getY() - 12);
        int maxY = Math.min(c.world.getTopY() - 1, center.getY() + 12);
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    mutable.set(x, y, z);
                    BlockEntity be = c.world.getBlockEntity(mutable);
                    if (be == null) continue;
                    BlockPos pos = mutable.toImmutable();

                    if (mode == Mode.LIFT && be instanceof LiftBlockEntity lift) {
                        String label = "Лифт §f" + lift.getLiftId() + " §7• этаж " + lift.getCurrentFloor() + " • " + shortPos(pos);
                        targets.add(new Target(label, label + " lift " + lift.getLiftId(),
                                () -> MinecraftClient.getInstance().setScreen(new LiftEditorScreen(this, pos,
                                        lift.getLiftId(), lift.getCurrentFloor(), lift.getOpenFloorMask(), lift.getStageOrigin()))));
                    } else if (mode == Mode.LIFT_PANEL && be instanceof LiftPanelBlockEntity panel) {
                        String link = panel.getLiftPos() == null ? "не привязана" : "→ " + panel.getLiftWorld() + " " + shortPos(panel.getLiftPos());
                        String label = "Панель §7• " + shortPos(pos) + " §8[§7" + link + "§8]";
                        targets.add(new Target(label, label + " panel lift_panel",
                                () -> MinecraftClient.getInstance().setScreen(new LiftPanelScreen(this, pos, panel.getEnabledMask(), true))));
                    } else if (mode == Mode.TV && be instanceof TelevisionBlockEntity tv) {
                        String label = "Телевизор §7• " + shortPos(pos);
                        targets.add(new Target(label, label + " tv television",
                                () -> MinecraftClient.getInstance().setScreen(new TvSettingsScreen(this, pos,
                                        tv.getQuality(), tv.getNoise(), tv.isMonochrome()))));
                    }
                }
            }
        }
    }

    private List<Target> filtered() {
        String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        if (q.isBlank()) return List.copyOf(targets);
        return targets.stream().filter(t -> (t.label + " " + t.searchable).toLowerCase(Locale.ROOT).contains(q)).toList();
    }

    private void refreshButtons() {
        if (search == null) return;
        List<Target> list = filtered();
        int maxPage = Math.max(0, (list.size() - 1) / Math.max(1, rows));
        page = Math.min(page, maxPage);
        int start = page * rows;
        int listY = safeTop() + 29;
        int idx = 0;
        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.widget.ClickableWidget b
                    && b.getY() >= listY && b.getY() < listY + rows * 26) {
                int p = start + idx++;
                if (p < list.size()) {
                    b.setMessage(Text.literal(list.get(p).label));
                    b.active = true;
                } else {
                    b.setMessage(Text.literal("-"));
                    b.active = false;
                }
            }
        }
        status = list.isEmpty() ? emptyMessage() : "Найдено: " + list.size() + " • страница " + (page + 1) + "/" + (maxPage + 1);
    }

    private void choose(int row) {
        List<Target> list = filtered();
        int index = page * rows + row;
        if (index >= 0 && index < list.size()) list.get(index).open.run();
    }

    private String emptyMessage() {
        return switch (mode) {
            case NPC -> "Рядом не найдено режиссёрских NPC.";
            case MFL -> "Рядом не найдено MFL.";
            case ANIMATION_CONDITION -> "Нет доступных сущностей.";
            case LIFT -> "В радиусе 20 блоков не найден физический лифт.";
            case LIFT_PANEL -> "В радиусе 20 блоков не найдена панель лифта.";
            case TV -> "В радиусе 20 блоков не найден телевизор Fiven.";
        };
    }

    private static String shortPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        horrorBackground(c);
        c.drawCenteredTextWithShadow(textRenderer, mode.title, width / 2, Math.max(22, safeTop() - 12), 0xFFD0B9AE);
        if (!status.isBlank()) c.drawCenteredTextWithShadow(textRenderer, status, width / 2,
                height - safeBottom() - 11, 0xFFD99090);
        super.render(c, mx, my, delta);
    }
}
