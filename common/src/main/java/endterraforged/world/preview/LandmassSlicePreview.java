package endterraforged.world.preview;

import java.util.Objects;

/** Immutable X/Z vertical preview of the runtime macro-landmass density field. */
public record LandmassSlicePreview(int width, int height, int[] colors, int solidSamples) {

    public LandmassSlicePreview {
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
        if (solidSamples < 0 || solidSamples > colors.length) {
            throw new IllegalArgumentException("solidSamples must be in [0, width*height]");
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
