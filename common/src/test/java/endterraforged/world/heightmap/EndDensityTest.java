package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.SeaMode;
import endterraforged.world.config.TestProfile;
import endterraforged.world.config.TopologyMode;

/**
 * Contract tests for {@link EndDensity}: the 3D solid/void column decision
 * that realises the three {@link SeaMode} behaviours below the reference
 * surface.
 *
 * <p>Asserts the column structure per sea mode (WITH_FLOOR fills a seabed,
 * NONE / NO_FLOOR carve void below the surface), the continent gate (void
 * where landness is 0), the terrain-top air boundary, determinism, and the
 * discrete 0/1 output contract. Tests sample a real {@link EndHeightmap} so
 * the integration between heightmap and density is exercised end-to-end.</p>
 */
class EndDensityTest {

    private static final int SEED = 42;
    private static final int SAMPLES = 400;

    private static float x(int i) { return i * 7.3F; }
    private static float z(int i) { return i * 11.1F; }

    /**
     * Finds a sample point where the continent reports solid land (landness > 0)
     * and the terrain top is comfortably above the surface, so the column has a
     * non-trivial solid band to test against.
     */
    private static int findSolidSample(EndHeightmap map) {
        for (int i = 0; i < SAMPLES; i++) {
            float landness = map.getLandness(x(i), z(i), SEED);
            float height = map.getHeight(x(i), z(i), SEED);
            if (landness > 0.5F && height > map.levels().surface + 0.05F) {
                return i;
            }
        }
        throw new AssertionError("no solid sample with terrain above surface found");
    }

    // ----- output contract ------------------------------------------------

    @Test
    void densityIsAlwaysZeroOrOne() {
        EndDensity density = new EndDensity(new EndHeightmap(TestProfile.defaultEnd(), SEED));
        for (int i = 0; i < SAMPLES; i++) {
            for (int y = -100; y <= 2000; y += 137) {
                float d = density.density(x(i), y, z(i), SEED);
                assertTrue(d == 0.0F || d == 1.0F,
                        "density must be discrete 0 or 1, got " + d + " at y=" + y);
            }
        }
    }

    @Test
    void isSolidAgreesWithDensity() {
        EndDensity density = new EndDensity(new EndHeightmap(TestProfile.defaultEnd(), SEED));
        for (int i = 0; i < SAMPLES; i++) {
            int y = 64;
            boolean solid = density.isSolid(x(i), y, z(i), SEED);
            float d = density.density(x(i), y, z(i), SEED);
            assertEquals(d >= 0.5F, solid, "isSolid must agree with density threshold");
        }
    }

    @Test
    void densityIsDeterministic() {
        EndDensity density = new EndDensity(new EndHeightmap(TestProfile.defaultEnd(), SEED));
        for (int i = 0; i < SAMPLES; i++) {
            int y = 100;
            float a = density.density(x(i), y, z(i), SEED);
            float b = density.density(x(i), y, z(i), SEED);
            assertEquals(a, b, 0.0F, "same args should yield same density");
        }
    }

    // ----- continent gate --------------------------------------------------

    @Test
    void voidWhereNoLandRegardlessOfSeaMode() {
        // Where landness == 0, the whole column must be void in every sea mode.
        for (SeaMode mode : SeaMode.values()) {
            TestProfile profile = new TestProfile(4064, -2032, 0, 0, mode, TopologyMode.ISLANDS, false);
            EndDensity density = new EndDensity(new EndHeightmap(profile, SEED));
            boolean foundVoid = false;
            for (int i = 0; i < SAMPLES; i++) {
                if (density.heightmap().getLandness(x(i), z(i), SEED) <= 0.0F) {
                    assertEquals(0.0F, density.density(x(i), 64, z(i), SEED), 0.0F,
                            mode + ": void expected where landness=0");
                    foundVoid = true;
                }
            }
            assertTrue(foundVoid, mode + ": ISLANDS should produce at least one void sample");
        }
    }

    // ----- terrain top boundary -------------------------------------------

    @Test
    void airAboveTerrainTop() {
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndDensity density = new EndDensity(map);
        int i = findSolidSample(map);
        float heightNorm = map.getHeight(x(i), z(i), SEED);
        int terrainTopY = map.levels().scale(heightNorm);
        // A few blocks above the terrain top must be air.
        float above = density.density(x(i), terrainTopY + 5, z(i), SEED);
        assertEquals(0.0F, above, 0.0F, "air expected above terrain top");
    }

    @Test
    void solidAtTerrainTop() {
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndDensity density = new EndDensity(map);
        int i = findSolidSample(map);
        float heightNorm = map.getHeight(x(i), z(i), SEED);
        int terrainTopY = map.levels().scale(heightNorm);
        // At or just below the terrain top must be solid (within the solid band
        // and above the surface, since findSolidSample guarantees height > surface).
        float at = density.density(x(i), terrainTopY, z(i), SEED);
        assertEquals(1.0F, at, 0.0F, "solid expected at terrain top");
    }

