package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.DataResult;

class BiomeVariantBlendConfigValidatorTest {

    @Test
    void successReturnsSameInstance() {
        BiomeVariantBlendConfig config = BiomeVariantBlendConfig.DEFAULT;
        DataResult<BiomeVariantBlendConfig> result =
                BiomeVariantBlendConfigValidator.validate(config);
        assertSame(config, result.result().orElseThrow());
    }

    @Test
    void rejectsInvalidScale() {
        assertInvalid(new BiomeVariantBlendConfig(0, 2), "scale");
        assertInvalid(new BiomeVariantBlendConfig(501, 2), "scale");
    }

    @Test
    void rejectsInvalidOctaves() {
        assertInvalid(new BiomeVariantBlendConfig(50, 0), "octaves");
        assertInvalid(new BiomeVariantBlendConfig(50, 6), "octaves");
    }

    private static void assertInvalid(BiomeVariantBlendConfig config, String field) {
        DataResult<BiomeVariantBlendConfig> result =
                BiomeVariantBlendConfigValidator.validate(config);
        assertTrue(result.error().isPresent(), "expected validation to fail");
        assertTrue(result.error().orElseThrow().message().contains(field),
                "expected error to mention " + field);
    }
}
