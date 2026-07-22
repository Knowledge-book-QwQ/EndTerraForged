package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.config.TerrainLayoutMode;
import endterraforged.world.config.TerrainShape;
import endterraforged.world.terrain.TerrainRegionBuffer;
import endterraforged.world.terrain.TerrainRegionEntry;
import endterraforged.world.terrain.TerrainRegionFamily;
import endterraforged.world.terrain.TerrainRegionLayout;

class EndTerrainRegionContinuityTest {

    private static final int SEED = 123456789;
    private static final float BLOCK_SCALE = 384.0F;
    private static final float MAX_ADJACENT_DELTA = 32.0F;

    @Test
    void threeRegionJunctionsAvoidAccidentalMegawalls() {
        EndTerrainRegionComposer composer = new EndTerrainRegionComposer(areaTerrain(), SEED);
        TerrainRegionBuffer buffer = new TerrainRegionBuffer();
        float largestDelta = 0.0F;
        int largestX = 0;
        int largestZ = 0;
        String largestAxis = "";
        int junctions = 0;

        for (int z = -12000; z <= 12000; z += 128) {
            for (int x = -12000; x <= 12000; x += 128) {
                composer.auxiliaryContribution(x, z, SEED, buffer);
                if (buffer.tertiaryWeight() < 0.05F) {
                    continue;
                }
                junctions++;
                if (junctions <= 256) {
                    LocalDelta local = localLargestDelta(composer, buffer, x, z);
                    if (local.delta() > largestDelta) {
                        largestDelta = local.delta();
                        largestX = local.x();
                        largestZ = local.z();
                        largestAxis = local.axis();
                    }
                }
            }
        }

        assertTrue(junctions > 0, "fixture must include at least one three-region junction");
        float largestBlockDelta = largestDelta * BLOCK_SCALE;
        composer.auxiliaryContribution(largestX, largestZ, SEED, buffer);
        String diagnostic = " at " + largestX + "," + largestZ + " axis=" + largestAxis
                + " owner=" + buffer.ownershipFamily() + "/" + buffer.ownershipWeight()
                + " boundary=" + buffer.boundaryFamily() + "/" + buffer.boundaryWeight()
                + " tertiary=" + buffer.tertiaryFamily() + "/" + buffer.tertiaryWeight();
        assertTrue(largestBlockDelta <= MAX_ADJACENT_DELTA,
                () -> "three-region junction changed by " + largestBlockDelta
                        + " blocks between adjacent columns" + diagnostic);
    }

    @Test
    void candidateOrderChangesKeepBlendedSignalsContinuous() {
        TerrainRegionLayout layout = areaLayout();
        TerrainRegionBuffer buffer = new TerrainRegionBuffer();
        float largestDelta = 0.0F;
        int junctions = 0;
        int largestCandidateCount = 0;

        for (int z = -12000; z <= 12000; z += 128) {
            for (int x = -12000; x <= 12000; x += 128) {
                layout.sample(x, z, buffer);
                largestCandidateCount = Math.max(largestCandidateCount, buffer.candidateCount());
                if (buffer.tertiaryWeight() < 0.05F) {
                    continue;
                }
                junctions++;
                if (junctions <= 256) {
                    for (int offset = -32; offset <= 32; offset++) {
                        float horizontal = blendedIdentitySignal(layout, buffer, x + offset - 1, z);
                        float horizontalNext = blendedIdentitySignal(layout, buffer, x + offset, z);
                        float vertical = blendedIdentitySignal(layout, buffer, x, z + offset - 1);
                        float verticalNext = blendedIdentitySignal(layout, buffer, x, z + offset);
                        largestDelta = Math.max(largestDelta, Math.abs(horizontalNext - horizontal));
                        largestDelta = Math.max(largestDelta, Math.abs(verticalNext - vertical));
                    }
                }
            }
        }

        assertTrue(junctions > 0, "fixture must include at least one three-region junction");
        int finalLargestCandidateCount = largestCandidateCount;
        assertTrue(finalLargestCandidateCount <= 8,
                () -> "terrain junction activated " + finalLargestCandidateCount + " blend candidates");
        float finalLargestDelta = largestDelta;
        assertTrue(finalLargestDelta <= 0.025F,
                () -> "candidate reorder created a discontinuous blend delta of " + finalLargestDelta);
    }

