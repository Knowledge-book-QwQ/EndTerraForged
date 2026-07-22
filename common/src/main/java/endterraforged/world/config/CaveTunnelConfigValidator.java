package endterraforged.world.config;

import com.mojang.serialization.DataResult;

/**
 * Range checks for cave tunnel planning controls.
 */
public final class CaveTunnelConfigValidator {

    public static final float MIN_PROBABILITY = 0.0F;
    public static final float MAX_PROBABILITY = 1.0F;
    public static final float MIN_CHEESE_DEPTH_OFFSET = 0.0F;
    public static final float MAX_CHEESE_DEPTH_OFFSET = 8.0F;

    private CaveTunnelConfigValidator() {
    }

    public static DataResult<CaveTunnelConfig> validate(CaveTunnelConfig config) {
        if (config == null) {
            return DataResult.error(() -> "cave tunnel config must not be null");
        }
        DataResult<CaveTunnelConfig> entrance = validateProbability(
                "subsurface.caves.entrance_probability",
                config.entranceProbability(), config);
        if (entrance.error().isPresent()) {
            return entrance;
        }
        if (!Float.isFinite(config.cheeseDepthOffset())
                || config.cheeseDepthOffset() < MIN_CHEESE_DEPTH_OFFSET
                || config.cheeseDepthOffset() > MAX_CHEESE_DEPTH_OFFSET) {
            return DataResult.error(() -> "subsurface.caves.cheese_depth_offset must be in ["
                    + MIN_CHEESE_DEPTH_OFFSET + ", " + MAX_CHEESE_DEPTH_OFFSET
                    + "], got " + config.cheeseDepthOffset());
        }
        DataResult<CaveTunnelConfig> cheese = validateProbability(
                "subsurface.caves.cheese_probability",
                config.cheeseProbability(), config);
        if (cheese.error().isPresent()) {
            return cheese;
        }
        DataResult<CaveTunnelConfig> spaghetti = validateProbability(
                "subsurface.caves.spaghetti_probability",
                config.spaghettiProbability(), config);
        if (spaghetti.error().isPresent()) {
            return spaghetti;
        }
        return validateProbability("subsurface.caves.noodle_probability",
                config.noodleProbability(), config);
    }

    private static DataResult<CaveTunnelConfig> validateProbability(
            String name, float value, CaveTunnelConfig config) {
        if (!Float.isFinite(value) || value < MIN_PROBABILITY || value > MAX_PROBABILITY) {
            return DataResult.error(() -> name + " must be in ["
                    + MIN_PROBABILITY + ", " + MAX_PROBABILITY + "], got " + value);
        }
        return DataResult.success(config);
    }
}
