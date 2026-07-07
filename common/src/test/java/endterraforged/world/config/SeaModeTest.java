package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Matrix tests for {@link SeaMode}'s three-state surface model.
 *
 * <p>{@link SeaMode} is one of the three orthogonal dimension-shape switches
 * exposed by {@link EndPreset}; {@link endterraforged.world.heightmap.EndDensity}
 * reads {@link SeaMode#hasFloor()} to decide whether to keep filling solid
 * below the surface or carve void. These tests pin the three-state truth table
 * so a future refactor of the enum cannot silently flip End generation between
 * "floating islands over void", "islands over sea with seabed", and "islands
 * over endless sea with no floor".</p>
 */
class SeaModeTest {

    @Test
    void noneHasNoSeaNoVoidingNoFloor() {
        // NONE = vanilla-End-like: sea level ignored, void below baseline,
        // erosion anchored to the island baseline instead of a waterline.
        SeaMode mode = SeaMode.NONE;
        assertFalse(mode.hasSea(), "NONE must not honour sea level");
        assertFalse(mode.voidsBelowSea(), "NONE must not void below sea (no sea to void against)");
        assertFalse(mode.hasFloor(), "NONE must not have a continuous floor");
    }

    @Test
    void withFloorHasSeaNoVoidingAndAFloor() {
        // WITH_FLOOR = RTF-overworld analogue transplanted into the End:
        // sea level honoured, ground extends below as a seabed.
        SeaMode mode = SeaMode.WITH_FLOOR;
        assertTrue(mode.hasSea(), "WITH_FLOOR must honour sea level");
        assertFalse(mode.voidsBelowSea(), "WITH_FLOOR must keep the seabed (no voiding)");
        assertTrue(mode.hasFloor(), "WITH_FLOOR must have a continuous floor");
    }

    @Test
    void noFloorHasSeaVoidingAndNoFloor() {
        // NO_FLOOR = floating islands suspended over an endless sea with no bed.
        SeaMode mode = SeaMode.NO_FLOOR;
        assertTrue(mode.hasSea(), "NO_FLOOR must honour sea level");
        assertTrue(mode.voidsBelowSea(), "NO_FLOOR must void below the waterline");
        assertFalse(mode.hasFloor(), "NO_FLOOR must have no seabed");
    }

    @Test
    void hasSeaIsFalseOnlyForNone() {
        // hasSea partitions the states: only NONE lacks a sea.
        for (SeaMode mode : SeaMode.values()) {
            assertEquals(mode != SeaMode.NONE, mode.hasSea(),
                    "hasSea must be false only for NONE, got " + mode);
        }
    }

    @Test
    void hasFloorIsTrueOnlyForWithFloor() {
        // hasFloor is the single switch EndDensity reads for "keep filling solid".
        for (SeaMode mode : SeaMode.values()) {
            assertEquals(mode == SeaMode.WITH_FLOOR, mode.hasFloor(),
                    "hasFloor must be true only for WITH_FLOOR, got " + mode);
        }
    }

    @Test
    void voidsBelowSeaIsTrueOnlyForNoFloor() {
        for (SeaMode mode : SeaMode.values()) {
            assertEquals(mode == SeaMode.NO_FLOOR, mode.voidsBelowSea(),
                    "voidsBelowSea must be true only for NO_FLOOR, got " + mode);
        }
    }
}
