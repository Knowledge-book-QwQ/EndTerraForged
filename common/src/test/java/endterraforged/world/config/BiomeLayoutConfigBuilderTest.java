package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BiomeLayoutConfigBuilderTest {

    @Test
    void defaultConstructorStartsAtDefaults() {
        assertEquals(BiomeLayoutConfig.DEFAULT, new BiomeLayoutConfigBuilder().build());
    }

    @Test
    void roundTripFromConfigIsIdentity() {
        BiomeLayoutConfig source = new BiomeLayoutConfig(32, 6.0F, 35.0F, -5.0F,
                180, 3, 2.5F, 0.65F, 22.0F,
                240, 36.0F,
                360, 2, 0.35F,
                new BiomeVariantBlendConfig(90, 3));
        assertEquals(source, new BiomeLayoutConfigBuilder(source).build());
    }

    @Test
    void settersStoreValues() {
        BiomeLayoutConfig built = new BiomeLayoutConfigBuilder()
                .mainIslandRadius(32)
                .radialCoefficient(6.0F)
                .highlandThreshold(35.0F)
                .midlandFloor(-5.0F)
                .biomeEdgeScale(180)
                .biomeEdgeOctaves(3)
                .biomeEdgeLacunarity(2.5F)
                .biomeEdgeGain(0.65F)
                .biomeEdgeStrength(22.0F)
                .biomeWarpScale(240)
                .biomeWarpStrength(36.0F)
                .outerNoiseScale(360)
                .outerNoiseOctaves(2)
                .outerNoiseThreshold(0.35F)
                .variantBlendScale(90)
                .variantBlendOctaves(3)
                .build();

        assertEquals(new BiomeLayoutConfig(32, 6.0F, 35.0F, -5.0F,
                180, 3, 2.5F, 0.65F, 22.0F,
                240, 36.0F,
                360, 2, 0.35F,
                new BiomeVariantBlendConfig(90, 3)), built);
    }

    @Test
    void resetRestoresDefaults() {
        BiomeLayoutConfigBuilder builder = new BiomeLayoutConfigBuilder()
                .mainIslandRadius(32)
                .radialCoefficient(6.0F);

        builder.reset();

        assertEquals(BiomeLayoutConfig.DEFAULT, builder.build());
    }

    @Test
    void buildRejectsInvalidState() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new BiomeLayoutConfigBuilder()
                        .midlandFloor(60.0F)
                        .build());

        assertTrue(e.getMessage().contains("midland_floor"));
    }
}
