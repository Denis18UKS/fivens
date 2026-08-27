package ru.fifth.horror.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LiftTravelHudPolicyTest {
    @Test
    void activeLiftSuppressesVanillaHudBecauseItRendersFromGameRendererFinalPass() {
        assertTrue(LiftTravelHudPolicy.cancelVanillaHud(true, true));
        assertTrue(LiftTravelHudPolicy.cancelVanillaHud(false, true));
        assertTrue(LiftTravelHudPolicy.cancelVanillaHud(true, false));
        assertFalse(LiftTravelHudPolicy.cancelVanillaHud(false, false));
    }
}
