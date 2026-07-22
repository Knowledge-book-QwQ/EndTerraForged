package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.DataResult;

class SubsurfaceConfigValidatorTest {

    @Test
    void acceptsDefaults() {
        assertTrue(SubsurfaceConfigValidator.validate(SubsurfaceConfig.DEFAULT).result().isPresent());
    }

    @Test
    void rejectsInvalidNestedAbyssConfig() {
        SubsurfaceConfig config = new SubsurfaceConfig(
                new AbyssPitConfig(true, 1600, 16, 0.5F, 0.1F, 384, 0.25F));

        DataResult<SubsurfaceConfig> result = SubsurfaceConfigValidator.validate(config);

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("pit_scale"));
    }

    @Test
    void rejectsInvalidDepthCurve() {
        SubsurfaceConfig config = new SubsurfaceConfig(
                new AbyssPitConfig(true, 1600, 900, 3, 2.0F, 0.5F,
                        0.5F, 0.1F, 384, 0.0F, 0.25F));

        DataResult<SubsurfaceConfig> result = SubsurfaceConfigValidator.validate(config);

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("depth_curve"));
    }

    @Test
    void rejectsInvalidNestedCaveConfig() {
        SubsurfaceConfig config = new SubsurfaceConfig(
                AbyssPitConfig.DEFAULT,
                new CaveTunnelConfig(true, 0.0F, 0, 1.1F, 0.0F, 0.0F));

        DataResult<SubsurfaceConfig> result = SubsurfaceConfigValidator.validate(config);

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("caves"));
        assertTrue(result.error().orElseThrow().message().contains("cheese_probability"));
    }

    @Test
    void rejectsInvalidNestedCaveSystemConfig() {
        SubsurfaceConfig config = new SubsurfaceConfig(
                AbyssPitConfig.DEFAULT, CaveTunnelConfig.DEFAULT,
                new CaveSystemConfig(true, 2400, 1600, 1200, 0.75F, 0.65F, 0.03F),
                CaveNetworkConfig.DEFAULT,
                CaveChamberConfig.DEFAULT);

        DataResult<SubsurfaceConfig> result = SubsurfaceConfigValidator.validate(config);

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("cave_system"));
        assertTrue(result.error().orElseThrow().message().contains("depth_start"));
    }

    @Test
    void rejectsInvalidNestedCaveNetworkConfig() {
        SubsurfaceConfig config = new SubsurfaceConfig(
                AbyssPitConfig.DEFAULT, CaveTunnelConfig.DEFAULT,
                CaveSystemConfig.DEFAULT,
                new CaveNetworkConfig(64, 0.35F, 384, 2.0F, 0.18F, 0.45F, 0.2F),
                CaveChamberConfig.DEFAULT);

        DataResult<SubsurfaceConfig> result = SubsurfaceConfigValidator.validate(config);

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("cave_network"));
        assertTrue(result.error().orElseThrow().message().contains("region_size"));
    }

    @Test
    void rejectsInvalidNestedCaveChamberConfig() {
        SubsurfaceConfig config = new SubsurfaceConfig(
                AbyssPitConfig.DEFAULT, CaveTunnelConfig.DEFAULT,
                CaveSystemConfig.DEFAULT,
                CaveNetworkConfig.DEFAULT,
                new CaveChamberConfig(0.45F, 240, 120, 1.6F, 0.35F, 0.55F));

        DataResult<SubsurfaceConfig> result = SubsurfaceConfigValidator.validate(config);

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("cave_chambers"));
        assertTrue(result.error().orElseThrow().message().contains("min_radius"));
    }
}
