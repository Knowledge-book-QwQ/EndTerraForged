package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.DataResult;

class CaveSystemConfigValidatorTest {

    @Test
    void acceptsDefaults() {
        assertTrue(CaveSystemConfigValidator.validate(CaveSystemConfig.DEFAULT).result().isPresent());
    }

    @Test
    void rejectsOutOfRangeSeedOffset() {
        DataResult<CaveSystemConfig> result = CaveSystemConfigValidator.validate(
                new CaveSystemConfig(true, 1_000_001, 128, 1536, 0.75F, 0.65F, 0.03F));

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("seed_offset"));
    }

    @Test
    void rejectsOutOfRangeDepth() {
        DataResult<CaveSystemConfig> result = CaveSystemConfigValidator.validate(
                new CaveSystemConfig(true, 2400, 128, 4097, 0.75F, 0.65F, 0.03F));

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("depth_end"));
    }

    @Test
    void rejectsNanUnitField() {
        DataResult<CaveSystemConfig> result = CaveSystemConfigValidator.validate(
                new CaveSystemConfig(true, 2400, 128, 1536, Float.NaN, 0.65F, 0.03F));

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("spectacle_bias"));
    }
}
