package endterraforged.client.gui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EditorScrollLayoutTest {

    @Test
    void contentBottomAccountsForColumnsAndTrailingRows() {
        assertEquals(246, EditorScrollLayout.contentBottom(44, 23, 18, 7, 2));
    }

    @Test
    void displayedRowsUsesLongestColumnOnlyWhenTwoColumnLayoutIsActive() {
        assertEquals(8, EditorScrollLayout.displayedRows(true, 4, 8));
        assertEquals(12, EditorScrollLayout.displayedRows(false, 4, 8));
    }

    @Test
    void maxScrollIsZeroWhenContentFits() {
        assertEquals(0, EditorScrollLayout.maxScroll(246, 300, 34));
    }

    @Test
    void maxScrollMeasuresOverflowPastBottomMargin() {
        assertEquals(20, EditorScrollLayout.maxScroll(246, 260, 34));
    }

    @Test
    void clampScrollBoundsOffset() {
        assertEquals(0, EditorScrollLayout.clampScroll(-5, 20));
        assertEquals(12, EditorScrollLayout.clampScroll(12, 20));
        assertEquals(20, EditorScrollLayout.clampScroll(50, 20));
    }

    @Test
    void scrollOffsetAfterWheelMovesAgainstWheelDirection() {
        assertEquals(0, EditorScrollLayout.scrollOffsetAfterWheel(10, 40, 1.0, 16));
        assertEquals(26, EditorScrollLayout.scrollOffsetAfterWheel(10, 40, -1.0, 16));
        assertEquals(10, EditorScrollLayout.scrollOffsetAfterWheel(10, 40, 0.0, 16));
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> EditorScrollLayout.contentBottom(-1, 23, 18, 7, 2));
        assertThrows(IllegalArgumentException.class,
                () -> EditorScrollLayout.contentBottom(44, 17, 18, 7, 2));
        assertThrows(IllegalArgumentException.class,
                () -> EditorScrollLayout.displayedRows(true, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> EditorScrollLayout.displayedRows(true, 0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> EditorScrollLayout.maxScroll(246, 0, 34));
        assertThrows(IllegalArgumentException.class,
                () -> EditorScrollLayout.clampScroll(0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> EditorScrollLayout.scrollOffsetAfterWheel(0, 0, 1.0, 0));
    }
}
