package endterraforged.world.river;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.SeaMode;
import endterraforged.world.config.TestProfile;
import endterraforged.world.config.TopologyMode;
import endterraforged.world.heightmap.EndHeightmap;
import endterraforged.world.heightmap.EndLevels;

/**
 * Stage-4.3 / 4.4 contract: the river water level is anchored to
 * {@link EndLevels#surface}, and {@code surface} follows
 * {@link endterraforged.world.config.SeaMode} — {@code NONE} → island baseline
 * (river spills into the void at the island edge), {@code WITH_FLOOR} /
 * {@code NO_FLOOR} → sea level (river meets the sea). These tests pin that
 * chain so a future refactor cannot silently break the sea-mode-dependent
 * river outlet.
 */
class EndRiverMapSeaModeTest {

    private static final int SEED = 42;
    private static final int SAMPLES = 800;
    private static final int WORLD_HEIGHT = 4064;
    private static final int MIN_Y = -2032;
    // Pick a sea level clearly above the island baseline so the two surfaces
    // are unambiguously different and the carving-floor comparison is stable.
    private static final int ISLAND_BASELINE_Y = 0;
    private static final int SEA_LEVEL_Y = 200;

    private static float x(int i) { return i * 7.3F; }
    private static float z(int i) { return i * 11.1F; }

    private static TestProfile profile(SeaMode seaMode) {
        return new TestProfile(WORLD_HEIGHT, MIN_Y, SEA_LEVEL_Y, ISLAND_BASELINE_Y,
                seaMode, TopologyMode.ISLANDS, false);
    }

    private static float carve(EndRiverMap rivers, EndHeightmap map, float x, float z, int seed) {
        return rivers.modifyHeight(x, z, seed, map, map.getTerrainHeight(x, z, seed));
    }

    @Test
    void surfaceDiffersBySeaMode() {
        // The contract the river carver indirectly relies on: surfaceY() and
        // therefore EndLevels.surface vary with SeaMode. NONE anchors to the
        // island baseline; the sea modes anchor to sea level.
        EndLevels noneLevels = new EndHeightmap(profile(SeaMode.NONE), SEED).levels();
        EndLevels withFloorLevels = new EndHeightmap(profile(SeaMode.WITH_FLOOR), SEED).levels();
        EndLevels noFloorLevels = new EndHeightmap(profile(SeaMode.NO_FLOOR), SEED).levels();

        // island baseline (0) < sea level (200) → NONE surface is lower.
        assertTrue(noneLevels.surface < withFloorLevels.surface,
                "NONE surface should anchor to island baseline (lower than sea level here)");
        assertEquals(withFloorLevels.surface, noFloorLevels.surface, 0.0F,
                "WITH_FLOOR and NO_FLOOR share the same surface (sea level); they differ in floor, not surface");
    }

    @Test
    void riverCarvingFloorFollowsSurfaceInEverySeaMode() {
        // The bed level at full riverness is `surface - bedDepth*elevationRange`.
        // So across all carving samples the carved height must stay >= that
        // floor (within float epsilon). This holds in every sea mode because
        // the carver reads levels().surface, which SeaMode drives.
        EndRiverMap rivers = new EndRiverMap(380, 1.0F, 12, 90, 0.04F);
        for (SeaMode seaMode : SeaMode.values()) {
            EndHeightmap map = new EndHeightmap(profile(seaMode), SEED);
            EndLevels levels = map.levels();
            float floor = levels.surface - rivers.bedDepth() * levels.elevationRange;
            for (int i = 0; i < SAMPLES; i++) {
                if (map.getLandness(x(i), z(i), SEED) <= 0.0F) continue;
                float carved = carve(rivers, map, x(i), z(i), SEED);
                assertTrue(carved >= floor - 1e-4F,
                        seaMode + " carved height " + carved + " below floor " + floor);
            }
        }
    }

