package ru.fifth.horror.trigger;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import ru.fifth.horror.FifthMod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Server-authoritative, session-local visualization subscriptions for director players. */
public final class TriggerZoneVisualizationServer {
    public static final Identifier PAYLOAD = FifthMod.id("trigger_zone_visualization");

    private static final Map<UUID, TriggerVisualizationSelection> SELECTIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_SENT = new ConcurrentHashMap<>();

    private TriggerZoneVisualizationServer() {}

    public static void reset() {
        SELECTIONS.clear();
        LAST_SENT.clear();
    }

    public static void clear(UUID playerId) {
        if (playerId == null) return;
        SELECTIONS.remove(playerId);
        LAST_SENT.remove(playerId);
    }

    public static void showAll(ServerPlayerEntity player) {
        TriggerVisualizationSelection selection = SELECTIONS.computeIfAbsent(player.getUuid(), ignored -> new TriggerVisualizationSelection());
        selection.showAll();
        sendSnapshot(player, true);
    }

    public static void hideAll(ServerPlayerEntity player) {
        clear(player.getUuid());
        sendClear(player);
    }

    public static void show(ServerPlayerEntity player, String id) {
        TriggerVisualizationSelection selection = SELECTIONS.computeIfAbsent(player.getUuid(), ignored -> new TriggerVisualizationSelection());
        selection.show(id);
        sendSnapshot(player, true);
    }

    public static void hide(ServerPlayerEntity player, String id) {
        TriggerVisualizationSelection selection = SELECTIONS.get(player.getUuid());
        if (selection == null) {
            sendClear(player);
            return;
        }
        selection.hide(id);
        if (selection.isEmpty()) {
            clear(player.getUuid());
            sendClear(player);
        } else {
            sendSnapshot(player, true);
        }
    }

    /** Called after server occupancy updates. Only changed snapshots are resent. */
    public static void sync(MinecraftServer server) {
        for (UUID playerId : new ArrayList<>(SELECTIONS.keySet())) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player == null) {
                clear(playerId);
                continue;
            }
            sendSnapshot(player, false);
        }
    }

    private static void sendSnapshot(ServerPlayerEntity player, boolean force) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        TriggerVisualizationSelection selection = SELECTIONS.get(player.getUuid());
        if (selection == null || selection.isEmpty()) {
            if (force || LAST_SENT.remove(player.getUuid()) != null) sendClear(player);
            return;
        }

        List<TriggerZoneManager.Zone> rows = new ArrayList<>();
        for (TriggerZoneManager.Zone zone : TriggerZoneManager.list(server)) {
            if (selection.includes(zone.id)) rows.add(zone);
        }

        String fingerprint = fingerprint(server, rows);
        if (!force && fingerprint.equals(LAST_SENT.get(player.getUuid()))) return;
        LAST_SENT.put(player.getUuid(), fingerprint);

        PacketByteBuf out = PacketByteBufs.create();
        out.writeBoolean(false);
        out.writeVarInt(rows.size());
        for (TriggerZoneManager.Zone zone : rows) {
            out.writeString(zone.id, 128);
            out.writeString(zone.world, 256);
            out.writeInt(zone.minX); out.writeInt(zone.minY); out.writeInt(zone.minZ);
            out.writeInt(zone.maxX); out.writeInt(zone.maxY); out.writeInt(zone.maxZ);
            out.writeString(zone.mode.name(), 16);
            out.writeBoolean(zone.enabled);
            out.writeVarInt(TriggerZoneManager.currentCount(server, zone.id));
            out.writeVarInt(TriggerOccupancyPolicy.minimum(zone.minPlayers));
        }
        ServerPlayNetworking.send(player, PAYLOAD, out);
    }

    private static void sendClear(ServerPlayerEntity player) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBoolean(true);
        ServerPlayNetworking.send(player, PAYLOAD, out);
    }

    private static String fingerprint(MinecraftServer server, List<TriggerZoneManager.Zone> rows) {
        StringBuilder value = new StringBuilder(rows.size() * 64);
        for (TriggerZoneManager.Zone zone : rows) {
            value.append(zone.id).append('|').append(zone.world).append('|')
                    .append(zone.minX).append(',').append(zone.minY).append(',').append(zone.minZ).append('|')
                    .append(zone.maxX).append(',').append(zone.maxY).append(',').append(zone.maxZ).append('|')
                    .append(zone.mode).append('|').append(zone.enabled).append('|')
                    .append(TriggerZoneManager.currentCount(server, zone.id)).append('/')
                    .append(TriggerOccupancyPolicy.minimum(zone.minPlayers)).append(';');
        }
        return value.toString();
    }
}
