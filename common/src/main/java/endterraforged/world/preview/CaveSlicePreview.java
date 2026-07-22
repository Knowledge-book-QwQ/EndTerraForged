package endterraforged.world.preview;

import java.util.Objects;

/**
 * Immutable vertical cave preview raster.
 *
 * @param width horizontal sample count
 * @param height vertical depth sample count
 * @param colors ARGB colors in row-major order
 * @param maxStrength strongest sampled cave strength in the slice
 */
public record CaveSlicePreview(int width, int height, int[] colors, float maxStrength) {

    public CaveSlicePreview {
        Objects.requireNonNull(colors, "colors");
        if (width <= 0) {
            throw new IllegalArgumentException("width must be > 0, got " + width);
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be > 0, got " + height);
        }
        if (colors.length != width * height) {
            throw new IllegalArgumentException("colors length must be width*height");
        }
        colors = colors.clone();
    }

    @Override
    public int[] colors() {
        return colors.clone();
    }

    public int colorAt(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IndexOutOfBoundsException("slice coordinate out of bounds: x="
                    + x + ", y=" + y + ", width=" + width + ", height=" + height);
        }
        return colors[y * width + x];
    }
}
