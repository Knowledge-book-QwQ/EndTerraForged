package endterraforged.world.preview;

import java.util.Objects;

/**
 * Immutable terrain preview raster.
 *
 * @param size square width/height in pixels
 * @param colors ARGB colors in row-major order
 * @param minHeight lowest sampled normalised height
 * @param maxHeight highest sampled normalised height
 * @param layerStats visible-land layer coverage for the sampled raster
 */
public record TerrainPreview(int size, int[] colors, float minHeight, float maxHeight,
                             TerrainPreviewLayerStats layerStats) {

    public TerrainPreview {
        Objects.requireNonNull(colors, "colors");
        Objects.requireNonNull(layerStats, "layerStats");
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0, got " + size);
        }
        if (colors.length != size * size) {
            throw new IllegalArgumentException("colors length must be size*size");
        }
        colors = colors.clone();
    }

    public TerrainPreview(int size, int[] colors, float minHeight, float maxHeight) {
        this(size, colors, minHeight, maxHeight, TerrainPreviewLayerStats.empty());
    }

    @Override
    public int[] colors() {
        return colors.clone();
    }

    public int colorAt(int x, int z) {
        if (x < 0 || x >= size || z < 0 || z >= size) {
            throw new IndexOutOfBoundsException("preview coordinate out of bounds: x="
                    + x + ", z=" + z + ", size=" + size);
        }
        return colors[z * size + x];
    }
}
