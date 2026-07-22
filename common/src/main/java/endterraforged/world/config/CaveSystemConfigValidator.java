package endterraforged.world.config;

import com.mojang.serialization.DataResult;

/**
 * Range checks for global cave-system controls.
 */
public final class CaveSystemConfigValidator {

    public static final int MIN_SEED_OFFSET = -1_000_000;
    public static final int MAX_SEED_OFFSET = 1_000_000;
    public static final int MIN_DEPTH = 0;
    public static final int MAX_DEPTH = 4096;
    public static final float MIN_UNIT = 0.0F;
    public static final float MAX_UNIT = 1.0F;

    private CaveSystemConfigValidator() {
    }

    public static DataResult<CaveSystemConfig> validate(CaveSystemConfig config) {
        if (config == null) {
            return DataResult.error(() -> "cave system config must not be null");
        }
        if (config.seedOffset() < MIN_SEED_OFFSET || config.seedOffset() > MAX_SEED_OFFSET) {
            return DataResult.error(() -> "subsurface.cave_system.seed_offset must be in ["
                    + MIN_SEED_OFFSET + ", " + MAX_SEED_OFFSET + "], got " + config.seedOffset());
        }
        DataResult<CaveSystemConfig> start = validateDepth(
                "subsurface.cave_system.depth_start", config.depthStart(), config);
        if (start.error().isPresent()) {
            return start;
        }
        DataResult<CaveSystemConfig> end = validateDepth(
                "subsurface.cave_system.depth_end", config.depthEnd(), config);
        if (end.error().isPresent()) {
            return end;
        }
        if (config.depthStart() > config.depthEnd()) {
            return DataResult.error(() -> "subsurface.cave_system.depth_start must be <= depth_end, got "
                    + config.depthStart() + " > " + config.depthEnd());
        }
        DataResult<CaveSystemConfig> spectacle = validateUnit(
                "subsurface.cave_system.spectacle_bias", config.spectacleBias(), config);
        if (spectacle.error().isPresent()) {
            return spectacle;
        }
        DataResult<CaveSystemConfig> connectivity = validateUnit(
                "subsurface.cave_system.connectivity", config.connectivity(), config);
        if (connectivity.error().isPresent()) {
            return connectivity;
        }
        return validateUnit("subsurface.cave_system.surface_opening_chance",
                config.surfaceOpeningChance(), config);
    }

    private static DataResult<CaveSystemConfig> validateDepth(
            String name, int value, CaveSystemConfig config) {
        if (value < MIN_DEPTH || value > MAX_DEPTH) {
            return DataResult.error(() -> name + " must be in ["
                    + MIN_DEPTH + ", " + MAX_DEPTH + "], got " + value);
        }
        return DataResult.success(config);
    }

    private static DataResult<CaveSystemConfig> validateUnit(
            String name, float value, CaveSystemConfig config) {
        if (!Float.isFinite(value) || value < MIN_UNIT || value > MAX_UNIT) {
            return DataResult.error(() -> name + " must be in ["
                    + MIN_UNIT + ", " + MAX_UNIT + "], got " + value);
        }
        return DataResult.success(config);
    }
}
