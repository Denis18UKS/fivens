package ru.fifth.horror.entity;

import java.util.UUID;

/** Deterministic, exactly-once lifecycle for one MFL victim. Minecraft integration lives elsewhere. */
public final class MflCapturePolicy {
    public enum State { IDLE, CAPTURED, DEATH_SCENE, RESOLVED }

    private State state = State.IDLE;
    private UUID victim;
    private int remainingTicks;
    private boolean resolutionConsumed;

    public boolean capture(UUID victim, int durationTicks) {
        if (state != State.IDLE || victim == null) return false;
        this.victim = victim;
        this.remainingTicks = Math.max(1, durationTicks);
        this.resolutionConsumed = false;
        this.state = State.DEATH_SCENE;
        return true;
    }

    public void tick() {
        if (state == State.DEATH_SCENE && remainingTicks > 0) remainingTicks--;
    }

    public boolean shouldResolve() {
        return state == State.DEATH_SCENE && remainingTicks <= 0 && !resolutionConsumed;
    }

    public boolean resolve() {
        if (!shouldResolve()) return false;
        resolutionConsumed = true;
        state = State.RESOLVED;
        return true;
    }

    public void reset() {
        state = State.IDLE;
        victim = null;
        remainingTicks = 0;
        resolutionConsumed = false;
    }

    public State state() { return state; }
    public UUID victim() { return victim; }
    public int remainingTicks() { return remainingTicks; }
}
