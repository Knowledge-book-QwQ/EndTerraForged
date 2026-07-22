package endterraforged.client.gui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EditorPageLayoutTest {

    @Test
    void keepsPreferredStepWhenThereIsEnoughHeight() {
        assertEquals(26, EditorPageLayout.rowStepForHeight(320, 38, 18, 20, 9, 26));
    }

    @Test
    void compactsStepToFitShortScreens() {
        assertEquals(20, EditorPageLayout.rowStepForHeight(240, 38, 18, 20, 9, 26));
    }

    @Test
    void neverCompactsBelowRowHeight() {
        assertEquals(20, EditorPageLayout.rowStepForHeight(180, 38, 18, 20, 9, 26));
    }

    @Test
    void singleRowUsesPreferredStep() {
        assertEquals(26, EditorPageLayout.rowStepForHeight(120, 38, 18, 20, 1, 26));
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> EditorPageLayout.rowStepForHeight(0, 38, 18, 20, 9, 26));
        assertThrows(IllegalArgumentException.class,
                () -> EditorPageLayout.rowStepForHeight(240, -1, 18, 20, 9, 26));
        assertThrows(IllegalArgumentException.class,
                () -> EditorPageLayout.rowStepForHeight(240, 38, -1, 20, 9, 26));
        assertThrows(IllegalArgumentException.class,
                () -> EditorPageLayout.rowStepForHeight(240, 38, 18, 0, 9, 26));
        assertThrows(IllegalArgumentException.class,
                () -> EditorPageLayout.rowStepForHeight(240, 38, 18, 20, 0, 26));
        assertThrows(IllegalArgumentException.class,
                () -> EditorPageLayout.rowStepForHeight(240, 38, 18, 20, 9, 19));
    }
}
