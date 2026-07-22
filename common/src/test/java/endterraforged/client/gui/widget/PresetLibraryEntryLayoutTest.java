package endterraforged.client.gui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PresetLibraryEntryLayoutTest {

    @Test
    void visibleRowsFitsOnlyCompleteRows() {
        assertEquals(3, PresetLibraryEntryLayout.visibleRows(240, 144, 28, 20, 22));
        assertEquals(0, PresetLibraryEntryLayout.visibleRows(160, 144, 28, 20, 22));
    }

    @Test
    void scrollIsBoundedByVisibleEntries() {
        int maxScroll = PresetLibraryEntryLayout.maxScrollRows(8, 3);
        assertEquals(5, maxScroll);
        assertEquals(5, PresetLibraryEntryLayout.clampScrollRows(10, maxScroll));
        assertEquals(4, PresetLibraryEntryLayout.scrollAfterWheel(5, maxScroll, 1.0D));
        assertEquals(5, PresetLibraryEntryLayout.scrollAfterWheel(5, maxScroll, -1.0D));
    }

    @Test
    void rowPositionTracksScrollOffset() {
        assertEquals(144, PresetLibraryEntryLayout.rowY(144, 22, 3, 3));
        assertEquals(188, PresetLibraryEntryLayout.rowY(144, 22, 5, 3));
    }

    @Test
    void rejectsInvalidLayoutArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> PresetLibraryEntryLayout.visibleRows(0, 144, 28, 20, 22));
        assertThrows(IllegalArgumentException.class,
                () -> PresetLibraryEntryLayout.maxScrollRows(-1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> PresetLibraryEntryLayout.rowY(144, 0, 0, 0));
    }
}
