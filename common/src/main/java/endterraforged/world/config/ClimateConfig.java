package endterraforged.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import endterraforged.world.climate.EndClimate;

/**
 * Serializable controls for the End climate field.
 *
 * <p>The runtime {@link EndClimate} owns immutable noise graphs. This record
 * stores only the scalar knobs needed to build those graphs from the world
 * seed, keeping preset JSON small and deterministic. The temperature/moisture
 * falloff/min/max/bias fields mirror the low-risk scalar part of RTF's
 * ClimateSettings.RangeValue: reshape the channel around its midpoint, apply a
 * half-strength bias, then clamp it to a configured range.</p>
 */
public record ClimateConfig(float climateRadius,
                            int temperatureScale,
                            int temperatureSeedOffset,
                            int moistureScale,
                            int moistureSeedOffset,
                            int windScale,
                            float perturbation,
                            float temperatureFalloff,
                            float temperatureMin,
                            float temperatureMax,
                            float temperatureBias,
                            float moistureFalloff,
                            float moistureMin,
                            float moistureMax,
                            float moistureBias) {

    public static final ClimateConfig DEFAULT =
            new ClimateConfig(4000.0F, 600, 600, 800, 700, 1000, 0.25F,
                    1.0F,
                    0.0F, 1.0F, 0.0F,
                    1.0F,
                    0.0F, 1.0F, 0.0F);

    public ClimateConfig(float climateRadius,
                         int temperatureScale,
                         int moistureScale,
                         int windScale,
                         float perturbation) {
        this(climateRadius, temperatureScale, DEFAULT.temperatureSeedOffset,
                moistureScale, DEFAULT.moistureSeedOffset, windScale, perturbation,
                DEFAULT.temperatureFalloff,
                0.0F, 1.0F, 0.0F,
                DEFAULT.moistureFalloff,
                0.0F, 1.0F, 0.0F);
    }

    public ClimateConfig(float climateRadius,
                         int temperatureScale,
                         int moistureScale,
                         int windScale,
                         float perturbation,
                         float temperatureMin, float temperatureMax, float temperatureBias,
                         float moistureMin, float moistureMax, float moistureBias) {
        this(climateRadius, temperatureScale, DEFAULT.temperatureSeedOffset,
                moistureScale, DEFAULT.moistureSeedOffset, windScale, perturbation,
                DEFAULT.temperatureFalloff, temperatureMin, temperatureMax, temperatureBias,
                DEFAULT.moistureFalloff, moistureMin, moistureMax, moistureBias);
    }

    public ClimateConfig(float climateRadius,
                         int temperatureScale,
                         int moistureScale,
                         int windScale,
                         float perturbation,
                         float temperatureFalloff,
                         float temperatureMin, float temperatureMax, float temperatureBias,
                         float moistureFalloff,
                         float moistureMin, float moistureMax, float moistureBias) {
        this(climateRadius, temperatureScale, DEFAULT.temperatureSeedOffset,
                moistureScale, DEFAULT.moistureSeedOffset, windScale, perturbation,
                temperatureFalloff, temperatureMin, temperatureMax, temperatureBias,
                moistureFalloff, moistureMin, moistureMax, moistureBias);
    }

    public ClimateConfig(float climateRadius,
                         int temperatureScale,
                         int temperatureSeedOffset,
                         int moistureScale,
                         int moistureSeedOffset,
                         int windScale,
                         float perturbation,
                         float temperatureMin, float temperatureMax, float temperatureBias,
                         float moistureMin, float moistureMax, float moistureBias) {
        this(climateRadius, temperatureScale, temperatureSeedOffset,
                moistureScale, moistureSeedOffset, windScale, perturbation,
                DEFAULT.temperatureFalloff, temperatureMin, temperatureMax, temperatureBias,
                DEFAULT.moistureFalloff, moistureMin, moistureMax, moistureBias);
    }

    private static final Codec<ClimateConfig> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("climate_radius", DEFAULT.climateRadius)
                    .forGetter(ClimateConfig::climateRadius),
            Codec.INT.optionalFieldOf("temperature_scale", DEFAULT.temperatureScale)
                    .forGetter(ClimateConfig::temperatureScale),
            Codec.INT.optionalFieldOf("temperature_seed_offset", DEFAULT.temperatureSeedOffset)
                    .forGetter(ClimateConfig::temperatureSeedOffset),
            Codec.INT.optionalFieldOf("moisture_scale", DEFAULT.moistureScale)
                    .forGetter(ClimateConfig::moistureScale),
            Codec.INT.optionalFieldOf("moisture_seed_offset", DEFAULT.moistureSeedOffset)
                    .forGetter(ClimateConfig::moistureSeedOffset),
            Codec.INT.optionalFieldOf("wind_scale", DEFAULT.windScale)
                    .forGetter(ClimateConfig::windScale),
            Codec.FLOAT.optionalFieldOf("perturbation", DEFAULT.perturbation)
                    .forGetter(ClimateConfig::perturbation),
            Codec.FLOAT.optionalFieldOf("temperature_falloff", DEFAULT.temperatureFalloff)
                    .forGetter(ClimateConfig::temperatureFalloff),
            Codec.FLOAT.optionalFieldOf("temperature_min", DEFAULT.temperatureMin)
                    .forGetter(ClimateConfig::temperatureMin),
            Codec.FLOAT.optionalFieldOf("temperature_max", DEFAULT.temperatureMax)
                    .forGetter(ClimateConfig::temperatureMax),
            Codec.FLOAT.optionalFieldOf("temperature_bias", DEFAULT.temperatureBias)
                    .forGetter(ClimateConfig::temperatureBias),
            Codec.FLOAT.optionalFieldOf("moisture_falloff", DEFAULT.moistureFalloff)
                    .forGetter(ClimateConfig::moistureFalloff),
            Codec.FLOAT.optionalFieldOf("moisture_min", DEFAULT.moistureMin)
                    .forGetter(ClimateConfig::moistureMin),
            Codec.FLOAT.optionalFieldOf("moisture_max", DEFAULT.moistureMax)
                    .forGetter(ClimateConfig::moistureMax),
            Codec.FLOAT.optionalFieldOf("moisture_bias", DEFAULT.moistureBias)
                    .forGetter(ClimateConfig::moistureBias)
    ).apply(instance, instance.stable(ClimateConfig::new)));

    public static final Codec<ClimateConfig> CODEC = BASE_CODEC.flatXmap(
            ClimateConfigValidator::validate,
            config -> DataResult.success(config));

    public EndClimate build(int seed) {
        return new EndClimate(seed, climateRadius, temperatureScale,
                temperatureSeedOffset, moistureScale, moistureSeedOffset,
                windScale, perturbation,
                temperatureFalloff, temperatureMin, temperatureMax, temperatureBias,
                moistureFalloff, moistureMin, moistureMax, moistureBias);
    }
}
