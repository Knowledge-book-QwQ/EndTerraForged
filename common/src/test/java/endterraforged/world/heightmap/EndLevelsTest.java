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

    /** Standard player-facing End: 512 tall, min_y=-256. */
    private static final int HEIGHT = 512;
    private static final int MIN_Y = -256;
    private static final int SEA_Y = 0;

    @Test
    void noSeaUsesIslandBaselineAsSurface() {
        // baseline at Y=64, sea ignored
        TestProfile profile = new TestProfile(HEIGHT, MIN_Y, SEA_Y, 64,
                SeaMode.NONE, TopologyMode.ISLANDS, false);
        EndLevels levels = new EndLevels(profile);

        assertEquals(64, profile.surfaceY(), "profile.surfaceY should pick baseline in NONE mode");
        assertEquals(64, levels.surfaceY, "EndLevels should mirror the profile surface");
        // Normalisation must be relative to min_y, not absolute world Y.
        assertEquals((63.0F - MIN_Y) / HEIGHT, levels.surface, 1e-5f);
        assertEquals((65.0F - MIN_Y) / HEIGHT, levels.ground, 1e-5f);
    }

    @Test
    void withFloorUsesSeaLevelAsSurface() {
        TestProfile profile = new TestProfile(HEIGHT, MIN_Y, SEA_Y, 64,
                SeaMode.WITH_FLOOR, TopologyMode.ISLANDS, false);
        EndLevels levels = new EndLevels(profile);

        assertEquals(SEA_Y, profile.surfaceY(), "profile.surfaceY should pick sea level in WITH_FLOOR");
        assertEquals(SEA_Y, levels.surfaceY);
        assertEquals((SEA_Y - 1.0F - MIN_Y) / HEIGHT, levels.surface, 1e-5f);
        assertEquals((SEA_Y + 1.0F - MIN_Y) / HEIGHT, levels.ground, 1e-5f);
    }

    @Test
    void elevationIsZeroAtOrBelowSurface() {
        TestProfile profile = TestProfile.defaultEnd();
        EndLevels levels = new EndLevels(profile);

        // defaultEnd: surfaceY=0, so the fill line is -1. Its normalised
        // coordinate must sit near the midpoint of the -256..255 envelope.
        assertEquals(0.0F, levels.elevation(levels.surface), 0.0f,
                "elevation at surface should be zero");
        assertTrue(levels.elevation(levels.ground) > 0.0F,
                "elevation above surface should be positive");
        assertTrue(levels.elevation(levels.scale(levels.maxY)) > 0.0F
                        && levels.elevation(levels.scale(levels.maxY)) <= 1.0F,
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

    @Test
    void largeLegacyEnvelopeAlsoUsesMinYRelativeCoordinates() {
        TestProfile legacy = new TestProfile(4064, -2032, 0, 0,
                SeaMode.NONE, TopologyMode.ISLANDS, false);
        EndLevels levels = new EndLevels(legacy);

        assertEquals(2031.0F / 4064.0F, levels.surface, 1e-5F);
        assertEquals(2033.0F / 4064.0F, levels.ground, 1e-5F);
        assertEquals(0.5F, levels.scale(0), 1e-5F);
        assertEquals(-2032, levels.scale(0.0F));
        assertEquals(2031.0F / 4064.0F, levels.scale(-1), 1e-5F);
        assertEquals(4063.0F / 4064.0F, levels.scale(2031), 1e-5F);
    }
}
