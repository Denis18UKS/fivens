package ru.fifth.horror.entity;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Non-persistent director-only chase test sessions. They are deliberately separate from authored map AI state,
 * so stopping a test restores the MFL mode/hunt/patrol flags that were active before testing.
 */
public final class MflTestModeManager {
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private MflTestModeManager() {}

    public static State start(MonsterForLiftEntity mfl, ServerPlayerEntity target) {
        if (mfl == null || target == null) return null;
        State old = STATES.get(mfl.getUuid());
        if (old != null) {
            old.targetUuid = target.getUuid();
            old.lastKnown = target.getPos();
            old.searchTicks = mfl.getSearchDurationTicks();
            return old;
        }

        State state = new State();
        state.targetUuid = target.getUuid();
        state.previousMode = mfl.getAiMode();
        state.previousHunt = mfl.isHuntEnabled();
        state.previousPatrol = mfl.isPatrolEnabled();
        state.lastKnown = target.getPos();
        state.searchTicks = mfl.getSearchDurationTicks();
        STATES.put(mfl.getUuid(), state);

        // Freeze authored AI while the test driver below owns navigation.
        mfl.setHuntEnabled(false);
        mfl.setPatrolEnabled(false);
        mfl.setAiMode(MonsterForLiftEntity.AiMode.OFF);
        mfl.preview("");
        return state;
    }

    public static boolean stop(MonsterForLiftEntity mfl) {
        if (mfl == null) return false;
        State state = STATES.remove(mfl.getUuid());
        if (state == null) return false;

        mfl.preview("");
        mfl.setAiMode(state.previousMode == null ? MonsterForLiftEntity.AiMode.OFF : state.previousMode);
        mfl.setHuntEnabled(state.previousHunt);
        mfl.setPatrolEnabled(state.previousPatrol);
        return true;
    }

    public static State state(MonsterForLiftEntity mfl) {
        return mfl == null ? null : STATES.get(mfl.getUuid());
    }

    public static boolean isActive(MonsterForLiftEntity mfl) {
        return state(mfl) != null;
    }

    public static final class State {
        public UUID targetUuid;
        public MonsterForLiftEntity.AiMode previousMode = MonsterForLiftEntity.AiMode.OFF;
        public boolean previousHunt;
        public boolean previousPatrol;
        public Vec3d lastKnown;
        public int searchTicks;
    }
}
