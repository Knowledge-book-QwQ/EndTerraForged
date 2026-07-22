package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldVerticalBoundsTest {

    @Test
    void maximumBuildYIsInclusiveTopOfTheEnvelope() {
        WorldVerticalBounds bounds = new WorldVerticalBounds(-256, 512);

        assertEquals(255, bounds.maxYInclusive());
        assertEquals(256, bounds.maxYExclusive());
    }

    @Test
    void containsUsesInclusiveBottomAndExclusiveTop() {
        WorldVerticalBounds bounds = new WorldVerticalBounds(-256, 512);

        assertEquals(256, bounds.maxYExclusive());
        assertTrue(bounds.contains(-256));
        assertTrue(bounds.contains(255));
        assertFalse(bounds.contains(-257));
        assertFalse(bounds.contains(256));
    }

    @Test
    void rejectsNonPositiveAndOverflowingHeights() {
        assertThrows(IllegalArgumentException.class, () -> new WorldVerticalBounds(0, 0));
        assertThrows(ArithmeticException.class,
                () -> new WorldVerticalBounds(Integer.MAX_VALUE, 1));
    }

    @Test
    void builderCanReplaceCoupledBoundsWithoutAnInvalidIntermediateSnapshot() {
        EndPreset preset = new EndPresetBuilder()
                .worldBounds(new WorldVerticalBounds(-512, 1536))
                .build();

        assertEquals(-512, preset.minY());
        assertEquals(1536, preset.worldHeight());
        assertEquals(1023, preset.worldBounds().maxYInclusive());
    }
}
