package ru.fifth.horror.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MflCapturePolicyTest {
    @Test
    void captureRunsForConfiguredTicksAndResolvesExactlyOnce() {
        MflCapturePolicy policy = new MflCapturePolicy();
        UUID victim = UUID.randomUUID();

        assertTrue(policy.capture(victim, 3));
        assertFalse(policy.capture(UUID.randomUUID(), 3));
        assertEquals(MflCapturePolicy.State.DEATH_SCENE, policy.state());
        assertEquals(victim, policy.victim());

        policy.tick();
        policy.tick();
        assertFalse(policy.shouldResolve());
        policy.tick();
        assertTrue(policy.shouldResolve());
        assertTrue(policy.resolve());
        assertFalse(policy.resolve());
        assertEquals(MflCapturePolicy.State.RESOLVED, policy.state());

        policy.reset();
        assertEquals(MflCapturePolicy.State.IDLE, policy.state());
        assertNull(policy.victim());
        assertTrue(policy.capture(victim, 1));
    }
}
