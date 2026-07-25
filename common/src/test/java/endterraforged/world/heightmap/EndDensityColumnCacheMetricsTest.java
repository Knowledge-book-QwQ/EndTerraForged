package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetDevelopmentProfiles;

class EndDensityColumnCacheMetricsTest {
    private static final int SEED = 123456789;
    private static final int COLUMN_COUNT = 16 * 16;
    private static final int[] SAMPLE_Y = {-256, -64, 0, 128, 255};

    @Test
    void reportsReuseAndCollisionsWithoutChangingDensityBits() {
        String property = EndPresetDevelopmentProfiles.P46_ARCHIPELAGO_SMOKE_TEST_PROPERTY;
        String previous = System.getProperty(property);
        System.setProperty(property, "true");
        EndDensity.configureColumnCacheMetrics(true);
        EndHeightmap.configureTerrainMetrics(true);
        try {
            EndPreset smoke = EndPresetDevelopmentProfiles.defaultFallback();
            EndDensity density = new EndDensity(new EndHeightmap(smoke, SEED));

            long orderedChecksum = sample(density, false);
            EndDensity.ColumnCacheMetrics ordered = EndDensity.columnCacheMetrics();
            EndHeightmap.TerrainMetrics orderedTerrain = EndHeightmap.terrainMetrics();

            EndDensity.configureColumnCacheMetrics(true);
            EndHeightmap.configureTerrainMetrics(true);
            long shuffledChecksum = sample(density, true);
            EndDensity.ColumnCacheMetrics shuffled = EndDensity.columnCacheMetrics();
            EndHeightmap.TerrainMetrics shuffledTerrain = EndHeightmap.terrainMetrics();

            assertEquals(orderedChecksum, shuffledChecksum,
                    "density bits must be independent of column access order");
            assertEquals(COLUMN_COUNT * SAMPLE_Y.length, ordered.requests());
            assertEquals(ordered.requests(), shuffled.requests());
            assertTrue(ordered.hits() > 0, "ordered traversal should reuse columns across Y");
            assertTrue(ordered.misses() > 0, "ordered traversal should build columns");
            assertTrue(ordered.collisions() > 0, "fixture should expose direct-map collisions");
            assertEquals(ordered.collisions(), ordered.evictions());
            assertTrue(ordered.fullColumnRefreshes() > 0);
            assertEquals(ordered.fullColumnRefreshes(), ordered.heightEvaluations(),
                    "each land-column refresh should perform one complete height evaluation");
            assertEquals(ordered.heightEvaluations(), shuffled.heightEvaluations(),
                    "height evaluation count must be independent of column access order");
            assertEquals(ordered.heightEvaluations(), orderedTerrain.rawTopEvaluations(),
                    "each complete height evaluation should request one raw top in this fixture");
            assertEquals(orderedTerrain.rawTopEvaluations(), shuffledTerrain.rawTopEvaluations(),
                    "raw-top evaluation count must be independent of column access order");
            System.out.printf(
                    "[perf] p47ColumnCache ordered requests=%d hits=%d misses=%d collisions=%d "
                            + "evictions=%d ownerSwaps=%d fullColumnRefreshes=%d heightEvaluations=%d; "
                            + "rawTopEvaluations=%d; shuffled misses=%d collisions=%d "
                            + "heightEvaluations=%d rawTopEvaluations=%d%n",
                    ordered.requests(), ordered.hits(), ordered.misses(), ordered.collisions(),
                    ordered.evictions(), ordered.ownerSwaps(), ordered.fullColumnRefreshes(),
                    ordered.heightEvaluations(), orderedTerrain.rawTopEvaluations(),
                    shuffled.misses(), shuffled.collisions(), shuffled.heightEvaluations(),
                    shuffledTerrain.rawTopEvaluations());
        } finally {
            EndDensity.configureColumnCacheMetrics(false);
            EndHeightmap.configureTerrainMetrics(false);
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    private static long sample(EndDensity density, boolean shuffled) {
        long checksum = 0L;
        for (int index = 0; index < COLUMN_COUNT; index++) {
            int column = shuffled ? ((index * 197 + 37) & 255) : index;
            float x = 8192.0F + (column & 15);
            float z = 8192.0F + (column >>> 4);
            for (int y : SAMPLE_Y) {
                checksum += Float.floatToIntBits(density.density(x, y, z, SEED));
            }
        }
        return checksum;
    }
}
