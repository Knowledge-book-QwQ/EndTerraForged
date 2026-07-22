package endterraforged.client.gui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class LandmassSlicePreviewLayoutTest {

    @Test
    void placesSliceControlsBelowTheTerrainPreview() {
        Optional<LandmassSlicePreviewLayout.Placement> placement =
                LandmassSlicePreviewLayout.place(640, 380, 470);

        LandmassSlicePreviewLayout.Placement expected = new LandmassSlicePreviewLayout.Placement(
                150,
                new ActionButtonLayout.Bounds(470, 236, 150, 18),
                new ActionButtonLayout.Bounds(470, 259, 150, 18),
                new ActionButtonLayout.Bounds(470, 282, 150, 64));
        assertEquals(Optional.of(expected), placement);
    }

    @Test
    void hidesWhenThePreviewColumnCannotFitAUsableSlice() {
        assertTrue(LandmassSlicePreviewLayout.place(580, 360, 470).isEmpty());
        assertTrue(LandmassSlicePreviewLayout.place(640, 325, 470).isEmpty());
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class,
                () -> LandmassSlicePreviewLayout.place(0, 360, 470));
        assertThrows(IllegalArgumentException.class,
                () -> LandmassSlicePreviewLayout.place(640, 0, 470));
        assertThrows(IllegalArgumentException.class,
                () -> LandmassSlicePreviewLayout.place(640, 360, -1));
    }
}
