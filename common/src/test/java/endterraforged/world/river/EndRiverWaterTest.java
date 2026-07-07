package endterraforged.world.river;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.TestProfile;
import endterraforged.world.heightmap.EndHeightmap;

/**
 * Contract tests for {@link EndRiverWater} — the stage-4.7 water placer.
 *
 * <p>These pin the water-band contract that the stage-5 surface system will
 * consume, without a running chunk generator:
 * <ul>
 *   <li>no-op configs (riverChance=0, cellSize=0, void) yield no water;</li>
 *   <li>water appears only at bed-centre columns (riverness==1);</li>
 *   <li>the water floor sits below the water surface (water sits in the carved
 *       channel, not on top of terrain);</li>
 *   <li>the water level is bounded by the dimension's surface and world top;</li>
 *   <li>{@code waterInfo}'s top/bottom match {@link EndRiverMap#waterLevel} and
 *       {@link EndHeightmap#getHeight} at bed-centre columns;</li>
 *   <li>waterfall detection requires riverness and is monotone in the drop
 *       threshold (a lower threshold never removes a waterfall);</li>
 *   <li>the placer fails fast when the heightmap does not carry its riverMap.</li>
 * </ul>
 *
 * <p>Tests that expect water attach the river map to the heightmap via
 * {@link EndHeightmap#withRivers} so {@link EndHeightmap#getHeight} returns the
 * carved bed floor — without that, the floor would be raw terrain and the
 * "floor below surface" contract could not hold.</p>
 */
class EndRiverWaterTest {

    private static final int SEED = 42;
    private static final int SAMPLES = 400;

    private static float x(int i) { return i * 7.3F; }
    private static float z(int i) { return i * 11.1F; }

    /**
     * A river map with riverChance=1 and a wide bed, so a coarse sample grid
     * reliably hits bed-centre columns (riverness==1). Used wherever a test
     * needs at least one water column to exist.
     */
    private static EndRiverMap denseRivers() {
        return new EndRiverMap(380.0F, 1.0F, 40.0F, 90.0F, 0.04F);
    }

    /** Heightmap with dense rivers attached, so getHeight returns the carved bed. */
    private static EndHeightmap carvedMap(EndRiverMap rivers) {
        return new EndHeightmap(TestProfile.defaultEnd(), SEED).withRivers(rivers);
    }

    // ----- no-op cases ----------------------------------------------------

    @Test
    void riverChanceZeroYieldsNoWater() {
        EndRiverMap rivers = new EndRiverMap(380, 0.0F, 40, 90, 0.04F);
        EndHeightmap map = carvedMap(rivers);
        EndRiverWater water = EndRiverWater.defaults(rivers);
        for (int i = 0; i < SAMPLES; i++) {
            assertFalse(water.waterInfo(x(i), z(i), SEED, map).hasWater(),
                    "riverChance=0 must place no water");
        }
    }

    @Test
    void degenerateCellSizeYieldsNoWater() {
        // cellSize=0 would make invCell=Inf; the placer guards it to a no-op.
        EndRiverMap rivers = new EndRiverMap(0.0F, 1.0F, 40, 90, 0.04F);
        EndHeightmap map = carvedMap(rivers);
        EndRiverWater water = EndRiverWater.defaults(rivers);
        for (int i = 0; i < SAMPLES; i++) {
            assertFalse(water.waterInfo(x(i), z(i), SEED, map).hasWater(),
                    "degenerate cellSize must place no water");
        }
    }

    @Test
    void voidColumnYieldsNoWater() {
        EndRiverMap rivers = denseRivers();
        EndHeightmap map = carvedMap(rivers);
        EndRiverWater water = EndRiverWater.defaults(rivers);
        for (int i = 0; i < SAMPLES; i++) {
            if (map.getLandness(x(i), z(i), SEED) <= 0.0F) {
                assertFalse(water.waterInfo(x(i), z(i), SEED, map).hasWater(),
                        "void columns must not host water");
            }
        }
    }

    // ----- placement contract --------------------------------------------

    @Test
    void waterOnlyAtFullRiverness() {
        // hasWater() implies the column is at bed centre (riverness==1).
        EndRiverMap rivers = denseRivers();
        EndHeightmap map = carvedMap(rivers);
        EndRiverWater water = EndRiverWater.defaults(rivers);
        for (int i = 0; i < SAMPLES; i++) {
            EndRiverWater.WaterInfo info = water.waterInfo(x(i), z(i), SEED, map);
            if (info.hasWater()) {
                assertEquals(1.0F, rivers.rivernessAt(x(i), z(i), SEED, map), 0.0F,
                        "water must only appear at bed centre (riverness==1)");
            }
        }
    }

    @Test
    void waterBottomBelowWaterTop() {
        // Wherever water is placed, the floor must sit below the surface —
        // water occupies the carved channel, not the terrain top.
        EndRiverMap rivers = denseRivers();
        EndHeightmap map = carvedMap(rivers);
        EndRiverWater water = EndRiverWater.defaults(rivers);
        int waterCount = 0;
        for (int i = 0; i < SAMPLES; i++) {
            EndRiverWater.WaterInfo info = water.waterInfo(x(i), z(i), SEED, map);
            if (info.hasWater()) {
                waterCount++;
                assertTrue(info.waterBottom() < info.waterTop(),
                        "water floor must be below water surface: " + info);
            }
        }
        assertTrue(waterCount > 0,
                "dense rivers (chance=1, bedWidth=40) should place at least some water");
    }

