package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.SeaMode;
import endterraforged.world.config.TestProfile;
import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.config.TerrainLayoutMode;
import endterraforged.world.config.TerrainShape;
import endterraforged.world.config.TopologyMode;
import endterraforged.world.noise.Noise;

class EndTerrainSignalBufferTest {

    private static final int SEED = 123456789;

    @Test
    void publicSignalPathSharesTheHeightContributionContract() {
        TerrainConfig terrain = new TerrainConfig(
                0, 1600, 1.0F, 1.0F, 0.0F, TerrainLayoutMode.REGION_PLANNED,
                TerrainShape.SHATTERED_RIDGES,
                new TerrainLayerConfig(1.0F, 0.8F, 1.0F, 1.0F),
                new TerrainLayerConfig(0.9F, 1.2F, 1.0F, 0.9F),
                new TerrainLayerConfig(0.8F, 1.5F, 1.0F, 1.1F),
                TerrainLayerConfig.DISABLED, TerrainLayerConfig.DISABLED);
        EndHeightmap heightmap = new EndHeightmap(
                new TestProfile(512, -256, 0, 0, SeaMode.NONE,
                        TopologyMode.CONTINENTAL, false, terrain),
                SEED, constantNoise(0.0F));
        EndTerrainSignalBuffer output = new EndTerrainSignalBuffer();

        for (int z = -6000; z <= 6000; z += 257) {
            for (int x = -6000; x <= 6000; x += 257) {
                heightmap.sampleTerrainSignals(x, z, SEED, output);
                float landness = heightmap.getLandness(x, z, SEED);
                float expectedHeight = heightmap.levels().surface
                        + heightmap.levels().elevationRange
                        * output.height() * heightmap.landmassVolume().edgeFade(landness);
                assertEquals(expectedHeight, heightmap.getTerrainHeight(x, z, SEED), 0.0F);
                assertTrue(output.roughness() >= 0.0F && output.roughness() <= 1.0F);
                assertTrue(output.erosionResistance() >= 0.0F
                        && output.erosionResistance() <= 1.0F);
                assertTrue(output.terrainTags() != 0);
            }
        }
    }

    private static Noise constantNoise(float value) {
        return new Noise() {
            @Override
            public float compute(float x, float z, int seed) {
                return value;
            }

            @Override
            public float minValue() {
                return value;
            }

            @Override
            public float maxValue() {
                return value;
            }

            @Override
            public Noise mapAll(Visitor visitor) {
                return visitor.apply(this);
            }
        };
    }
}
