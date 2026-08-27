package ru.fifth.horror.client;

/** Pure rule: either a hidden-HUD cutscene or lift travel suppresses the ordinary in-game HUD. */
public final class LiftTravelHudPolicy {
    private LiftTravelHudPolicy() {}

    public static boolean cancelVanillaHud(boolean cutsceneHideHud, boolean liftActive) {
        return cutsceneHideHud || liftActive;
    }
}
