package ru.fifth.horror.cabinet;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pure server-side occupancy policy: one player and one cabinet at a time. */
public final class CabinetOccupancyPolicy {
    private final Map<UUID, String> playerToCabinet = new HashMap<>();
    private final Map<String, UUID> cabinetToPlayer = new HashMap<>();

    public boolean claim(UUID player, String cabinetId) {
        if (player == null || cabinetId == null || cabinetId.isBlank()) return false;
        if (playerToCabinet.containsKey(player) || cabinetToPlayer.containsKey(cabinetId)) return false;
        playerToCabinet.put(player, cabinetId);
        cabinetToPlayer.put(cabinetId, player);
        return true;
    }

    public boolean release(UUID player, String cabinetId) {
        if (player == null || cabinetId == null) return false;
        if (!player.equals(cabinetToPlayer.get(cabinetId))) return false;
        cabinetToPlayer.remove(cabinetId);
        playerToCabinet.remove(player);
        return true;
    }

    public String cabinetOf(UUID player) { return playerToCabinet.get(player); }
    public UUID ownerOf(String cabinetId) { return cabinetToPlayer.get(cabinetId); }
}