    @Test
    void noneCarvesDeeperThanWithFloorWhenIslandBaselineBelowSea() {
        // With island baseline (0) < sea level (200), the NONE surface is
        // lower, so NONE rivers can carve deeper than WITH_FLOOR rivers.
        // Sampling the minimum carved land height in each mode should show
        // NONE min <= WITH_FLOOR min (rivers reach a lower outlet in NONE).
        EndRiverMap rivers = new EndRiverMap(380, 1.0F, 12, 90, 0.04F);
        EndHeightmap noneMap = new EndHeightmap(profile(SeaMode.NONE), SEED);
        EndHeightmap withFloorMap = new EndHeightmap(profile(SeaMode.WITH_FLOOR), SEED);

        float noneMin = Float.MAX_VALUE;
        float withFloorMin = Float.MAX_VALUE;
        for (int i = 0; i < SAMPLES; i++) {
            if (noneMap.getLandness(x(i), z(i), SEED) <= 0.0F) continue;
            noneMin = Math.min(noneMin, carve(rivers, noneMap, x(i), z(i), SEED));
            withFloorMin = Math.min(withFloorMin, carve(rivers, withFloorMap, x(i), z(i), SEED));
        }
        assertTrue(noneMin < Float.MAX_VALUE, "NONE should have carved land samples");
        assertTrue(withFloorMin < Float.MAX_VALUE, "WITH_FLOOR should have carved land samples");
        assertTrue(noneMin < withFloorMin,
                "NONE rivers should reach a lower outlet than WITH_FLOOR when island baseline < sea level: "
                        + noneMin + " vs " + withFloorMin);
    }

    @Test
    void noFloorBehavesLikeWithFloorForRiverSurface() {
        // NO_FLOOR and WITH_FLOOR share the same surface (sea level); they
        // differ only in column floor (WITH_FLOOR fills seabed, NO_FLOOR
        // voids below surface). The river carver only reads surface, so its
        // output must be identical between the two sea modes.
        EndRiverMap rivers = EndRiverMap.defaults();
        EndHeightmap noFloorMap = new EndHeightmap(profile(SeaMode.NO_FLOOR), SEED);
        EndHeightmap withFloorMap = new EndHeightmap(profile(SeaMode.WITH_FLOOR), SEED);
        for (int i = 0; i < SAMPLES; i++) {
            float a = carve(rivers, noFloorMap, x(i), z(i), SEED);
            float b = carve(rivers, withFloorMap, x(i), z(i), SEED);
            assertEquals(a, b, 1e-5F,
                    "NO_FLOOR and WITH_FLOOR should produce identical river carving (same surface)");
        }
    }

    @Test
    void riverCarvingRespectsLevelsSurfaceNotHardcodedConstant() {
        // Regression guard: if someone hardcodes a sea-level constant in the
        // carver instead of reading levels().surface, NONE mode (no sea) would
        // break. Move the surface up by lifting islandBaselineY and verify the
        // carving floor moves with it.
        EndRiverMap rivers = new EndRiverMap(380, 1.0F, 12, 90, 0.04F);
        TestProfile lowBaseline = new TestProfile(WORLD_HEIGHT, MIN_Y, SEA_LEVEL_Y,
                0, SeaMode.NONE, TopologyMode.ISLANDS, false);
        TestProfile highBaseline = new TestProfile(WORLD_HEIGHT, MIN_Y, SEA_LEVEL_Y,
                400, SeaMode.NONE, TopologyMode.ISLANDS, false);
        EndHeightmap lowMap = new EndHeightmap(lowBaseline, SEED);
        EndHeightmap highMap = new EndHeightmap(highBaseline, SEED);

        float lowMin = Float.MAX_VALUE;
        float highMin = Float.MAX_VALUE;
        for (int i = 0; i < SAMPLES; i++) {
            if (lowMap.getLandness(x(i), z(i), SEED) <= 0.0F) continue;
            lowMin = Math.min(lowMin, carve(rivers, lowMap, x(i), z(i), SEED));
            highMin = Math.min(highMin, carve(rivers, highMap, x(i), z(i), SEED));
        }
        assertTrue(lowMin < Float.MAX_VALUE && highMin < Float.MAX_VALUE,
                "both baselines should have carved land samples");
        assertTrue(highMin > lowMin,
                "raising islandBaselineY should lift the river carving floor (surface-anchored, not hardcoded): "
                        + highMin + " vs " + lowMin);
    }
}
