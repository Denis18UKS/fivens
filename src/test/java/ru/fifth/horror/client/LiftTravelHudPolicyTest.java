package ru.fifth.horror.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LiftTravelHudPolicyTest {
    @Test
    void activeLiftOwnsFinalHudPassEvenWhenCutsceneWouldHideHud() {
        assertFalse(LiftTravelHudPolicy.cancelVanillaHud(true, true));
        assertTrue(LiftTravelHudPolicy.cancelVanillaHud(true, false));
        assertFalse(LiftTravelHudPolicy.cancelVanillaHud(false, false));
    }
}
