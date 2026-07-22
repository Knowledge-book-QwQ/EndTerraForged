package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.DataResult;

class CaveChamberConfigValidatorTest {

    @Test
    void acceptsDefaults() {
        assertTrue(CaveChamberConfigValidator.validate(CaveChamberConfig.DEFAULT).result().isPresent());
    }

    @Test
    void rejectsOutOfRangeProbability() {
        DataResult<CaveChamberConfig> result = CaveChamberConfigValidator.validate(
                new CaveChamberConfig(1.1F, 48, 224, 1.6F, 0.35F, 0.55F));

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("chamber_probability"));
    }

    @Test
    void rejectsOutOfRangeRadius() {
        DataResult<CaveChamberConfig> result = CaveChamberConfigValidator.validate(
                new CaveChamberConfig(0.45F, 4, 224, 1.6F, 0.35F, 0.55F));

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("min_radius"));
    }

    @Test
    void rejectsNanRoughness() {
        DataResult<CaveChamberConfig> result = CaveChamberConfigValidator.validate(
                new CaveChamberConfig(0.45F, 48, 224, 1.6F, 0.35F, Float.NaN));

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("roughness"));
    }
}