    @Test
    void regionCompositionIsStableAcrossThreads() throws Exception {
        EndTerrainRegionComposer composer = new EndTerrainRegionComposer(areaTerrain(), SEED);
        int[] expected = sampleBits(composer);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<int[]>> tasks = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                tasks.add(() -> sampleBits(composer));
            }
            for (Future<int[]> future : executor.invokeAll(tasks)) {
                assertArrayEquals(expected, future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void familyChannelsUseTheSameHeightContributionPath() {
        EndTerrainRegionComposer composer = new EndTerrainRegionComposer(areaTerrain(), SEED);
        TerrainRegionBuffer region = new TerrainRegionBuffer();
        EndTerrainSignalBuffer signals = new EndTerrainSignalBuffer();

        for (int z = -6000; z <= 6000; z += 257) {
            for (int x = -6000; x <= 6000; x += 257) {
                float scalar = composer.auxiliaryContribution(x, z, SEED, region);
                composer.sampleSignals(x, z, SEED, region, signals, 1.0F);
                assertEquals(scalar, signals.height(), 0.0F);
                assertTrue(signals.roughness() >= 0.0F && signals.roughness() <= 1.0F);
                assertTrue(signals.erosionResistance() >= 0.0F
                        && signals.erosionResistance() <= 1.0F);
                assertTrue(signals.terrainTags() != 0);
            }
        }
    }

    private static LocalDelta localLargestDelta(EndTerrainRegionComposer composer,
                                                TerrainRegionBuffer buffer,
                                                int centerX,
                                                int centerZ) {
        float largest = 0.0F;
        int largestX = centerX;
        int largestZ = centerZ;
        String largestAxis = "";
        for (int offset = -32; offset <= 32; offset++) {
            float x = centerX + offset;
            float z = centerZ + offset;
            float horizontal = composer.auxiliaryContribution(x - 1.0F, centerZ, SEED, buffer);
            float horizontalNext = composer.auxiliaryContribution(x, centerZ, SEED, buffer);
            float vertical = composer.auxiliaryContribution(centerX, z - 1.0F, SEED, buffer);
            float verticalNext = composer.auxiliaryContribution(centerX, z, SEED, buffer);
            float horizontalDelta = Math.abs(horizontalNext - horizontal);
            if (horizontalDelta > largest) {
                largest = horizontalDelta;
                largestX = Math.round(x);
                largestZ = centerZ;
                largestAxis = "X";
            }
            float verticalDelta = Math.abs(verticalNext - vertical);
            if (verticalDelta > largest) {
                largest = verticalDelta;
                largestX = centerX;
                largestZ = Math.round(z);
                largestAxis = "Z";
            }
        }
        return new LocalDelta(largest, largestX, largestZ, largestAxis);
    }

    private static int[] sampleBits(EndTerrainRegionComposer composer) {
        int[] result = new int[289];
        TerrainRegionBuffer buffer = new TerrainRegionBuffer();
        int index = 0;
        for (int z = -8192; z <= 8192; z += 1024) {
            for (int x = -8192; x <= 8192; x += 1024) {
                result[index++] = Float.floatToIntBits(
                        composer.auxiliaryContribution(x, z, SEED, buffer));
            }
        }
        return result;
    }

    private static float blendedIdentitySignal(TerrainRegionLayout layout,
                                               TerrainRegionBuffer buffer,
                                               float x,
                                               float z) {
        layout.sample(x, z, buffer);
        float result = 0.0F;
        for (int candidate = 0; candidate < buffer.candidateCount(); candidate++) {
            result += identityValue(buffer.candidateRegionId(candidate), buffer.candidateEntryId(candidate))
                    * buffer.candidateWeight(candidate);
        }
        return result;
    }

    private static float identityValue(long regionId, int entryId) {
        long mixed = regionId ^ (regionId >>> 33) ^ (entryId * 0x9E3779B97F4A7C15L);
        mixed ^= mixed >>> 29;
        return (mixed & 0xFFFFL) / 65535.0F;
    }

    private static TerrainRegionLayout areaLayout() {
        return new TerrainRegionLayout(SEED, 3200, 200.0F, List.of(
                TerrainRegionEntry.area(0, TerrainRegionFamily.PLAINS, 1.0F, 1600),
                TerrainRegionEntry.area(1, TerrainRegionFamily.HILLS, 0.9F, 1440),
                TerrainRegionEntry.area(2, TerrainRegionFamily.PLATEAU, 0.8F, 1760)));
    }

    private static TerrainConfig areaTerrain() {
        TerrainLayerConfig plains = new TerrainLayerConfig(1.0F, 0.8F, 1.0F, 1.0F);
        TerrainLayerConfig hills = new TerrainLayerConfig(0.9F, 1.2F, 1.0F, 0.9F);
        TerrainLayerConfig plateau = new TerrainLayerConfig(0.8F, 1.5F, 1.0F, 1.1F);
        return new TerrainConfig(
                0, 1600, 1.0F, 1.0F, 0.0F, TerrainLayoutMode.REGION_PLANNED,
                TerrainShape.SHATTERED_RIDGES,
                plains, hills, plateau, TerrainLayerConfig.DISABLED, TerrainLayerConfig.DISABLED);
    }

    private record LocalDelta(float delta, int x, int z, String axis) {
    }
}
