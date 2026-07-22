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

class EndTerrainProfileTest {
    private static final int SEED = 73491;

    @Test
    void flatRawTerrainProducesZeroSlopeAndCurvature() {
        EndHeightmap heightmap = new EndHeightmap(flatProfile(), SEED, constantNoise(0.0F));
        EndTerrainProfileBuffer profile = new EndTerrainProfileBuffer();

        heightmap.sampleTerrainProfile(128.0F, -256.0F, SEED, profile);

        assertEquals(heightmap.levels().surface, profile.rawTop(), 0.0F);
        assertEquals(0.0F, profile.slope(), 0.0F);
        assertEquals(0.0F, profile.curvature(), 0.0F);
        assertEquals(0, profile.terrainTags());
    }

    @Test
    void profileIsDeterministicAndDoesNotChangeTheRawHeightPath() {
        EndHeightmap heightmap = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndTerrainProfileBuffer first = new EndTerrainProfileBuffer();
        EndTerrainProfileBuffer second = new EndTerrainProfileBuffer();
        float before = heightmap.getTerrainHeight(409.25F, -811.75F, SEED);

        heightmap.sampleTerrainProfile(409.25F, -811.75F, SEED, first);
        heightmap.sampleTerrainProfile(409.25F, -811.75F, SEED, second);
        float after = heightmap.getTerrainHeight(409.25F, -811.75F, SEED);

        assertEquals(first.rawTop(), second.rawTop(), 0.0F);
        assertEquals(first.slope(), second.slope(), 0.0F);
        assertEquals(first.curvature(), second.curvature(), 0.0F);
        assertEquals(first.roughness(), second.roughness(), 0.0F);
        assertEquals(first.erosionResistance(), second.erosionResistance(), 0.0F);
        assertEquals(first.terrainTags(), second.terrainTags());
        assertEquals(before, first.rawTop(), 0.0F);
        assertEquals(before, after, 0.0F);
        assertTrue(first.slope() >= 0.0F && first.slope() < 1.0F);
        assertTrue(first.curvature() >= -1.0F && first.curvature() <= 1.0F);
    }

    @Test
    void nullProfileDestinationIsRejected() {
        EndHeightmap heightmap = new EndHeightmap(flatProfile(), SEED, constantNoise(0.0F));

        org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> heightmap.sampleTerrainProfile(0.0F, 0.0F, SEED, null));
    }

    private static TestProfile flatProfile() {
        TerrainLayerConfig disabled = TerrainLayerConfig.DISABLED;
        TerrainConfig terrain = new TerrainConfig(
                0, 1600, 1.0F, 1.0F, 0.0F, TerrainLayoutMode.REGION_PLANNED,
                TerrainShape.SHATTERED_RIDGES, disabled, disabled, disabled, disabled, disabled);
        return new TestProfile(512, -256, 0, 0, SeaMode.NONE,
                TopologyMode.CONTINENTAL, false, terrain);
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
