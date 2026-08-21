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
    private static final Map<String,String> SCRIPTS = new ConcurrentHashMap<>();
    private static final Map<String,Boolean> FLAGS = new ConcurrentHashMap<>();
    private static long tick;
    private static final Pattern NPC_CALL = Pattern.compile("npc\\(\\\"([^\\\"]+)\\\"\\)->([a-zA-Z]+)\\((.*?)\\)\\s*;?");
    private static final Pattern SCENE_CALL = Pattern.compile("scene\\(\\\"([^\\\"]+)\\\"\\)->play\\(\\)\\s*;?");
    private static final Pattern LAYER_CALL = Pattern.compile("layer\\(\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"([^\\\"]+)\\\"\\)->activate\\(\\)\\s*;?");
    private static final Pattern FLAG_CALL = Pattern.compile("setFlag\\(\\\"([^\\\"]+)\\\"\\s*,\\s*(true|false)\\)\\s*;?");

    private FifthScriptEngine() {}

    public static void tick(MinecraftServer server) {
        tick++;
        if (tick % 5 != 0) return;
        for (Map.Entry<String,String> e : SCRIPTS.entrySet()) runBlock(server, null, extractBlock(e.getValue(), "onTick"), null);
    }

    public static void reload(MinecraftServer server) {
        SCRIPTS.clear(); Path dir = scriptsDir(server);
        try {
            Files.createDirectories(dir);
            try (var stream = Files.list(dir)) { stream.filter(p -> p.getFileName().toString().endsWith(".fifth.php")).forEach(p -> {
                try { SCRIPTS.put(stripExt(p.getFileName().toString()), Files.readString(p)); } catch (IOException ignored) {}
            }); }
        } catch (IOException ignored) {}
    }

    public static void saveScript(MinecraftServer server, String name, String script) {
        name = safe(name); SCRIPTS.put(name, script == null ? "" : script);
        try { Files.createDirectories(scriptsDir(server)); Files.writeString(scriptsDir(server).resolve(name + ".fifth.php"), script == null ? "" : script); }
        catch (IOException e) { throw new RuntimeException(e); }
    }

    public static void runNamed(MinecraftServer server, String name, ServerPlayerEntity executor) {
        String key = safe(name);
        String script = SCRIPTS.get(key);
        if (script == null) { reload(server); script = SCRIPTS.get(key); }
        if (script == null) { if (executor != null) executor.sendMessage(Text.literal("§cСценарий не найден: " + name), false); return; }
        String onRun = extractBlock(script, "onRun");
        // A pure onNpcTick program needs an NPC context. Do not silently execute its body with self=null.
        if (onRun.isBlank() && !extractBlock(script, "onNpcTick").isBlank()) {
            if (executor != null) executor.sendMessage(Text.literal("§eЭтот сценарий содержит onNpcTick(). Выбери NPC в библиотеке компьютера и нажми «Запустить»."), false);
            return;
        }
        runBlock(server, executor, onRun.isBlank() ? script : onRun, null);
    }

    /** Runs the current computer script with the picked library NPC bound to the runtime alias self. */
    public static void runNamedForNpc(MinecraftServer server, String name, ServerPlayerEntity executor, DirectorNpcEntity npc) {
        String key = safe(name);
        String script = SCRIPTS.get(key);
        if (script == null) { reload(server); script = SCRIPTS.get(key); }
        if (script == null) { if (executor != null) executor.sendMessage(Text.literal("§cСценарий не найден: " + name), false); return; }

        String onRun = extractBlock(script, "onRun");
        String onNpcTick = extractBlock(script, "onNpcTick");

        if (!onRun.isBlank()) {
            runBlock(server, executor, onRun, npc);
        } else if (onNpcTick.isBlank()) {
            // Flat scripts such as npc("self")->moveTo(...) execute immediately for the selected NPC.
            runBlock(server, executor, script, npc);
        }

        if (!onNpcTick.isBlank()) {
            // Selecting an NPC + launching an onNpcTick script is the explicit act that turns the statue into a programmed NPC.
            npc.setAiScript(key);
            npc.setAiEnabled(true);
            runBlock(server, executor, onNpcTick, npc); // first tick immediately, then entity tick continues it
            if (executor != null) executor.sendMessage(Text.literal("§8[§cПятый§8] §7NPC §f" + npc.getNpcId() + " §7привязан к §f" + key + " §7и ИИ запущен."), false);
        } else if (executor != null) {
            executor.sendMessage(Text.literal("§8[§cПятый§8] §7Код выполнен для NPC §f" + npc.getNpcId() + "§7."), false);
        }
    }

    public static void emitTrigger(MinecraftServer server, String trigger) {
        for (String script : SCRIPTS.values()) runBlock(server, null, extractNamedBlock(script, "onTrigger", trigger), null);
    }

    public static void runNpcTick(DirectorNpcEntity npc, String scriptNameOrInline) {
        MinecraftServer server = npc.getWorld().getServer(); if (server == null) return;
        String script = SCRIPTS.getOrDefault(safe(scriptNameOrInline), scriptNameOrInline);
        String block = extractBlock(script, "onNpcTick"); if (block.isBlank()) block = extractBlock(script, "onTick");
        if (!block.isBlank()) runBlock(server, null, block, npc);
    }

    public static boolean flag(String name) { return FLAGS.getOrDefault(name, false); }

    private static void runBlock(MinecraftServer server, ServerPlayerEntity executor, String block, DirectorNpcEntity self) {
        if (block == null || block.isBlank()) return;
        for (String raw : block.split("[\\r\\n]+")) {
            String line = raw.trim(); if (line.isBlank() || line.startsWith("//") || line.equals("{") || line.equals("}")) continue;
            if (line.startsWith("if (flag(")) {
                // compact one-line condition: if (flag("x")) npc(...);
                int q1=line.indexOf('"'), q2=line.indexOf('"',q1+1), close=line.indexOf(')', q2);
                if(q1>0&&q2>q1&&FLAGS.getOrDefault(line.substring(q1+1,q2),false)) runLine(server, executor, line.substring(close+1).trim(), self);
                continue;
            }
            runLine(server, executor, line, self);
        }
    }

    private static void runLine(MinecraftServer server, ServerPlayerEntity executor, String line, DirectorNpcEntity self) {
        Matcher m = NPC_CALL.matcher(line);
        if (m.find()) { DirectorNpcEntity npc = "self".equals(m.group(1)) ? self : findNpc(server, m.group(1)); if (npc != null) applyNpcCall(server, npc, m.group(2), splitArgs(m.group(3))); return; }
        m = SCENE_CALL.matcher(line); if (m.find()) { CutsceneManager.play(server, m.group(1)); return; }
        m = LAYER_CALL.matcher(line); if (m.find()) { ServerWorld w = executor != null ? executor.getServerWorld() : server.getOverworld(); StructureLayerManager.activate(server, w, m.group(1), m.group(2)); return; }
        m = FLAG_CALL.matcher(line); if (m.find()) { FLAGS.put(m.group(1), Boolean.parseBoolean(m.group(2))); return; }
        if (line.startsWith("trigger(")) { String s = firstString(line); if (s != null) emitTrigger(server, s); }
    }

    private static void applyNpcCall(MinecraftServer server, DirectorNpcEntity npc, String method, List<String> args) {
        try {
            switch (method) {
                case "startAi" -> npc.setAiEnabled(true);
                case "stopAi" -> npc.setAiEnabled(false);
                case "animation" -> npc.setCurrentAnimation(unquote(args.get(0)));
                case "stopAnimation" -> npc.setCurrentAnimation("");
                case "followPath" -> npc.followPath(args.isEmpty() || Boolean.parseBoolean(args.get(0)), args.size() > 1 ? Double.parseDouble(args.get(1)) : 0.25);
                case "stopPath" -> npc.stopPath();
                case "moveTo" -> npc.moveToTarget(Double.parseDouble(args.get(0)), Double.parseDouble(args.get(1)), Double.parseDouble(args.get(2)), args.size()>3?Double.parseDouble(args.get(3)):0.25);
                case "lookAtNearestPlayer" -> { double r=args.isEmpty()?10:Double.parseDouble(args.get(0)); PlayerEntity p=npc.getWorld().getClosestPlayer(npc,r); if(p!=null) npc.getLookControl().lookAt(p,30,30); }
                case "say" -> { if(!args.isEmpty() && npc.getWorld() instanceof ServerWorld w) for(ServerPlayerEntity p:w.getPlayers()) p.sendMessage(Text.literal("§8[§f"+(npc.getCustomName()==null?npc.getNpcId():npc.getCustomName().getString())+"§8] §7"+unquote(args.get(0))),false); }
                case "script" -> { if(!args.isEmpty()) npc.setAiScript(unquote(args.get(0))); }
            }
        } catch (Exception ignored) {}
    }

    public static DirectorNpcEntity findNpc(MinecraftServer server, String id) {
        Box all = new Box(-30_000_000, -2048, -30_000_000, 30_000_000, 4096, 30_000_000);
        for (ServerWorld w : server.getWorlds()) {
            List<DirectorNpcEntity> list = w.getEntitiesByClass(DirectorNpcEntity.class, all, n -> id.equals(n.getNpcId()));
            if (!list.isEmpty()) return list.get(0);
        }
        return null;
    }

    /** Exact library lookup by UUID, with ID fallback for worlds reloaded from disk. */
    public static DirectorNpcEntity findNpc(MinecraftServer server, UUID uuid, String idFallback) {
        if (uuid != null) {
            for (ServerWorld w : server.getWorlds()) {
                if (w.getEntity(uuid) instanceof DirectorNpcEntity npc) return npc;
            }
        }
        return idFallback == null || idFallback.isBlank() ? null : findNpc(server, idFallback);
    }

    private static String extractBlock(String script, String name) {
        int start = script.indexOf(name + "("); if (start < 0) start = script.indexOf(name + " {"); if (start < 0) return "";
        int brace = script.indexOf('{', start); return brace < 0 ? "" : bodyAt(script, brace);
    }
    private static String extractNamedBlock(String script, String name, String arg) {
        Pattern p=Pattern.compile(name+"\\(\\s*\\\""+Pattern.quote(arg)+"\\\"\\s*\\)\\s*\\{"); Matcher m=p.matcher(script); return m.find()?bodyAt(script,script.indexOf('{',m.start())):"";
    }
    private static String bodyAt(String s,int brace){int depth=0;for(int i=brace;i<s.length();i++){char c=s.charAt(i);if(c=='{')depth++;else if(c=='}'){depth--;if(depth==0)return s.substring(brace+1,i);}}return "";}
    private static List<String> splitArgs(String args){List<String> out=new ArrayList<>();if(args==null||args.isBlank())return out;boolean q=false;StringBuilder b=new StringBuilder();for(char c:args.toCharArray()){if(c=='"')q=!q;if(c==','&&!q){out.add(b.toString().trim());b.setLength(0);}else b.append(c);}out.add(b.toString().trim());return out;}
    private static String unquote(String s){s=s.trim();return s.length()>=2&&s.startsWith("\"")&&s.endsWith("\"")?s.substring(1,s.length()-1):s;}
    private static String firstString(String s){int a=s.indexOf('"'),b=s.indexOf('"',a+1);return a>=0&&b>a?s.substring(a+1,b):null;}
    private static String safe(String s){return (s==null?"main":s.trim().toLowerCase(Locale.ROOT)).replaceAll("[^a-z0-9_\\-]","_");}
    private static String stripExt(String s){return s.endsWith(".fifth.php")?s.substring(0,s.length()-10):s;}
    private static Path scriptsDir(MinecraftServer server){return server.getSavePath(WorldSavePath.ROOT).resolve("fiven").resolve("scripts");}
}
