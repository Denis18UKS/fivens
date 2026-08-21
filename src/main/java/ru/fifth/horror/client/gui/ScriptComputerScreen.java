package ru.fifth.horror.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import ru.fifth.horror.network.FifthNetworking;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** FifthScript workstation with a live auto-detected NPC library. */
public class ScriptComputerScreen extends HorrorScreen {
    public record NpcInfo(UUID uuid, String id, String name, boolean aiEnabled, String aiScript, int pathPoints, String world) {}
    private record CodeTemplate(String name, String description, String code) {}

    /* All templates use self. The NPC selected in the library becomes self when the script is launched. */
    private static final CodeTemplate[] TEMPLATES = {
            new CodeTemplate("Запустить интеллект", "Включает выбранного NPC. Пока код не запущен, NPC остаётся статуей.", "npc(\"self\")->startAi();"),
            new CodeTemplate("Остановить интеллект", "Останавливает ИИ и текущий маршрут выбранного NPC.", "npc(\"self\")->stopAi();"),
            new CodeTemplate("Пройти маршрут один раз", "Идёт по точкам маршрутного инструмента один раз.", "npc(\"self\")->startAi();\nnpc(\"self\")->followPath(false, 0.25);"),
            new CodeTemplate("Патрулировать маршрут", "Зацикливает маршрут, заданный маршрутным инструментом.", "npc(\"self\")->startAi();\nnpc(\"self\")->followPath(true, 0.25);"),
            new CodeTemplate("Идти к координатам", "Одно движение к указанной точке мира.", "npc(\"self\")->moveTo(100, 64, -30, 0.25);"),
            new CodeTemplate("Смотреть на игрока", "Постоянный AI: NPC смотрит на ближайшего игрока.", "onNpcTick() {\n    npc(\"self\")->lookAtNearestPlayer(12);\n}"),
            new CodeTemplate("Маршрут + взгляд", "Постоянный AI: патруль по маршруту и взгляд на игрока.", "onNpcTick() {\n    npc(\"self\")->followPath(true, 0.25);\n    npc(\"self\")->lookAtNearestPlayer(10);\n}"),
            new CodeTemplate("Реплика", "Отправляет игрокам реплику от имени выбранного NPC.", "npc(\"self\")->say(\"Я что-то слышал...\");"),
            new CodeTemplate("Проиграть анимацию", "Запускает GeckoLib-анимацию у выбранного NPC.", "npc(\"self\")->animation(\"animation.npc.walk\");"),
            new CodeTemplate("Назначить этот AI-файл", "Явно привязывает текущий .fifth.php к выбранному NPC и запускает ИИ.", "npc(\"self\")->script(\"%SCRIPT%\");\nnpc(\"self\")->startAi();")
    };

    private final BlockPos pos;
    private final String initialName;
    private final String initialScript;
    private TextFieldWidget name;
    private TextFieldWidget npcSearch;
    private EditBoxWidget editor;
    private String status = "";
    private List<NpcInfo> npcLibrary = List.of();
    private String selectedNpc = "";
    private UUID selectedNpcUuid;
    private int npcPage;
    private int templateIndex;
    private int narrowView; // 0 code, 1 NPC, 2 templates
    private int refreshTicks;
    private boolean rebuilding;

    public ScriptComputerScreen(BlockPos pos, String name, String script) {
        super(Text.literal("ПЯТЫЙ / FIFTHSCRIPT COMPUTER"));
        this.pos = pos;
        this.initialName = name;
        this.initialScript = script;
    }

