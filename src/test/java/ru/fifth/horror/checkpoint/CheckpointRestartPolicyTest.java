package ru.fifth.horror.checkpoint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CheckpointRestartPolicyTest {
    @Test
    void onlyOneRestartCanRunAtATimeAndFinishRearmsTheGuard() {
        CheckpointRestartPolicy policy = new CheckpointRestartPolicy();

        assertTrue(policy.tryBegin());
        assertTrue(policy.isRestarting());
        assertFalse(policy.tryBegin());

        policy.finish();
        assertFalse(policy.isRestarting());
        assertTrue(policy.tryBegin());
    }
}
