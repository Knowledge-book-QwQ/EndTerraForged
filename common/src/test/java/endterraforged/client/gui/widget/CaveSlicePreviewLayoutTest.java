package endterraforged.client.gui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class CaveSlicePreviewLayoutTest {

    @Test
    void placesDefaultSliceControlsBesidePreview() {
        Optional<CaveSlicePreviewLayout.Placement> placement =
                CaveSlicePreviewLayout.place(640, 380, 470);

        CaveSlicePreviewLayout.Placement expected = new CaveSlicePreviewLayout.Placement(
                150,
                new ActionButtonLayout.Bounds(470, 240, 150, 18),
                new ActionButtonLayout.Bounds(470, 263, 150, 18),
                new ActionButtonLayout.Bounds(470, 286, 150, 60));

        assertEquals(Optional.of(expected), placement);
    }

    @Test
    void capsPreviewWidthAtMaximum() {
        Optional<CaveSlicePreviewLayout.Placement> placement =
                CaveSlicePreviewLayout.place(900, 420, 500);

        assertTrue(placement.isPresent());
        assertEquals(150, placement.get().previewWidth());
        assertEquals(72, placement.get().sliceBounds().height());
    }

    @Test
    void usesAvailableWidthWhenBelowMaximumButStillLargeEnough() {
        Optional<CaveSlicePreviewLayout.Placement> placement =
                CaveSlicePreviewLayout.place(610, 360, 470);

        assertTrue(placement.isPresent());
        assertEquals(120, placement.get().previewWidth());
        assertEquals(new ActionButtonLayout.Bounds(470, 210, 120, 18),
                placement.get().axisBounds());
    }

    @Test
    void hidesWhenWidthIsTooSmall() {
        Optional<CaveSlicePreviewLayout.Placement> placement =
                CaveSlicePreviewLayout.place(580, 360, 470);

        assertTrue(placement.isEmpty());
    }

    @Test
    void hidesWhenHeightIsTooSmall() {
        Optional<CaveSlicePreviewLayout.Placement> placement =
                CaveSlicePreviewLayout.place(640, 330, 470);

        assertTrue(placement.isEmpty());
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class,
                () -> CaveSlicePreviewLayout.place(0, 360, 470));
        assertThrows(IllegalArgumentException.class,
                () -> CaveSlicePreviewLayout.place(640, 0, 470));
        assertThrows(IllegalArgumentException.class,
                () -> CaveSlicePreviewLayout.place(640, 360, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new CaveSlicePreviewLayout.Metrics(0, 95, 96,
                        34, 24, 18, 23, 8, 34, 48, 72));
        assertThrows(IllegalArgumentException.class,
                () -> new CaveSlicePreviewLayout.Metrics(20, 150, 96,
                        34, 24, 24, 18, 8, 34, 48, 72));
        assertThrows(IllegalArgumentException.class,
                () -> new CaveSlicePreviewLayout.Metrics(20, 150, 96,
                        34, 24, 18, 23, 8, 34, 73, 72));
    }
}
