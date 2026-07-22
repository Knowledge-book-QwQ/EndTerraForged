package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.DataResult;

class BiomeLayoutConfigValidatorTest {

    @Test
    void successReturnsSameInstance() {
        BiomeLayoutConfig config = BiomeLayoutConfig.DEFAULT;
        DataResult<BiomeLayoutConfig> result = BiomeLayoutConfigValidator.validate(config);
        assertSame(config, result.result().orElseThrow());
    }

    @Test
    void rejectsInvalidWarpScale() {
        assertInvalid(new BiomeLayoutConfig(18, 8.0F, 40.0F, 0.0F,
                200, 4, 2.0F, 0.5F, 15.0F,
                0, 0.0F,
                400, 4, 0.2F), "biome_warp_scale");
    }

    @Test
    void rejectsInvalidWarpStrength() {
        assertInvalid(new BiomeLayoutConfig(18, 8.0F, 40.0F, 0.0F,
                200, 4, 2.0F, 0.5F, 15.0F,
                300, Float.NaN,
                400, 4, 0.2F), "biome_warp_strength");
        assertInvalid(new BiomeLayoutConfig(18, 8.0F, 40.0F, 0.0F,
                200, 4, 2.0F, 0.5F, 15.0F,
                300, -0.01F,
                400, 4, 0.2F), "biome_warp_strength");
        assertInvalid(new BiomeLayoutConfig(18, 8.0F, 40.0F, 0.0F,
                200, 4, 2.0F, 0.5F, 15.0F,
                300, 500.01F,
                400, 4, 0.2F), "biome_warp_strength");
    }

    @Test
    void rejectsInvalidVariantBlend() {
        assertInvalid(new BiomeLayoutConfig(18, 8.0F, 40.0F, 0.0F,
                200, 4, 2.0F, 0.5F, 15.0F,
                300, 0.0F,
                400, 4, 0.2F,
                new BiomeVariantBlendConfig(0, 2)), "variant_blend");
        assertInvalid(new BiomeLayoutConfig(18, 8.0F, 40.0F, 0.0F,
                200, 4, 2.0F, 0.5F, 15.0F,
                300, 0.0F,
                400, 4, 0.2F,
                new BiomeVariantBlendConfig(50, 0)), "variant_blend");
    }

    private static void assertInvalid(BiomeLayoutConfig config, String field) {
        DataResult<BiomeLayoutConfig> result = BiomeLayoutConfigValidator.validate(config);
        assertTrue(result.error().isPresent(), "expected validation to fail");
        assertTrue(result.error().orElseThrow().message().contains(field),
                "expected error to mention " + field);
    }
}
