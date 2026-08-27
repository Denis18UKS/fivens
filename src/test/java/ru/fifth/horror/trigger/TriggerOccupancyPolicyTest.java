package ru.fifth.horror.trigger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TriggerOccupancyPolicyTest {
    @Test
    void enterFiresOnlyWhenCrossingConfiguredThreshold() {
        assertTrue(TriggerOccupancyPolicy.enterCrossed(2, 3, 3));
        assertFalse(TriggerOccupancyPolicy.enterCrossed(3, 4, 3));
        assertFalse(TriggerOccupancyPolicy.enterCrossed(3, 3, 3));
        assertTrue(TriggerOccupancyPolicy.enterCrossed(2, 3, 3));
    }

    @Test
    void stayRequiresEnoughPlayersInside() {
        assertFalse(TriggerOccupancyPolicy.stayEligible(2, 3));
        assertTrue(TriggerOccupancyPolicy.stayEligible(3, 3));
        assertTrue(TriggerOccupancyPolicy.stayEligible(4, 3));
    }

    @Test
    void exitRequiresPreviouslyQualifiedGroupAndAnActualExit() {
        assertFalse(TriggerOccupancyPolicy.exitQualified(2, 1, 3));
        assertTrue(TriggerOccupancyPolicy.exitQualified(3, 2, 3));
        assertTrue(TriggerOccupancyPolicy.exitQualified(4, 3, 3));
        assertFalse(TriggerOccupancyPolicy.exitQualified(3, 3, 3));
    }

    @Test
    void minimumNeverFallsBelowOne() {
        assertEquals(1, TriggerOccupancyPolicy.minimum(0));
        assertEquals(1, TriggerOccupancyPolicy.minimum(-5));
        assertEquals(4, TriggerOccupancyPolicy.minimum(4));
    }
}
