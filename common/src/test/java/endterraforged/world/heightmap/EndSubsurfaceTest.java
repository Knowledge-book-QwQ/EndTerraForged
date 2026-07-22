package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.AbyssPitConfig;
import endterraforged.world.config.CaveChamberConfig;
import endterraforged.world.config.CaveNetworkConfig;
import endterraforged.world.config.CaveSystemConfig;
import endterraforged.world.config.CaveTunnelConfig;
import endterraforged.world.config.SubsurfaceConfig;

class EndSubsurfaceTest {

    private static final int SEED = 42;
    private static final int WORLD_HEIGHT = 4064;

    @Test
    void disabledRuntimeNeverCarves() {
        EndSubsurface subsurface = SubsurfaceConfig.DISABLED.buildRuntime(SEED);

        assertFalse(subsurface.enabled());
        assertFalse(subsurface.carves(0.0F, 0.0F, 1.0F,
                0.5F, 0.75F, WORLD_HEIGHT));
        assertEquals(0.0F, subsurface.abyssStrength(0.0F, 0.0F, 1.0F));
        assertEquals(0.0F, subsurface.caveStrength(0.0F, 0.0F, 1.0F,
                0.5F, 0.75F, WORLD_HEIGHT));
    }

    @Test
    void enabledRuntimeCarvesOnlyWithinConfiguredDepthFromTerrainTop() {
        EndSubsurface subsurface = enabledSubsurface();
        Sample sample = findCarvingSample(subsurface);
        float terrainTop = 0.8F;
        float depthNorm = configuredDepthNorm(sample.strength);

        assertTrue(subsurface.carves(sample.x, sample.z, 1.0F,
                terrainTop - depthNorm * 0.5F, terrainTop, WORLD_HEIGHT));
        assertFalse(subsurface.carves(sample.x, sample.z, 1.0F,
                terrainTop - depthNorm - 0.01F, terrainTop, WORLD_HEIGHT));
        assertFalse(subsurface.carves(sample.x, sample.z, 1.0F,
                terrainTop + 0.01F, terrainTop, WORLD_HEIGHT));
    }

    @Test
    void minLandnessGatesAbyssCarving() {
        EndSubsurface subsurface = enabledSubsurface();
        Sample sample = findCarvingSample(subsurface);

        assertEquals(0.0F, subsurface.abyssStrength(sample.x, sample.z, 0.49F));
        assertFalse(subsurface.carves(sample.x, sample.z, 0.49F,
                0.75F, 0.8F, WORLD_HEIGHT));
    }

    @Test
    void abyssStrengthIsDeterministic() {
        EndSubsurface subsurface = enabledSubsurface();
        Sample sample = findCarvingSample(subsurface);

        assertEquals(sample.strength, subsurface.abyssStrength(sample.x, sample.z, 1.0F),
                0.0F);
    }

    @Test
    void depthCurveChangesCarvingDepth() {
        EndSubsurface linear = enabledSubsurface();
        EndSubsurface curved = new SubsurfaceConfig(new AbyssPitConfig(
                true, 0, 64, 3, 2.0F, 0.5F,
                0.0F, 1.0F, 256, 2.0F, 0.5F))
                .buildRuntime(SEED);
        Sample sample = findCarvingSample(linear);
        float terrainTop = 0.8F;
        float linearDepth = configuredDepthNorm(sample.strength);
        float y = terrainTop - linearDepth * 0.75F;

        assertTrue(linear.carves(sample.x, sample.z, 1.0F, y, terrainTop, WORLD_HEIGHT));
        assertFalse(curved.carves(sample.x, sample.z, 1.0F, y, terrainTop, WORLD_HEIGHT));
    }