    // ----- sea-mode column shape ------------------------------------------

    @Test
    void withFloorFillsSolidBelowSurface() {
        // WITH_FLOOR: below the surface is solid seabed (where land exists).
        TestProfile profile = new TestProfile(4064, -2032, 0, 0,
                SeaMode.WITH_FLOOR, TopologyMode.CONTINENTAL_SHATTERED, false);
        EndHeightmap map = new EndHeightmap(profile, SEED);
        EndDensity density = new EndDensity(map);
        int i = findSolidSample(map);
        int surfaceY = map.levels().surfaceY;
        // A point well below the surface, on land, must be solid (seabed).
        float below = density.density(x(i), surfaceY - 100, z(i), SEED);
        assertEquals(1.0F, below, 0.0F,
                "WITH_FLOOR: solid seabed expected below surface on land");
    }

    @Test
    void noneCarvesVoidBelowSurface() {
        // NONE: below the island baseline is void (floating islands).
        TestProfile profile = new TestProfile(4064, -2032, 0, 64,
                SeaMode.NONE, TopologyMode.CONTINENTAL_SHATTERED, false);
        EndHeightmap map = new EndHeightmap(profile, SEED);
        EndDensity density = new EndDensity(map);
        int i = findSolidSample(map);
        int surfaceY = map.levels().surfaceY;
        // Below the baseline (surfaceY = 64) must be void.
        float below = density.density(x(i), surfaceY - 10, z(i), SEED);
        assertEquals(0.0F, below, 0.0F,
                "NONE: void expected below island baseline");
    }

    @Test
    void noFloorCarvesVoidBelowSeaLevel() {
        // NO_FLOOR: below sea level is void (floorless sea), even on land.
        TestProfile profile = new TestProfile(4064, -2032, 0, 0,
                SeaMode.NO_FLOOR, TopologyMode.CONTINENTAL_SHATTERED, false);
        EndHeightmap map = new EndHeightmap(profile, SEED);
        EndDensity density = new EndDensity(map);
        int i = findSolidSample(map);
        int surfaceY = map.levels().surfaceY;
        float below = density.density(x(i), surfaceY - 10, z(i), SEED);
        assertEquals(0.0F, below, 0.0F,
                "NO_FLOOR: void expected below sea level");
    }

    @Test
    void noneAndNoFloorProduceSameColumnShape() {
        // NONE and NO_FLOOR have identical solid/void structure; the difference
        // is only semantic (water above the void in NO_FLOOR). With the same
        // surface Y, both must agree at every sampled Y.
        int surfaceY = 64;
        TestProfile none = new TestProfile(4064, -2032, surfaceY, surfaceY,
                SeaMode.NONE, TopologyMode.CONTINENTAL_SHATTERED, false);
        TestProfile noFloor = new TestProfile(4064, -2032, surfaceY, surfaceY,
                SeaMode.NO_FLOOR, TopologyMode.CONTINENTAL_SHATTERED, false);
        EndDensity noneD = new EndDensity(new EndHeightmap(none, SEED));
        EndDensity noFloorD = new EndDensity(new EndHeightmap(noFloor, SEED));
        for (int i = 0; i < SAMPLES; i++) {
            for (int y = -200; y <= 2000; y += 73) {
                assertEquals(noneD.density(x(i), y, z(i), SEED),
                        noFloorD.density(x(i), y, z(i), SEED), 0.0F,
                        "NONE and NO_FLOOR must agree at y=" + y);
            }
        }
    }

    // ----- solid band bounds ----------------------------------------------

    @Test
    void solidBandLiesBetweenSurfaceAndTerrainTop() {
        // For NONE/NO_FLOOR, solid blocks must satisfy surfaceY <= y <= terrainTopY.
        // Walking the column on a known-land sample, every solid block must be
        // inside that band and every void block outside it.
        TestProfile profile = new TestProfile(4064, -2032, 0, 64,
                SeaMode.NONE, TopologyMode.CONTINENTAL_SHATTERED, false);
        EndHeightmap map = new EndHeightmap(profile, SEED);
        EndDensity density = new EndDensity(map);
        int i = findSolidSample(map);
        float heightNorm = map.getHeight(x(i), z(i), SEED);
        int terrainTopY = map.levels().scale(heightNorm);
        int surfaceY = map.levels().surfaceY;
        for (int y = surfaceY - 200; y <= terrainTopY + 200; y++) {
            float d = density.density(x(i), y, z(i), SEED);
            boolean inBand = y >= surfaceY && y <= terrainTopY;
            if (d > 0.5F) {
                assertTrue(inBand, "solid block outside [surface, terrainTop] at y=" + y);
            } else {
                assertTrue(!inBand, "void block inside [surface, terrainTop] at y=" + y);
            }
        }
    }
}
