package endterraforged.world.cave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.AbyssPitConfig;
import endterraforged.world.config.CaveChamberConfig;
import endterraforged.world.config.CaveNetworkConfig;
import endterraforged.world.config.CaveSystemConfig;
import endterraforged.world.config.CaveTunnelConfig;
import endterraforged.world.config.SubsurfaceConfig;

class EndCaveGraphTest {

    private static final int SEED = 123;
    private static final int WORLD_HEIGHT = 4096;
    private static final long DEFAULT_GRAPH_SIGNATURE = -1688973162643330851L;

    @Test
    void disabledConfigHasNoGraphStrength() {
        EndCaveGraph graph = EndCaveGraph.fromConfig(SubsurfaceConfig.DISABLED, SEED);

        assertFalse(graph.enabled());
        assertEquals(0.0F, graph.strength(0.0F, 0.0F, 1.0F,
                0.5F, 0.86F, WORLD_HEIGHT), 0.0F);
    }

    @Test
    void enabledGraphProducesChamberOrCorridorStrength() {
        EndCaveGraph graph = EndCaveGraph.fromConfig(config(2400), SEED);
        Sample sample = findGraphSample(graph);

        assertTrue(graph.strength(sample.x, sample.z, 1.0F,
                sample.yNorm, 0.86F, WORLD_HEIGHT) > 0.0F);
    }

    @Test
    void graphStrengthIsDeterministic() {
        EndCaveGraph graph = EndCaveGraph.fromConfig(config(2400), SEED);
        Sample sample = findGraphSample(graph);

        assertEquals(
                graph.strength(sample.x, sample.z, 1.0F,
                        sample.yNorm, 0.86F, WORLD_HEIGHT),
                graph.strength(sample.x, sample.z, 1.0F,
                        sample.yNorm, 0.86F, WORLD_HEIGHT),
                0.0F);
    }

    @Test
    void defaultGraphSignatureRemainsStable() {
        assertEquals(DEFAULT_GRAPH_SIGNATURE,
                signature(EndCaveGraph.fromConfig(config(2400), SEED)));
    }

    @Test
    void seedOffsetChangesGraphSignature() {
        EndCaveGraph base = EndCaveGraph.fromConfig(config(2400), SEED);
        EndCaveGraph shifted = EndCaveGraph.fromConfig(config(2600), SEED);

        assertNotEquals(signature(base), signature(shifted));
    }

    @Test
    void sharedWorkerCacheInvalidatesWhenGraphRuntimeChanges() {
        EndCaveGraph first = EndCaveGraph.fromConfig(config(2400), SEED);
        EndCaveGraph second = EndCaveGraph.fromConfig(config(2600), SEED);
        long firstExpected = signature(first);
        long secondExpected = signature(second);

        assertEquals(firstExpected, signature(first));
        assertEquals(secondExpected, signature(second));
        assertEquals(firstExpected, signature(first));
    }

    @Test
    void spectacleBiasChangesRiftWeightedGraphSignature() {
        EndCaveGraph lowSpectacle = EndCaveGraph.fromConfig(config(2400,
                0.05F, 0.4F, 0.0F), SEED);
        EndCaveGraph highSpectacle = EndCaveGraph.fromConfig(config(2400,
                1.0F, 0.4F, 0.0F), SEED);

        assertNotEquals(signature(lowSpectacle), signature(highSpectacle));
    }

    @Test
    void loopChanceChangesFlowWeightedGraphSignature() {
        EndCaveGraph lowFlow = EndCaveGraph.fromConfig(config(2400,
                0.4F, 0.2F, 0.0F), SEED);
        EndCaveGraph highFlow = EndCaveGraph.fromConfig(config(2400,
                0.4F, 1.0F, 1.0F), SEED);

        assertNotEquals(signature(lowFlow), signature(highFlow));
    }

    private static SubsurfaceConfig config(int seedOffset) {
        return config(seedOffset, 0.9F, 0.9F, 0.6F);
    }

    private static SubsurfaceConfig config(int seedOffset, float spectacleBias,
                                           float connectivity, float loopChance) {
        return new SubsurfaceConfig(
                AbyssPitConfig.DISABLED,
                CaveTunnelConfig.DISABLED,
                new CaveSystemConfig(true, seedOffset, 96, 768,
                        spectacleBias, connectivity, 0.0F),
                new CaveNetworkConfig(384, 0.95F, 128,
                        4.0F, loopChance, 0.45F, 0.6F),
                new CaveChamberConfig(0.95F, 96, 384,
                        2.2F, 0.35F, 0.7F));
    }

    private static Sample findGraphSample(EndCaveGraph graph) {
        float terrainTop = 0.86F;
        for (int z = -24; z <= 24; z++) {
            for (int x = -24; x <= 24; x++) {
                float worldX = x * 43.0F;
                float worldZ = z * 47.0F;
                for (float depth = 128.0F; depth <= 720.0F; depth += 32.0F) {
                    float yNorm = terrainTop - depth / WORLD_HEIGHT;
                    if (graph.strength(worldX, worldZ, 1.0F,
                            yNorm, terrainTop, WORLD_HEIGHT) > 0.0F) {
                        return new Sample(worldX, worldZ, yNorm);
                    }
                }
            }
        }
        throw new AssertionError("no active graph sample found");
    }

    private static long signature(EndCaveGraph graph) {
        long hash = 0L;
        float terrainTop = 0.86F;
        for (int z = -8; z <= 8; z++) {
            for (int x = -8; x <= 8; x++) {
                float worldX = x * 97.0F;
                float worldZ = z * 101.0F;
                float yNorm = terrainTop - 384.0F / WORLD_HEIGHT;
                hash = hash * 31 + Float.floatToIntBits(graph.strength(worldX, worldZ,
                        1.0F, yNorm, terrainTop, WORLD_HEIGHT));
            }
        }
        return hash;
    }

    private record Sample(float x, float z, float yNorm) {
    }
}
