package endterraforged.world.heightmap;

import endterraforged.world.config.TopologyMode;
import endterraforged.world.continent.EndCentralRegionPolicy;

/**
 * Pure physical-relief gate for bounded terrain features.
 *
 * <p>Terrain-region ownership remains stable even where a ridge cannot be
 * physically supported. This policy only scales ridge relief, so the AREA
 * underlay continues to define the landmass surface, biome context, and
 * diagnostic identity.</p>
 */
final class EndTerrainEligibilityPolicy {
    private static final float COAST_START = 0.45F;
    private static final float COAST_FULL = 0.80F;
    private static final float INLAND_START = 0.20F;
    private static final float INLAND_FULL = 0.60F;
    private static final float SHELF_START_BLOCKS = 48.0F;
    private static final float SHELF_FULL_BLOCKS = 96.0F;

    private final boolean outerTopology;
    private final EndLandmassVolume landmassVolume;

    EndTerrainEligibilityPolicy(TopologyMode topologyMode, EndLandmassVolume landmassVolume) {
        this.outerTopology = topologyMode == TopologyMode.OUTER_CONTINENTS;
        this.landmassVolume = landmassVolume;
    }

    /**
     * Returns the physical-relief multiplier for a REGION_PLANNED ridge.
     *
     * <p>The central policy applies only to the outer-continent topology. The
     * older CONTINENTAL test and migration paths intentionally retain their
     * historic coordinate behaviour near the origin.</p>
     */
    float ridgeMultiplier(float x, float z, float landness, float inlandness) {
        float central = this.outerTopology ? EndCentralRegionPolicy.outerActivation(x, z) : 1.0F;
        if (central <= 0.0F) {
            return 0.0F;
        }

        float coast = smoothstepRange(landness, COAST_START, COAST_FULL);
        float interior = smoothstepRange(inlandness, INLAND_START, INLAND_FULL);
        if (!this.landmassVolume.isFinite()) {
            return central * coast * interior;
        }

        float support = smoothstepRange(
                this.landmassVolume.availableShelfThicknessBlocks(x, z, landness),
                SHELF_START_BLOCKS, SHELF_FULL_BLOCKS);
        return central * coast * interior * support;
    }

    private static float smoothstepRange(float value, float lower, float upper) {
        float t = Math.clamp((value - lower) / (upper - lower), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
