package endterraforged.world.lake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.SeaMode;
import endterraforged.world.config.TestProfile;
import endterraforged.world.config.TopologyMode;
import endterraforged.world.heightmap.EndHeightmap;

/**
 * Contract tests for {@link EndLakeMap} basin carving.
 *
 * <p>Pins the carving contract: lakes only lower terrain (never raise),
 * {@code lakeChance=0} is a no-op, {@code lakeChance=1} produces visible
 * carving, void is untouched, output is deterministic and seed-sensitive, and
 * the lake level is locally referenced (a lake on a high island sits high, not
 * at sea level).</p>
 */
class EndLakeMapTest {

    private static final int SEED = 42;
    private static final int SAMPLES = 600;

    private static float x(int i) { return i * 7.3F; }
    private static float z(int i) { return i * 11.1F; }

    /** Runs the lake carver with raw terrain as the upstream height. */
    private static float carve(EndLakeMap lakes, EndHeightmap map, float x, float z, int seed) {
        return lakes.modifyHeight(x, z, seed, map, map.getTerrainHeight(x, z, seed));
    }

    // ----- no-op cases ----------------------------------------------------

    @Test
    void lakeChanceZeroIsNoOp() {
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndLakeMap lakes = new EndLakeMap(620, 0.0F, 28, 75, 0.06F);
        for (int i = 0; i < SAMPLES; i++) {
            float original = map.getTerrainHeight(x(i), z(i), SEED);
            float carved = carve(lakes, map, x(i), z(i), SEED);
            assertEquals(original, carved, 0.0F,
                    "lakeChance=0 must not modify terrain");
        }
    }

    @Test
    void voidIsNotCarved() {
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndLakeMap lakes = EndLakeMap.defaults();
        for (int i = 0; i < SAMPLES; i++) {
            if (map.getLandness(x(i), z(i), SEED) <= 0.0F) {
                float original = map.getTerrainHeight(x(i), z(i), SEED);
                float carved = carve(lakes, map, x(i), z(i), SEED);
                assertEquals(original, carved, 0.0F,
                        "void must not be carved");
            }
        }
    }

    // ----- carving contract -----------------------------------------------

    @Test
    void carvedHeightNeverExceedsOriginal() {
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndLakeMap lakes = EndLakeMap.defaults();
        for (int i = 0; i < SAMPLES; i++) {
            float original = map.getTerrainHeight(x(i), z(i), SEED);
            float carved = carve(lakes, map, x(i), z(i), SEED);
            assertTrue(carved <= original + 1e-5F,
                    "carved height should not exceed original: " + carved + " > " + original);
        }
    }

    @Test
    void lakeChanceOneProducesVisibleCarving() {
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndLakeMap lakes = new EndLakeMap(620, 1.0F, 28, 75, 0.06F);
        int carvedCount = 0;
        int landCount = 0;
        for (int i = 0; i < SAMPLES; i++) {
            if (map.getLandness(x(i), z(i), SEED) > 0.0F) {
                landCount++;
                float original = map.getTerrainHeight(x(i), z(i), SEED);
                float carved = carve(lakes, map, x(i), z(i), SEED);
                if (carved < original - 1e-4F) {
                    carvedCount++;
                }
            }
        }
        assertTrue(landCount > 0, "should have some land samples");
        assertTrue(carvedCount > 0,
                "lakeChance=1 should carve at least some land, got 0/" + landCount);
    }

    @Test
    void carvedHeightBoundedBelowByCenterMinusDepth() {
        // The lake floor at full lakerness is centerHeight - depth*elevRange.
        // With partial lakerness the floor interpolates, so the absolute lower
        // bound is (any land sample's carved) >= surface - depth*elevRange
        // (centre terrain >= surface, so lakeLevel >= surface - depth*elevRange).
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndLakeMap lakes = EndLakeMap.defaults();
        float surface = map.levels().surface;
        float depthAbs = lakes.depth() * map.levels().elevationRange;
        for (int i = 0; i < SAMPLES; i++) {
            float carved = carve(lakes, map, x(i), z(i), SEED);
            assertTrue(carved >= surface - depthAbs - 1e-4F,
                    "carved height below surface-depth: " + carved);
        }
    }

    // ----- determinism ----------------------------------------------------

    @Test
    void modifyHeightIsDeterministic() {
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndLakeMap lakes = EndLakeMap.defaults();
        for (int i = 0; i < SAMPLES; i++) {
            float a = carve(lakes, map, x(i), z(i), SEED);
            float b = carve(lakes, map, x(i), z(i), SEED);
            assertEquals(a, b, 0.0F, "same args should yield same carved height");
        }
    }

    @Test
    void modifyHeightIsSeedSensitive() {
        EndHeightmap mapA = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndHeightmap mapB = new EndHeightmap(TestProfile.defaultEnd(), SEED + 1);
        EndLakeMap lakes = EndLakeMap.defaults();
        boolean anyDifference = false;
        for (int i = 0; i < SAMPLES; i++) {
            float a = carve(lakes, mapA, x(i), z(i), SEED);
            float b = carve(lakes, mapB, x(i), z(i), SEED + 1);
            if (Float.floatToIntBits(a) != Float.floatToIntBits(b)) {
                anyDifference = true;
                break;
            }
        }
        assertTrue(anyDifference, "different seeds should produce different lake carving");
    }

    // ----- lakerness falloff ---------------------------------------------

