package ru.fifth.horror.lift;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import ru.fifth.horror.entity.LiftEntity;
import ru.fifth.horror.network.FifthNetworking;
import ru.fifth.horror.structure.StructureLayerManager;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime/persistent floor, call-button and ride controller. */
public final class LiftManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type BIND_TYPE = new TypeToken<Map<String, ButtonBinding>>() {}.getType();
    private static final Map<String, ButtonBinding> BUTTONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Ride> RIDES = new ConcurrentHashMap<>();
    private static MinecraftServer loadedServer;

    private LiftManager() {}

    public static void load(MinecraftServer server) {
        if (loadedServer == server) return;
        loadedServer = server;
        BUTTONS.clear();
        try {
            Path p = bindingsFile(server);
            if (Files.exists(p)) {
                Map<String, ButtonBinding> m = GSON.fromJson(Files.readString(p), BIND_TYPE);
                if (m != null) BUTTONS.putAll(m);
            }
        } catch (Exception ignored) {}
    }

    public static void bindButton(MinecraftServer server, ServerWorld world, BlockPos pos, UUID liftUuid, int floor) {
        load(server);
        floor = clampFloor(floor);
        BUTTONS.put(key(world, pos), new ButtonBinding(liftUuid.toString(), floor));
        save(server);
    }

    public static ButtonBinding getBinding(MinecraftServer server, ServerWorld world, BlockPos pos) {
        load(server);
        return BUTTONS.get(key(world, pos));
    }

    public static boolean callBoundButton(ServerPlayerEntity player, BlockPos pos) {
        ButtonBinding binding = getBinding(player.getServer(), player.getServerWorld(), pos);
        if (binding == null) return false;
        LiftEntity lift = findLift(player.getServer(), binding.liftUuid());
        if (lift == null) {
            player.sendMessage(Text.literal("§8[§cFiven§8] §7Привязанный лифт не найден."), true);
            return true;
        }
        travel(player, lift, binding.floor());
        return true;
    }

    public static LiftEntity nearestLift(ServerPlayerEntity player, double radius) {
        Box box = player.getBoundingBox().expand(radius);
        return player.getServerWorld().getEntitiesByClass(LiftEntity.class, box, e -> !e.isRemoved())
                .stream().min(Comparator.comparingDouble(player::squaredDistanceTo)).orElse(null);
    }

    public static LiftEntity findLift(MinecraftServer server, String uuid) {
        try {
            return findLift(server, UUID.fromString(uuid));
        } catch (Exception e) {
            return null;
        }
    }

    public static LiftEntity findLift(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) return null;
        for (ServerWorld world : server.getWorlds()) {
            var entity = world.getEntity(uuid);
            if (entity instanceof LiftEntity lift && !lift.isRemoved()) return lift;
        }
        return null;
    }

    public static boolean travel(ServerPlayerEntity initiator, LiftEntity lift, int targetFloor) {
        targetFloor = clampFloor(targetFloor);
        if (lift == null || lift.getWorld().isClient) return false;
        int from = lift.getCurrentFloor();
        if (from == targetFloor) {
            if (lift.canOpenOnFloor(targetFloor)) lift.playDoors();
            else {
                initiator.playSound(SoundEvents.BLOCK_IRON_DOOR_CLOSE, 0.8f, 0.55f);
                initiator.sendMessage(Text.literal("§8[§cFiven§8] §7Двери на этаже §c" + targetFloor + " §7заблокированы."), true);
            }
            return true;
        }

        int ticks = Math.max(40, Math.abs(targetFloor - from) * 32);
        lift.setTargetFloor(targetFloor);
        RIDES.put(lift.getUuid(), new Ride(lift.getUuid(), initiator.getUuid(), from, targetFloor, ticks, ticks));
        sendRideStart(lift, from, targetFloor, ticks);
        initiator.playSound(SoundEvents.BLOCK_PISTON_EXTEND, 0.55f, 0.45f);
        return true;
    }

    public static void tick(MinecraftServer server) {
        load(server);
        Iterator<Map.Entry<UUID, Ride>> it = RIDES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Ride> en = it.next();
            Ride ride = en.getValue();
            LiftEntity lift = findLift(server, ride.liftUuid);
            if (lift == null) {
                it.remove();
                continue;
            }

            int left = ride.remaining - 1;
            if (left > 0) {
                en.setValue(ride.withRemaining(left));
                continue;
            }

            ServerWorld world = (ServerWorld) lift.getWorld();
            int floor = ride.toFloor;
            BlockPos origin = lift.getStageOrigin();
            boolean restored = StructureLayerManager.activateFloor(server, world, lift.getLiftId(), floor, origin);
            lift.setCurrentFloor(floor);
            lift.setTargetFloor(floor);

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(ride.playerUuid);
            if (player != null) {
                player.playSound(restored ? SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value() : SoundEvents.BLOCK_IRON_DOOR_CLOSE,
                        0.8f, restored ? 0.9f : 0.55f);
                if (!restored) {
                    player.sendMessage(Text.literal("§8[§cFiven§8] §7Для этажа §f" + lift.getLiftId() + ":§c" + floor + " §7не найден сохранённый слой."), true);
                }
            }

            if (lift.canOpenOnFloor(floor) && restored) {
                lift.playDoors();
            } else if (player != null && restored) {
                player.sendMessage(Text.literal("§8[§cFiven§8] §7Лифт приехал на §c" + floor + "§7, но двери заблокированы."), true);
            }

            sendRideEnd(lift, floor);
            it.remove();
        }
    }

    private static Set<ServerPlayerEntity> recipients(LiftEntity lift) {
        Set<ServerPlayerEntity> players = new LinkedHashSet<>(PlayerLookup.tracking(lift));
        if (lift.getWorld() instanceof ServerWorld sw) {
            players.addAll(sw.getPlayers(player -> player.squaredDistanceTo(lift) < 64.0));
        }
        return players;
    }

    private static void sendRideStart(LiftEntity lift, int from, int to, int ticks) {
        for (ServerPlayerEntity player : recipients(lift)) {
            PacketByteBuf out = PacketByteBufs.create();
            out.writeVarInt(from);
            out.writeVarInt(to);
            out.writeVarInt(ticks);
            ServerPlayNetworking.send(player, FifthNetworking.LIFT_TRAVEL_START, out);
        }
    }

    private static void sendRideEnd(LiftEntity lift, int floor) {
        for (ServerPlayerEntity player : recipients(lift)) {
            PacketByteBuf out = PacketByteBufs.create();
            out.writeVarInt(floor);
            ServerPlayNetworking.send(player, FifthNetworking.LIFT_TRAVEL_END, out);
        }
    }

    private static String key(ServerWorld world, BlockPos pos) {
        return world.getRegistryKey().getValue() + "|" + pos.asLong();
    }

    private static Path bindingsFile(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("fiven").resolve("lift_buttons.json");
    }

    private static void save(MinecraftServer server) {
        try {
            Path p = bindingsFile(server);
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(BUTTONS, BIND_TYPE));
        } catch (Exception ignored) {}
    }

    private static int clampFloor(int floor) {
        return Math.max(1, Math.min(9, floor));
    }

    public record ButtonBinding(String liftUuid, int floor) {}

    private record Ride(UUID liftUuid, UUID playerUuid, int fromFloor, int toFloor, int total, int remaining) {
        Ride withRemaining(int n) {
            return new Ride(liftUuid, playerUuid, fromFloor, toFloor, total, n);
        }
    }
}
