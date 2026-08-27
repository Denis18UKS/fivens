package ru.fifth.horror.client;

/** Pure rule used by the HUD mixin so lift travel always owns the final render pass. */
public final class LiftTravelHudPolicy {
    private LiftTravelHudPolicy() {}

    public static boolean cancelVanillaHud(boolean cutsceneHideHud, boolean liftActive) {
        return cutsceneHideHud && !liftActive;
    }
}
