package endterraforged.client.gui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ActionButtonLayoutTest {

    @Test
    void threeButtonRowMatchesExistingEditorGeometry() {
        ActionButtonLayout.Bounds[] bounds =
                ActionButtonLayout.row(10, 20, 200, 18, 3);

        assertEquals(new ActionButtonLayout.Bounds(10, 20, 64, 18), bounds[0]);
        assertEquals(new ActionButtonLayout.Bounds(78, 20, 64, 18), bounds[1]);
        assertEquals(new ActionButtonLayout.Bounds(146, 20, 64, 18), bounds[2]);
    }

    @Test
    void twoButtonRowMatchesExistingFallbackGeometry() {
        ActionButtonLayout.Bounds[] bounds =
                ActionButtonLayout.row(30, 40, 210, 20, 2);

        assertEquals(new ActionButtonLayout.Bounds(30, 40, 103, 20), bounds[0]);
        assertEquals(new ActionButtonLayout.Bounds(137, 40, 103, 20), bounds[1]);
    }

    @Test
    void remainderPixelsAreDistributedLeftToRight() {
        ActionButtonLayout.Bounds[] bounds =
                ActionButtonLayout.row(0, 0, 205, 20, 3);

        assertEquals(new ActionButtonLayout.Bounds(0, 0, 66, 20), bounds[0]);
        assertEquals(new ActionButtonLayout.Bounds(70, 0, 66, 20), bounds[1]);
        assertEquals(new ActionButtonLayout.Bounds(140, 0, 65, 20), bounds[2]);
    }

    @Test
    void rejectsInvalidRows() {
        assertThrows(IllegalArgumentException.class,
                () -> ActionButtonLayout.row(0, 0, 10, 20, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ActionButtonLayout.row(0, 0, 10, 0, 2));
        assertThrows(IllegalArgumentException.class,
                () -> ActionButtonLayout.row(0, 0, 10, 20, 2, -1));
        assertThrows(IllegalArgumentException.class,
                () -> ActionButtonLayout.row(0, 0, 5, 20, 3, 4));
    }
}
