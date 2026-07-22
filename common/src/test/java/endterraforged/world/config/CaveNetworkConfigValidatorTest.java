package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.DataResult;

class CaveNetworkConfigValidatorTest {

    @Test
    void acceptsDefaults() {
        assertTrue(CaveNetworkConfigValidator.validate(CaveNetworkConfig.DEFAULT).result().isPresent());
    }

    @Test
    void rejectsOutOfRangeDensity() {
        DataResult<CaveNetworkConfig> result = CaveNetworkConfigValidator.validate(
                new CaveNetworkConfig(1024, 1.1F, 384, 2.0F, 0.18F, 0.45F, 0.2F));

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("network_density"));
    }

    @Test
    void rejectsOutOfRangeBranching() {
        DataResult<CaveNetworkConfig> result = CaveNetworkConfigValidator.validate(
                new CaveNetworkConfig(1024, 0.35F, 384, 8.1F, 0.18F, 0.45F, 0.2F));

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("branching_factor"));
    }

    @Test
    void rejectsNanSlope() {
        DataResult<CaveNetworkConfig> result = CaveNetworkConfigValidator.validate(
                new CaveNetworkConfig(1024, 0.35F, 384, 2.0F, 0.18F, Float.NaN, 0.2F));

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("max_slope"));
    }
}
