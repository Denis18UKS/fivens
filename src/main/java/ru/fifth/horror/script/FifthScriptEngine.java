package ru.fifth.horror.script;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.Box;
import ru.fifth.horror.cutscene.CutsceneManager;
import ru.fifth.horror.entity.DirectorNpcEntity;
import ru.fifth.horror.structure.StructureLayerManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FifthScriptEngine {
    private static final Map<String, String> SCRIPTS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> FLAGS = new ConcurrentHashMap<>();
    /** Fast runtime NPC index; full-world scans are now only a cold-cache fallback. */
    private static final Map<String, UUID> NPC_BY_ID = new ConcurrentHashMap<>();
    private static final Map<UUID, DirectorNpcEntity> NPC_BY_UUID = new ConcurrentHashMap<>();
    private static long tick;

    private static final Pattern NPC_CALL = Pattern.compile("npc\\(\\\"([^\\\"]+)\\\"\\)->([a-zA-Z]+)\\((.*?)\\)\\s*;?");
    private static final Pattern SCENE_CALL = Pattern.compile("scene\\(\\\"([^\\\"]+)\\\"\\)->play\\(\\)\\s*;?");
    private static final Pattern LAYER_CALL = Pattern.compile("layer\\(\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"([^\\\"]+)\\\"\\)->activate\\(\\)\\s*;?");
    private static final Pattern FLAG_CALL = Pattern.compile("setFlag\\(\\\"([^\\\"]+)\\\"\\s*,\\s*(true|false)\\)\\s*;?");

    private FifthScriptEngine() {}

    public static void tick(MinecraftServer server) {
        tick++;
        if (tick % 200 == 0) pruneNpcIndex(server);
        if (tick % 5 != 0) return;
        for (Map.Entry<String, String> entry : SCRIPTS.entrySet()) {
            runBlock(server, null, extractBlock(entry.getValue(), "onTick"), null);
        }
    }

    public static void reload(MinecraftServer server) {
        SCRIPTS.clear();
        Path dir = scriptsDir(server);
        try {
            Files.createDirectories(dir);
            try (var stream = Files.list(dir)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".fifth.php")).forEach(path -> {
                    try {
                        SCRIPTS.put(stripExt(path.getFileName().toString()), Files.readString(path));
                    } catch (IOException ignored) {}
                });
            }
        } catch (IOException ignored) {}
    }

    public static void saveScript(MinecraftServer server, String name, String script) {
        name = safe(name);
        SCRIPTS.put(name, script == null ? "" : script);
        try {
            Files.createDirectories(scriptsDir(server));
            Files.writeString(scriptsDir(server).resolve(name + ".fifth.php"), script == null ? "" : script);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void runNamed(MinecraftServer server, String name, ServerPlayerEntity executor) {
        String key = safe(name);
        String script = SCRIPTS.get(key);
        if (script == null) {
            reload(server);
            script = SCRIPTS.get(key);
        }
        if (script == null) {
            if (executor != null) executor.sendMessage(Text.literal("§cСценарий не найден: " + name), false);
            return;
        }
        String onRun = extractBlock(script, "onRun");
        if (onRun.isBlank() && !extractBlock(script, "onNpcTick").isBlank()) {
            if (executor != null) {
                executor.sendMessage(Text.literal("§eЭтот сценарий содержит onNpcTick(). Выбери NPC в библиотеке компьютера и нажми «Запустить»."), false);
            }
            return;
        }
        runBlock(server, executor, onRun.isBlank() ? script : onRun, null);
    }

    /** Runs the current computer script with the picked library NPC bound to the runtime alias self. */
    public static void runNamedForNpc(MinecraftServer server, String name, ServerPlayerEntity executor, DirectorNpcEntity npc) {
        indexNpc(npc);
        String key = safe(name);
        String script = SCRIPTS.get(key);
        if (script == null) {
            reload(server);
            script = SCRIPTS.get(key);
        }
        if (script == null) {
            if (executor != null) executor.sendMessage(Text.literal("§cСценарий не найден: " + name), false);
            return;
        }

        String onRun = extractBlock(script, "onRun");
        String onNpcTick = extractBlock(script, "onNpcTick");
        if (!onRun.isBlank()) runBlock(server, executor, onRun, npc);
        else if (onNpcTick.isBlank()) runBlock(server, executor, script, npc);

        if (!onNpcTick.isBlank()) {
            npc.setAiScript(key);
            npc.setAiEnabled(true);
            runBlock(server, executor, onNpcTick, npc);
            if (executor != null) {
                executor.sendMessage(Text.literal("§8[§cПятый§8] §7NPC §f" + npc.getNpcId() + " §7привязан к §f" + key + " §7и ИИ запущен."), false);
            }
        } else if (executor != null) {
            executor.sendMessage(Text.literal("§8[§cПятый§8] §7Код выполнен для NPC §f" + npc.getNpcId() + "§7."), false);
        }
    }

    public static void emitTrigger(MinecraftServer server, String trigger) {
        for (String script : SCRIPTS.values()) {
            runBlock(server, null, extractNamedBlock(script, "onTrigger", trigger), null);
        }
    }

    public static void runNpcTick(DirectorNpcEntity npc, String scriptNameOrInline) {
        MinecraftServer server = npc.getWorld().getServer();
        if (server == null) return;
        indexNpc(npc);
        String script = SCRIPTS.getOrDefault(safe(scriptNameOrInline), scriptNameOrInline);
        String block = extractBlock(script, "onNpcTick");
        if (block.isBlank()) block = extractBlock(script, "onTick");
        if (!block.isBlank()) runBlock(server, null, block, npc);
    }

    public static boolean flag(String name) {
        return FLAGS.getOrDefault(name, false);
    }

    private static void runBlock(MinecraftServer server, ServerPlayerEntity executor, String block, DirectorNpcEntity self) {
        if (block == null || block.isBlank()) return;
        for (String raw : block.split("[\\r\\n]+")) {
            String line = raw.trim();
            if (line.isBlank() || line.startsWith("//") || line.equals("{") || line.equals("}")) continue;
            if (line.startsWith("if (flag(")) {
                int q1 = line.indexOf('"'), q2 = line.indexOf('"', q1 + 1), close = line.indexOf(')', q2);
                if (q1 > 0 && q2 > q1 && close >= 0 && FLAGS.getOrDefault(line.substring(q1 + 1, q2), false)) {
                    runLine(server, executor, line.substring(close + 1).trim(), self);
                }
                continue;
            }
            runLine(server, executor, line, self);
        }
    }

    private static void runLine(MinecraftServer server, ServerPlayerEntity executor, String line, DirectorNpcEntity self) {
        Matcher matcher = NPC_CALL.matcher(line);
        if (matcher.find()) {
            DirectorNpcEntity npc = "self".equals(matcher.group(1)) ? self : findNpc(server, matcher.group(1));
            if (npc != null) applyNpcCall(npc, matcher.group(2), splitArgs(matcher.group(3)));
            return;
        }
        matcher = SCENE_CALL.matcher(line);
        if (matcher.find()) {
            CutsceneManager.play(server, matcher.group(1));
            return;
        }
        matcher = LAYER_CALL.matcher(line);
        if (matcher.find()) {
            ServerWorld world = executor != null ? executor.getServerWorld() : server.getOverworld();
            StructureLayerManager.activate(server, world, matcher.group(1), matcher.group(2));
            return;
        }
        matcher = FLAG_CALL.matcher(line);
        if (matcher.find()) {
            FLAGS.put(matcher.group(1), Boolean.parseBoolean(matcher.group(2)));
            return;
        }
        if (line.startsWith("trigger(")) {
            String trigger = firstString(line);
            if (trigger != null) emitTrigger(server, trigger);
        }
    }

    private static void applyNpcCall(DirectorNpcEntity npc, String method, List<String> args) {
        try {
            switch (method) {
                case "startAi" -> npc.setAiEnabled(true);
                case "stopAi" -> npc.setAiEnabled(false);
                case "animation" -> npc.setCurrentAnimation(unquote(args.get(0)));
                case "stopAnimation" -> npc.setCurrentAnimation("");
                case "followPath" -> npc.followPath(args.isEmpty() || Boolean.parseBoolean(args.get(0)), args.size() > 1 ? Double.parseDouble(args.get(1)) : 0.25);
                case "stopPath" -> npc.stopPath();
                case "moveTo" -> npc.moveToTarget(Double.parseDouble(args.get(0)), Double.parseDouble(args.get(1)), Double.parseDouble(args.get(2)), args.size() > 3 ? Double.parseDouble(args.get(3)) : 0.25);
                case "lookAtNearestPlayer" -> {
                    double radius = args.isEmpty() ? 10 : Double.parseDouble(args.get(0));
                    PlayerEntity player = npc.getWorld().getClosestPlayer(npc, radius);
                    if (player != null) npc.getLookControl().lookAt(player, 30, 30);
                }
                case "say" -> {
                    if (!args.isEmpty() && npc.getWorld() instanceof ServerWorld world) {
                        String speaker = npc.getCustomName() == null ? npc.getNpcId() : npc.getCustomName().getString();
                        for (ServerPlayerEntity player : world.getPlayers()) {
                            player.sendMessage(Text.literal("§8[§f" + speaker + "§8] §7" + unquote(args.get(0))), false);
                        }
                    }
                }
                case "script" -> {
                    if (!args.isEmpty()) npc.setAiScript(unquote(args.get(0)));
                }
            }
        } catch (Exception ignored) {}
    }

    public static void indexNpc(DirectorNpcEntity npc) {
        if (npc == null || npc.isRemoved() || npc.getWorld().isClient) return;
        NPC_BY_UUID.put(npc.getUuid(), npc);
        String id = npc.getNpcId();
        if (id != null && !id.isBlank()) NPC_BY_ID.put(id, npc.getUuid());
    }

    public static DirectorNpcEntity findNpc(MinecraftServer server, String id) {
        if (server == null || id == null || id.isBlank()) return null;
        UUID cachedUuid = NPC_BY_ID.get(id);
        if (cachedUuid != null) {
            DirectorNpcEntity cached = NPC_BY_UUID.get(cachedUuid);
            if (validCachedNpc(server, cached, id)) return cached;
            NPC_BY_ID.remove(id, cachedUuid);
            if (cached != null) NPC_BY_UUID.remove(cachedUuid, cached);
        }

        // Cold-cache fallback. Unlike the old implementation this runs once per uncached ID, not every script tick.
        Box all = new Box(-30_000_000, -2048, -30_000_000, 30_000_000, 4096, 30_000_000);
        for (ServerWorld world : server.getWorlds()) {
            List<DirectorNpcEntity> list = world.getEntitiesByClass(DirectorNpcEntity.class, all,
                    npc -> !npc.isRemoved() && id.equals(npc.getNpcId()));
            if (!list.isEmpty()) {
                DirectorNpcEntity npc = list.get(0);
                indexNpc(npc);
                return npc;
            }
        }
        return null;
    }

    /** Exact library lookup by UUID, with ID fallback for worlds reloaded from disk. */
    public static DirectorNpcEntity findNpc(MinecraftServer server, UUID uuid, String idFallback) {
        if (server == null) return null;
        if (uuid != null) {
            DirectorNpcEntity cached = NPC_BY_UUID.get(uuid);
            if (validCachedNpc(server, cached, null)) {
                indexNpc(cached);
                return cached;
            }
            for (ServerWorld world : server.getWorlds()) {
                if (world.getEntity(uuid) instanceof DirectorNpcEntity npc && !npc.isRemoved()) {
                    indexNpc(npc);
                    return npc;
                }
            }
        }
        return idFallback == null || idFallback.isBlank() ? null : findNpc(server, idFallback);
    }

    private static boolean validCachedNpc(MinecraftServer server, DirectorNpcEntity npc, String expectedId) {
        if (npc == null || npc.isRemoved() || npc.getWorld().isClient || npc.getWorld().getServer() != server) return false;
        return expectedId == null || expectedId.equals(npc.getNpcId());
    }

    private static void pruneNpcIndex(MinecraftServer server) {
        NPC_BY_UUID.entrySet().removeIf(entry -> !validCachedNpc(server, entry.getValue(), null));
        NPC_BY_ID.entrySet().removeIf(entry -> {
            DirectorNpcEntity npc = NPC_BY_UUID.get(entry.getValue());
            return !validCachedNpc(server, npc, entry.getKey());
        });
    }

    private static String extractBlock(String script, String name) {
        int start = script.indexOf(name + "(");
        if (start < 0) start = script.indexOf(name + " {");
        if (start < 0) return "";
        int brace = script.indexOf('{', start);
        return brace < 0 ? "" : bodyAt(script, brace);
    }

    private static String extractNamedBlock(String script, String name, String arg) {
        Pattern pattern = Pattern.compile(name + "\\(\\s*\\\"" + Pattern.quote(arg) + "\\\"\\s*\\)\\s*\\{");
        Matcher matcher = pattern.matcher(script);
        return matcher.find() ? bodyAt(script, script.indexOf('{', matcher.start())) : "";
    }

    private static String bodyAt(String value, int brace) {
        int depth = 0;
        for (int i = brace; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return value.substring(brace + 1, i);
            }
        }
        return "";
    }

    private static List<String> splitArgs(String args) {
        List<String> out = new ArrayList<>();
        if (args == null || args.isBlank()) return out;
        boolean quoted = false;
        StringBuilder builder = new StringBuilder();
        for (char c : args.toCharArray()) {
            if (c == '"') quoted = !quoted;
            if (c == ',' && !quoted) {
                out.add(builder.toString().trim());
                builder.setLength(0);
            } else builder.append(c);
        }
        out.add(builder.toString().trim());
        return out;
    }

    private static String unquote(String value) {
        value = value.trim();
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1) : value;
    }

    private static String firstString(String value) {
        int a = value.indexOf('"'), b = value.indexOf('"', a + 1);
        return a >= 0 && b > a ? value.substring(a + 1, b) : null;
    }

    private static String safe(String value) {
        return (value == null ? "main" : value.trim().toLowerCase(Locale.ROOT)).replaceAll("[^a-z0-9_\\-]", "_");
    }

    private static String stripExt(String value) {
        return value.endsWith(".fifth.php") ? value.substring(0, value.length() - 10) : value;
    }

    private static Path scriptsDir(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("fiven").resolve("scripts");
    }
}
