package ru.fifth.horror.trigger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent command trigger zones used by the director tool and map scripts. */
public final class TriggerZoneManager {
    public enum Mode { ENTER, EXIT, STAY }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type ZONE_TYPE = new TypeToken<Map<String, Zone>>() {}.getType();
    private static final Map<String, Zone> ZONES = new ConcurrentHashMap<>();
    private static final Map<String, Set<UUID>> INSIDE = new ConcurrentHashMap<>();
    private static final Map<String, Map<UUID, Long>> LAST_FIRE = new ConcurrentHashMap<>();
    private static MinecraftServer loadedServer;
    private static long tick;

    private TriggerZoneManager() {}

    public static void load(MinecraftServer server) {
        if (loadedServer == server) return;
        loadedServer = server;
        ZONES.clear();
        INSIDE.clear();
        LAST_FIRE.clear();
        try {
            Path file = file(server);
            if (!Files.exists(file)) return;
            Map<String, Zone> data = GSON.fromJson(Files.readString(file), ZONE_TYPE);
            if (data != null) {
                for (Map.Entry<String, Zone> entry : data.entrySet()) {
                    Zone zone = sanitize(entry.getValue());
                    if (zone != null) ZONES.put(zone.id, zone);
                }
            }
        } catch (Exception ignored) {}
    }

    public static Collection<Zone> list(MinecraftServer server) {
        load(server);
        List<Zone> out = new ArrayList<>(ZONES.values());
        out.sort(Comparator.comparing(z -> z.id, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(out);
    }

    public static Zone get(MinecraftServer server, String id) {
        load(server);
        return ZONES.get(safeId(id));
    }

    public static Zone put(MinecraftServer server, ServerWorld world, String id, BlockPos a, BlockPos b,
                           String command, Mode mode, int cooldownTicks, boolean once) {
        load(server);
        String key = safeId(id);
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        Zone zone = new Zone();
        zone.id = key;
        zone.world = world.getRegistryKey().getValue().toString();
        zone.minX = min.getX(); zone.minY = min.getY(); zone.minZ = min.getZ();
        zone.maxX = max.getX(); zone.maxY = max.getY(); zone.maxZ = max.getZ();
        zone.command = normalizeCommand(command);
        zone.mode = mode == null ? Mode.ENTER : mode;
        zone.cooldownTicks = Math.max(0, Math.min(72_000, cooldownTicks));
        zone.once = once;
        zone.enabled = true;
        ZONES.put(key, zone);
        INSIDE.remove(key);
        LAST_FIRE.remove(key);
        save(server);
        return zone;
    }

    public static boolean delete(MinecraftServer server, String id) {
        load(server);
        String key = safeId(id);
        boolean removed = ZONES.remove(key) != null;
        INSIDE.remove(key);
        LAST_FIRE.remove(key);
        if (removed) save(server);
        return removed;
    }

    public static boolean setMode(MinecraftServer server, String id, Mode mode) {
        Zone zone = get(server, id);
        if (zone == null || mode == null) return false;
        zone.mode = mode;
        save(server);
        return true;
    }

    public static boolean setCooldown(MinecraftServer server, String id, int ticks) {
        Zone zone = get(server, id);
        if (zone == null) return false;
        zone.cooldownTicks = Math.max(0, Math.min(72_000, ticks));
        save(server);
        return true;
    }

    public static boolean setOnce(MinecraftServer server, String id, boolean once) {
        Zone zone = get(server, id);
        if (zone == null) return false;
        zone.once = once;
        save(server);
        return true;
    }

    public static boolean setEnabled(MinecraftServer server, String id, boolean enabled) {
        Zone zone = get(server, id);
        if (zone == null) return false;
        zone.enabled = enabled;
        INSIDE.remove(zone.id);
        LAST_FIRE.remove(zone.id);
        save(server);
        return true;
    }

    public static boolean fire(MinecraftServer server, String id, ServerPlayerEntity player) {
        Zone zone = get(server, id);
        if (zone == null || !zone.enabled) return false;
        execute(zone, server, player);
        return true;
    }

    public static void tick(MinecraftServer server) {
        load(server);
        if (++tick % 2 != 0) return;

        for (Zone zone : List.copyOf(ZONES.values())) {
            if (!zone.enabled) continue;
            ServerWorld world = resolveWorld(server, zone.world);
            if (world == null) continue;

            Box box = zone.box();
            Set<UUID> previous = INSIDE.computeIfAbsent(zone.id, ignored -> ConcurrentHashMap.newKeySet());
            Set<UUID> current = new HashSet<>();

            for (ServerPlayerEntity player : world.getPlayers()) {
                if (!player.isAlive() || player.isSpectator()) continue;
                boolean inside = box.intersects(player.getBoundingBox());
                if (inside) current.add(player.getUuid());

                boolean fire = switch (zone.mode) {
                    case ENTER -> inside && !previous.contains(player.getUuid());
                    case STAY -> inside;
                    case EXIT -> false;
                };
                if (fire) tryFire(zone, server, player);
            }

            if (zone.mode == Mode.EXIT) {
                for (UUID uuid : new HashSet<>(previous)) {
                    if (current.contains(uuid)) continue;
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                    if (player != null) tryFire(zone, server, player);
                }
            }

            previous.clear();
            previous.addAll(current);
        }
    }

    private static void tryFire(Zone zone, MinecraftServer server, ServerPlayerEntity player) {
        Map<UUID, Long> last = LAST_FIRE.computeIfAbsent(zone.id, ignored -> new ConcurrentHashMap<>());
        long now = server.getTicks();
        long previous = last.getOrDefault(player.getUuid(), Long.MIN_VALUE / 4);
        if (zone.cooldownTicks > 0 && now - previous < zone.cooldownTicks) return;
        last.put(player.getUuid(), now);
        execute(zone, server, player);
        if (zone.once) {
            zone.enabled = false;
            INSIDE.remove(zone.id);
            LAST_FIRE.remove(zone.id);
            save(server);
        }
    }

    private static void execute(Zone zone, MinecraftServer server, ServerPlayerEntity player) {
        String raw = zone.command == null ? "" : zone.command;
        for (String part : raw.split("(?:\\r?\\n|;;)+")) {
            String command = substitute(part.trim(), player);
            if (command.startsWith("/")) command = command.substring(1);
            if (command.isBlank()) continue;
            try {
                server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4), command);
            } catch (Throwable error) {
                System.err.println("[Fiven/Trigger] Zone '" + zone.id + "' failed command: " + command + " -> " + error.getMessage());
            }
        }
    }

