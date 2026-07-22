package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.DataResult;

class ClimateConfigValidatorTest {

    @Test
    void defaultConfigPassesValidation() {
        assertTrue(ClimateConfigValidator.validate(ClimateConfig.DEFAULT).isSuccess());
    }

    @Test
    void successReturnsSameInstance() {
        ClimateConfig config = new ClimateConfig(6000.0F, 900, 1200, 1500, 0.4F);
        DataResult<ClimateConfig> result = ClimateConfigValidator.validate(config);
        assertTrue(result.isSuccess());
        assertSame(config, result.result().orElseThrow());
    }

    @Test
    void climateRadiusMustBeFiniteAndInRange() {
        assertInvalid(new ClimateConfig(Float.NaN, 600, 800, 1000, 0.25F), "climate_radius");
        assertInvalid(new ClimateConfig(255.0F, 600, 800, 1000, 0.25F), "climate_radius");
        assertInvalid(new ClimateConfig(32001.0F, 600, 800, 1000, 0.25F), "climate_radius");
    }

    @Test
    void noiseScalesMustBeInRange() {
        assertInvalid(new ClimateConfig(4000.0F, 15, 800, 1000, 0.25F), "temperature_scale");
        assertInvalid(new ClimateConfig(4000.0F, 600, 15, 1000, 0.25F), "moisture_scale");
        assertInvalid(new ClimateConfig(4000.0F, 600, 800, 15, 0.25F), "wind_scale");
    }

    @Test
    void seedOffsetsMustBeInRange() {
        assertInvalid(new ClimateConfig(4000.0F, 600, -1_000_001,
                800, 700, 1000, 0.25F,
                0.0F, 1.0F, 0.0F,
                0.0F, 1.0F, 0.0F), "temperature_seed_offset");
        assertInvalid(new ClimateConfig(4000.0F, 600, 600,
                800, 1_000_001, 1000, 0.25F,
                0.0F, 1.0F, 0.0F,
                0.0F, 1.0F, 0.0F), "moisture_seed_offset");
    }

    @Test
    void perturbationMustBeFiniteAndInRange() {
        assertInvalid(new ClimateConfig(4000.0F, 600, 800, 1000, Float.NaN), "perturbation");
        assertInvalid(new ClimateConfig(4000.0F, 600, 800, 1000, -0.01F), "perturbation");
        assertInvalid(new ClimateConfig(4000.0F, 600, 800, 1000, 1.01F), "perturbation");
    }

    @Test
    void temperatureRangeAndBiasMustBeValid() {
        assertInvalid(configWithTemperature(Float.NaN, 0.0F, 1.0F, 0.0F), "temperature_falloff");
        assertInvalid(configWithTemperature(0.99F, 0.0F, 1.0F, 0.0F), "temperature_falloff");
        assertInvalid(configWithTemperature(10.01F, 0.0F, 1.0F, 0.0F), "temperature_falloff");
        assertInvalid(configWithTemperature(1.0F, Float.NaN, 1.0F, 0.0F), "temperature_min");
        assertInvalid(configWithTemperature(1.0F, 0.0F, Float.NaN, 0.0F), "temperature_max");
        assertInvalid(configWithTemperature(1.0F, -0.01F, 1.0F, 0.0F), "temperature_min");
        assertInvalid(configWithTemperature(1.0F, 0.0F, 1.01F, 0.0F), "temperature_max");
        assertInvalid(configWithTemperature(1.0F, 0.8F, 0.2F, 0.0F), "temperature_min");
        assertInvalid(configWithTemperature(1.0F, 0.0F, 1.0F, -1.01F), "temperature_bias");
        assertInvalid(configWithTemperature(1.0F, 0.0F, 1.0F, 1.01F), "temperature_bias");
    }

    @Test
    void moistureRangeAndBiasMustBeValid() {
        assertInvalid(configWithMoisture(Float.NaN, 0.0F, 1.0F, 0.0F), "moisture_falloff");
        assertInvalid(configWithMoisture(0.99F, 0.0F, 1.0F, 0.0F), "moisture_falloff");
        assertInvalid(configWithMoisture(10.01F, 0.0F, 1.0F, 0.0F), "moisture_falloff");
        assertInvalid(configWithMoisture(1.0F, Float.NaN, 1.0F, 0.0F), "moisture_min");
        assertInvalid(configWithMoisture(1.0F, 0.0F, Float.NaN, 0.0F), "moisture_max");
        assertInvalid(configWithMoisture(1.0F, -0.01F, 1.0F, 0.0F), "moisture_min");
        assertInvalid(configWithMoisture(1.0F, 0.0F, 1.01F, 0.0F), "moisture_max");
        assertInvalid(configWithMoisture(1.0F, 0.8F, 0.2F, 0.0F), "moisture_min");
        assertInvalid(configWithMoisture(1.0F, 0.0F, 1.0F, -1.01F), "moisture_bias");
        assertInvalid(configWithMoisture(1.0F, 0.0F, 1.0F, 1.01F), "moisture_bias");
    }

    private static void assertInvalid(ClimateConfig config, String field) {
        DataResult<ClimateConfig> result = ClimateConfigValidator.validate(config);
        assertFalse(result.isSuccess());
        assertTrue(result.error().orElseThrow().message().contains(field));
    }

    private static ClimateConfig configWithTemperature(float falloff, float min, float max, float bias) {
        return new ClimateConfig(4000.0F, 600, 800, 1000, 0.25F,
                falloff, min, max, bias,
                1.0F, 0.0F, 1.0F, 0.0F);
    }

    private static ClimateConfig configWithMoisture(float falloff, float min, float max, float bias) {
        return new ClimateConfig(4000.0F, 600, 800, 1000, 0.25F,
                1.0F, 0.0F, 1.0F, 0.0F,
                falloff, min, max, bias);
    }
}
