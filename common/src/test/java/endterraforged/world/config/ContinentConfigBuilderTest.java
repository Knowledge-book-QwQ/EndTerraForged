package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.noise.DistanceFunction;

class ContinentConfigBuilderTest {

    @Test
    void defaultConstructorStartsAtDefaults() {
        assertEquals(ContinentConfig.defaults(), new ContinentConfigBuilder().build());
    }

    @Test
    void roundTripFromConfigIsIdentity() {
        ContinentConfig config = custom();
        assertEquals(config, new ContinentConfigBuilder(config).build());
    }

    @Test
    void resetRestoresDefaults() {
        ContinentConfigBuilder builder = new ContinentConfigBuilder(custom());
        assertNotEquals(ContinentConfig.defaults(), builder.build());
        builder.reset();
        assertEquals(ContinentConfig.defaults(), builder.build());
    }

    @Test
    void recommendedRtfMultiProfileUsesTheDocumentedMacroBaseline() {
        ContinentConfig built = new ContinentConfigBuilder()
                .applyRecommendedProfile(ContinentAlgorithm.RTF_MULTI)
                .build();

        assertEquals(ContinentConfig.rtfMultiDefaults(), built);
        assertEquals(3000, built.continentScale());
        assertEquals(0.7F, built.continentJitter());
    }

    @Test
    void resetForCurrentAlgorithmKeepsTheSelectedAlgorithmProfile() {
        ContinentConfig built = new ContinentConfigBuilder(custom())
                .continentAlgorithm(ContinentAlgorithm.RTF_MULTI)
                .resetForCurrentAlgorithm()
                .build();

        assertEquals(ContinentConfig.rtfMultiDefaults(), built);
    }

    @Test
    void eachSetterStoresValue() {
        ContinentConfig built = new ContinentConfigBuilder()
                .islandsScale(320)
                .continentScale(960)
                .continentShape(DistanceFunction.NATURAL)
                .continentJitter(0.8F)
                .continentSkipping(0.2F)
                .continentSizeVariance(0.45F)
                .continentNoiseOctaves(6)
                .continentNoiseGain(0.3F)
                .continentNoiseLacunarity(3.5F)
                .featureSpread(0.9F)
                .islandRadius(0.7F)
                .islandScatter(0.35F)
                .riftThreshold(0.55F)
                .riftStrength(0.75F)
                .warpScale(420)
                .warpStrength(64.0F)
                .outerContinentScale(4096)
                .landmassVolumeMode(LandmassVolumeMode.FLOATING_SHELF)
                .shelfThickness(224)
                .shelfEdgeThickness(64)
                .coastShape(ContinentCoastShape.ORGANIC)
                .coastScale(2304)
                .coastStrength(0.35F)
                .coastCellBlend(0.85F)
                .continentAlgorithm(ContinentAlgorithm.RTF_MULTI)
                .continentBands(new ContinentBandsConfig(true, 0.08F, 0.22F, 0.34F, 0.46F, 0.58F))
                .build();
        assertEquals(new ContinentConfigBuilder(custom())
                .continentBands(new ContinentBandsConfig(true, 0.08F, 0.22F, 0.34F, 0.46F, 0.58F))
                .build(), built);
    }

    @Test
    void buildRejectsInvalidState() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new ContinentConfigBuilder()
                        .islandsScale(8)
                        .build());
        assertTrue(e.getMessage().contains("islands_scale"));
    }

    private static ContinentConfig custom() {
        return new ContinentConfig(320, 960, DistanceFunction.NATURAL,
                0.8F, 0.2F, 0.45F, 6, 0.3F, 3.5F,
                0.9F, 0.7F, 0.35F, 0.55F, 0.75F, 420, 64.0F, 4096,
                LandmassVolumeMode.FLOATING_SHELF, 224, 64,
                ContinentCoastShape.ORGANIC, 2304, 0.35F, 0.85F,
                ContinentAlgorithm.RTF_MULTI);
    }
}
