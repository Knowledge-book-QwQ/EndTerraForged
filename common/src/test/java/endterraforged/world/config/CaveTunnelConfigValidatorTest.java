package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.DataResult;

class CaveTunnelConfigValidatorTest {

    @Test
    void acceptsDefaults() {
        assertTrue(CaveTunnelConfigValidator.validate(CaveTunnelConfig.DEFAULT).result().isPresent());
    }

    @Test
    void rejectsInvalidEntranceProbability() {
        DataResult<CaveTunnelConfig> result = CaveTunnelConfigValidator.validate(
                new CaveTunnelConfig(true, -0.01F, 0, 0.0F, 0.0F, 0.0F));

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("entrance_probability"));
    }

    @Test
    void rejectsInvalidCheeseDepthOffset() {
        DataResult<CaveTunnelConfig> result = CaveTunnelConfigValidator.validate(
                new CaveTunnelConfig(true, 0.0F, 8.1F, 0.0F, 0.0F, 0.0F));

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("cheese_depth_offset"));
    }

    @Test
    void rejectsNanProbability() {
        DataResult<CaveTunnelConfig> result = CaveTunnelConfigValidator.validate(
                new CaveTunnelConfig(true, 0.0F, 0, Float.NaN, 0.0F, 0.0F));

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("cheese_probability"));
    }
}
