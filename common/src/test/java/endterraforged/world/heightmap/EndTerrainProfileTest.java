package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetDevelopmentProfiles;
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
    void profileDerivativesUseWorldBlockUnitsAcrossHeightPresets() {
        EndHeightmap standard = new EndHeightmap(
                scaledProfile(512), SEED, normalizedPlaneAndBowl(512));
        EndHeightmap tall = new EndHeightmap(
                scaledProfile(1024), SEED, normalizedPlaneAndBowl(1024));
        EndTerrainProfileBuffer standardProfile = new EndTerrainProfileBuffer();
        EndTerrainProfileBuffer tallProfile = new EndTerrainProfileBuffer();

        standard.sampleTerrainProfile(128.0F, 0.0F, SEED, standardProfile);
        tall.sampleTerrainProfile(128.0F, 0.0F, SEED, tallProfile);

        assertEquals(standardProfile.slope(), tallProfile.slope(), 1.0E-5F);
        assertEquals(standardProfile.curvature(), tallProfile.curvature(), 1.0E-5F);
        assertEquals(512.0F, standardProfile.worldHeightBlocks(), 0.0F);
        assertEquals(1024.0F, tallProfile.worldHeightBlocks(), 0.0F);
        assertTrue(standardProfile.slope() > 0.0F);
        assertTrue(standardProfile.curvature() > 0.0F);
    }

    @Test
    void smokeProfileReportsFiveRawTopEvaluationsPerRequest() {
        String property = EndPresetDevelopmentProfiles.P46_ARCHIPELAGO_SMOKE_TEST_PROPERTY;
        String previous = System.getProperty(property);
        System.setProperty(property, "true");
        EndHeightmap.configureTerrainMetrics(true);
        try {
            int smokeSeed = 123456789;
            EndPreset smoke = EndPresetDevelopmentProfiles.defaultFallback();
            EndHeightmap heightmap = new EndHeightmap(smoke, smokeSeed);
            EndTerrainProfileBuffer profile = new EndTerrainProfileBuffer();

            for (int column = 0; column < 16 * 16; column++) {
                float x = 8192.0F + (column & 15);
                float z = 8192.0F + (column >>> 4);
                heightmap.sampleTerrainProfile(x, z, smokeSeed, profile);
            }

            EndHeightmap.TerrainMetrics metrics = EndHeightmap.terrainMetrics();
            assertEquals(16 * 16, metrics.terrainProfileRequests());
            assertEquals(metrics.terrainProfileRequests() * 5, metrics.rawTopEvaluations());
            System.out.printf(
                    "[perf] p47TerrainProfile requests=%d rawTopEvaluations=%d%n",
                    metrics.terrainProfileRequests(), metrics.rawTopEvaluations());
        } finally {
            EndHeightmap.configureTerrainMetrics(false);
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    @Test
    void scaledLegacyKnownLandnessCountsOneRawTopEvaluation() {
        TerrainConfig terrain = new TerrainConfig(
                0, 1600, 1.0F, 2.0F, 0.0F, TerrainLayoutMode.LEGACY_SELECTOR,
                TerrainShape.SHATTERED_RIDGES,
                TerrainLayerConfig.DISABLED, TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DISABLED, TerrainLayerConfig.DEFAULT,
                TerrainLayerConfig.DISABLED);
        TestProfile profile = new TestProfile(512, -256, 0, 0, SeaMode.NONE,
                TopologyMode.CONTINENTAL, false, terrain);
        EndHeightmap heightmap = new EndHeightmap(profile, SEED);
        EndHeightmap.configureTerrainMetrics(true);
        try {
            heightmap.getHeight(128.0F, -256.0F, SEED, 1.0F, 1.0F);

            EndHeightmap.TerrainMetrics metrics = EndHeightmap.terrainMetrics();
            assertEquals(1, metrics.rawTopEvaluations());
            assertEquals(0, metrics.terrainProfileRequests());
        } finally {
            EndHeightmap.configureTerrainMetrics(false);
        }
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

    private static TestProfile scaledProfile(int worldHeight) {
        return new TestProfile(worldHeight, 0, 0, 0,
                SeaMode.NONE, TopologyMode.CONTINENTAL, false);
    }

    private static Noise normalizedPlaneAndBowl(float worldHeight) {
        return new Noise() {
            @Override
            public float compute(float x, float z, int seed) {
                return 0.25F + 0.05F * x / worldHeight + 0.0001F * x * x / worldHeight;
            }

            @Override
            public float minValue() {
                return 0.0F;
            }

            @Override
            public float maxValue() {
                return 1.0F;
            }

            @Override
            public Noise mapAll(Visitor visitor) {
                return visitor.apply(this);
            }
        };
    }
}
