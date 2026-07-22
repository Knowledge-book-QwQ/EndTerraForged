package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TerrainConfigBuilderTest {

    @Test
    void defaultConstructorStartsAtDefaults() {
        assertEquals(TerrainConfig.DEFAULT, new TerrainConfigBuilder().build());
    }

    @Test
    void sourceConstructorPreservesEveryField() {
        TerrainConfig source = customConfig();

        assertEquals(source, new TerrainConfigBuilder(source).build());
    }

    @Test
    void settersBuildExpectedConfig() {
        TerrainConfig built = new TerrainConfigBuilder()
                .terrainSeedOffset(42)
                .terrainRegionSize(1600)
                .globalVerticalScale(0.75F)
                .globalHorizontalScale(2.25F)
                .terrainBlendRange(0.35F)
                .terrainLayoutMode(TerrainLayoutMode.REGION_PLANNED)
                .terrainShape(TerrainShape.ROLLING_RIDGES)
                .plains(new TerrainLayerConfig(0.5F, 1.0F, 2.0F, 3.0F))
                .hills(new TerrainLayerConfig(0.75F, 1.1F, 2.5F, 3.5F))
                .plateau(new TerrainLayerConfig(1.25F, 1.2F, 3.0F, 4.0F))
                .mountains(new TerrainLayerConfig(2.0F, 1.25F, 3.0F, 4.0F))
                .volcano(TerrainLayerConfig.DISABLED)
                .build();

        assertEquals(customConfig(), built);
    }

    @Test
    void resetRestoresDefaults() {
        TerrainConfigBuilder builder = new TerrainConfigBuilder(customConfig());

        builder.reset();

        assertEquals(TerrainConfig.DEFAULT, builder.build());
    }

    @Test
    void buildRejectsInvalidState() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new TerrainConfigBuilder().terrainBlendRange(1.1F).build());

        assertTrue(error.getMessage().contains("terrain_blend_range"));
    }

    private static TerrainConfig customConfig() {
        return new TerrainConfig(42, 1600, 0.75F, 2.25F, 0.35F,
                TerrainLayoutMode.REGION_PLANNED, TerrainShape.ROLLING_RIDGES,
                new TerrainLayerConfig(0.5F, 1.0F, 2.0F, 3.0F),
                new TerrainLayerConfig(0.75F, 1.1F, 2.5F, 3.5F),
                new TerrainLayerConfig(1.25F, 1.2F, 3.0F, 4.0F),
                new TerrainLayerConfig(2.0F, 1.25F, 3.0F, 4.0F),
                TerrainLayerConfig.DISABLED);
    }
}
