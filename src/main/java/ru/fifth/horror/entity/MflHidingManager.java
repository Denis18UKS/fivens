package ru.fifth.horror.entity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Persistent hiding volumes for MFL. Map makers mark cupboard/closet interiors in-game;
 * a player whose body is inside one of these volumes is not a valid LOGICAL-AI target.
 */
public final class MflHidingManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<List<Zone>>() {}.getType();
    private static final List<Zone> ZONES = new ArrayList<>();
    private static MinecraftServer loaded;

    private MflHidingManager() {}

    public static void load(MinecraftServer server) {
        if (server == null || loaded == server) return;
        loaded = server;
        ZONES.clear();
        try {
            Path path = file(server);
            if (Files.exists(path)) {
                List<Zone> saved = GSON.fromJson(Files.readString(path), TYPE);
                if (saved != null) ZONES.addAll(saved);
            }
        } catch (Exception ignored) {}
    }

    public static int add(ServerWorld world, BlockPos a, BlockPos b) {
        if (world == null || a == null || b == null) return 0;
        MinecraftServer server = world.getServer();
        load(server);
        String dimension = world.getRegistryKey().getValue().toString();
        Zone zone = new Zone(dimension,
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        ZONES.add(zone);
        persist(server);
        return ZONES.size();
    }

    public static int removeAt(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null) return 0;
        MinecraftServer server = world.getServer();
        load(server);
        String dimension = world.getRegistryKey().getValue().toString();
        int removed = 0;
        Iterator<Zone> it = ZONES.iterator();
        while (it.hasNext()) {
            Zone zone = it.next();
            if (dimension.equals(zone.dimension) && zone.contains(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) persist(server);
        return removed;
    }

    public static int clear(ServerWorld world) {
        if (world == null) return 0;
        MinecraftServer server = world.getServer();
        load(server);
        String dimension = world.getRegistryKey().getValue().toString();
        int before = ZONES.size();
        ZONES.removeIf(zone -> dimension.equals(zone.dimension));
        int removed = before - ZONES.size();
        if (removed > 0) persist(server);
        return removed;
    }

    public static boolean isHidden(ServerPlayerEntity player) {
        if (player == null || player.getServer() == null) return false;
        load(player.getServer());
        String dimension = player.getServerWorld().getRegistryKey().getValue().toString();
        Box body = player.getBoundingBox();
        double x = (body.minX + body.maxX) * .5;
        double y = (body.minY + body.maxY) * .5;
        double z = (body.minZ + body.maxZ) * .5;
        for (Zone zone : ZONES) {
            if (dimension.equals(zone.dimension) && zone.contains(x, y, z)) return true;
        }
        return false;
    }

    public static int count(ServerWorld world) {
        if (world == null) return 0;
        load(world.getServer());
        String dimension = world.getRegistryKey().getValue().toString();
        int count = 0;
        for (Zone zone : ZONES) if (dimension.equals(zone.dimension)) count++;
        return count;
    }

    private static Path file(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("fiven").resolve("mfl_hiding_zones.json");
    }

    private static void persist(MinecraftServer server) {
        try {
            Path path = file(server);
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(ZONES, TYPE));
        } catch (Exception ignored) {}
    }

    public static final class Zone {
        public String dimension = "minecraft:overworld";
        public int minX, minY, minZ, maxX, maxY, maxZ;

        public Zone() {}

        public Zone(String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.dimension = dimension;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public boolean contains(double x, double y, double z) {
            return x >= minX && x <= maxX + 1.0
                    && y >= minY && y <= maxY + 1.0
                    && z >= minZ && z <= maxZ + 1.0;
        }
    }
}
