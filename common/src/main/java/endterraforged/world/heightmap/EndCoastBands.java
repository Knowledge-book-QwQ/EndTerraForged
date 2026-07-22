package endterraforged.world.heightmap;

/**
 * Converts an archipelago mask into continuous End-specific coast signals.
 *
 * <p>The thresholds are runtime constants until the region-planned preset
 * schema is promoted to a new format version. Keeping them here prevents a
 * preview-only control from silently becoming a persisted worldgen contract.</p>
 */
public final class EndCoastBands {

    private static final float SHELF_START = 0.02F;
    private static final float SHELF_END = 0.16F;
    private static final float COAST_END = 0.38F;
    private static final float INLAND_START = 0.28F;
    private static final float INLAND_END = 0.78F;

    private EndCoastBands() {
    }

    /** Maps mask strength to finite landmass support. */
    public static float landness(float mask) {
        float shelf = smoothstep(mask, SHELF_START, SHELF_END);
        float body = smoothstep(mask, 0.10F, COAST_END);
        return clamp01(shelf * (0.24F + 0.76F * body));
    }

    /** Maps mask strength to inland relief eligibility. */
    public static float inlandness(float mask) {
        return smoothstep(mask, INLAND_START, INLAND_END);
    }

    /** Maps mask strength to the broad relief contribution envelope. */
    public static float reliefWeight(float mask) {
        return smoothstep(mask, 0.18F, 0.74F);
    }

    /** Returns a diagnostic band without introducing a second numeric path. */
    public static EndCoastBand band(float mask) {
        float value = clamp01(mask);
        if (value < SHELF_START) {
            return EndCoastBand.VOID_EDGE;
        }
        if (value < INLAND_START) {
            return EndCoastBand.SHELF;
        }
        if (value < INLAND_END) {
            return EndCoastBand.COAST;
        }
        return EndCoastBand.INLAND;
    }

    private static float smoothstep(float value, float start, float end) {
        float alpha = clamp01((value - start) / (end - start));
        return alpha * alpha * (3.0F - 2.0F * alpha);
    }

    private static float clamp01(float value) {
        return Math.clamp(value, 0.0F, 1.0F);
    }
}
