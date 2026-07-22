package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CaveSystemConfigBuilderTest {

    @Test
    void defaultConstructorStartsAtDefaults() {
        assertEquals(CaveSystemConfig.DEFAULT, new CaveSystemConfigBuilder().build());
    }

    @Test
    void roundTripFromConfigIsIdentity() {
        CaveSystemConfig original = customConfig();

        assertEquals(original, new CaveSystemConfigBuilder(original).build());
    }

    @Test
    void resetRestoresDefaults() {
        CaveSystemConfigBuilder builder = customBuilder();

        assertNotEquals(CaveSystemConfig.DEFAULT, builder.build());
        builder.reset();

        assertEquals(CaveSystemConfig.DEFAULT, builder.build());
    }

    @Test
    void eachSetterStoresValue() {
        assertEquals(customConfig(), customBuilder().build());
    }

    @Test
    void gettersReflectCurrentState() {
        CaveSystemConfigBuilder builder = new CaveSystemConfigBuilder()
                .enabled(true)
                .depthStart(64)
                .depthEnd(2048)
                .spectacleBias(0.95F);

        assertTrue(builder.enabled());
        assertEquals(64, builder.depthStart());
        assertEquals(2048, builder.depthEnd());
        assertEquals(0.95F, builder.spectacleBias());
        assertEquals(CaveSystemConfig.DEFAULT.connectivity(), builder.connectivity());
    }

    @Test
    void buildRejectsInvalidState() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new CaveSystemConfigBuilder()
                        .depthStart(2000)
                        .depthEnd(1000)
                        .build());

        assertTrue(e.getMessage().contains("depth_start"));
    }

    private static CaveSystemConfigBuilder customBuilder() {
        return new CaveSystemConfigBuilder()
                .enabled(true)
                .seedOffset(2600)
                .depthStart(96)
                .depthEnd(1800)
                .spectacleBias(0.9F)
                .connectivity(0.72F)
                .surfaceOpeningChance(0.05F);
    }

    private static CaveSystemConfig customConfig() {
        return new CaveSystemConfig(true, 2600, 96, 1800, 0.9F, 0.72F, 0.05F);
    }
}