    @Test
    void caveRuntimeCarvesInsideThreeDimensionalField() {
        EndSubsurface subsurface = caveSubsurface();
        CaveSample sample = findCaveSample(subsurface);
        float terrainTop = 0.86F;
        float yNorm = terrainTop - sample.depthBlocks / WORLD_HEIGHT;

        assertTrue(subsurface.caveStrength(sample.x, sample.z, 1.0F,
                yNorm, terrainTop, WORLD_HEIGHT) >= 0.35F);
        assertTrue(subsurface.carves(sample.x, sample.z, 1.0F,
                yNorm, terrainTop, WORLD_HEIGHT));
        assertFalse(subsurface.carves(sample.x, sample.z, 0.2F,
                yNorm, terrainTop, WORLD_HEIGHT));
    }

    @Test
    void caveRuntimeDoesNotEnableAbyssStrength() {
        EndSubsurface subsurface = caveSubsurface();

        assertEquals(0.0F, subsurface.abyssStrength(0.0F, 0.0F, 1.0F), 0.0F);
    }

    @Test
    void legacyTunnelConfigDoesNotEnableSpectacleCaveRuntime() {
        EndSubsurface subsurface = new SubsurfaceConfig(
                AbyssPitConfig.DISABLED,
                new CaveTunnelConfig(true, 1.0F, 1.5625F, 1.0F, 1.0F, 1.0F),
                CaveSystemConfig.DISABLED,
                CaveNetworkConfig.DEFAULT,
                CaveChamberConfig.DEFAULT)
                .buildRuntime(SEED);

        assertFalse(subsurface.enabled());
        assertFalse(subsurface.carves(0.0F, 0.0F, 1.0F,
                0.5F, 0.75F, WORLD_HEIGHT));
        assertEquals(0.0F, subsurface.caveStrength(0.0F, 0.0F, 1.0F,
                0.5F, 0.75F, WORLD_HEIGHT), 0.0F);
    }

    private static EndSubsurface enabledSubsurface() {
        return new SubsurfaceConfig(new AbyssPitConfig(
                true, 0, 64, 0.0F, 1.0F, 256, 0.5F))
                .buildRuntime(SEED);
    }

    private static EndSubsurface caveSubsurface() {
        return new SubsurfaceConfig(
                AbyssPitConfig.DISABLED,
                CaveTunnelConfig.DISABLED,
                new CaveSystemConfig(true, 2400, 96, 768,
                        0.9F, 0.9F, 0.0F),
                new CaveNetworkConfig(384, 0.95F, 128,
                        4.0F, 0.6F, 0.45F, 0.6F),
                new CaveChamberConfig(0.95F, 96, 384,
                        2.2F, 0.35F, 0.7F))
                .buildRuntime(SEED);
    }

    private static Sample findCarvingSample(EndSubsurface subsurface) {
        for (int i = 0; i < 200; i++) {
            float x = i * 13.0F;
            float z = i * 17.0F;
            float strength = subsurface.abyssStrength(x, z, 1.0F);
            if (strength > 0.05F) {
                return new Sample(x, z, strength);
            }
        }
        throw new AssertionError("no abyss sample with positive strength found");
    }

    private static float configuredDepthNorm(float strength) {
        return (256.0F * strength) / WORLD_HEIGHT;
    }

    private record Sample(float x, float z, float strength) {
    }

    private static CaveSample findCaveSample(EndSubsurface subsurface) {
        float terrainTop = 0.86F;
        for (int z = -24; z <= 24; z++) {
            for (int x = -24; x <= 24; x++) {
                float worldX = x * 43.0F;
                float worldZ = z * 47.0F;
                for (float depth = 128.0F; depth <= 720.0F; depth += 32.0F) {
                    float yNorm = terrainTop - depth / WORLD_HEIGHT;
                    if (subsurface.caveStrength(worldX, worldZ, 1.0F,
                            yNorm, terrainTop, WORLD_HEIGHT) >= 0.35F) {
                        return new CaveSample(worldX, worldZ, depth);
                    }
                }
            }
        }
        throw new AssertionError("no cave sample with positive strength found");
    }

    private record CaveSample(float x, float z, float depthBlocks) {
    }
}
