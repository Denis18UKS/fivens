package ru.fifth.horror.checkpoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import ru.fifth.horror.CheckpointFeature;
import ru.fifth.horror.block.LiftBlockEntity;
import ru.fifth.horror.entity.DirectorNpcEntity;
import ru.fifth.horror.entity.MflDeathSequenceManager;
import ru.fifth.horror.entity.MonsterForLiftEntity;
import ru.fifth.horror.lift.LiftManager;
import ru.fifth.horror.mixin.MonsterForLiftRuntimeAccess;
import ru.fifth.horror.script.FifthScriptEngine;
import ru.fifth.horror.structure.StructureLayerManager;
import ru.fifth.horror.trigger.TriggerZoneManager;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** One shared checkpoint and a bounded snapshot of Fiven-owned runtime state. */
public final class CheckpointManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STRING_MAP = new TypeToken<Map<String, String>>() {}.getType();
    private static final Box ALL = new Box(-30_000_000, -2048, -30_000_000, 30_000_000, 4096, 30_000_000);
    private static final CheckpointRestartPolicy RESTART = new CheckpointRestartPolicy();
    private static MinecraftServer loadedServer;
    private static Data data = new Data();

    private CheckpointManager() {}

    public static synchronized void load(MinecraftServer server) {
        if (loadedServer == server) return;
        loadedServer = server;
        data = new Data();
        try {
            Path file = file(server);
            if (Files.isRegularFile(file)) {
                Data read = GSON.fromJson(Files.readString(file), Data.class);
                if (read != null) data = read;
            }
        } catch (Exception error) {
            System.err.println("[Fiven/Checkpoint] load failed: " + error.getMessage());
        }
        sanitize();
    }

    public static synchronized Checkpoint set(MinecraftServer server, String id, ServerPlayerEntity player) {
        return set(server, id, player.getServerWorld(), player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
    }

    public static synchronized Checkpoint set(MinecraftServer server, String id, ServerWorld world,
                                               double x, double y, double z, float yaw, float pitch) {
        load(server);
        Checkpoint cp = new Checkpoint();
        cp.id = safe(id);
        cp.world = world.getRegistryKey().getValue().toString();
        cp.x = x; cp.y = y; cp.z = z; cp.yaw = yaw; cp.pitch = pitch;
        data.checkpoints.put(cp.id, cp);
        save(server);
        return cp;
    }

    public static synchronized boolean activate(MinecraftServer server, String id) {
        load(server);
        Checkpoint cp = data.checkpoints.get(safe(id));
        if (cp == null) return false;
        data.activeId = cp.id;
        data.snapshot = captureRuntime(server);
        save(server);
        return true;
    }

    public static synchronized boolean delete(MinecraftServer server, String id) {
        load(server);
        String key = safe(id);
        boolean removed = data.checkpoints.remove(key) != null;
        if (key.equals(data.activeId)) { data.activeId = ""; data.snapshot = null; }
        if (removed) save(server);
        return removed;
    }

    public static synchronized List<Checkpoint> list(MinecraftServer server) {
        load(server);
        return data.checkpoints.values().stream().sorted(Comparator.comparing(c -> c.id, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public static synchronized Checkpoint current(MinecraftServer server) {
        load(server);
        return data.activeId == null || data.activeId.isBlank() ? null : data.checkpoints.get(data.activeId);
    }

    public static synchronized Checkpoint nearest(MinecraftServer server, ServerPlayerEntity player, double radius) {
        load(server);
        String world = player.getServerWorld().getRegistryKey().getValue().toString();
        double best = radius * radius;
        Checkpoint found = null;
        for (Checkpoint cp : data.checkpoints.values()) {
            if (!world.equals(cp.world)) continue;
            double dx = cp.x - player.getX(), dy = cp.y - player.getY(), dz = cp.z - player.getZ();
            double d = dx * dx + dy * dy + dz * dz;
            if (d <= best) { best = d; found = cp; }
        }
        return found;
    }

    public static synchronized void markGameStarted(MinecraftServer server) {
        load(server);
        data.gameRunning = true;
        if (data.snapshot == null && current(server) != null) data.snapshot = captureRuntime(server);
        save(server);
    }

    public static synchronized void markGameStopped(MinecraftServer server) {
        load(server); data.gameRunning = false; save(server);
    }

    public static synchronized boolean isGameRunning(MinecraftServer server) { load(server); return data.gameRunning; }

    public static boolean restart(MinecraftServer server) {
        load(server);
        Checkpoint cp = current(server);
        if (cp == null || !RESTART.tryBegin()) return false;
        try {
            RuntimeSnapshot snapshot = data.snapshot;
            MflDeathSequenceManager.resetAll(server);
            resetClients(server);
            if (snapshot != null) {
                restoreLayers(server, snapshot.layers);
                restoreTriggers(server, snapshot.triggerEnabled);
                restoreFlags(snapshot.flags);
                restoreMfl(server, snapshot.mfl);
                restoreNpcs(server, snapshot.npcs);
                restoreLifts(server, snapshot.lifts);
            }
            ServerWorld world = resolveWorld(server, cp.world);
            if (world == null) world = server.getOverworld();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.isSpectator()) continue;
                player.teleport(world, cp.x, cp.y, cp.z, cp.yaw, cp.pitch);
                player.setVelocity(Vec3d.ZERO);
                player.fallDistance = 0;
                player.setFireTicks(0);
                player.setHealth(player.getMaxHealth());
                player.getHungerManager().setFoodLevel(20);
                player.getHungerManager().setSaturationLevel(5.0f);
            }
            FifthScriptEngine.emitTrigger(server, "checkpoint_restart");
            return true;
        } catch (Throwable error) {
            System.err.println("[Fiven/Checkpoint] restart failed: " + error.getMessage());
            error.printStackTrace();
            return false;
        } finally {
            RESTART.finish();
        }
    }

    private static RuntimeSnapshot captureRuntime(MinecraftServer server) {
        RuntimeSnapshot out = new RuntimeSnapshot();
        for (TriggerZoneManager.Zone zone : TriggerZoneManager.list(server)) out.triggerEnabled.put(zone.id, zone.enabled);
        out.flags.putAll(readFlags());
        out.layers.putAll(readLayerSelections(server));

        for (ServerWorld world : server.getWorlds()) {
            String worldId = world.getRegistryKey().getValue().toString();
            for (MonsterForLiftEntity mfl : world.getEntitiesByClass(MonsterForLiftEntity.class, ALL, Entity::isAlive)) {
                MflState s = new MflState();
                s.uuid = mfl.getUuidAsString(); s.world = worldId;
                s.x = mfl.getX(); s.y = mfl.getY(); s.z = mfl.getZ(); s.yaw = mfl.getYaw(); s.pitch = mfl.getPitch();
                s.mode = mfl.getAiMode().ordinal(); s.hunt = mfl.isHuntEnabled(); s.patrol = mfl.isPatrolEnabled();
                out.mfl.add(s);
            }
            for (DirectorNpcEntity npc : world.getEntitiesByClass(DirectorNpcEntity.class, ALL, e -> !e.isRemoved())) {
                NpcState s = new NpcState();
                s.uuid = npc.getUuidAsString(); s.world = worldId;
                s.x = npc.getX(); s.y = npc.getY(); s.z = npc.getZ(); s.yaw = npc.getYaw(); s.pitch = npc.getPitch();
                s.ai = npc.isAiEnabled(); s.route = npc.isRouteRunning(); s.script = npc.getAiScript(); s.animation = npc.getCurrentAnimation();
                out.npcs.add(s);
            }
        }

        for (LiftBlockEntity lift : registeredLifts()) {
            if (!(lift.getWorld() instanceof ServerWorld world) || lift.isRemoved()) continue;
            LiftState s = new LiftState();
            s.world = world.getRegistryKey().getValue().toString(); s.pos = lift.getPos().asLong();
            s.current = lift.getCurrentFloor(); s.target = lift.getTargetFloor(); s.openMask = lift.getOpenFloorMask();
            s.cursed = lift.isCursed(); s.doorOpen = lift.isDoorOpen();
            if (lift.getConfiguredStageOrigin() != null) s.stageOrigin = lift.getConfiguredStageOrigin().asLong();
            out.lifts.add(s);
        }
        return out;
    }

    private static void restoreTriggers(MinecraftServer server, Map<String, Boolean> values) {
        if (values == null) return;
        for (var entry : values.entrySet()) TriggerZoneManager.setEnabled(server, entry.getKey(), Boolean.TRUE.equals(entry.getValue()));
    }

    private static void restoreMfl(MinecraftServer server, List<MflState> values) {
        if (values == null) return;
        for (MflState s : values) {
            ServerWorld world = resolveWorld(server, s.world); if (world == null) continue;
            try {
                if (!(world.getEntity(UUID.fromString(s.uuid)) instanceof MonsterForLiftEntity mfl)) continue;
                mfl.getNavigation().stop(); mfl.setVelocity(Vec3d.ZERO);
                mfl.refreshPositionAndAngles(s.x, s.y, s.z, s.yaw, s.pitch);
                mfl.setAiMode(MonsterForLiftEntity.AiMode.values()[Math.max(0, Math.min(MonsterForLiftEntity.AiMode.values().length - 1, s.mode))]);
                mfl.setHuntEnabled(s.hunt); mfl.setPatrolEnabled(s.patrol);
                MonsterForLiftRuntimeAccess access = (MonsterForLiftRuntimeAccess) (Object) mfl;
                access.fiven$setManualAnimationTicks(0); access.fiven$setCurrentAnimation("idle");
            } catch (Exception ignored) {}
        }
    }

    private static void restoreNpcs(MinecraftServer server, List<NpcState> values) {
        if (values == null) return;
        for (NpcState s : values) {
            ServerWorld world = resolveWorld(server, s.world); if (world == null) continue;
            try {
                if (!(world.getEntity(UUID.fromString(s.uuid)) instanceof DirectorNpcEntity npc)) continue;
                npc.stopAllMovement(); npc.setVelocity(Vec3d.ZERO);
                npc.refreshPositionAndAngles(s.x, s.y, s.z, s.yaw, s.pitch);
                npc.setAiScript(s.script); npc.setCurrentAnimation(s.animation); npc.setAiEnabled(s.ai);
                if (s.ai && s.route) npc.followPath(true, .25);
            } catch (Exception ignored) {}
        }
    }

    private static void restoreLifts(MinecraftServer server, List<LiftState> values) {
        if (values == null) return;
        for (LiftState s : values) {
            LiftBlockEntity lift = LiftManager.findLift(server, s.world, BlockPos.fromLong(s.pos));
            if (lift == null) continue;
            lift.setOpenFloorMask(s.openMask); lift.setCursed(s.cursed);
            lift.setStageOrigin(s.stageOrigin == null ? null : BlockPos.fromLong(s.stageOrigin));
            lift.setCurrentFloor(s.current); lift.setTargetFloor(s.target);
            if (s.doorOpen) lift.openDoors(80); else lift.closeDoors();
        }
    }

    private static Map<String, Boolean> readFlags() {
        Map<String, Boolean> copy = new LinkedHashMap<>();
        try {
            Field field = FifthScriptEngine.class.getDeclaredField("FLAGS"); field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Map<?, ?> map) for (var e : map.entrySet()) if (e.getKey() instanceof String k && e.getValue() instanceof Boolean v) copy.put(k, v);
        } catch (Exception ignored) {}
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static void restoreFlags(Map<String, Boolean> values) {
        try {
            Field field = FifthScriptEngine.class.getDeclaredField("FLAGS"); field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Map<?, ?>) {
                Map<String, Boolean> map = (Map<String, Boolean>) value;
                map.clear(); if (values != null) map.putAll(values);
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static Collection<LiftBlockEntity> registeredLifts() {
        try {
            Field field = LiftManager.class.getDeclaredField("LIFTS"); field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Map<?, ?> map) return new ArrayList<>((Collection<LiftBlockEntity>) map.values());
        } catch (Exception ignored) {}
        return List.of();
    }

    private static Map<String, String> readLayerSelections(MinecraftServer server) {
        try {
            Path file = layerActiveFile(server);
            if (!Files.isRegularFile(file)) return new LinkedHashMap<>();
            Map<String, String> map = GSON.fromJson(Files.readString(file), STRING_MAP);
            return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
        } catch (Exception ignored) { return new LinkedHashMap<>(); }
    }

    private static void restoreLayers(MinecraftServer server, Map<String, String> values) {
        if (values == null) return;
        for (var entry : values.entrySet()) {
            try {
                String key = entry.getKey(), variant = entry.getValue();
                int colon = key.indexOf(':'); if (colon <= 0) continue;
                String build = key.substring(0, colon);
                int at = key.indexOf('@');
                if (at > colon) {
                    String loc = key.substring(at + 1);
                    int bar = loc.lastIndexOf('|'); if (bar <= 0) continue;
                    ServerWorld world = resolveWorld(server, loc.substring(0, bar)); if (world == null) continue;
                    BlockPos origin = BlockPos.fromLong(Long.parseLong(loc.substring(bar + 1)));
                    StructureLayerManager.activateAt(server, world, build, variant, origin);
                } else {
                    StructureLayerManager.activate(server, server.getOverworld(), build, variant);
                }
            } catch (Exception ignored) {}
        }
        try {
            Path file = layerActiveFile(server); Files.createDirectories(file.getParent()); Files.writeString(file, GSON.toJson(values, STRING_MAP));
        } catch (Exception ignored) {}
    }

    private static void resetClients(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            try { ServerPlayNetworking.send(player, CheckpointFeature.CLIENT_RESET, PacketByteBufs.empty()); } catch (Exception ignored) {}
        }
    }

    private static ServerWorld resolveWorld(MinecraftServer server, String value) {
        Identifier id = Identifier.tryParse(value); if (id == null) return null;
        return server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
    }

    private static void sanitize() {
        if (data.checkpoints == null) data.checkpoints = new LinkedHashMap<>();
        if (data.activeId == null) data.activeId = "";
        data.checkpoints.values().removeIf(Objects::isNull);
    }

    private static String safe(String value) {
        String s = value == null ? "checkpoint" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        return s.isBlank() ? "checkpoint" : s;
    }

    private static Path file(MinecraftServer server) { return server.getSavePath(WorldSavePath.ROOT).resolve("fiven").resolve("checkpoints.json"); }
    private static Path layerActiveFile(MinecraftServer server) { return server.getSavePath(WorldSavePath.ROOT).resolve("fiven").resolve("structures").resolve("active.json"); }
    private static synchronized void save(MinecraftServer server) {
        try { Path file = file(server); Files.createDirectories(file.getParent()); Files.writeString(file, GSON.toJson(data)); }
        catch (Exception error) { System.err.println("[Fiven/Checkpoint] save failed: " + error.getMessage()); }
    }

    public static final class Checkpoint {
        public String id = "checkpoint", world = "minecraft:overworld";
        public double x, y, z; public float yaw, pitch;
        public String positionText() { return String.format(Locale.ROOT, "%.1f %.1f %.1f", x, y, z); }
    }
    private static final class Data {
        public Map<String, Checkpoint> checkpoints = new LinkedHashMap<>(); public String activeId = "";
        public boolean gameRunning; public RuntimeSnapshot snapshot;
    }
    private static final class RuntimeSnapshot {
        public Map<String, Boolean> triggerEnabled = new LinkedHashMap<>();
        public Map<String, Boolean> flags = new LinkedHashMap<>();
        public Map<String, String> layers = new LinkedHashMap<>();
        public List<MflState> mfl = new ArrayList<>(); public List<NpcState> npcs = new ArrayList<>(); public List<LiftState> lifts = new ArrayList<>();
    }
    private static final class MflState { public String uuid, world; public double x,y,z; public float yaw,pitch; public int mode; public boolean hunt,patrol; }
    private static final class NpcState { public String uuid,world,script="",animation=""; public double x,y,z; public float yaw,pitch; public boolean ai,route; }
    private static final class LiftState { public String world; public long pos; public int current,target,openMask; public boolean cursed,doorOpen; public Long stageOrigin; }
}
