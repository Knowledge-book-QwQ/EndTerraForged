package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CaveChamberConfigBuilderTest {

    @Test
    void defaultConstructorStartsAtDefaults() {
        assertEquals(CaveChamberConfig.DEFAULT, new CaveChamberConfigBuilder().build());
    }

    @Test
    void roundTripFromConfigIsIdentity() {
        CaveChamberConfig original = customConfig();

        assertEquals(original, new CaveChamberConfigBuilder(original).build());
    }

    @Test
    void resetRestoresDefaults() {
        CaveChamberConfigBuilder builder = customBuilder();

        assertNotEquals(CaveChamberConfig.DEFAULT, builder.build());
        builder.reset();

        assertEquals(CaveChamberConfig.DEFAULT, builder.build());
    }

    @Test
    void eachSetterStoresValue() {
        assertEquals(customConfig(), customBuilder().build());
    }

    @Test
    void gettersReflectCurrentState() {
        CaveChamberConfigBuilder builder = new CaveChamberConfigBuilder()
                .chamberProbability(0.62F)
                .minRadius(72)
                .maxRadius(320);

        assertEquals(0.62F, builder.chamberProbability());
        assertEquals(72, builder.minRadius());
        assertEquals(320, builder.maxRadius());
        assertEquals(CaveChamberConfig.DEFAULT.roughness(), builder.roughness());
    }

    @Test
    void buildRejectsInvalidState() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new CaveChamberConfigBuilder()
                        .minRadius(240)
                        .maxRadius(120)
                        .build());

        assertTrue(e.getMessage().contains("min_radius"));
    }

    private static CaveChamberConfigBuilder customBuilder() {
        return new CaveChamberConfigBuilder()
                .chamberProbability(0.62F)
                .minRadius(72)
                .maxRadius(320)
                .verticalStretch(2.2F)
                .floorBias(0.42F)
                .roughness(0.7F);
    }

    private static CaveChamberConfig customConfig() {
        return new CaveChamberConfig(0.62F, 72, 320, 2.2F, 0.42F, 0.7F);
    }
}
