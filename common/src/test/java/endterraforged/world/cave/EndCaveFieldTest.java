package endterraforged.world.cave;

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

class EndCaveFieldTest {

    private static final int SEED = 123;
    private static final int WORLD_HEIGHT = 4096;

    @Test
    void disabledConfigHasNoThreeDimensionalCaveStrength() {
        EndCaveField field = EndCaveField.fromConfig(SubsurfaceConfig.DISABLED, SEED);

        assertFalse(field.enabled());
        assertEquals(0.0F, field.strength(0.0F, 0.0F, 1.0F,
                0.5F, 0.8F, WORLD_HEIGHT), 0.0F);
        assertFalse(field.carves(0.0F, 0.0F, 1.0F,
                0.5F, 0.8F, WORLD_HEIGHT));
    }

    @Test
    void enabledFieldCarvesInsideConfiguredDepthBand() {
        EndCaveField field = enabledField();
        Sample sample = findCaveSample(field);
        float terrainTop = 0.86F;
        float inside = terrainTop - sample.depthBlocks / WORLD_HEIGHT;

        assertTrue(field.carves(sample.x, sample.z, 1.0F, inside,
                terrainTop, WORLD_HEIGHT));
        assertFalse(field.carves(sample.x, sample.z, 1.0F,
                terrainTop - 20.0F / WORLD_HEIGHT, terrainTop, WORLD_HEIGHT));
        assertFalse(field.carves(sample.x, sample.z, 1.0F,
                terrainTop - 900.0F / WORLD_HEIGHT, terrainTop, WORLD_HEIGHT));
    }

    @Test
    void minLandnessGatesCaveCarving() {
        EndCaveField field = enabledField();
        Sample sample = findCaveSample(field);
        float terrainTop = 0.86F;
        float inside = terrainTop - sample.depthBlocks / WORLD_HEIGHT;

        assertEquals(0.0F, field.strength(sample.x, sample.z, 0.59F,
                inside, terrainTop, WORLD_HEIGHT), 0.0F);
        assertFalse(field.carves(sample.x, sample.z, 0.59F,
                inside, terrainTop, WORLD_HEIGHT));
    }

    @Test
    void caveStrengthIsDeterministic() {
        EndCaveField field = enabledField();
        Sample sample = findCaveSample(field);
        float terrainTop = 0.86F;
        float inside = terrainTop - sample.depthBlocks / WORLD_HEIGHT;

        assertEquals(
                field.strength(sample.x, sample.z, 1.0F, inside, terrainTop, WORLD_HEIGHT),
                field.strength(sample.x, sample.z, 1.0F, inside, terrainTop, WORLD_HEIGHT),
                0.0F);
    }

    private static EndCaveField enabledField() {
        return EndCaveField.fromConfig(config(), SEED);
    }

    private static SubsurfaceConfig config() {
        return new SubsurfaceConfig(
                AbyssPitConfig.DISABLED,
                CaveTunnelConfig.DISABLED,
                new CaveSystemConfig(true, 2400, 96, 768,
                        0.9F, 0.9F, 0.0F),
                new CaveNetworkConfig(384, 0.95F, 128,
                        4.0F, 0.6F, 0.45F, 0.6F),
                new CaveChamberConfig(0.95F, 96, 384,
                        2.2F, 0.35F, 0.7F));
    }

    private static Sample findCaveSample(EndCaveField field) {
        float terrainTop = 0.86F;
        for (int z = -24; z <= 24; z++) {
            for (int x = -24; x <= 24; x++) {
                float worldX = x * 43.0F;
                float worldZ = z * 47.0F;
                for (float depth = 128.0F; depth <= 720.0F; depth += 32.0F) {
                    float yNorm = terrainTop - depth / WORLD_HEIGHT;
                    if (field.strength(worldX, worldZ, 1.0F,
                            yNorm, terrainTop, WORLD_HEIGHT) >= 0.35F) {
                        return new Sample(worldX, worldZ, depth);
                    }
                }
            }
        }
        throw new AssertionError("no active 3D cave sample found");
    }

    private record Sample(float x, float z, float depthBlocks) {
    }
}
