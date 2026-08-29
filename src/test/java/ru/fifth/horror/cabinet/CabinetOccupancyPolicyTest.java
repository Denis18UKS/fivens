package ru.fifth.horror.cabinet;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CabinetOccupancyPolicyTest {
    @Test
    void onePlayerCanOwnOnlyOneCabinetAndSameCabinetRejectsSecondPlayer() {
        CabinetOccupancyPolicy policy = new CabinetOccupancyPolicy();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(policy.claim(first, "cabinet-a"));
        assertEquals("cabinet-a", policy.cabinetOf(first));
        assertFalse(policy.claim(second, "cabinet-a"));
        assertFalse(policy.claim(first, "cabinet-b"));
        assertEquals(first, policy.ownerOf("cabinet-a"));
    }

    @Test
    void releasingCabinetMakesItAvailableAgain() {
        CabinetOccupancyPolicy policy = new CabinetOccupancyPolicy();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(policy.claim(first, "cabinet-a"));
        assertTrue(policy.release(first, "cabinet-a"));
        assertTrue(policy.claim(second, "cabinet-a"));
        assertEquals(second, policy.ownerOf("cabinet-a"));
    }
}
