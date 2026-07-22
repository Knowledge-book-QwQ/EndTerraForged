package endterraforged.world.continent;

/**
 * Immutable coordinate policy separating the vanilla End centre from ETF's
 * outer-terrain domain.
 *
 * <p>The protected radius intentionally includes the main island and its
 * nearby vanilla buffer. ETF density delegates to vanilla inside that radius;
 * macro landmass code can use {@link #outerActivation(float, float)} to start
 * only after a deterministic void-to-outer-terrain transition. The policy is
 * pure and allocation-free so it is safe on parallel chunk-generation paths.</p>
 */
public final class EndCentralRegionPolicy {

    /** Radius where vanilla End final density remains authoritative. */
    public static final int VANILLA_RADIUS_BLOCKS = 1536;

    /** Radius where outer ETF topology reaches full strength. */
    public static final int OUTER_TERRAIN_RADIUS_BLOCKS = 2048;

    private static final double VANILLA_RADIUS_SQUARED =
            (double) VANILLA_RADIUS_BLOCKS * VANILLA_RADIUS_BLOCKS;
    private static final double OUTER_TERRAIN_RADIUS_SQUARED =
            (double) OUTER_TERRAIN_RADIUS_BLOCKS * OUTER_TERRAIN_RADIUS_BLOCKS;
    private static final double TRANSITION_SPAN_SQUARED =
            OUTER_TERRAIN_RADIUS_SQUARED - VANILLA_RADIUS_SQUARED;

    private EndCentralRegionPolicy() {
    }

    /** Returns whether the block column must retain vanilla End density. */
    public static boolean usesVanillaDensity(int blockX, int blockZ) {
        return squaredDistance(blockX, blockZ) <= VANILLA_RADIUS_SQUARED;
    }

    /**
     * Returns the outer-topology multiplier for a world column.
     *
     * <p>The squared-distance interpolation avoids a square root on the density
     * hot path while still providing a continuous, deterministic transition.
     * It is zero in the protected centre and one outside the startup band.</p>
     */
    public static float outerActivation(float x, float z) {
        double squaredDistance = squaredDistance(x, z);
        if (squaredDistance <= VANILLA_RADIUS_SQUARED) {
            return 0.0F;
        }
        if (squaredDistance >= OUTER_TERRAIN_RADIUS_SQUARED) {
            return 1.0F;
        }
        double t = (squaredDistance - VANILLA_RADIUS_SQUARED) / TRANSITION_SPAN_SQUARED;
        return (float) (t * t * (3.0D - 2.0D * t));
    }

    private static double squaredDistance(int x, int z) {
        return (double) x * x + (double) z * z;
    }

    private static double squaredDistance(float x, float z) {
        return (double) x * x + (double) z * z;
    }
}
