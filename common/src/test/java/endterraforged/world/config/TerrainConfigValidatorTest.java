package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.DataResult;

import org.junit.jupiter.api.Test;

class TerrainConfigValidatorTest {

    @Test
    void rejectsNullConfigAndMissingShape() {
        assertInvalid(TerrainConfigValidator.validate(null), "terrain config");

        TerrainConfig missingShape = new TerrainConfig(
                0, 1200, 1.0F, 1.0F, 0.0F, null,
                TerrainLayerConfig.DISABLED, TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DISABLED, TerrainLayerConfig.DEFAULT,
                TerrainLayerConfig.DISABLED);
        assertInvalid(TerrainConfigValidator.validate(missingShape), "terrain_shape");
    }

    @Test
    void rejectsOutOfRangeRegionAndNonFiniteScales() {
        assertInvalid(TerrainConfigValidator.validate(config(124, 1.0F, 1.0F,
                TerrainLayerConfig.DEFAULT)), "terrain_region_size");
        assertInvalid(TerrainConfigValidator.validate(config(1200, Float.NaN, 1.0F,
                TerrainLayerConfig.DEFAULT)), "global_vertical_scale");
        assertInvalid(TerrainConfigValidator.validate(config(1200, 1.0F,
                Float.POSITIVE_INFINITY, TerrainLayerConfig.DEFAULT)), "global_horizontal_scale");
    }

    @Test
    void rejectsInvalidLayerWithLayerContext() {
        TerrainLayerConfig invalidVolcano = new TerrainLayerConfig(10.1F, 1.0F, 1.0F, 1.0F);

        TerrainConfig config = new TerrainConfig(
                0, 1200, 1.0F, 1.0F, 0.0F, TerrainShape.SHATTERED_RIDGES,
                TerrainLayerConfig.DISABLED, TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DISABLED, TerrainLayerConfig.DEFAULT, invalidVolcano);

        assertInvalid(TerrainConfigValidator.validate(config), "terrain.volcano");
    }

    @Test
    void rejectsRegionPlannedCompactVolcanoUntilItsRuntimeIsValidated() {
        TerrainConfig config = new TerrainConfig(
                0, 1600, 1.0F, 1.0F, 0.0F, TerrainLayoutMode.REGION_PLANNED,
                TerrainShape.SHATTERED_RIDGES,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DISABLED, TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DEFAULT);

        assertInvalid(TerrainConfigValidator.validate(config), "terrain.volcano");
    }

    private static TerrainConfig config(int regionSize, float verticalScale,
                                        float horizontalScale, TerrainLayerConfig mountains) {
        return new TerrainConfig(
                0, regionSize, verticalScale, horizontalScale, 0.0F,
                TerrainShape.SHATTERED_RIDGES,
                TerrainLayerConfig.DISABLED, TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DISABLED, mountains, TerrainLayerConfig.DISABLED);
    }

    private static void assertInvalid(DataResult<TerrainConfig> result, String expectedMessage) {
        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains(expectedMessage));
    }
}
