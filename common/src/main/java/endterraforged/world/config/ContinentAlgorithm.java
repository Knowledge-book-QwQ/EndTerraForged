package endterraforged.world.config;

/**
 * Selects the macro-continent field used by {@code OUTER_CONTINENTS}.
 *
 * <p>Algorithms are explicit so a saved preset never changes shape merely
 * because a newer ETF release gains a better implementation.</p>
 */
public enum ContinentAlgorithm {
    LEGACY_RADIAL,
    RTF_MULTI,
    RTF_ADVANCED;

    /**
     * Returns whether this algorithm has a complete runtime implementation in
     * the current release.
     */
    public boolean isImplemented() {
        return this == LEGACY_RADIAL || this == RTF_MULTI;
    }

    /**
     * Returns whether this algorithm interprets {@code continent_scale} as the
     * RTF tectonic-cell scale instead of using {@code outer_continent_scale}.
     *
     * <p>This runtime policy is intentionally separate from
     * {@link #isImplemented()}: an algorithm may be internally integrated for
     * tests before it is safe to expose through persisted presets or the
     * editor.</p>
     */
    public boolean usesRtfTectonicScale() {
        return this == RTF_MULTI
                || this == RTF_ADVANCED;
    }

    /**
     * Returns whether the current runtime primitive can expose raw RTF edge
     * values to the End-specific shelf and inland band wrapper.
     */
    public boolean supportsContinentBands() {
        return this == RTF_MULTI || this == RTF_ADVANCED;
    }
}
