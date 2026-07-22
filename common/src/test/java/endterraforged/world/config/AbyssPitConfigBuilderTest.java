package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AbyssPitConfigBuilderTest {

    @Test
    void defaultConstructorStartsAtDefaults() {
        AbyssPitConfigBuilder builder = new AbyssPitConfigBuilder();

        assertEquals(AbyssPitConfig.DEFAULT, builder.build());
    }

    @Test
    void roundTripFromConfigIsIdentity() {
        AbyssPitConfig original = customConfig();

        assertEquals(original, new AbyssPitConfigBuilder(original).build());
    }

    @Test
    void resetRestoresDefaults() {
        AbyssPitConfigBuilder builder = customBuilder();

        assertNotEquals(AbyssPitConfig.DEFAULT, builder.build());
        builder.reset();

        assertEquals(AbyssPitConfig.DEFAULT, builder.build());
    }

    @Test
    void eachSetterStoresValue() {
        assertEquals(customConfig(), customBuilder().build());
    }

    @Test
    void gettersReflectCurrentState() {
        AbyssPitConfigBuilder builder = new AbyssPitConfigBuilder()
                .enabled(true)
                .pitScale(512)
                .pitOctaves(5)
                .depth(256);

        assertTrue(builder.enabled());
        assertEquals(512, builder.pitScale());
        assertEquals(5, builder.pitOctaves());
        assertEquals(256, builder.depth());
        assertEquals(AbyssPitConfig.DEFAULT.threshold(), builder.threshold());
    }

    @Test
    void buildRejectsInvalidState() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new AbyssPitConfigBuilder()
                        .pitScale(16)
                        .build());

        assertTrue(e.getMessage().contains("pit_scale"));
    }

    private static AbyssPitConfigBuilder customBuilder() {
        return new AbyssPitConfigBuilder()
                .enabled(true)
                .seedOffset(1700)
                .pitScale(512)
                .pitOctaves(5)
                .pitLacunarity(2.5F)
                .pitGain(0.65F)
                .threshold(0.7F)
                .edgeFalloff(0.2F)
                .depth(256)
                .depthCurve(1.4F)
                .minLandness(0.5F);
    }

    private static AbyssPitConfig customConfig() {
        return new AbyssPitConfig(true, 1700, 512, 5, 2.5F, 0.65F,
                0.7F, 0.2F, 256, 1.4F, 0.5F);
    }
}