    @Override
    protected void init() {
        String keepName = name == null ? initialName : name.getText();
        String keepScript = editor == null ? initialScript : editor.getText();
        String keepSearch = npcSearch == null ? "" : npcSearch.getText();
        beginHorrorInit();

        int margin = Math.max(8, Math.min(16, width / 42));
        int top = safeTop();
        boolean wide = width >= 700 && height >= 285;

        if (wide) {
            int rightW = Math.max(230, Math.min(320, width / 3));
            int leftW = Math.max(320, width - margin * 3 - rightW);
            int leftX = margin;
            int rightX = leftX + leftW + margin;
            buildCodePanel(leftX, top, leftW, keepName, keepScript, false);
            buildNpcPanel(rightX, top, rightW, keepSearch, true);
        } else {
            int w = Math.max(220, width - margin * 2);
            int x = margin;
            int tabGap = 4;
            int tabW = Math.max(58, (w - tabGap * 2) / 3);
            addDrawableChild(HorrorButton.builder(Text.literal(narrowView == 0 ? "[ КОД ]" : "КОД"), b -> switchNarrow(0)).dimensions(x, top, tabW, 20).build());
            addDrawableChild(HorrorButton.builder(Text.literal(narrowView == 1 ? "[ NPC ]" : "NPC"), b -> switchNarrow(1)).dimensions(x + tabW + tabGap, top, tabW, 20).build());
            addDrawableChild(HorrorButton.builder(Text.literal(narrowView == 2 ? "[ ШАБЛОНЫ ]" : "ШАБЛОНЫ"), b -> switchNarrow(2)).dimensions(x + (tabW + tabGap) * 2, top, w - (tabW + tabGap) * 2, 20).build());
            int bodyTop = top + 27;
            if (narrowView == 0) buildCodePanel(x, bodyTop, w, keepName, keepScript, true);
            else if (narrowView == 1) buildNpcPanel(x, bodyTop, w, keepSearch, false);
            else buildTemplatePanel(x, bodyTop, w);
        }

        if (!rebuilding) requestNpcLibrary();
    }

