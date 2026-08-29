package ru.fifth.horror.vhs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VhsFrameNavigationPolicyTest {
    @Test
    void movesOneFrameAtATimeAndClampsAtEnds() {
        assertEquals(0, VhsFrameNavigationPolicy.move(0, -1, 4));
        assertEquals(1, VhsFrameNavigationPolicy.move(0, 1, 4));
        assertEquals(2, VhsFrameNavigationPolicy.move(3, -1, 4));
        assertEquals(3, VhsFrameNavigationPolicy.move(3, 1, 4));
    }

    @Test
    void sanitizesInvalidCurrentFrameAndEmptyRecordings() {
        assertEquals(0, VhsFrameNavigationPolicy.move(-10, 0, 4));
        assertEquals(3, VhsFrameNavigationPolicy.move(99, 0, 4));
        assertEquals(0, VhsFrameNavigationPolicy.move(5, 1, 0));
    }
}
