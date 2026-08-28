package ru.fifth.horror.entity;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.fifth.horror.checkpoint.CheckpointManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Turns an idle/outside-game MFL hostile after three Adventure-mode hits. */
public final class MflAggressionManager {
    private static final int REQUIRED_HITS = 3;
    private static final int RESET_TICKS = 100;
    private static final Map<UUID, HitState> HITS = new ConcurrentHashMap<>();

    private MflAggressionManager() {}

    public static void recordHit(MonsterForLiftEntity mfl, PlayerEntity attacker) {
        if (mfl == null || attacker == null || mfl.getWorld().isClient) return;
        if (!(attacker instanceof ServerPlayerEntity player)) return;
        if (player.interactionManager == null || !player.interactionManager.getGameMode().isSurvivalLike()) return;
        if (CheckpointManager.isGameRunning(player.getServer())) return;
        if (mfl.isHuntEnabled() || MflTestModeManager.isActive(mfl)) return;

        long now = mfl.age;
        HitState state = HITS.computeIfAbsent(mfl.getUuid(), ignored -> new HitState());
        if (!player.getUuid().equals(state.playerId) || now - state.lastTick > RESET_TICKS) {
            state.playerId = player.getUuid();
            state.count = 0;
        }
        state.lastTick = now;
        state.count++;

        if (state.count >= REQUIRED_HITS) {
            HITS.remove(mfl.getUuid());
            mfl.setHuntEnabled(true);
            mfl.setPatrolEnabled(false);
            mfl.setAiMode(MonsterForLiftEntity.AiMode.LOGICAL);
            player.sendMessage(Text.literal("§8[§cFiven§8] §cMFL разозлился и начал охоту."), true);
        } else {
            player.sendMessage(Text.literal("§8[§cFiven§8] §7MFL: удар " + state.count + "/" + REQUIRED_HITS), true);
        }
    }

    public static void clear(MonsterForLiftEntity mfl) {
        if (mfl != null) HITS.remove(mfl.getUuid());
    }

    private static final class HitState {
        UUID playerId;
        int count;
        long lastTick;
    }
}
