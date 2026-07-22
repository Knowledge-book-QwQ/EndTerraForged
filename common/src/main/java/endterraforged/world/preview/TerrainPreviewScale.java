package endterraforged.world.preview;

/**
 * Geographic span represented by the preview raster.
 */
public enum TerrainPreviewScale {
    CLOSE(1, 2),
    NORMAL(1, 1),
    WIDE(2, 1);

    private final int numerator;
    private final int denominator;

    TerrainPreviewScale(int numerator, int denominator) {
        this.numerator = numerator;
        this.denominator = denominator;
    }

    int worldSpan(int defaultSpan) {
        return Math.max(1, Math.round(defaultSpan * this.numerator / (float) this.denominator));
    }
}