    private void buildCodePanel(int x, int top, int w, String keepName, String keepScript, boolean narrow) {
        int buttonH = 21;
        int bottomButtons = 2 * buttonH + 8;
        name = horrorField(x, top, Math.max(120, Math.min(narrow ? w : 250, w)), 20, keepName, 128);
        int editorY = top + 27;
        int bottom = height - safeBottom() - bottomButtons;
        int editorH = Math.max(58, bottom - editorY - 5);
        editor = new EditBoxWidget(textRenderer, x, editorY, w, editorH, Text.empty(), Text.literal("FifthScript"));
        editor.setMaxLength(1_000_000);
        editor.setText(keepScript == null ? "" : keepScript);
        frameWidget(editor);
        addDrawableChild(editor);

        int gap = 5;
        int by = height - safeBottom() - buttonH * 2 - gap;
        int half = Math.max(70, (w - gap) / 2);
        addDrawableChild(HorrorButton.builder(Text.literal("Проверить код"), b -> validateScript()).dimensions(x, by, half, buttonH).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Сохранить"), b -> save()).dimensions(x + half + gap, by, w - half - gap, buttonH).build());
        String runLabel = selectedNpc.isBlank() ? "▶ Запустить" : "▶ Запустить для: " + selectedNpc;
        addDrawableChild(HorrorButton.builder(Text.literal(runLabel), b -> run()).dimensions(x, by + buttonH + gap, half, buttonH).compact().build());
        addDrawableChild(HorrorButton.builder(Text.literal("Закрыть"), b -> close()).dimensions(x + half + gap, by + buttonH + gap, w - half - gap, buttonH).build());
    }

    private void buildNpcPanel(int x, int top, int w, String searchText, boolean includeTemplateControls) {
        npcSearch = horrorField(x, top, w, 20, searchText, 100);
        npcSearch.setPlaceholder(Text.literal("Поиск NPC..."));
        npcSearch.setChangedListener(v -> { npcPage = 0; rebuildPreserving(); });

        List<NpcInfo> filtered = filteredNpcs(searchText);
        int bottomReserve = includeTemplateControls ? 104 : 52;
        int available = Math.max(46, height - safeBottom() - (top + 27) - bottomReserve);
        int entryH = 28, gap = 4;
        int per = Math.max(1, available / (entryH + gap));
        int pages = Math.max(1, (filtered.size() + per - 1) / per);
        npcPage = Math.max(0, Math.min(npcPage, pages - 1));
        int from = npcPage * per, to = Math.min(filtered.size(), from + per);
        int y = top + 27;
        for (int i = from; i < to; i++) {
            NpcInfo info = filtered.get(i);
            boolean selected = selectedNpcUuid != null && selectedNpcUuid.equals(info.uuid);
            String select = selected ? "§e▶ ВЫБРАН §r" : "";
            String ai = info.aiEnabled ? "§aИИ:ВКЛ§r" : "§7ИИ:ВЫКЛ§r";
            String label = select + ai + "  " + info.name + " §8[" + info.id + "] §7• путь:" + info.pathPoints;
            addDrawableChild(HorrorButton.builder(Text.literal(label), b -> selectNpc(info)).dimensions(x, y, w, entryH).compact().build());
            y += entryH + gap;
        }

        int navY = height - safeBottom() - (includeTemplateControls ? 92 : 23);
        int side = 38;
        addDrawableChild(HorrorButton.builder(Text.literal("‹"), b -> { if (npcPage > 0) { npcPage--; rebuildPreserving(); } }).dimensions(x, navY, side, 21).build());
        addDrawableChild(HorrorButton.builder(Text.literal((npcPage + 1) + "/" + pages + "   Обновить"), b -> requestNpcLibrary()).dimensions(x + side + 5, navY, w - side * 2 - 10, 21).build());
        addDrawableChild(HorrorButton.builder(Text.literal("›"), b -> { if (npcPage + 1 < pages) { npcPage++; rebuildPreserving(); } }).dimensions(x + w - side, navY, side, 21).build());

        if (includeTemplateControls) {
            int ty = navY + 27;
            addDrawableChild(HorrorButton.builder(Text.literal("Шаблон: " + TEMPLATES[templateIndex].name), b -> {
                templateIndex = (templateIndex + 1) % TEMPLATES.length;
                status = TEMPLATES[templateIndex].description;
                rebuildPreserving();
            }).dimensions(x, ty, w, 21).compact().build());
            addDrawableChild(HorrorButton.builder(Text.literal("Вставить шаблон в код"), b -> insertTemplate()).dimensions(x, ty + 27, w, 21).build());
        }
    }

    private void buildTemplatePanel(int x, int top, int w) {
        int h = 24, gap = 5;
        int max = Math.max(1, (height - safeBottom() - top - 58) / (h + gap));
        int start = Math.max(0, Math.min(templateIndex, Math.max(0, TEMPLATES.length - max)));
        int end = Math.min(TEMPLATES.length, start + max);
        int y = top;
        for (int i = start; i < end; i++) {
            int idx = i;
            addDrawableChild(HorrorButton.builder(Text.literal((i == templateIndex ? "› " : "") + TEMPLATES[i].name), b -> {
                templateIndex = idx;
                status = TEMPLATES[idx].description;
                rebuildPreserving();
            }).dimensions(x, y, w, h).compact().build());
            y += h + gap;
        }
        int by = height - safeBottom() - 23;
        addDrawableChild(HorrorButton.builder(Text.literal("Вставить выбранный шаблон"), b -> insertTemplate()).dimensions(x, by, w, 22).build());
    }

    private List<NpcInfo> filteredNpcs(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<NpcInfo> out = new ArrayList<>();
        for (NpcInfo n : npcLibrary) {
            if (q.isBlank() || n.id.toLowerCase(Locale.ROOT).contains(q) || n.name.toLowerCase(Locale.ROOT).contains(q) || n.world.toLowerCase(Locale.ROOT).contains(q)) out.add(n);
        }
        return out;
    }

    private void selectNpc(NpcInfo info) {
        selectedNpc = info.id;
        selectedNpcUuid = info.uuid;
        if (editor != null) {
            String s = editor.getText();
            // Migration for old templates from 0.4.0: a picked NPC becomes runtime "self".
            s = s.replace("npc(\"npc_id\")", "npc(\"self\")").replace("%NPC%", "self");
            editor.setText(s);
        }
        status = "NPC выбран: " + info.id + ". Кнопка «Запустить» теперь выполняет код именно для него.";
        rebuildPreserving();
    }

    private void insertTemplate() {
        if (editor == null) {
            narrowView = 0;
            rebuildPreserving();
            status = "Открыл редактор кода. Повтори вставку шаблона.";
            return;
        }
        String scriptName = name == null || name.getText().isBlank() ? "main" : name.getText();
        String code = TEMPLATES[templateIndex].code.replace("%SCRIPT%", scriptName);
        String current = editor.getText();
        if (!current.isBlank() && !current.endsWith("\n")) current += "\n";
        editor.setText(current + code + "\n");
        status = selectedNpc.isBlank()
                ? "Шаблон вставлен. Для npc(\"self\") сначала выбери NPC в библиотеке."
                : "Шаблон вставлен. self = " + selectedNpc;
    }

    private void switchNarrow(int view) {
        if (narrowView == view) return;
        narrowView = view;
        rebuildPreserving();
    }

    private void rebuildPreserving() {
        if (client == null || rebuilding) return;
        rebuilding = true;
        clearAndInit();
        rebuilding = false;
    }

    public void updateNpcLibrary(List<NpcInfo> rows) {
        List<NpcInfo> fresh = rows == null ? List.of() : List.copyOf(rows);
        boolean changed = !fresh.equals(npcLibrary);
        npcLibrary = fresh;
        if (selectedNpcUuid != null) {
            NpcInfo selected = npcLibrary.stream().filter(n -> selectedNpcUuid.equals(n.uuid)).findFirst().orElse(null);
            if (selected == null) {
                selectedNpcUuid = null;
                selectedNpc = "";
                status = "Выбранный NPC исчез из загруженного мира.";
            } else {
                selectedNpc = selected.id;
            }
        }
        if (changed && selectedNpcUuid == null) status = "Автообнаружено NPC: " + npcLibrary.size();
        if (changed) rebuildPreserving();
    }

    private void requestNpcLibrary() {
        if (client == null || client.getNetworkHandler() == null) return;
        ClientPlayNetworking.send(FifthNetworking.REQUEST_NPC_LIBRARY, PacketByteBufs.create());
        refreshTicks = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (editor != null) editor.tick();
        if (++refreshTicks >= 40) requestNpcLibrary();
    }

    private void validateScript() {
        if (editor == null) return;
        String s = editor.getText();
        int depth = 0;
        boolean bad = false;
        for (char c : s.toCharArray()) {
            if (c == '{') depth++;
            if (c == '}') depth--;
            if (depth < 0) bad = true;
        }
        status = !bad && depth == 0 ? "Синтаксис блоков: OK" : "Ошибка: несбалансированные { }";
    }

    private void save() {
        if (name == null || editor == null) return;
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBlockPos(pos);
        out.writeString(name.getText(), 128);
        out.writeString(editor.getText(), 1_000_000);
        ClientPlayNetworking.send(FifthNetworking.SAVE_COMPUTER, out);
        status = "Сохранено: " + name.getText();
    }

    private void run() {
        if (name == null || editor == null) return;
        PacketByteBuf out = PacketByteBufs.create();
        // Run is atomic now: the server receives the current unsaved editor text together with the selected NPC.
        out.writeBlockPos(pos);
        out.writeString(name.getText(), 128);
        out.writeString(editor.getText(), 1_000_000);
        out.writeBoolean(selectedNpcUuid != null);
        if (selectedNpcUuid != null) {
            out.writeUuid(selectedNpcUuid);
            out.writeString(selectedNpc, 128);
        }
        ClientPlayNetworking.send(FifthNetworking.RUN_COMPUTER, out);
        status = selectedNpcUuid == null
                ? "Сценарий запущен без выбранного NPC."
                : "Запущено для NPC: " + selectedNpc + " (self).";
    }

    @Override
    public void render(DrawContext c, int mx, int my, float d) {
        horrorBackground(c);
        int m = Math.max(8, Math.min(16, width / 42));
        boolean wide = width >= 700 && height >= 285;
        if (wide) {
            int rightW = Math.max(230, Math.min(320, width / 3));
            int leftW = Math.max(320, width - m * 3 - rightW);
            int top = safeTop();
            panel(c, m - 3, top - 4, leftW + 6, Math.max(70, height - safeBottom() - top + 8));
            panel(c, m * 2 + leftW - 3, top - 4, rightW + 6, Math.max(70, height - safeBottom() - top + 8));
            c.drawTextWithShadow(textRenderer, "КОД / .fifth.php", m + 4, top - 13, 0xFFC5B2A8);
            c.drawTextWithShadow(textRenderer, "ЖИВАЯ БИБЛИОТЕКА NPC", m * 2 + leftW + 4, top - 13, 0xFFC5B2A8);
            if (!selectedNpc.isBlank()) {
                String label = "self = " + selectedNpc;
                c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(label, rightW - 10), m * 2 + leftW + 5, height - safeBottom() - 116, 0xFFFFD39A);
            }
        }
        if (!status.isBlank()) {
            String shown = textRenderer.trimToWidth(status, Math.max(80, width - 20));
            c.drawCenteredTextWithShadow(textRenderer, shown, width / 2, Math.max(31, safeTop() - 14), status.startsWith("Ошибка") ? 0xFFFF8585 : 0xFFB9A8A0);
        }
        super.render(c, mx, my, d);
    }
}
