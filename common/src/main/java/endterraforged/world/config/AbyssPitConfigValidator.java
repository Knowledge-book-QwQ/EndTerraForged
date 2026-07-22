package endterraforged.world.config;

import com.mojang.serialization.DataResult;

/**
 * Range checks for abyss pit controls.
 */
public final class AbyssPitConfigValidator {

    public static final int MIN_SEED_OFFSET = -1_000_000;
    public static final int MAX_SEED_OFFSET = 1_000_000;
    public static final int MIN_PIT_SCALE = 32;
    public static final int MAX_PIT_SCALE = 4096;
    public static final int MIN_PIT_OCTAVES = 1;
    public static final int MAX_PIT_OCTAVES = 8;
    public static final float MIN_PIT_LACUNARITY = 1.0F;
    public static final float MAX_PIT_LACUNARITY = 4.0F;
    public static final float MIN_PIT_GAIN = 0.0F;
    public static final float MAX_PIT_GAIN = 1.0F;
    public static final float MIN_THRESHOLD = 0.0F;
    public static final float MAX_THRESHOLD = 1.0F;
    public static final float MIN_EDGE_FALLOFF = 0.001F;
    public static final float MAX_EDGE_FALLOFF = 1.0F;
    public static final int MIN_DEPTH = 1;
    public static final int MAX_DEPTH = 4096;
    public static final float MIN_DEPTH_CURVE = 0.1F;
    public static final float MAX_DEPTH_CURVE = 4.0F;
    public static final float MIN_LANDNESS = 0.0F;
    public static final float MAX_LANDNESS = 1.0F;

    private AbyssPitConfigValidator() {
    }

    public static DataResult<AbyssPitConfig> validate(AbyssPitConfig config) {
        if (config == null) {
            return DataResult.error(() -> "abyss pit config must not be null");
        }
        DataResult<AbyssPitConfig> seed = validateIntRange(
                "subsurface.abyss.seed_offset", config.seedOffset(),
                MIN_SEED_OFFSET, MAX_SEED_OFFSET, config);
        if (seed.error().isPresent()) {
            return seed;
        }
        DataResult<AbyssPitConfig> scale = validateIntRange(
                "subsurface.abyss.pit_scale", config.pitScale(),
                MIN_PIT_SCALE, MAX_PIT_SCALE, config);
        if (scale.error().isPresent()) {
            return scale;
        }
        DataResult<AbyssPitConfig> octaves = validateIntRange(
                "subsurface.abyss.pit_octaves", config.pitOctaves(),
                MIN_PIT_OCTAVES, MAX_PIT_OCTAVES, config);
        if (octaves.error().isPresent()) {
            return octaves;
        }
        DataResult<AbyssPitConfig> lacunarity = validateRange(
                "subsurface.abyss.pit_lacunarity", config.pitLacunarity(),
                MIN_PIT_LACUNARITY, MAX_PIT_LACUNARITY, config);
        if (lacunarity.error().isPresent()) {
            return lacunarity;
        }
        DataResult<AbyssPitConfig> gain = validateRange(
                "subsurface.abyss.pit_gain", config.pitGain(),
                MIN_PIT_GAIN, MAX_PIT_GAIN, config);
        if (gain.error().isPresent()) {
            return gain;
        }
        DataResult<AbyssPitConfig> threshold = validateRange(
                "subsurface.abyss.threshold", config.threshold(),
                MIN_THRESHOLD, MAX_THRESHOLD, config);
        if (threshold.error().isPresent()) {
            return threshold;
        }
        DataResult<AbyssPitConfig> edge = validateRange(
                "subsurface.abyss.edge_falloff", config.edgeFalloff(),
                MIN_EDGE_FALLOFF, MAX_EDGE_FALLOFF, config);
        if (edge.error().isPresent()) {
            return edge;
        }
        DataResult<AbyssPitConfig> depth = validateIntRange(
                "subsurface.abyss.depth", config.depth(), MIN_DEPTH, MAX_DEPTH, config);
        if (depth.error().isPresent()) {
            return depth;
        }
        DataResult<AbyssPitConfig> depthCurve = validateRange(
                "subsurface.abyss.depth_curve", config.depthCurve(),
                MIN_DEPTH_CURVE, MAX_DEPTH_CURVE, config);
        if (depthCurve.error().isPresent()) {
            return depthCurve;
        }
        return validateRange("subsurface.abyss.min_landness", config.minLandness(),
                MIN_LANDNESS, MAX_LANDNESS, config);
    }

    private static DataResult<AbyssPitConfig> validateRange(String name, float value,
                                                            float min, float max,
                                                            AbyssPitConfig config) {
        if (!Float.isFinite(value) || value < min || value > max) {
            return DataResult.error(() -> name + " must be in ["
                    + min + ", " + max + "], got " + value);
        }
        return DataResult.success(config);
    }

    private static DataResult<AbyssPitConfig> validateIntRange(String name, int value,
                                                               int min, int max,
                                                               AbyssPitConfig config) {
        if (value < min || value > max) {
            return DataResult.error(() -> name + " must be in ["
                    + min + ", " + max + "], got " + value);
        }
        return DataResult.success(config);
    }
}
