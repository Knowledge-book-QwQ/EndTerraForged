package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.config.TerrainLayoutMode;
import endterraforged.world.config.TerrainShape;
import endterraforged.world.terrain.TerrainRegionBuffer;
import endterraforged.world.terrain.TerrainRidgeBuffer;

class EndTerrainRidgeRuntimeTest {
    private static final int SEED = 9173;

    @Test
    void ridgeOverlayIsFiniteDeterministicAndLeavesAreaOwnershipStable() {
        EndTerrainRegionComposer withRidges = new EndTerrainRegionComposer(config(true), SEED);
        EndTerrainRegionComposer areaOnly = new EndTerrainRegionComposer(config(false), SEED);
        boolean sawFeature = false;
        boolean sawOutside = false;

        for (int z = -16000; z <= 16000; z += 96) {
            for (int x = -16000; x <= 16000; x += 96) {
                TerrainRegionBuffer first = new TerrainRegionBuffer();
                TerrainRegionBuffer second = new TerrainRegionBuffer();
                TerrainRegionBuffer baseline = new TerrainRegionBuffer();
                float firstRelief = withRidges.auxiliaryContribution(x, z, SEED, first);
                float secondRelief = withRidges.auxiliaryContribution(x, z, SEED, second);
                float areaRelief = areaOnly.auxiliaryContribution(x, z, SEED, baseline);

                assertEquals(firstRelief, secondRelief, 0.0F);
                assertEquals(first.regionId(), baseline.regionId());
                assertEquals(first.ownershipEntryId(), baseline.ownershipEntryId());
                assertEquals(first.ownershipFamily(), baseline.ownershipFamily());
                assertTrue(Float.isFinite(firstRelief));
                assertTrue(firstRelief >= areaRelief);

                if (first.physicalInfluence() > 0.0F) {
                    sawFeature = true;
                    assertEquals(first.featureAnchorKey(), second.featureAnchorKey());
                } else {
                    sawOutside = true;
                    assertEquals(0L, first.featureAnchorKey());
                    assertEquals(areaRelief, firstRelief, 0.0F,
                            "outside every ridge footprint the result must be exactly AREA-only");
                    assertEquals(first.ownershipFamily(), first.visibleFamily());
                }
            }
        }

        assertTrue(sawFeature, "fixture must include an independent ridge anchor");
        assertTrue(sawOutside, "finite ridge anchors must leave AREA-only coordinates");
    }

    @Test
    void ridgeReliefChangesContinuouslyAcrossChunkBoundaries() {
        EndTerrainRegionComposer composer = new EndTerrainRegionComposer(config(true), SEED);
        TerrainRegionBuffer left = new TerrainRegionBuffer();
        TerrainRegionBuffer right = new TerrainRegionBuffer();

        for (int z = -10000; z <= 10000; z += 503) {
            for (int x = -10000; x <= 10000; x += 16) {
                float leftRelief = composer.auxiliaryContribution(x + 15.999F, z, SEED, left);
                float rightRelief = composer.auxiliaryContribution(x + 16.001F, z, SEED, right);
                assertTrue(Math.abs(leftRelief - rightRelief) < 0.08F,
                        "finite ridge relief must not form chunk-aligned cliffs");
            }
        }
    }

    @Test
    void overlappingRidgesUseMaximumReliefInsteadOfSumming() {
        EndTerrainRegionComposer composer = new EndTerrainRegionComposer(config(true), SEED);
        EndTerrainRegionComposer areaOnly = new EndTerrainRegionComposer(config(false), SEED);
        EndTerrainRidgeRuntime runtime = new EndTerrainRidgeRuntime(config(true), SEED);
        TerrainRidgeBuffer candidates = new TerrainRidgeBuffer();
        TerrainRegionBuffer fullRegion = new TerrainRegionBuffer();
        TerrainRegionBuffer areaRegion = new TerrainRegionBuffer();

        for (int z = -16000; z <= 16000; z += 32) {
            for (int x = -16000; x <= 16000; x += 32) {
                composer.sampleRidgeCandidates(x, z, candidates);
                if (candidates.candidateCount() < 2) {
                    continue;
                }
                float maximum = 0.0F;
                float sum = 0.0F;
                for (int candidate = 0; candidate < candidates.candidateCount(); candidate++) {
                    float relief = runtime.contribution(
                            candidates.anchorSeed(candidate),
                            candidates.centerX(candidate), candidates.centerZ(candidate),
                            candidates.rotationCos(candidate), candidates.rotationSin(candidate),
                            x, z);
                    maximum = Math.max(maximum, relief);
                    sum += relief;
                }
                if (sum <= maximum + 1.0E-5F) {
                    continue;
                }

                float full = composer.auxiliaryContribution(x, z, SEED, fullRegion);
                float area = areaOnly.auxiliaryContribution(x, z, SEED, areaRegion);
                assertEquals(maximum, full - area, 1.0E-6F);
                assertTrue(full - area < sum,
                        "ridge overlap must not accumulate every candidate relief");
                assertEquals(candidates.anchorKey(0), fullRegion.featureAnchorKey());
                return;
            }
        }

        throw new AssertionError("fixture did not expose two positive overlapping ridges");
    }

    private static TerrainConfig config(boolean ridges) {
        return new TerrainConfig(
                0, 1600, 1.0F, 1.0F, 0.0F, TerrainLayoutMode.REGION_PLANNED,
                TerrainShape.SHATTERED_RIDGES,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED,
                ridges ? TerrainLayerConfig.DEFAULT : TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DISABLED);
    }
}
