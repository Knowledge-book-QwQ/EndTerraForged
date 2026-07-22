package endterraforged.world.config;

import com.mojang.serialization.DataResult;

/**
 * Range checks for giant chamber controls.
 */
public final class CaveChamberConfigValidator {

    public static final float MIN_UNIT = 0.0F;
    public static final float MAX_UNIT = 1.0F;
    public static final int MIN_RADIUS = 8;
    public static final int MAX_RADIUS = 512;
    public static final float MIN_VERTICAL_STRETCH = 0.25F;
    public static final float MAX_VERTICAL_STRETCH = 8.0F;

    private CaveChamberConfigValidator() {
    }

    public static DataResult<CaveChamberConfig> validate(CaveChamberConfig config) {
        if (config == null) {
            return DataResult.error(() -> "cave chamber config must not be null");
        }
        DataResult<CaveChamberConfig> probability = validateUnit(
                "subsurface.cave_chambers.chamber_probability",
                config.chamberProbability(), config);
        if (probability.error().isPresent()) {
            return probability;
        }
        DataResult<CaveChamberConfig> minRadius = validateRadius(
                "subsurface.cave_chambers.min_radius", config.minRadius(), config);
        if (minRadius.error().isPresent()) {
            return minRadius;
        }
        DataResult<CaveChamberConfig> maxRadius = validateRadius(
                "subsurface.cave_chambers.max_radius", config.maxRadius(), config);
        if (maxRadius.error().isPresent()) {
            return maxRadius;
        }
        if (config.minRadius() > config.maxRadius()) {
            return DataResult.error(() -> "subsurface.cave_chambers.min_radius must be <= max_radius, got "
                    + config.minRadius() + " > " + config.maxRadius());
        }
        DataResult<CaveChamberConfig> stretch = validateFloatRange(
                "subsurface.cave_chambers.vertical_stretch", config.verticalStretch(),
                MIN_VERTICAL_STRETCH, MAX_VERTICAL_STRETCH, config);
        if (stretch.error().isPresent()) {
            return stretch;
        }
        DataResult<CaveChamberConfig> floor = validateUnit(
                "subsurface.cave_chambers.floor_bias", config.floorBias(), config);
        if (floor.error().isPresent()) {
            return floor;
        }
        return validateUnit("subsurface.cave_chambers.roughness", config.roughness(), config);
    }

    private static DataResult<CaveChamberConfig> validateRadius(
            String name, int value, CaveChamberConfig config) {
        if (value < MIN_RADIUS || value > MAX_RADIUS) {
            return DataResult.error(() -> name + " must be in ["
                    + MIN_RADIUS + ", " + MAX_RADIUS + "], got " + value);
        }
        return DataResult.success(config);
    }

    private static DataResult<CaveChamberConfig> validateUnit(
            String name, float value, CaveChamberConfig config) {
        return validateFloatRange(name, value, MIN_UNIT, MAX_UNIT, config);
    }

    private static DataResult<CaveChamberConfig> validateFloatRange(
            String name, float value, float min, float max, CaveChamberConfig config) {
        if (!Float.isFinite(value) || value < min || value > max) {
            return DataResult.error(() -> name + " must be in ["
                    + min + ", " + max + "], got " + value);
        }
        return DataResult.success(config);
    }
}
