package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.SeaMode;
import endterraforged.world.config.TestProfile;
import endterraforged.world.config.TopologyMode;

/**
 * Locks down {@link EndLevels}' normalisation against a known dimension
 * profile, and verifies the SeaMode.NONE / WITH_FLOOR branching of the
 * reference surface that the whole erosion anchoring relies on.
 */
class EndLevelsTest {

    /** RTF-parity End: 4064 tall, min_y=-2032. */
    private static final int HEIGHT = 4064;
    private static final int MIN_Y = -2032;
    private static final int SEA_Y = 0;

    @Test
    void noSeaUsesIslandBaselineAsSurface() {
        // baseline at Y=64, sea ignored
        TestProfile profile = new TestProfile(HEIGHT, MIN_Y, SEA_Y, 64,
                SeaMode.NONE, TopologyMode.ISLANDS, false);
        EndLevels levels = new EndLevels(profile);

        assertEquals(64, profile.surfaceY(), "profile.surfaceY should pick baseline in NONE mode");
        assertEquals(64, levels.surfaceY, "EndLevels should mirror the profile surface");
        // surface normalised = fillY(63) / worldHeight
        assertEquals(63.0F / HEIGHT, levels.surface, 1e-5f);
        assertEquals(65.0F / HEIGHT, levels.ground, 1e-5f);
    }

    @Test
    void withFloorUsesSeaLevelAsSurface() {
        TestProfile profile = new TestProfile(HEIGHT, MIN_Y, SEA_Y, 64,
                SeaMode.WITH_FLOOR, TopologyMode.ISLANDS, false);
        EndLevels levels = new EndLevels(profile);

        assertEquals(SEA_Y, profile.surfaceY(), "profile.surfaceY should pick sea level in WITH_FLOOR");
        assertEquals(SEA_Y, levels.surfaceY);
        assertEquals((SEA_Y - 1.0F) / HEIGHT, levels.surface, 1e-5f);
        assertEquals((SEA_Y + 1.0F) / HEIGHT, levels.ground, 1e-5f);
    }

    @Test
    void elevationIsZeroAtOrBelowSurface() {
        TestProfile profile = TestProfile.defaultEnd();
        EndLevels levels = new EndLevels(profile);

        // defaultEnd: surfaceY=0, fillY clamped to min(−1, height)=−1 → surface = −1/height (negative)
        // elevation below surface must be 0, above must climb into [0,1]
        assertEquals(0.0F, levels.elevation(levels.surface), 0.0f,
                "elevation at surface should be zero");
        assertTrue(levels.elevation(levels.ground) > 0.0F,
                "elevation above surface should be positive");
        assertTrue(levels.elevation(1.0F) > 0.0F && levels.elevation(1.0F) <= 1.0F,
                "elevation at world top should be in (0,1]");
    }

    @Test
    void scaleRoundTripsHeight() {
        EndLevels levels = new EndLevels(TestProfile.defaultEnd());
        // scale(float) -> Y, scale(int) -> float; round-trip within 1 block
        int y = levels.scale(0.5f);
        assertEquals(0.5f, levels.scale(y), 1.0f / HEIGHT,
                "normalised<->Y round trip should hold within one block");
    }
}