    @Test
    void waterLevelBoundedBySurfaceAndOne() {
        EndRiverMap rivers = denseRivers();
        EndHeightmap map = carvedMap(rivers);
        float surface = map.levels().surface;
        for (int i = 0; i < SAMPLES; i++) {
            float level = rivers.waterLevel(x(i), z(i), SEED, map);
            if (!Float.isNaN(level)) {
                assertTrue(level >= surface - 1e-4F,
                        "water level below surface: " + level + " < " + surface);
                assertTrue(level <= 1.0F + 1e-4F,
                        "water level above world top: " + level);
            }
        }
    }

    @Test
    void waterTopMatchesRiverMapWaterLevel() {
        EndRiverMap rivers = denseRivers();
        EndHeightmap map = carvedMap(rivers);
        EndRiverWater water = EndRiverWater.defaults(rivers);
        for (int i = 0; i < SAMPLES; i++) {
            EndRiverWater.WaterInfo info = water.waterInfo(x(i), z(i), SEED, map);
            if (info.hasWater()) {
                assertEquals(rivers.waterLevel(x(i), z(i), SEED, map),
                        info.waterTop(), 0.0F,
                        "waterInfo.waterTop must equal riverMap.waterLevel at bed centre");
            }
        }
    }

    @Test
    void waterBottomMatchesCarvedTerrain() {
        EndRiverMap rivers = denseRivers();
        EndHeightmap map = carvedMap(rivers);
        EndRiverWater water = EndRiverWater.defaults(rivers);
        for (int i = 0; i < SAMPLES; i++) {
            EndRiverWater.WaterInfo info = water.waterInfo(x(i), z(i), SEED, map);
            if (info.hasWater()) {
                assertEquals(map.getHeight(x(i), z(i), SEED),
                        info.waterBottom(), 0.0F,
                        "waterInfo.waterBottom must equal the carved terrain height");
            }
        }
    }

    // ----- determinism ----------------------------------------------------

    @Test
    void waterInfoIsDeterministic() {
        EndRiverMap rivers = denseRivers();
        EndHeightmap map = carvedMap(rivers);
        EndRiverWater water = EndRiverWater.defaults(rivers);
        for (int i = 0; i < SAMPLES; i++) {
            EndRiverWater.WaterInfo a = water.waterInfo(x(i), z(i), SEED, map);
            EndRiverWater.WaterInfo b = water.waterInfo(x(i), z(i), SEED, map);
            assertEquals(a.waterTop(), b.waterTop(), 0.0F, "waterTop must be deterministic");
            assertEquals(a.waterBottom(), b.waterBottom(), 0.0F, "waterBottom must be deterministic");
            assertEquals(a.isWaterfall(), b.isWaterfall(), "isWaterfall must be deterministic");
        }
    }

    // ----- waterfalls -----------------------------------------------------

    @Test
    void waterfallRequiresRiverness() {
        // riverChance=0 → riverness 0 everywhere → never a waterfall, even
        // though the terrain itself may have cliffs.
        EndRiverMap rivers = new EndRiverMap(380, 0.0F, 40, 90, 0.04F);
        EndHeightmap map = carvedMap(rivers);
        EndRiverWater water = EndRiverWater.defaults(rivers);
        for (int i = 0; i < SAMPLES; i++) {
            assertFalse(water.isWaterfall(x(i), z(i), SEED, map),
                    "a non-river column must not be flagged a waterfall");
        }
    }

    @Test
    void waterfallFlagInsideWaterInfoIsConsistent() {
        // Where water is placed, waterInfo.isWaterfall must match a direct
        // isWaterfall() call for the same column.
        EndRiverMap rivers = denseRivers();
        EndHeightmap map = carvedMap(rivers);
        EndRiverWater water = EndRiverWater.defaults(rivers);
        for (int i = 0; i < SAMPLES; i++) {
            EndRiverWater.WaterInfo info = water.waterInfo(x(i), z(i), SEED, map);
            if (info.hasWater()) {
                assertEquals(water.isWaterfall(x(i), z(i), SEED, map),
                        info.isWaterfall(),
                        "waterInfo.isWaterfall must match isWaterfall() at bed centre");
            }
        }
    }

    @Test
    void zeroDropThresholdNeverRemovesWaterfalls() {
        // Lowering the drop threshold is monotone: every column flagged a
        // waterfall at the default threshold is still flagged at threshold 0.
        EndRiverMap rivers = denseRivers();
        EndHeightmap map = carvedMap(rivers);
        EndRiverWater defaultDrop = EndRiverWater.defaults(rivers);
        EndRiverWater zeroDrop = new EndRiverWater(rivers, 0.0F, 16.0F);
        for (int i = 0; i < SAMPLES; i++) {
            boolean wfDefault = defaultDrop.isWaterfall(x(i), z(i), SEED, map);
            boolean wfZero = zeroDrop.isWaterfall(x(i), z(i), SEED, map);
            assertTrue(!wfDefault || wfZero,
                    "zero threshold must not remove waterfalls (column " + i + ")");
        }
    }

    // ----- fail-fast contract (audit fix) --------------------------------

    @Test
    void waterInfoFailsFastWhenRiverMapNotAttached() {
        // A heightmap built WITHOUT withRivers has riverMap()==null, so the
        // placer must throw rather than silently place water on raw terrain.
        EndRiverMap rivers = denseRivers();
        EndHeightmap uncarvedMap = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndRiverWater water = EndRiverWater.defaults(rivers);
        assertThrows(IllegalStateException.class,
                () -> water.waterInfo(x(0), z(0), SEED, uncarvedMap),
                "waterInfo must fail fast when riverMap is not attached to the heightmap");
    }
}
