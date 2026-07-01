package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.SeaMode;
import endterraforged.world.config.TestProfile;
import endterraforged.world.config.TopologyMode;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.Perlin;

/**
 * Contract tests for {@link EndHeightmap}: the composition
 * {@code continent × mountains}, the world-height scaling, and the seam
 * between the continent layer and the mountain layer.
 *
 * <p>Asserts invariants rather than magic numbers: height stays in
 * {@code [surface, 1]}, landness in {@code [0, 1]}, void (landness 0) maps
 * to exactly the surface, and both topologies produce their characteristic
 * shape (islands scatter to void, shattered stays mostly solid). The
 * {@code mapAll} reach test proves the whole noise tree is traversable for
 * future {@code Cache2d} insertion.</p>
 */
class EndHeightmapTest {

    private static final int SEED = 42;
    private static final int SAMPLES = 400;

    private static float x(int i) { return i * 7.3F; }
    private static float z(int i) { return i * 11.1F; }

    // ----- range & determinism --------------------------------------------

    @Test
    void heightStaysAboveSurfaceAndBelowWorldTop() {
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        float surface = map.levels().surface;
        for (int i = 0; i < SAMPLES; i++) {
            float h = map.getHeight(x(i), z(i), SEED);
            assertTrue(Float.isFinite(h), "height not finite: " + h);
            assertTrue(h >= surface - 1e-4f, "height below surface: " + h + " < " + surface);
            assertTrue(h <= 1.0f + 1e-4f, "height above world top: " + h);
        }
    }

    @Test
    void heightIsDeterministic() {
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        for (int i = 0; i < SAMPLES; i++) {
            float a = map.getHeight(x(i), z(i), SEED);
            float b = map.getHeight(x(i), z(i), SEED);
            assertEquals(a, b, 0.0f, "same args should yield same height");
        }
    }

    @Test
    void heightIsSeedSensitive() {
        EndHeightmap a = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndHeightmap b = new EndHeightmap(TestProfile.defaultEnd(), SEED + 1);
        boolean anyDifference = false;
        for (int i = 0; i < SAMPLES; i++) {
            if (Float.floatToIntBits(a.getHeight(x(i), z(i), SEED))
                    != Float.floatToIntBits(b.getHeight(x(i), z(i), SEED + 1))) {
                anyDifference = true;
                break;
            }
        }
        assertTrue(anyDifference, "different seeds should produce different terrain");
    }

    @Test
    void landnessStaysInUnitRange() {
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        for (int i = 0; i < SAMPLES; i++) {
            float l = map.getLandness(x(i), z(i), SEED);
            assertTrue(Float.isFinite(l), "landness not finite: " + l);
            assertTrue(l >= 0.0f - 1e-5f && l <= 1.0f + 1e-5f,
                    "landness out of [0,1]: " + l);
        }
    }

    // ----- composition contract -------------------------------------------

    @Test
    void voidWhereNoLandHasHeightAtSurface() {
        // Where the continent says landness == 0, the terrain product is 0
        // (Multiply short-circuits), so height must be exactly the surface.
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        float surface = map.levels().surface;
        boolean foundVoid = false;
        for (int i = 0; i < SAMPLES; i++) {
            float landness = map.getLandness(x(i), z(i), SEED);
            if (landness <= 0.0f) {
                float h = map.getHeight(x(i), z(i), SEED);
                assertEquals(surface, h, 1e-5f,
                        "void (landness=0) should map to surface, got " + h);
                foundVoid = true;
            }
        }
        assertTrue(foundVoid, "ISLANDS topology should produce at least one void sample");
    }

    @Test
    void terrainProductMatchesContinentTimesMountains() {
        // terrain.compute should equal continent.compute * mountains.compute
        // (the documented composition). Verifies the wiring, not a new
        // composition rule.
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        Noise mountains = EndMountains.mountains2(SEED);
        for (int i = 0; i < SAMPLES; i++) {
            float c = map.continent().compute(x(i), z(i), SEED);
            float m = mountains.compute(x(i), z(i), SEED);
            float terrain = map.terrain().compute(x(i), z(i), SEED);
            if (c <= 0.0f) {
                // Multiply short-circuit: mountains not sampled, terrain == 0
                assertEquals(0.0f, terrain, 0.0f,
                        "terrain should be 0 where continent is 0");
            } else {
                assertEquals(c * m, terrain, 1e-4f,
                        "terrain should equal continent * mountains");
            }
        }
    }

