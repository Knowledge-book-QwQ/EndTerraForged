package endterraforged.world.river;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.SeaMode;
import endterraforged.world.config.TestProfile;
import endterraforged.world.config.TopologyMode;
import endterraforged.world.heightmap.EndHeightmap;

/**
 * Contract tests for {@link River} geometry and {@link EndRiverMap} valley
 * carving.
 *
 * <p>River tests pin the distance / projection math against hand-computed
 * values. EndRiverMap tests assert the carving contract: rivers only lower
 * terrain (never raise), {@code riverChance=0} is a no-op, {@code riverChance=1}
 * produces visible carving, void is untouched, and the output is deterministic.
 * No magic numbers for the carver — only the contract that EndHeightmap and
 * stage-4.2 will rely on.</p>
 */
class EndRiverMapTest {

    private static final int SEED = 42;
    private static final int SAMPLES = 400;

    private static float x(int i) { return i * 7.3F; }
    private static float z(int i) { return i * 11.1F; }

    // ----- River geometry --------------------------------------------------

    @Test
    void riverDistanceToMidpoint() {
        // Segment from (0,0) to (10,0): midpoint (5,0) has distance 0.
        River r = River.of(0, 0, 10, 0);
        assertEquals(0.0F, r.distanceTo(5, 0), 1e-4f, "midpoint distance should be 0");
    }

    @Test
    void riverDistanceToPerpendicular() {
        // Segment (0,0)->(10,0), point (5, 3): perpendicular distance = 3.
        River r = River.of(0, 0, 10, 0);
        assertEquals(3.0F, r.distanceTo(5, 3), 1e-4f, "perpendicular distance should be 3");
    }

    @Test
    void riverDistanceClampsBeyondEnd() {
        // Segment (0,0)->(10,0), point (15, 0): clamped to endpoint, distance = 5.
        River r = River.of(0, 0, 10, 0);
        assertEquals(5.0F, r.distanceTo(15, 0), 1e-4f, "distance past end should clamp to endpoint");
    }

    @Test
    void riverDistanceClampsBeforeStart() {
        // Segment (0,0)->(10,0), point (-3, 4): clamped to start, distance = 5.
        River r = River.of(0, 0, 10, 0);
        assertEquals(5.0F, r.distanceTo(-3, 4), 1e-4f, "distance before start should clamp to startpoint");
    }

    @Test
    void riverProjectionAtStart() {
        River r = River.of(0, 0, 10, 0);
        assertEquals(0.0F, r.projection(0, 0), 1e-4f, "projection at start = 0");
    }

    @Test
    void riverProjectionAtEnd() {
        River r = River.of(0, 0, 10, 0);
        assertEquals(1.0F, r.projection(10, 0), 1e-4f, "projection at end = 1");
    }

    @Test
    void riverProjectionAtMidpoint() {
        River r = River.of(0, 0, 10, 0);
        assertEquals(0.5F, r.projection(5, 0), 1e-4f, "projection at midpoint = 0.5");
    }

    @Test
    void riverZeroLengthIsSafe() {
        // Zero-length segment must not divide by zero.
        River r = River.of(5, 5, 5, 5);
        assertEquals(0.0F, r.projection(5, 5), 0.0f, "zero-length projection should not NaN");
        assertTrue(Float.isFinite(r.distanceTo(5, 5)), "zero-length distance should be finite");
    }

    // ----- EndRiverMap: no-op cases ---------------------------------------

    @Test
    void riverChanceZeroIsNoOp() {
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndRiverMap rivers = new EndRiverMap(380, 0.0F, 12, 90, 0.04F);
        for (int i = 0; i < SAMPLES; i++) {
            float original = map.getHeight(x(i), z(i), SEED);
            float carved = rivers.modifyHeight(x(i), z(i), SEED, map);
            assertEquals(original, carved, 0.0F,
                    "riverChance=0 must not modify terrain");
        }
    }

    @Test
    void voidIsNotCarved() {
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndRiverMap rivers = EndRiverMap.defaults();
        for (int i = 0; i < SAMPLES; i++) {
            if (map.getLandness(x(i), z(i), SEED) <= 0.0F) {
                float original = map.getHeight(x(i), z(i), SEED);
                float carved = rivers.modifyHeight(x(i), z(i), SEED, map);
                assertEquals(original, carved, 0.0F,
                        "void must not be carved");
            }
        }
    }

    // ----- EndRiverMap: carving contract ----------------------------------

    @Test
    void carvedHeightNeverExceedsOriginal() {
        // Rivers only lower terrain (lerp toward a lower bed), never raise it.
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndRiverMap rivers = EndRiverMap.defaults();
        for (int i = 0; i < SAMPLES; i++) {
            float original = map.getHeight(x(i), z(i), SEED);
            float carved = rivers.modifyHeight(x(i), z(i), SEED, map);
            assertTrue(carved <= original + 1e-5f,
                    "carved height should not exceed original: " + carved + " > " + original);
        }
    }

