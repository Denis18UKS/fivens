package ru.fifth.horror.lift;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import ru.fifth.horror.block.LiftBlockEntity;
import ru.fifth.horror.cutscene.CutsceneManager;
import ru.fifth.horror.script.FifthScriptEngine;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persistent arrival events for cursed physical lifts.
 * Each lift/floor can launch a saved cutscene and/or emit a FifthScript trigger when the ride actually arrives.
 */
public final class CursedLiftEventManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, Map<Integer, EventConfig>>>() {}.getType();
    private static final Map<String, Map<Integer, EventConfig>> EVENTS = new LinkedHashMap<>();
    private static MinecraftServer loaded;

    private CursedLiftEventManager() {}

    public static void load(MinecraftServer server) {
        if (server == null || loaded == server) return;
        loaded = server;
        EVENTS.clear();
        try {
            Path path = file(server);
            if (Files.exists(path)) {
                Map<String, Map<Integer, EventConfig>> saved = GSON.fromJson(Files.readString(path), TYPE);
                if (saved != null) EVENTS.putAll(saved);
            }
        } catch (Exception ignored) {}
    }

    public static boolean bindCutscene(MinecraftServer server, LiftBlockEntity lift, int floor, String cutsceneId) {
        if (server == null || lift == null || cutsceneId == null || cutsceneId.isBlank()) return false;
        if (CutsceneManager.load(server, cutsceneId) == null) return false;
        EventConfig config = getOrCreate(server, lift, floor);
        config.cutsceneId = cutsceneId.trim();
        persist(server);
        return true;
    }

    public static boolean bindTrigger(MinecraftServer server, LiftBlockEntity lift, int floor, String trigger) {
        if (server == null || lift == null || trigger == null || trigger.isBlank()) return false;
        EventConfig config = getOrCreate(server, lift, floor);
        config.scriptTrigger = trigger.trim();
        persist(server);
        return true;
    }

    public static boolean bindScript(MinecraftServer server, LiftBlockEntity lift, int floor, String scriptName) {
        if (server == null || lift == null || scriptName == null || scriptName.isBlank()) return false;
        EventConfig config = getOrCreate(server, lift, floor);
        config.scriptName = scriptName.trim();
        persist(server);
        return true;
    }

    public static int clear(MinecraftServer server, LiftBlockEntity lift, int floor) {
        load(server);
        Map<Integer, EventConfig> floors = EVENTS.get(key(lift));
        if (floors == null) return 0;
        EventConfig removed = floors.remove(clampFloor(floor));
        if (floors.isEmpty()) EVENTS.remove(key(lift));
        if (removed != null) persist(server);
        return removed == null ? 0 : 1;
    }

    public static EventConfig get(MinecraftServer server, LiftBlockEntity lift, int floor) {
        load(server);
        Map<Integer, EventConfig> floors = EVENTS.get(key(lift));
        if (floors == null) return null;
        return floors.get(clampFloor(floor));
    }

    /** Called only when a real ride finishes and the destination layer has been switched. */
    public static void onArrival(MinecraftServer server, LiftBlockEntity lift, int floor, ServerPlayerEntity initiator) {
        if (server == null || lift == null || !lift.isCursed()) return;
        EventConfig config = get(server, lift, floor);
        if (config == null || !config.enabled) return;

        if (config.cutsceneId != null && !config.cutsceneId.isBlank()) {
            CutsceneManager.play(server, config.cutsceneId);
        }
        if (config.scriptName != null && !config.scriptName.isBlank()) {
            FifthScriptEngine.runNamed(server, config.scriptName, initiator);
        }
        if (config.scriptTrigger != null && !config.scriptTrigger.isBlank()) {
            FifthScriptEngine.emitTrigger(server, config.scriptTrigger);
        }
    }

    private static EventConfig getOrCreate(MinecraftServer server, LiftBlockEntity lift, int floor) {
        load(server);
        return EVENTS.computeIfAbsent(key(lift), ignored -> new LinkedHashMap<>())
                .computeIfAbsent(clampFloor(floor), ignored -> new EventConfig());
    }

    private static String key(LiftBlockEntity lift) {
        if (lift == null || !(lift.getWorld() instanceof ServerWorld world)) return "unknown|0";
        return world.getRegistryKey().getValue() + "|" + lift.getPos().asLong();
    }

    private static int clampFloor(int floor) { return Math.max(1, Math.min(9, floor)); }

    private static Path file(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("fiven").resolve("cursed_lift_events.json");
    }

    private static void persist(MinecraftServer server) {
        try {
            Path path = file(server);
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(EVENTS, TYPE));
        } catch (Exception ignored) {}
    }

    public static final class EventConfig {
        public String cutsceneId = "";
        public String scriptName = "";
        public String scriptTrigger = "";
        public boolean enabled = true;

        public EventConfig() {}

        public String describe() {
            return "cutscene=" + blank(cutsceneId) + ", script=" + blank(scriptName) + ", trigger=" + blank(scriptTrigger);
        }

        private static String blank(String value) { return value == null || value.isBlank() ? "-" : value; }
    }
}
