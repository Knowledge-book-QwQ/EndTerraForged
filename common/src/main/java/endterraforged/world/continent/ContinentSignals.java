package endterraforged.world.continent;

/**
 * Immutable macro-continent diagnostics for a single world X/Z position.
 *
 * <p>This value object is for preview and tests. Density sampling writes into
 * a caller-owned {@link ContinentSignalBuffer} instead, so it never allocates
 * one signal record per terrain column. Identity describes discrete ownership
 * and therefore remains stable while wrappers blend or scale the three
 * continuous values.</p>
 *
 * @param edge macro-continent edge signal in {@code [0,1]}
 * @param landness finite landmass activation in {@code [0,1]}
 * @param inlandness relief eligibility in {@code [0,1]}
 * @param identified whether a stable macro-continent owner exists
 * @param continentId stable packed owner-cell identity
 * @param centerX representative owner centre in world coordinates
 * @param centerZ representative owner centre in world coordinates
 */
public record ContinentSignals(float edge,
                               float landness,
                               float inlandness,
                               boolean identified,
                               long continentId,
                               int centerX,
                               int centerZ) {

    public ContinentSignals {
        requireUnit("edge", edge);
        requireUnit("landness", landness);
        requireUnit("inlandness", inlandness);
        if (!identified) {
            continentId = 0L;
            centerX = 0;
            centerZ = 0;
        }
    }

    private static void requireUnit(String name, float value) {
        if (!Float.isFinite(value) || value < 0.0F || value > 1.0F) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1], got " + value);
        }
    }
}
