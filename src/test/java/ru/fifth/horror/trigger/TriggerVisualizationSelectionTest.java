package ru.fifth.horror.trigger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TriggerVisualizationSelectionTest {
    @Test
    void allModeCanHideAndRestoreOneZone() {
        TriggerVisualizationSelection selection = new TriggerVisualizationSelection();
        selection.showAll();

        assertTrue(selection.includes("intro"));
        assertTrue(selection.includes("ending"));

        selection.hide("intro");
        assertFalse(selection.includes("intro"));
        assertTrue(selection.includes("ending"));

        selection.show("intro");
        assertTrue(selection.includes("intro"));
    }

    @Test
    void singleZoneModeDoesNotRevealUnrelatedZones() {
        TriggerVisualizationSelection selection = new TriggerVisualizationSelection();
        selection.show("intro");

        assertTrue(selection.includes("intro"));
        assertFalse(selection.includes("ending"));
        assertFalse(selection.isEmpty());
    }

    @Test
    void hideAllClearsEverySelection() {
        TriggerVisualizationSelection selection = new TriggerVisualizationSelection();
        selection.showAll();
        selection.hide("intro");
        selection.hideAll();

        assertTrue(selection.isEmpty());
        assertFalse(selection.includes("intro"));
        assertFalse(selection.includes("ending"));
    }
}
