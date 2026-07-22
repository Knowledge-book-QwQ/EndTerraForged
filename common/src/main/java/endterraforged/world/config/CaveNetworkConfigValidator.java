package endterraforged.world.config;

import com.mojang.serialization.DataResult;

/**
 * Range checks for cave network controls.
 */
public final class CaveNetworkConfigValidator {

    public static final int MIN_REGION_SIZE = 128;
    public static final int MAX_REGION_SIZE = 8192;
    public static final int MIN_CHAMBER_SPACING = 64;
    public static final int MAX_CHAMBER_SPACING = 4096;
    public static final float MIN_UNIT = 0.0F;
    public static final float MAX_UNIT = 1.0F;
    public static final float MIN_BRANCHING = 0.0F;
    public static final float MAX_BRANCHING = 8.0F;
    public static final float MIN_MAX_SLOPE = 0.0F;
    public static final float MAX_MAX_SLOPE = 4.0F;

    private CaveNetworkConfigValidator() {
    }

    public static DataResult<CaveNetworkConfig> validate(CaveNetworkConfig config) {
        if (config == null) {
            return DataResult.error(() -> "cave network config must not be null");
        }
        DataResult<CaveNetworkConfig> region = validateIntRange(
                "subsurface.cave_network.region_size", config.regionSize(),
                MIN_REGION_SIZE, MAX_REGION_SIZE, config);
        if (region.error().isPresent()) {
            return region;
        }
        DataResult<CaveNetworkConfig> density = validateUnit(
                "subsurface.cave_network.network_density", config.networkDensity(), config);
        if (density.error().isPresent()) {
            return density;
        }
        DataResult<CaveNetworkConfig> spacing = validateIntRange(
                "subsurface.cave_network.chamber_spacing", config.chamberSpacing(),
                MIN_CHAMBER_SPACING, MAX_CHAMBER_SPACING, config);
        if (spacing.error().isPresent()) {
            return spacing;
        }
        DataResult<CaveNetworkConfig> branching = validateFloatRange(
                "subsurface.cave_network.branching_factor", config.branchingFactor(),
                MIN_BRANCHING, MAX_BRANCHING, config);
        if (branching.error().isPresent()) {
            return branching;
        }
        DataResult<CaveNetworkConfig> loops = validateUnit(
                "subsurface.cave_network.loop_chance", config.loopChance(), config);
        if (loops.error().isPresent()) {
            return loops;
        }
        DataResult<CaveNetworkConfig> slope = validateFloatRange(
                "subsurface.cave_network.max_slope", config.maxSlope(),
                MIN_MAX_SLOPE, MAX_MAX_SLOPE, config);
        if (slope.error().isPresent()) {
            return slope;
        }
        return validateUnit("subsurface.cave_network.min_landness", config.minLandness(), config);
    }

    private static DataResult<CaveNetworkConfig> validateUnit(
            String name, float value, CaveNetworkConfig config) {
        return validateFloatRange(name, value, MIN_UNIT, MAX_UNIT, config);
    }

    private static DataResult<CaveNetworkConfig> validateFloatRange(
            String name, float value, float min, float max, CaveNetworkConfig config) {
        if (!Float.isFinite(value) || value < min || value > max) {
            return DataResult.error(() -> name + " must be in ["
                    + min + ", " + max + "], got " + value);
        }
        return DataResult.success(config);
    }

    private static DataResult<CaveNetworkConfig> validateIntRange(
            String name, int value, int min, int max, CaveNetworkConfig config) {
        if (value < min || value > max) {
            return DataResult.error(() -> name + " must be in ["
                    + min + ", " + max + "], got " + value);
        }
        return DataResult.success(config);
    }
}
