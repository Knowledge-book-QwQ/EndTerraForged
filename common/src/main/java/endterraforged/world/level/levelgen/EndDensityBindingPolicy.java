package endterraforged.world.level.levelgen;

/**
 * Decides whether a {@code RandomState} construction may bind ETF's End
 * density placeholders.
 *
 * <p>ETF replaces the End final-density leaf, so a matching vanilla End
 * fallback is mandatory before it can preserve the frozen central region.
 * A direct settings-only {@code RandomState.create} call does not provide
 * the density-function registry required to build that fallback. Rejecting
 * that route is safer than creating central air or partially generated End
 * chunks.</p>
 */
public final class EndDensityBindingPolicy {

    private EndDensityBindingPolicy() {
    }

    /** Result of classifying a {@code RandomState.create} route. */
    public enum Decision {
        SKIP,
        BIND,
        REJECT_MISSING_VANILLA_FALLBACK
    }

    /**
     * Chooses the only safe action for the captured density settings.
     *
     * @param containsEndDensity whether the settings contain ETF's End leaf
     * @param hasDensityRegistry whether vanilla End fallback construction has registry access
     * @return the binding decision for this construction
     */
    public static Decision decide(boolean containsEndDensity, boolean hasDensityRegistry) {
        if (!containsEndDensity) {
            return Decision.SKIP;
        }
        return hasDensityRegistry ? Decision.BIND : Decision.REJECT_MISSING_VANILLA_FALLBACK;
    }
}
