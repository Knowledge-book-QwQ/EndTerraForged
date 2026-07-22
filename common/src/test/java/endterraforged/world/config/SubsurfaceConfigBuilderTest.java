package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SubsurfaceConfigBuilderTest {

    @Test
    void defaultConstructorStartsAtDisabledDefaults() {
        SubsurfaceConfigBuilder builder = new SubsurfaceConfigBuilder();

        assertEquals(SubsurfaceConfig.DISABLED, builder.build());
    }

    @Test
    void roundTripFromConfigIsIdentity() {
        SubsurfaceConfig original = customConfig();

        assertEquals(original, new SubsurfaceConfigBuilder(original).build());
    }

    @Test
    void resetRestoresDisabledDefaults() {
        SubsurfaceConfigBuilder builder = new SubsurfaceConfigBuilder()
                .abyssEnabled(true)
                .abyssSeedOffset(1700)
                .abyssPitScale(512)
                .abyssPitOctaves(5)
                .abyssPitLacunarity(2.5F)
                .abyssPitGain(0.65F)
                .abyssThreshold(0.7F)
                .abyssEdgeFalloff(0.2F)
                .abyssDepth(256)
                .abyssDepthCurve(1.4F)
                .abyssMinLandness(0.5F)
                .cavesEnabled(true)
                .caveEntranceProbability(0.25F)
                .caveCheeseDepthOffset(1.75F)
                .caveCheeseProbability(0.4F)
                .caveSpaghettiProbability(0.3F)
                .caveNoodleProbability(0.2F)
                .caveSystemConfig(customCaveSystemConfig())
                .caveNetworkConfig(customCaveNetworkConfig())
                .caveChamberConfig(customCaveChamberConfig());

        assertNotEquals(SubsurfaceConfig.DISABLED, builder.build());
        builder.reset();

        assertEquals(SubsurfaceConfig.DISABLED, builder.build());
    }

    @Test
    void eachSetterStoresValue() {
        SubsurfaceConfig built = new SubsurfaceConfigBuilder()
                .abyssEnabled(true)
                .abyssSeedOffset(1700)
                .abyssPitScale(512)
                .abyssPitOctaves(5)
                .abyssPitLacunarity(2.5F)
                .abyssPitGain(0.65F)
                .abyssThreshold(0.7F)
                .abyssEdgeFalloff(0.2F)
                .abyssDepth(256)
                .abyssDepthCurve(1.4F)
                .abyssMinLandness(0.5F)
                .cavesEnabled(true)
                .caveEntranceProbability(0.25F)
                .caveCheeseDepthOffset(1.75F)
                .caveCheeseProbability(0.4F)
                .caveSpaghettiProbability(0.3F)
                .caveNoodleProbability(0.2F)
                .caveSystemConfig(customCaveSystemConfig())
                .caveNetworkConfig(customCaveNetworkConfig())
                .caveChamberConfig(customCaveChamberConfig())
                .build();

        assertEquals(customConfig(), built);
    }

    @Test
    void gettersReflectCurrentState() {
        SubsurfaceConfigBuilder builder = new SubsurfaceConfigBuilder()
                .abyssEnabled(true)
                .abyssPitScale(512)
                .abyssPitOctaves(5)
                .abyssDepth(256)
                .cavesEnabled(true)
                .caveCheeseDepthOffset(1.75F)
                .caveCheeseProbability(0.4F)
                .caveSystemConfig(customCaveSystemConfig())
                .caveNetworkConfig(customCaveNetworkConfig())
                .caveChamberConfig(customCaveChamberConfig());

        assertTrue(builder.abyssEnabled());
        assertEquals(512, builder.abyssPitScale());
        assertEquals(5, builder.abyssPitOctaves());
        assertEquals(256, builder.abyssDepth());
        assertEquals(SubsurfaceConfig.DEFAULT.abyssPitConfig().threshold(),
                builder.abyssThreshold());
        assertTrue(builder.cavesEnabled());
        assertEquals(1.75F, builder.caveCheeseDepthOffset());
        assertEquals(0.4F, builder.caveCheeseProbability());
        assertEquals(SubsurfaceConfig.DEFAULT.caveTunnelConfig().noodleProbability(),
                builder.caveNoodleProbability());
        assertEquals(customCaveSystemConfig(), builder.caveSystemConfig());
        assertEquals(customCaveNetworkConfig(), builder.caveNetworkConfig());
        assertEquals(customCaveChamberConfig(), builder.caveChamberConfig());
    }

    @Test
    void buildRejectsInvalidState() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new SubsurfaceConfigBuilder()
                        .abyssPitScale(16)
                        .build());

        assertTrue(e.getMessage().contains("pit_scale"));
    }

    @Test
    void buildRejectsInvalidNestedCaveSystemState() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new SubsurfaceConfigBuilder()
                        .caveSystemConfig(new CaveSystemConfig(
                                true, 2600, 1800, 96, 0.9F, 0.72F, 0.05F))
                        .build());

        assertTrue(e.getMessage().contains("depth_start"));
    }

    private static SubsurfaceConfig customConfig() {
        return new SubsurfaceConfig(
                new AbyssPitConfig(true, 1700, 512, 5, 2.5F, 0.65F,
                        0.7F, 0.2F, 256, 1.4F, 0.5F),
                new CaveTunnelConfig(true, 0.25F, 1.75F, 0.4F, 0.3F, 0.2F),
                customCaveSystemConfig(),
                customCaveNetworkConfig(),
                customCaveChamberConfig());
    }

    private static CaveSystemConfig customCaveSystemConfig() {
        return new CaveSystemConfig(true, 2600, 96, 1800, 0.9F, 0.72F, 0.05F);
    }

    private static CaveNetworkConfig customCaveNetworkConfig() {
        return new CaveNetworkConfig(1536, 0.42F, 512, 2.5F, 0.22F, 0.6F, 0.3F);
    }

    private static CaveChamberConfig customCaveChamberConfig() {
        return new CaveChamberConfig(0.62F, 72, 320, 2.2F, 0.42F, 0.7F);
    }
}