    private static String substitute(String command, ServerPlayerEntity player) {
        if (player == null) return command;
        return command
                .replace("{player}", player.getGameProfile().getName())
                .replace("{uuid}", player.getUuidAsString())
                .replace("{x}", Integer.toString(player.getBlockX()))
                .replace("{y}", Integer.toString(player.getBlockY()))
                .replace("{z}", Integer.toString(player.getBlockZ()));
    }

    private static ServerWorld resolveWorld(MinecraftServer server, String value) {
        Identifier id = Identifier.tryParse(value);
        if (id == null) return null;
        for (ServerWorld world : server.getWorlds()) {
            if (world.getRegistryKey().getValue().equals(id)) return world;
        }
        return null;
    }

    private static Zone sanitize(Zone zone) {
        if (zone == null) return null;
        zone.id = safeId(zone.id);
        zone.world = zone.world == null || zone.world.isBlank() ? "minecraft:overworld" : zone.world;
        zone.command = normalizeCommand(zone.command);
        zone.mode = zone.mode == null ? Mode.ENTER : zone.mode;
        zone.cooldownTicks = Math.max(0, Math.min(72_000, zone.cooldownTicks));
        return zone;
    }

    private static String normalizeCommand(String command) {
        String value = command == null ? "" : command.trim();
        return value.startsWith("/") ? value.substring(1) : value;
    }

    private static String safeId(String value) {
        String out = value == null ? "trigger" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]", "_");
        return out.isBlank() ? "trigger" : out;
    }

    private static Path file(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("fiven").resolve("trigger_zones.json");
    }

    private static void save(MinecraftServer server) {
        try {
            Path file = file(server);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(new TreeMap<>(ZONES), ZONE_TYPE));
        } catch (Exception error) {
            System.err.println("[Fiven/Trigger] Failed to save trigger zones: " + error.getMessage());
        }
    }

    public static final class Zone {
        public String id = "trigger";
        public String world = "minecraft:overworld";
        public int minX, minY, minZ, maxX, maxY, maxZ;
        public String command = "";
        public Mode mode = Mode.ENTER;
        public int cooldownTicks = 10;
        public boolean once;
        public boolean enabled = true;

        public Box box() {
            return new Box(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
        }

        public String sizeText() {
            return (maxX - minX + 1) + "x" + (maxY - minY + 1) + "x" + (maxZ - minZ + 1);
        }
    }
}
