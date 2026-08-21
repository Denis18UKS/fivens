package ru.fifth.horror.lift;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import ru.fifth.horror.block.LiftBlockEntity;
import ru.fifth.horror.network.FifthNetworking;
import ru.fifth.horror.structure.StructureLayerManager;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime/persistent floor, vanilla call-button and ride controller for physical lift blocks. */
public final class LiftManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type BIND_TYPE = new TypeToken<Map<String, ButtonBinding>>() {}.getType();
    private static final Map<String, ButtonBinding> BUTTONS = new ConcurrentHashMap<>();
    private static final Map<String, LiftBlockEntity> LIFTS = new ConcurrentHashMap<>();
    private static final Map<String, Ride> RIDES = new ConcurrentHashMap<>();
    private static MinecraftServer loadedServer;

    private LiftManager() {}

    public static void load(MinecraftServer server) {
        if (loadedServer == server) return;
        loadedServer = server;
        BUTTONS.clear();
        LIFTS.clear();
        RIDES.clear();
        try {
            Path p = bindingsFile(server);
            if (Files.exists(p)) {
                Map<String, ButtonBinding> m = GSON.fromJson(Files.readString(p), BIND_TYPE);
                if (m != null) BUTTONS.putAll(m);
            }
        } catch (Exception ignored) {}
        CursedLiftEventManager.load(server);
    }

    public static void register(LiftBlockEntity lift) {
        if (!(lift.getWorld() instanceof ServerWorld world) || lift.isRemoved()) return;
        LIFTS.put(liftKey(world, lift.getPos()), lift);
    }

    public static void unregister(LiftBlockEntity lift) {
        if (!(lift.getWorld() instanceof ServerWorld world)) return;
        String key = liftKey(world, lift.getPos());
        LIFTS.remove(key, lift);
        RIDES.remove(key);
    }

    public static LiftBlockEntity findLift(MinecraftServer server, String worldId, BlockPos pos) {
        if (server == null || pos == null || worldId == null || worldId.isBlank()) return null;
        String key = worldId + "|" + pos.asLong();
        LiftBlockEntity cached = LIFTS.get(key);
        if (cached != null && !cached.isRemoved() && cached.getWorld() != null) return cached;
        for (ServerWorld world : server.getWorlds()) {
            if (!world.getRegistryKey().getValue().toString().equals(worldId)) continue;
            if (world.getBlockEntity(pos) instanceof LiftBlockEntity lift) {
                register(lift);
                return lift;
            }
        }
        LIFTS.remove(key);
        return null;
    }

    public static LiftBlockEntity nearestLift(ServerPlayerEntity player, double radius) {
        load(player.getServer());
        ServerWorld world = player.getServerWorld();
        String prefix = world.getRegistryKey().getValue().toString() + "|";
        double maxSq = radius * radius;
        LiftBlockEntity best = null;
        double bestSq = maxSq;
        for (var entry : LIFTS.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) continue;
            LiftBlockEntity lift = entry.getValue();
            if (lift == null || lift.isRemoved() || lift.getWorld() != world) continue;
            double d = Vec3d.ofCenter(lift.getPos()).squaredDistanceTo(player.getPos());
            if (d <= bestSq) { best = lift; bestSq = d; }
        }
        return best;
    }

    public static void bindButton(MinecraftServer server, ServerWorld buttonWorld, BlockPos buttonPos,
                                  String liftWorld, BlockPos liftPos, int floor) {
        load(server);
        BUTTONS.put(buttonKey(buttonWorld, buttonPos), new ButtonBinding(liftWorld, liftPos.asLong(), clampFloor(floor)));
        save(server);
    }

    public static ButtonBinding getBinding(MinecraftServer server, ServerWorld world, BlockPos pos) {
        load(server);
        return BUTTONS.get(buttonKey(world, pos));
    }

    /** @return true when the clicked vanilla stone button is owned by Fiven and vanilla use should be cancelled. */
    public static boolean callBoundButton(ServerPlayerEntity player, BlockPos pos) {
        ButtonBinding binding = getBinding(player.getServer(), player.getServerWorld(), pos);
        if (binding == null) return false;
        if (binding.liftWorld == null || binding.liftWorld.isBlank() || binding.liftPos == 0L) {
            player.sendMessage(Text.literal("§8[§cFiven§8] §7Эта привязка создана старой версией. Перепривяжи кнопку к блоку лифта."), true);
            return true;
        }
        LiftBlockEntity lift = findLift(player.getServer(), binding.liftWorld, BlockPos.fromLong(binding.liftPos));
        if (lift == null) {
            player.sendMessage(Text.literal("§8[§cFiven§8] §7Привязанный лифт не найден."), true);
            return true;
        }
        player.getServerWorld().playSound(null, pos, SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON, net.minecraft.sound.SoundCategory.BLOCKS, 0.35f, 0.65f);
        travel(player, lift, binding.floor);
        return true;
    }

    public static boolean travel(ServerPlayerEntity initiator, LiftBlockEntity lift, int targetFloor) {
        targetFloor = clampFloor(targetFloor);
        if (lift == null || !(lift.getWorld() instanceof ServerWorld world)) return false;
        register(lift);
        String key = liftKey(world, lift.getPos());
        if (RIDES.containsKey(key)) {
            initiator.sendMessage(Text.literal("§8[§cFiven§8] §7Лифт уже движется."), true);
            return false;
        }

        int from = lift.getCurrentFloor();
        if (from == targetFloor) {
            if (lift.canOpenOnFloor(targetFloor)) lift.openDoors(80);
            else {
                world.playSound(null, lift.getPos(), SoundEvents.BLOCK_IRON_DOOR_CLOSE, net.minecraft.sound.SoundCategory.BLOCKS, 0.8f, 0.55f);
                initiator.sendMessage(Text.literal("§8[§cFiven§8] §7Двери на этаже §c" + targetFloor + " §7заблокированы."), true);
            }
            return true;
        }

        lift.closeDoors();
        int ticks = Math.max(40, Math.abs(targetFloor - from) * 32);
        lift.setTargetFloor(targetFloor);
        RIDES.put(key, new Ride(key, initiator.getUuid(), from, targetFloor, ticks, ticks));
        sendRideStart(lift, from, targetFloor, ticks);
        world.playSound(null, lift.getPos(), SoundEvents.BLOCK_PISTON_EXTEND, net.minecraft.sound.SoundCategory.BLOCKS, 0.55f, 0.45f);
        return true;
    }

    public static void tick(MinecraftServer server) {
        load(server);
        Iterator<Map.Entry<String, Ride>> it = RIDES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Ride> en = it.next();
            Ride ride = en.getValue();
            LiftBlockEntity lift = LIFTS.get(ride.liftKey);
            if (lift == null || lift.isRemoved() || !(lift.getWorld() instanceof ServerWorld world)) {
                it.remove();
                continue;
            }

            int left = ride.remaining - 1;
            if (left > 0) {
                en.setValue(ride.withRemaining(left));
                continue;
            }

            int floor = ride.toFloor;
            // null is meaningful: restore at the floor snapshot's original captured origin.
            // A relocated common stage is used only when the author explicitly enabled/configured it.
            BlockPos origin = lift.getConfiguredStageOrigin();
            boolean restored = StructureLayerManager.activateFloor(server, world, lift.getLiftId(), floor, origin);
            lift.setCurrentFloor(floor);
            lift.setTargetFloor(floor);

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(ride.playerUuid);
            if (restored) {
                world.playSound(null, lift.getPos(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), net.minecraft.sound.SoundCategory.BLOCKS, 0.8f, 0.9f);
            } else {
                world.playSound(null, lift.getPos(), SoundEvents.BLOCK_IRON_DOOR_CLOSE, net.minecraft.sound.SoundCategory.BLOCKS, 0.8f, 0.55f);
                if (player != null) player.sendMessage(Text.literal("§8[§cFiven§8] §7Для этажа §f" + lift.getLiftId() + ":§c" + floor + " §7не найден сохранённый слой."), true);
            }

            if (lift.canOpenOnFloor(floor) && restored) {
                lift.openDoors(100);
            } else if (player != null && restored) {
                player.sendMessage(Text.literal("§8[§cFiven§8] §7Лифт приехал на §c" + floor + "§7, но двери заблокированы."), true);
            }

            if (restored && lift.isCursed()) {
                CursedLiftEventManager.onArrival(server, lift, floor, player);
            }

            sendRideEnd(lift, floor);
            it.remove();
        }
    }

    private static Collection<ServerPlayerEntity> recipients(LiftBlockEntity lift) {
        if (!(lift.getWorld() instanceof ServerWorld world)) return List.of();
        Vec3d center = Vec3d.ofCenter(lift.getPos());
        return world.getPlayers(p -> p.squaredDistanceTo(center) <= 64.0 * 64.0);
    }

    private static void sendRideStart(LiftBlockEntity lift, int from, int to, int ticks) {
        for (ServerPlayerEntity player : recipients(lift)) {
            PacketByteBuf out = PacketByteBufs.create();
            out.writeVarInt(from); out.writeVarInt(to); out.writeVarInt(ticks);
            ServerPlayNetworking.send(player, FifthNetworking.LIFT_TRAVEL_START, out);
        }
    }

    private static void sendRideEnd(LiftBlockEntity lift, int floor) {
        for (ServerPlayerEntity player : recipients(lift)) {
            PacketByteBuf out = PacketByteBufs.create(); out.writeVarInt(floor);
            ServerPlayNetworking.send(player, FifthNetworking.LIFT_TRAVEL_END, out);
        }
    }

    private static String buttonKey(ServerWorld world, BlockPos pos) { return world.getRegistryKey().getValue() + "|" + pos.asLong(); }
    private static String liftKey(ServerWorld world, BlockPos pos) { return world.getRegistryKey().getValue() + "|" + pos.asLong(); }
    private static Path bindingsFile(MinecraftServer server) { return server.getSavePath(WorldSavePath.ROOT).resolve("fiven").resolve("lift_buttons.json"); }
    private static void save(MinecraftServer server) {
        try { Path p = bindingsFile(server); Files.createDirectories(p.getParent()); Files.writeString(p, GSON.toJson(BUTTONS, BIND_TYPE)); }
        catch (Exception ignored) {}
    }
    private static int clampFloor(int floor) { return Math.max(1, Math.min(9, floor)); }

    public static final class ButtonBinding {
        public String liftWorld = "";
        public long liftPos;
        public int floor = 1;
        public ButtonBinding() {}
        public ButtonBinding(String liftWorld, long liftPos, int floor) { this.liftWorld = liftWorld; this.liftPos = liftPos; this.floor = floor; }
    }

    private record Ride(String liftKey, UUID playerUuid, int fromFloor, int toFloor, int total, int remaining) {
        Ride withRemaining(int n) { return new Ride(liftKey, playerUuid, fromFloor, toFloor, total, n); }
    }
}
