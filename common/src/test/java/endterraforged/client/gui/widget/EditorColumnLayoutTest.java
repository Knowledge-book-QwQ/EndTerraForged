package endterraforged.client.gui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EditorColumnLayoutTest {

    @Test
    void nextRowReturnsBoundsAndAdvancesByStep() {
        EditorColumnLayout layout = new EditorColumnLayout(20, 50, 200, 20, 26);

        assertEquals(new ActionButtonLayout.Bounds(20, 50, 200, 20), layout.nextRow());
        assertEquals(new ActionButtonLayout.Bounds(20, 76, 200, 20), layout.nextRow());
        assertEquals(102, layout.nextY());
    }

    @Test
    void nextActionRowUsesCurrentYThenAdvances() {
        EditorColumnLayout layout = new EditorColumnLayout(10, 30, 200, 20, 26);

        ActionButtonLayout.Bounds[] bounds = layout.nextActionRow(3);

        assertEquals(new ActionButtonLayout.Bounds(10, 30, 64, 20), bounds[0]);
        assertEquals(new ActionButtonLayout.Bounds(78, 30, 64, 20), bounds[1]);
        assertEquals(new ActionButtonLayout.Bounds(146, 30, 64, 20), bounds[2]);
        assertEquals(56, layout.nextY());
    }

    @Test
    void exposesColumnGeometryForPreviewPlacement() {
        EditorColumnLayout layout = new EditorColumnLayout(42, 50, 210, 20, 24);

        assertEquals(42, layout.x());
        assertEquals(210, layout.width());
        assertEquals(50, layout.nextY());
    }

    @Test
    void spaceAdvancesWithoutReturningBounds() {
        EditorColumnLayout layout = new EditorColumnLayout(10, 30, 200, 20, 26);

        layout.nextRow();
        layout.space(4);

        assertEquals(new ActionButtonLayout.Bounds(10, 60, 200, 20), layout.nextRow());
    }

    @Test
    void rejectsInvalidGeometry() {
        assertThrows(IllegalArgumentException.class,
                () -> new EditorColumnLayout(0, 0, 0, 20, 26));
        assertThrows(IllegalArgumentException.class,
                () -> new EditorColumnLayout(0, 0, 200, 0, 26));
        assertThrows(IllegalArgumentException.class,
                () -> new EditorColumnLayout(0, 0, 200, 20, 19));
        assertThrows(IllegalArgumentException.class,
                () -> new EditorColumnLayout(0, 0, 200, 20, 26).space(-1));
    }
}