    // ----- topology shape -------------------------------------------------

    @Test
    void islandsTopologyProducesVoidsBetweenIslands() {
        // ISLANDS should have both void (landness 0) and land (landness > 0)
        // across the sample set — scatter=0.5 gates half the cells, and the
        // falloff reaches 0 at island rims.
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        boolean hasVoid = false;
        boolean hasLand = false;
        for (int i = 0; i < SAMPLES; i++) {
            float l = map.getLandness(x(i), z(i), SEED);
            if (l <= 0.0f) hasVoid = true;
            if (l > 0.5f) hasLand = true;
        }
        assertTrue(hasVoid, "ISLANDS should produce void samples");
        assertTrue(hasLand, "ISLANDS should produce solid land samples");
    }

    @Test
    void shatteredTopologyStaysMostlySolid() {
        // CONTINENTAL_SHATTERED carves rifts but keeps solid centres, so the
        // vast majority of samples should be land.riftStrength=0.85 means
        // rifts drop to 0.15, not 0 — but the threshold still leaves most of
        // the field near 1.
        TestProfile profile = new TestProfile(4064, -2032, 0, 0,
                SeaMode.NONE, TopologyMode.CONTINENTAL_SHATTERED, false);
        EndHeightmap map = new EndHeightmap(profile, SEED);
        int solidCount = 0;
        for (int i = 0; i < SAMPLES; i++) {
            if (map.getLandness(x(i), z(i), SEED) > 0.5f) solidCount++;
        }
        assertTrue(solidCount > SAMPLES * 0.5,
                "shattered should be mostly solid, got " + solidCount + "/" + SAMPLES);
    }

    // ----- sea-mode surface selection -------------------------------------

    @Test
    void seaModeAffectsSurfaceBaseline() {
        // NONE: surface = islandBaselineY-derived; WITH_FLOOR: surface = seaLevelY-derived.
        // With baseline=64, sea=0, the two surfaces must differ.
        TestProfile none = new TestProfile(4064, -2032, 0, 64,
                SeaMode.NONE, TopologyMode.ISLANDS, false);
        TestProfile sea = new TestProfile(4064, -2032, 0, 64,
                SeaMode.WITH_FLOOR, TopologyMode.ISLANDS, false);
        EndHeightmap noneMap = new EndHeightmap(none, SEED);
        EndHeightmap seaMap = new EndHeightmap(sea, SEED);
        assertTrue(noneMap.levels().surface > seaMap.levels().surface,
                "NONE surface (baseline=64) should be above WITH_FLOOR surface (sea=0)");
    }

    @Test
    void noneModeSurfaceIsIslandBaseline() {
        TestProfile profile = new TestProfile(4064, -2032, 0, 64,
                SeaMode.NONE, TopologyMode.ISLANDS, false);
        EndHeightmap map = new EndHeightmap(profile, SEED);
        // surfaceFillY = min(64-1, 4064) = 63; surface = 63/4064
        assertEquals(63.0f / 4064, map.levels().surface, 1e-5f);
    }

    // ----- tree traversal -------------------------------------------------

    @Test
    void mapAllReachesAllLeaves() {
        // The terrain tree is continent × mountains2. mountains2 has 4 perlin
        // leaves (worleyEdge driver, warp perlin x2, blur perlin, surface
        // perlinRidge) and the continent has 2 warp-driver perlin leaves.
        // A counting visitor should reach >= 6 Perlin instances.
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        int[] count = {0};
        Noise.Visitor counter = n -> {
            if (n instanceof Perlin) count[0]++;
            return n;
        };
        map.terrain().mapAll(counter);
        assertTrue(count[0] >= 6,
                "mapAll should reach >= 6 Perlin leaves (mountains + continent warp), got " + count[0]);
    }

    @Test
    void mountains1AlsoComposesViaPackagePrivateConstructor() {
        // The package-private constructor lets callers swap the mountain
        // recipe. mountains1 should compose without error and stay in range.
        TestProfile profile = TestProfile.defaultEnd();
        EndHeightmap map = new EndHeightmap(profile, SEED, EndMountains.mountains1(SEED));
        float surface = map.levels().surface;
        for (int i = 0; i < SAMPLES; i++) {
            float h = map.getHeight(x(i), z(i), SEED);
            assertTrue(Float.isFinite(h), "mountains1 height not finite: " + h);
            assertTrue(h >= surface - 1e-4f && h <= 1.0f + 1e-4f,
                    "mountains1 height out of range: " + h);
        }
    }
}