    @Test
    void carvedHeightStaysAboveSurface() {
        // The bed meets the surface at t=1 but should not go below it (bedDepth
        // is a fraction of elevationRange, and surface + 0 - bedDepth*elevRange
        // could dip slightly below surface; allow that small margin).
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndRiverMap rivers = EndRiverMap.defaults();
        float surface = map.levels().surface;
        float bedDepthAbs = rivers.bedDepth() * map.levels().elevationRange;
        for (int i = 0; i < SAMPLES; i++) {
            float carved = rivers.modifyHeight(x(i), z(i), SEED, map);
            assertTrue(carved >= surface - bedDepthAbs - 1e-4f,
                    "carved height below surface-bedDepth: " + carved);
        }
    }

    @Test
    void riverChanceOneProducesVisibleCarving() {
        // With riverChance=1, every cell has a river, so a good fraction of
        // land samples should be carved (height < original).
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndRiverMap rivers = new EndRiverMap(380, 1.0F, 12, 90, 0.04F);
        int carvedCount = 0;
        int landCount = 0;
        for (int i = 0; i < SAMPLES; i++) {
            if (map.getLandness(x(i), z(i), SEED) > 0.0F) {
                landCount++;
                float original = map.getHeight(x(i), z(i), SEED);
                float carved = rivers.modifyHeight(x(i), z(i), SEED, map);
                if (carved < original - 1e-4f) {
                    carvedCount++;
                }
            }
        }
        assertTrue(landCount > 0, "should have some land samples");
        assertTrue(carvedCount > 0,
                "riverChance=1 should carve at least some land, got 0/" + landCount);
    }

    // ----- EndRiverMap: determinism ---------------------------------------

    @Test
    void modifyHeightIsDeterministic() {
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndRiverMap rivers = EndRiverMap.defaults();
        for (int i = 0; i < SAMPLES; i++) {
            float a = rivers.modifyHeight(x(i), z(i), SEED, map);
            float b = rivers.modifyHeight(x(i), z(i), SEED, map);
            assertEquals(a, b, 0.0F, "same args should yield same carved height");
        }
    }

    @Test
    void modifyHeightIsSeedSensitive() {
        EndHeightmap mapA = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndHeightmap mapB = new EndHeightmap(TestProfile.defaultEnd(), SEED + 1);
        EndRiverMap rivers = EndRiverMap.defaults();
        boolean anyDifference = false;
        for (int i = 0; i < SAMPLES; i++) {
            float a = rivers.modifyHeight(x(i), z(i), SEED, mapA);
            float b = rivers.modifyHeight(x(i), z(i), SEED + 1, mapB);
            if (Float.floatToIntBits(a) != Float.floatToIntBits(b)) {
                anyDifference = true;
                break;
            }
        }
        assertTrue(anyDifference, "different seeds should produce different river carving");
    }

    // ----- EndRiverMap: riverness falloff ---------------------------------

    @Test
    void rivernessIsOneInsideBed() {
        EndRiverMap rivers = new EndRiverMap(380, 1.0F, 12, 90, 0.04F);
        assertEquals(1.0F, rivers.riverness(0.0F), 0.0F, "riverness at distance 0 = 1");
        assertEquals(1.0F, rivers.riverness(12.0F), 0.0F, "riverness at bedWidth = 1");
    }

    @Test
    void rivernessIsZeroAtValleyEdge() {
        EndRiverMap rivers = new EndRiverMap(380, 1.0F, 12, 90, 0.04F);
        assertEquals(0.0F, rivers.riverness(90.0F), 0.0F, "riverness at valleyWidth = 0");
        assertEquals(0.0F, rivers.riverness(100.0F), 0.0F, "riverness past valleyWidth = 0");
    }

    @Test
    void rivernessDecreasesMonotonically() {
        // Between bedWidth and valleyWidth, riverness should decrease.
        EndRiverMap rivers = new EndRiverMap(380, 1.0F, 12, 90, 0.04F);
        float prev = 1.0F;
        for (float d = 12; d <= 90; d += 5) {
            float r = rivers.riverness(d);
            assertTrue(r <= prev + 1e-5f, "riverness should decrease: d=" + d + " r=" + r);
            prev = r;
        }
    }

    // ----- integration: shattered continent also works --------------------

    @Test
    void worksWithShatteredContinent() {
        TestProfile profile = new TestProfile(4064, -2032, 0, 0,
                SeaMode.NONE, TopologyMode.CONTINENTAL_SHATTERED, false);
        EndHeightmap map = new EndHeightmap(profile, SEED);
        EndRiverMap rivers = EndRiverMap.defaults();
        float surface = map.levels().surface;
        for (int i = 0; i < SAMPLES; i++) {
            float carved = rivers.modifyHeight(x(i), z(i), SEED, map);
            assertTrue(Float.isFinite(carved), "carved height not finite");
            assertTrue(carved >= surface - 0.2F,
                    "carved height way below surface: " + carved);
        }
    }
}
