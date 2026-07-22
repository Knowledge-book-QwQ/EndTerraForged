package endterraforged.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-terrain-layer scale controls mirroring RTF's terrain entry settings.
 */
public record TerrainLayerConfig(float weight, float baseScale,
                                 float verticalScale, float horizontalScale) {

    public static final TerrainLayerConfig DEFAULT = new TerrainLayerConfig(1.0F, 1.0F, 1.0F, 1.0F);
    public static final TerrainLayerConfig DISABLED = new TerrainLayerConfig(0.0F, 1.0F, 1.0F, 1.0F);

    private static final Codec<TerrainLayerConfig> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("weight", DEFAULT.weight)
                    .forGetter(TerrainLayerConfig::weight),
            Codec.FLOAT.optionalFieldOf("base_scale", DEFAULT.baseScale)
                    .forGetter(TerrainLayerConfig::baseScale),
            Codec.FLOAT.optionalFieldOf("vertical_scale", DEFAULT.verticalScale)
                    .forGetter(TerrainLayerConfig::verticalScale),
            Codec.FLOAT.optionalFieldOf("horizontal_scale", DEFAULT.horizontalScale)
                    .forGetter(TerrainLayerConfig::horizontalScale)
    ).apply(instance, instance.stable(TerrainLayerConfig::new)));

    public static final Codec<TerrainLayerConfig> CODEC = BASE_CODEC.flatXmap(
            TerrainLayerConfig::validate,
            config -> DataResult.success(config));

    static DataResult<TerrainLayerConfig> validate(TerrainLayerConfig config) {
        if (config == null) {
            return DataResult.error(() -> "terrain layer config must not be null");
        }
        DataResult<TerrainLayerConfig> weight = validateRange("weight", config.weight, 0.0F, 10.0F, config);
        if (weight.error().isPresent()) {
            return weight;
        }
        DataResult<TerrainLayerConfig> base = validateRange("base_scale", config.baseScale, 0.0F, 2.0F, config);
        if (base.error().isPresent()) {
            return base;
        }
        DataResult<TerrainLayerConfig> vertical = validateRange("vertical_scale", config.verticalScale, 0.0F, 10.0F, config);
        if (vertical.error().isPresent()) {
            return vertical;
        }
        return validateRange("horizontal_scale", config.horizontalScale, 0.0F, 10.0F, config);
    }

    private static DataResult<TerrainLayerConfig> validateRange(String name, float value,
                                                                float min, float max,
                                                                TerrainLayerConfig config) {
        if (!Float.isFinite(value) || value < min || value > max) {
            return DataResult.error(() -> "terrain layer " + name
                    + " must be in [" + min + ", " + max + "], got " + value);
        }
        return DataResult.success(config);
    }
}
