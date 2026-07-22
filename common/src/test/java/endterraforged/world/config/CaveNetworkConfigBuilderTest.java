package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CaveNetworkConfigBuilderTest {

    @Test
    void defaultConstructorStartsAtDefaults() {
        assertEquals(CaveNetworkConfig.DEFAULT, new CaveNetworkConfigBuilder().build());
    }

    @Test
    void roundTripFromConfigIsIdentity() {
        CaveNetworkConfig original = customConfig();

        assertEquals(original, new CaveNetworkConfigBuilder(original).build());
    }

    @Test
    void resetRestoresDefaults() {
        CaveNetworkConfigBuilder builder = customBuilder();

        assertNotEquals(CaveNetworkConfig.DEFAULT, builder.build());
        builder.reset();

        assertEquals(CaveNetworkConfig.DEFAULT, builder.build());
    }

    @Test
    void eachSetterStoresValue() {
        assertEquals(customConfig(), customBuilder().build());
    }

    @Test
    void gettersReflectCurrentState() {
        CaveNetworkConfigBuilder builder = new CaveNetworkConfigBuilder()
                .regionSize(1536)
                .networkDensity(0.42F)
                .loopChance(0.22F);

        assertEquals(1536, builder.regionSize());
        assertEquals(0.42F, builder.networkDensity());
        assertEquals(0.22F, builder.loopChance());
        assertEquals(CaveNetworkConfig.DEFAULT.chamberSpacing(), builder.chamberSpacing());
    }

    @Test
    void buildRejectsInvalidState() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new CaveNetworkConfigBuilder()
                        .regionSize(64)
                        .build());

        assertTrue(e.getMessage().contains("region_size"));
    }

    private static CaveNetworkConfigBuilder customBuilder() {
        return new CaveNetworkConfigBuilder()
                .regionSize(1536)
                .networkDensity(0.42F)
                .chamberSpacing(512)
                .branchingFactor(2.5F)
                .loopChance(0.22F)
                .maxSlope(0.6F)
                .minLandness(0.3F);
    }

    private static CaveNetworkConfig customConfig() {
        return new CaveNetworkConfig(1536, 0.42F, 512, 2.5F, 0.22F, 0.6F, 0.3F);
    }
}
