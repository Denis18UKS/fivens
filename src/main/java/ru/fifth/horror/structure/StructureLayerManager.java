package ru.fifth.horror.structure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Stores replaceable building layers. A layer may optionally be registered as a lift floor 1..9. */
public final class StructureLayerManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type META_TYPE = new TypeToken<List<Meta>>(){}.getType();
    private StructureLayerManager() {}

    public static void capture(MinecraftServer server, ServerWorld world, String build, String variant, String group,
                               boolean defaultActive, boolean restoreOnLoad, BlockPos a, BlockPos b) {
        capture(server, world, build, variant, group, defaultActive, restoreOnLoad, 0, a, b);
    }

    public static void capture(MinecraftServer server, ServerWorld world, String build, String variant, String group,
                               boolean defaultActive, boolean restoreOnLoad, int floor, BlockPos a, BlockPos b) {
        build = safe(build); variant = safe(variant); group = safe(group); floor = Math.max(0, Math.min(9, floor));
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        Snapshot snap = new Snapshot();
        snap.build = build; snap.variant = variant; snap.group = group; snap.defaultActive = defaultActive;
        snap.restoreOnLoad = restoreOnLoad; snap.floor = floor;
        snap.world = world.getRegistryKey().getValue().toString();
        snap.minX = min.getX(); snap.minY = min.getY(); snap.minZ = min.getZ(); snap.maxX = max.getX(); snap.maxY = max.getY(); snap.maxZ = max.getZ();
        for (BlockPos p : BlockPos.iterate(min, max)) {
            BlockState state = world.getBlockState(p); Cell c = new Cell();
            c.x = p.getX() - min.getX(); c.y = p.getY() - min.getY(); c.z = p.getZ() - min.getZ();
            c.block = Registries.BLOCK.getId(state.getBlock()).toString(); c.properties = new LinkedHashMap<>();
            for (Property<?> prop : state.getProperties()) c.properties.put(prop.getName(), valueName(state, prop));
            BlockEntity be = world.getBlockEntity(p);
            if (be != null) c.blockEntityNbt = be.createNbtWithId().asString();
            snap.cells.add(c);
        }
        try {
            Path file = file(server, build, variant); Files.createDirectories(file.getParent()); Files.writeString(file, GSON.toJson(snap));
            upsertMeta(server, new Meta(build, variant, group, defaultActive, restoreOnLoad, snap.world, floor));
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public static boolean activate(MinecraftServer server, ServerWorld fallback, String build, String variant) {
        Snapshot s = load(server, safe(build), safe(variant)); if (s == null) return false;
        return activateSnapshot(server, fallback, s, null);
    }

    /** Restore a snapshot at another origin; useful when every floor uses one common lift-stage area. */
    public static boolean activateAt(MinecraftServer server, ServerWorld fallback, String build, String variant, BlockPos targetOrigin) {
        Snapshot s = load(server, safe(build), safe(variant)); if (s == null) return false;
        return activateSnapshot(server, fallback, s, targetOrigin);
    }

    private static boolean activateSnapshot(MinecraftServer server, ServerWorld fallback, Snapshot s, BlockPos overrideOrigin) {
        ServerWorld world = server.getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, Identifier.tryParse(s.world)));
        if (world == null) world = fallback; if (world == null) return false;
        String activeOld = getActiveVariant(server, s.build, s.group);
        if (activeOld != null && !activeOld.equals(s.variant)) {
            Snapshot old = load(server, s.build, activeOld);
            if (old != null) clearSnapshot(world, old, overrideOrigin);
        }
        BlockPos origin = overrideOrigin == null ? new BlockPos(s.minX, s.minY, s.minZ) : overrideOrigin;
        for (Cell c : s.cells) {
            BlockPos p = origin.add(c.x, c.y, c.z); Identifier id = Identifier.tryParse(c.block); if (id == null) continue;
            Block block = Registries.BLOCK.get(id); BlockState state = applyProperties(block.getDefaultState(), c.properties);
            world.setBlockState(p, state, Block.NOTIFY_ALL | Block.FORCE_STATE);
            if (c.blockEntityNbt != null && !c.blockEntityNbt.isBlank()) {
                try {
                    NbtCompound nbt = StringNbtReader.parse(c.blockEntityNbt);
                    BlockEntity be = BlockEntity.createFromNbt(p, state, nbt);
                    if (be != null) { world.removeBlockEntity(p); world.addBlockEntity(be); be.markDirty(); world.getChunkManager().markForUpdate(p); }
                } catch (Exception ignored) {}
            }
        }
        setActiveVariant(server, s.build, s.group, s.variant);
        return true;
    }

    public static Optional<Meta> findFloor(MinecraftServer server, int floor) {
        return readMeta(server).stream().filter(m -> m.floor == floor).findFirst();
    }

    public static boolean activateFloor(MinecraftServer server, ServerWorld fallback, int floor, BlockPos targetOrigin) {
        Optional<Meta> m = findFloor(server, floor);
        return m.isPresent() && activateAt(server, fallback, m.get().build, m.get().variant, targetOrigin);
    }

    public static void restoreDefaults(MinecraftServer server) {
        for (Meta m : readMeta(server)) if (m.defaultActive && m.restoreOnLoad) {
            ServerWorld w = server.getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, Identifier.tryParse(m.world)));
            if (w != null) activate(server, w, m.build, m.variant);
        }
    }

    public static List<Meta> list(MinecraftServer server) { return List.copyOf(readMeta(server)); }

    private static Snapshot load(MinecraftServer server, String build, String variant) {
        try { Path p = file(server, build, variant); return Files.exists(p) ? GSON.fromJson(Files.readString(p), Snapshot.class) : null; }
        catch (Exception e) { return null; }
    }
    private static Path root(MinecraftServer server) { return server.getSavePath(WorldSavePath.ROOT).resolve("fiven").resolve("structures"); }
    private static Path file(MinecraftServer server, String build, String variant) { return root(server).resolve(build).resolve(variant + ".json"); }
    private static Path metaFile(MinecraftServer server) { return root(server).resolve("layers.json"); }
    private static Path activeFile(MinecraftServer server) { return root(server).resolve("active.json"); }
    private static String safe(String s) { s = s == null ? "layer" : s.trim().toLowerCase(Locale.ROOT); s = s.replaceAll("[^a-z0-9_\\-]", "_"); return s.isBlank() ? "layer" : s; }

    private static List<Meta> readMeta(MinecraftServer server) {
        try { Path p = metaFile(server); if (!Files.exists(p)) return new ArrayList<>(); List<Meta> v = GSON.fromJson(Files.readString(p), META_TYPE); return v == null ? new ArrayList<>() : new ArrayList<>(v); }
        catch (Exception e) { return new ArrayList<>(); }
    }
    private static void upsertMeta(MinecraftServer server, Meta meta) throws IOException {
        List<Meta> list = readMeta(server); list.removeIf(m -> m.build.equals(meta.build) && m.variant.equals(meta.variant));
        if (meta.floor > 0) list.removeIf(m -> m.floor == meta.floor);
        list.add(meta); Files.createDirectories(metaFile(server).getParent()); Files.writeString(metaFile(server), GSON.toJson(list, META_TYPE));
    }

    private static void clearSnapshot(ServerWorld world, Snapshot s, BlockPos overrideOrigin) {
        BlockPos origin = overrideOrigin == null ? new BlockPos(s.minX, s.minY, s.minZ) : overrideOrigin;
        int dx = s.maxX - s.minX, dy = s.maxY - s.minY, dz = s.maxZ - s.minZ;
        for (BlockPos p : BlockPos.iterate(origin, origin.add(dx, dy, dz))) world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.FORCE_STATE);
    }
    private static String getActiveVariant(MinecraftServer server, String build, String group) {
        try { Path p=activeFile(server); if(!Files.exists(p))return null; Type t=new TypeToken<Map<String,String>>(){}.getType(); Map<String,String> m=GSON.fromJson(Files.readString(p),t); return m==null?null:m.get(build+":"+group); } catch(Exception e){ return null; }
    }
    private static void setActiveVariant(MinecraftServer server, String build, String group, String variant) {
        try {
            Map<String,String> active = new LinkedHashMap<>(); Path p = activeFile(server);
            if (Files.exists(p)) { Type t = new TypeToken<Map<String,String>>(){}.getType(); Map<String,String> old = GSON.fromJson(Files.readString(p), t); if (old != null) active.putAll(old); }
            active.put(build + ":" + group, variant); Files.createDirectories(p.getParent()); Files.writeString(p, GSON.toJson(active));
        } catch (Exception ignored) {}
    }
    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) { return property.name(state.get(property)); }
    private static BlockState applyProperties(BlockState state, Map<String,String> values) {
        if (values == null) return state;
        for (Property<?> p : state.getProperties()) if (values.containsKey(p.getName())) state = applyProperty(state, p, values.get(p.getName()));
        return state;
    }
    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> prop, String value) {
        Optional<T> parsed = prop.parse(value); return parsed.map(v -> state.with(prop, v)).orElse(state);
    }

    public static final class Snapshot {
        public String build, variant, group, world; public boolean defaultActive, restoreOnLoad; public int floor;
        public int minX,minY,minZ,maxX,maxY,maxZ; public List<Cell> cells = new ArrayList<>();
    }
    public static final class Cell { public int x,y,z; public String block; public Map<String,String> properties; public String blockEntityNbt; }
    public static final class Meta {
        public String build, variant, group, world; public boolean defaultActive, restoreOnLoad; public int floor;
        public Meta() {}
        public Meta(String build,String variant,String group,boolean defaultActive,boolean restoreOnLoad,String world,int floor) {
            this.build=build;this.variant=variant;this.group=group;this.defaultActive=defaultActive;this.restoreOnLoad=restoreOnLoad;this.world=world;this.floor=floor;
        }
    }
}
