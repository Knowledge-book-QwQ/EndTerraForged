package endterraforged.world.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.heightmap.EndTerrainLayer;

class TerrainPreviewLayerStatsTest {

    @Test
    void statsDefensivelyCopiesCounts() {
        int[] counts = new int[EndTerrainLayer.values().length];
        counts[EndTerrainLayer.PLAINS.ordinal()] = 3;

        TerrainPreviewLayerStats stats = TerrainPreviewLayerStats.fromCounts(counts);
        counts[EndTerrainLayer.PLAINS.ordinal()] = 0;

        assertEquals(3, stats.count(EndTerrainLayer.PLAINS));

        int[] exposed = stats.counts();
        exposed[EndTerrainLayer.PLAINS.ordinal()] = 0;
        assertEquals(3, stats.count(EndTerrainLayer.PLAINS));
    }

    @Test
    void coverageUsesVisibleLandSampleTotal() {
        int[] counts = new int[EndTerrainLayer.values().length];
        counts[EndTerrainLayer.NONE.ordinal()] = 2;
        counts[EndTerrainLayer.PLAINS.ordinal()] = 6;
        counts[EndTerrainLayer.HILLS.ordinal()] = 2;
        TerrainPreviewLayerStats stats = TerrainPreviewLayerStats.fromCounts(counts, 3);

        assertEquals(10, stats.total());
        assertEquals(0.6F, stats.coverage(EndTerrainLayer.PLAINS), 1e-6F);
        assertEquals(0.2F, stats.coverage(EndTerrainLayer.HILLS), 1e-6F);
        assertEquals(3, stats.blendedCount());
        assertEquals(0.3F, stats.blendCoverage(), 1e-6F);
        assertTrue(stats.hasBlendedSamples());
        assertTrue(stats.hasAuxiliaryLayers());
    }

    @Test
    void emptyStatsHaveNoAuxiliaryCoverage() {
        TerrainPreviewLayerStats stats = TerrainPreviewLayerStats.empty();

        assertEquals(0, stats.total());
        assertEquals(0.0F, stats.coverage(EndTerrainLayer.PLAINS), 0.0F);
        assertEquals(0.0F, stats.blendCoverage(), 0.0F);
        assertFalse(stats.hasBlendedSamples());
        assertFalse(stats.hasAuxiliaryLayers());
    }

    @Test
    void rejectsInvalidCounts() {
        assertThrows(IllegalArgumentException.class,
                () -> TerrainPreviewLayerStats.fromCounts(new int[1]));

        int[] negative = new int[EndTerrainLayer.values().length];
        negative[EndTerrainLayer.PLAINS.ordinal()] = -1;
        assertThrows(IllegalArgumentException.class,
                () -> TerrainPreviewLayerStats.fromCounts(negative));

        int[] oneVisible = new int[EndTerrainLayer.values().length];
        oneVisible[EndTerrainLayer.PLAINS.ordinal()] = 1;
        assertThrows(IllegalArgumentException.class,
                () -> TerrainPreviewLayerStats.fromCounts(oneVisible, -1));
        assertThrows(IllegalArgumentException.class,
                () -> TerrainPreviewLayerStats.fromCounts(oneVisible, 2));
    }
}
