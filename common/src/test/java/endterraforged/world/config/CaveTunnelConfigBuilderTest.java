package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CaveTunnelConfigBuilderTest {

    @Test
    void defaultConstructorStartsAtDefaults() {
        CaveTunnelConfigBuilder builder = new CaveTunnelConfigBuilder();

        assertEquals(CaveTunnelConfig.DEFAULT, builder.build());
    }

    @Test
    void roundTripFromConfigIsIdentity() {
        CaveTunnelConfig original = customConfig();

        assertEquals(original, new CaveTunnelConfigBuilder(original).build());
    }

    @Test
    void resetRestoresDefaults() {
        CaveTunnelConfigBuilder builder = customBuilder();

        assertNotEquals(CaveTunnelConfig.DEFAULT, builder.build());
        builder.reset();

        assertEquals(CaveTunnelConfig.DEFAULT, builder.build());
    }

    @Test
    void eachSetterStoresValue() {
        assertEquals(customConfig(), customBuilder().build());
    }

    @Test
    void gettersReflectCurrentState() {
        CaveTunnelConfigBuilder builder = new CaveTunnelConfigBuilder()
                .enabled(true)
                .entranceProbability(0.25F)
                .cheeseDepthOffset(1.75F)
                .cheeseProbability(0.4F);

        assertTrue(builder.enabled());
        assertEquals(0.25F, builder.entranceProbability());
        assertEquals(1.75F, builder.cheeseDepthOffset());
        assertEquals(0.4F, builder.cheeseProbability());
        assertEquals(CaveTunnelConfig.DEFAULT.noodleProbability(), builder.noodleProbability());
    }

    @Test
    void buildRejectsInvalidState() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new CaveTunnelConfigBuilder()
                        .cheeseProbability(1.1F)
                        .build());

        assertTrue(e.getMessage().contains("cheese_probability"));
    }

    private static CaveTunnelConfigBuilder customBuilder() {
        return new CaveTunnelConfigBuilder()
                .enabled(true)
                .entranceProbability(0.25F)
                .cheeseDepthOffset(1.75F)
                .cheeseProbability(0.4F)
                .spaghettiProbability(0.3F)
                .noodleProbability(0.2F);
    }

    private static CaveTunnelConfig customConfig() {
        return new CaveTunnelConfig(true, 0.25F, 1.75F, 0.4F, 0.3F, 0.2F);
    }
}
