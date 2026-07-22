package endterraforged.world.preview;

import java.util.Objects;

import endterraforged.world.heightmap.EndTerrainLayer;

/**
 * Immutable layer coverage summary for a terrain preview raster.
 *
 * <p>Counts are based on visible land samples only. Void samples are excluded
 * so layer percentages describe terrain distribution rather than empty space.
 * {@code blendedCount} records how many visible samples are inside a smoothed
 * terrain-layer transition band.</p>
 */
public record TerrainPreviewLayerStats(int[] counts, int blendedCount) {

    public TerrainPreviewLayerStats {
        Objects.requireNonNull(counts, "counts");
        if (counts.length != EndTerrainLayer.values().length) {
            throw new IllegalArgumentException("counts length must match EndTerrainLayer values");
        }
        if (blendedCount < 0) {
            throw new IllegalArgumentException("blendedCount must be >= 0");
        }
        counts = counts.clone();
        int total = 0;
        for (int count : counts) {
            if (count < 0) {
                throw new IllegalArgumentException("layer counts must be >= 0");
            }
            total += count;
        }
        if (blendedCount > total) {
            throw new IllegalArgumentException("blendedCount must be <= visible sample total");
        }
    }

    public TerrainPreviewLayerStats(int[] counts) {
        this(counts, 0);
    }

    public static TerrainPreviewLayerStats empty() {
        return new TerrainPreviewLayerStats(new int[EndTerrainLayer.values().length]);
    }

    public static TerrainPreviewLayerStats fromCounts(int[] counts) {
        return new TerrainPreviewLayerStats(counts);
    }

    public static TerrainPreviewLayerStats fromCounts(int[] counts, int blendedCount) {
        return new TerrainPreviewLayerStats(counts, blendedCount);
    }

    @Override
    public int[] counts() {
        return counts.clone();
    }

    public int count(EndTerrainLayer layer) {
        return counts[Objects.requireNonNull(layer, "layer").ordinal()];
    }

    public int total() {
        int total = 0;
        for (int count : this.counts) {
            total += count;
        }
        return total;
    }

    public float coverage(EndTerrainLayer layer) {
        int total = total();
        if (total == 0) {
            return 0.0F;
        }
        return count(layer) / (float) total;
    }

    public float blendCoverage() {
        int total = total();
        if (total == 0) {
            return 0.0F;
        }
        return this.blendedCount / (float) total;
    }

    public boolean hasBlendedSamples() {
        return this.blendedCount > 0;
    }

    public boolean hasAuxiliaryLayers() {
        for (EndTerrainLayer layer : EndTerrainLayer.values()) {
            if (layer != EndTerrainLayer.NONE && count(layer) > 0) {
                return true;
            }
        }
        return false;
    }
}