    @Test
    void lakernessIsOneInsideBed() {
        EndLakeMap lakes = new EndLakeMap(620, 1.0F, 28, 75, 0.06F);
        assertEquals(1.0F, lakes.lakerness(0.0F), 0.0F, "lakerness at distance 0 = 1");
        assertEquals(1.0F, lakes.lakerness(28.0F), 0.0F, "lakerness at bedRadius = 1");
    }

    @Test
    void lakernessIsZeroAtValleyEdge() {
        EndLakeMap lakes = new EndLakeMap(620, 1.0F, 28, 75, 0.06F);
        assertEquals(0.0F, lakes.lakerness(75.0F), 0.0F, "lakerness at valleyRadius = 0");
        assertEquals(0.0F, lakes.lakerness(100.0F), 0.0F, "lakerness past valleyRadius = 0");
    }

    @Test
    void lakernessDecreasesMonotonically() {
        EndLakeMap lakes = new EndLakeMap(620, 1.0F, 28, 75, 0.06F);
        float prev = 1.0F;
        for (float d = 28; d <= 75; d += 5) {
            float r = lakes.lakerness(d);
            assertTrue(r <= prev + 1e-5F, "lakerness should decrease: d=" + d + " r=" + r);
            prev = r;
        }
    }

    // ----- integration: shattered continent + chaining -------------------

    @Test
    void worksWithShatteredContinent() {
        TestProfile profile = new TestProfile(4064, -2032, 0, 0,
                SeaMode.NONE, TopologyMode.CONTINENTAL_SHATTERED, false);
        EndHeightmap map = new EndHeightmap(profile, SEED);
        EndLakeMap lakes = EndLakeMap.defaults();
        float surface = map.levels().surface;
        for (int i = 0; i < SAMPLES; i++) {
            float carved = carve(lakes, map, x(i), z(i), SEED);
            assertTrue(Float.isFinite(carved), "carved height not finite");
            assertTrue(carved >= surface - 0.2F,
                    "carved height way below surface: " + carved);
        }
    }

    @Test
    void lakeLevelIsLocallyReferencedNotSeaAnchored() {
        // Two islands at very different heights should both host lakes whose
        // floor tracks the local terrain, not a global sea level. We can't
        // easily isolate two islands in one profile, but we can assert the
        // weaker contract: where carving happens, the carved height depends on
        // the LOCAL centre height (raw terrain at the lake centre), so carving
        // a high-altitude sample and a low-altitude sample yields floors that
        // differ by roughly the centre-height difference — not by a constant
        // sea level. This is a smoke test for the "no sea" design.
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndLakeMap lakes = new EndLakeMap(620, 1.0F, 28, 75, 0.06F);
        float minOriginal = Float.MAX_VALUE;
        float maxOriginal = -Float.MAX_VALUE;
        float minCarved = Float.MAX_VALUE;
        float maxCarved = -Float.MAX_VALUE;
        for (int i = 0; i < SAMPLES; i++) {
            if (map.getLandness(x(i), z(i), SEED) <= 0.0F) continue;
            float original = map.getTerrainHeight(x(i), z(i), SEED);
            float carved = carve(lakes, map, x(i), z(i), SEED);
            if (carved < original - 1e-4F) {  // only where carving happened
                minOriginal = Math.min(minOriginal, original);
                maxOriginal = Math.max(maxOriginal, original);
                minCarved = Math.min(minCarved, carved);
                maxCarved = Math.max(maxCarved, carved);
            }
        }
        // If every lake floor were sea-anchored, (maxCarved - minCarved) would
        // be ~0. Locally-referenced floors track local terrain, so the carved
        // range should be a substantial fraction of the original range.
        assertTrue(maxCarved > minCarved,
                "lake floors should vary with local terrain, not be sea-anchored constant");
        float carvedRange = maxCarved - minCarved;
        float originalRange = maxOriginal - minOriginal;
        assertTrue(carvedRange > 0.1F * originalRange,
                "lake floor range " + carvedRange + " should track local terrain range "
                        + originalRange + " (locally referenced, not sea-anchored)");
    }

    // ----- bug regression: B4 lake level never below surface, B7 degenerate cellSize

    @Test
    void lakeLevelNeverBelowSurface() {
        // Regression for B4: lakeLevel = centerHeight - depth*elevRange could
        // drop below surface on low ground → in SeaMode.NONE that becomes a
        // void pit instead of open water. Now clamped to >= surface.
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndLakeMap lakes = new EndLakeMap(620, 1.0F, 28, 75, 0.06F);
        float surface = map.levels().surface;
        for (int i = 0; i < SAMPLES; i++) {
            if (map.getLandness(x(i), z(i), SEED) <= 0.0F) continue;
            float carved = carve(lakes, map, x(i), z(i), SEED);
            assertTrue(carved >= surface - 1e-4F,
                    "lake-carved height must not drop below surface (void threshold): " + carved);
        }
    }

    @Test
    void degenerateCellSizeIsNoOp() {
        // Regression for B7: cellSize=0 would make invCell=Inf and poison the
        // worley scan with NaN. Now no-op.
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndLakeMap lakes = new EndLakeMap(0.0F, 1.0F, 28, 75, 0.06F);
        for (int i = 0; i < SAMPLES; i++) {
            float raw = map.getTerrainHeight(x(i), z(i), SEED);
            float carved = carve(lakes, map, x(i), z(i), SEED);
            assertEquals(raw, carved, 0.0F,
                    "cellSize=0 must be no-op (not NaN)");
        }
    }
}
