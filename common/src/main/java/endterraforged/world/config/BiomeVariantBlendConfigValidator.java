package endterraforged.world.config;

import com.mojang.serialization.DataResult;

/**
 * Range checks for climate-variant blend noise controls.
 */
public final class BiomeVariantBlendConfigValidator {

    public static final int MIN_SCALE = 1;
    public static final int MAX_SCALE = 500;
    public static final int MIN_OCTAVES = 1;
    public static final int MAX_OCTAVES = 5;

    private BiomeVariantBlendConfigValidator() {
    }

    public static DataResult<BiomeVariantBlendConfig> validate(BiomeVariantBlendConfig config) {
        if (config == null) {
            return DataResult.error(() -> "biome variant blend config must not be null");
        }
        DataResult<BiomeVariantBlendConfig> scale = validateIntRange(
                "biome_variant_blend.scale", config.scale(), MIN_SCALE, MAX_SCALE, config);
        if (scale.error().isPresent()) {
            return scale;
        }
        return validateIntRange("biome_variant_blend.octaves", config.octaves(),
                MIN_OCTAVES, MAX_OCTAVES, config);
    }

    private static DataResult<BiomeVariantBlendConfig> validateIntRange(
            String name, int value, int min, int max, BiomeVariantBlendConfig config) {
        if (value < min || value > max) {
            return DataResult.error(() -> name + " must be in ["
                    + min + ", " + max + "], got " + value);
        }
        return DataResult.success(config);
    }
}
