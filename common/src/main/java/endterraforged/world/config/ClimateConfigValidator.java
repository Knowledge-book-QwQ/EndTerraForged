package endterraforged.world.config;

import com.mojang.serialization.DataResult;

/**
 * Range checks for the End climate noise controls.
 */
public final class ClimateConfigValidator {

    public static final float MIN_CLIMATE_RADIUS = 256.0F;
    public static final float MAX_CLIMATE_RADIUS = 32000.0F;
    public static final int MIN_NOISE_SCALE = 16;
    public static final int MAX_NOISE_SCALE = 32000;
    public static final float MIN_CHANNEL_VALUE = 0.0F;
    public static final float MAX_CHANNEL_VALUE = 1.0F;
    public static final float MIN_CHANNEL_BIAS = -1.0F;
    public static final float MAX_CHANNEL_BIAS = 1.0F;
    public static final float MIN_FALLOFF = 1.0F;
    public static final float MAX_FALLOFF = 10.0F;
    public static final int MIN_SEED_OFFSET = -1_000_000;
    public static final int MAX_SEED_OFFSET = 1_000_000;

    private ClimateConfigValidator() {
    }

    public static DataResult<ClimateConfig> validate(ClimateConfig config) {
        if (config == null) {
            return DataResult.error(() -> "climate config must not be null");
        }
        DataResult<ClimateConfig> radius = validateRange(
                "climate.climate_radius", config.climateRadius(),
                MIN_CLIMATE_RADIUS, MAX_CLIMATE_RADIUS, config);
        if (radius.error().isPresent()) {
            return radius;
        }
        DataResult<ClimateConfig> temperature = validateIntRange(
                "climate.temperature_scale", config.temperatureScale(),
                MIN_NOISE_SCALE, MAX_NOISE_SCALE, config);
        if (temperature.error().isPresent()) {
            return temperature;
        }
        DataResult<ClimateConfig> temperatureSeed = validateIntRange(
                "climate.temperature_seed_offset", config.temperatureSeedOffset(),
                MIN_SEED_OFFSET, MAX_SEED_OFFSET, config);
        if (temperatureSeed.error().isPresent()) {
            return temperatureSeed;
        }
        DataResult<ClimateConfig> moisture = validateIntRange(
                "climate.moisture_scale", config.moistureScale(),
                MIN_NOISE_SCALE, MAX_NOISE_SCALE, config);
        if (moisture.error().isPresent()) {
            return moisture;
        }
        DataResult<ClimateConfig> moistureSeed = validateIntRange(
                "climate.moisture_seed_offset", config.moistureSeedOffset(),
                MIN_SEED_OFFSET, MAX_SEED_OFFSET, config);
        if (moistureSeed.error().isPresent()) {
            return moistureSeed;
        }
        DataResult<ClimateConfig> wind = validateIntRange(
                "climate.wind_scale", config.windScale(),
                MIN_NOISE_SCALE, MAX_NOISE_SCALE, config);
        if (wind.error().isPresent()) {
            return wind;
        }
        DataResult<ClimateConfig> perturbation = validateRange(
                "climate.perturbation", config.perturbation(),
                MIN_CHANNEL_VALUE, MAX_CHANNEL_VALUE, config);
        if (perturbation.error().isPresent()) {
            return perturbation;
        }
        DataResult<ClimateConfig> temperatureFalloff = validateRange(
                "climate.temperature_falloff", config.temperatureFalloff(),
                MIN_FALLOFF, MAX_FALLOFF, config);
        if (temperatureFalloff.error().isPresent()) {
            return temperatureFalloff;
        }
        DataResult<ClimateConfig> temperatureMin = validateRange(
                "climate.temperature_min", config.temperatureMin(),
                MIN_CHANNEL_VALUE, MAX_CHANNEL_VALUE, config);
        if (temperatureMin.error().isPresent()) {
            return temperatureMin;
        }
        DataResult<ClimateConfig> temperatureMax = validateRange(
                "climate.temperature_max", config.temperatureMax(),
                MIN_CHANNEL_VALUE, MAX_CHANNEL_VALUE, config);
        if (temperatureMax.error().isPresent()) {
            return temperatureMax;
        }
        if (config.temperatureMin() > config.temperatureMax()) {
            return DataResult.error(() -> "climate.temperature_min must be <= "
                    + "climate.temperature_max, got " + config.temperatureMin()
                    + " > " + config.temperatureMax());
        }
        DataResult<ClimateConfig> temperatureBias = validateRange(
                "climate.temperature_bias", config.temperatureBias(),
                MIN_CHANNEL_BIAS, MAX_CHANNEL_BIAS, config);
        if (temperatureBias.error().isPresent()) {
            return temperatureBias;
        }
        DataResult<ClimateConfig> moistureFalloff = validateRange(
                "climate.moisture_falloff", config.moistureFalloff(),
                MIN_FALLOFF, MAX_FALLOFF, config);
        if (moistureFalloff.error().isPresent()) {
            return moistureFalloff;
        }
        DataResult<ClimateConfig> moistureMin = validateRange(
                "climate.moisture_min", config.moistureMin(),
                MIN_CHANNEL_VALUE, MAX_CHANNEL_VALUE, config);
        if (moistureMin.error().isPresent()) {
            return moistureMin;
        }
        DataResult<ClimateConfig> moistureMax = validateRange(
                "climate.moisture_max", config.moistureMax(),
                MIN_CHANNEL_VALUE, MAX_CHANNEL_VALUE, config);
        if (moistureMax.error().isPresent()) {
            return moistureMax;
        }
        if (config.moistureMin() > config.moistureMax()) {
            return DataResult.error(() -> "climate.moisture_min must be <= "
                    + "climate.moisture_max, got " + config.moistureMin()
                    + " > " + config.moistureMax());
        }
        return validateRange("climate.moisture_bias", config.moistureBias(),
                MIN_CHANNEL_BIAS, MAX_CHANNEL_BIAS, config);
    }

    private static DataResult<ClimateConfig> validateRange(String name, float value,
                                                           float min, float max,
                                                           ClimateConfig config) {
        if (!Float.isFinite(value) || value < min || value > max) {
            return DataResult.error(() -> name + " must be in ["
                    + min + ", " + max + "], got " + value);
        }
        return DataResult.success(config);
    }

    private static DataResult<ClimateConfig> validateIntRange(String name, int value,
                                                              int min, int max,
                                                              ClimateConfig config) {
        if (value < min || value > max) {
            return DataResult.error(() -> name + " must be in ["
                    + min + ", " + max + "], got " + value);
        }
        return DataResult.success(config);
    }
}
