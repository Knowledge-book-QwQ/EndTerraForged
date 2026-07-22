package endterraforged.world.terrain;

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

import endterraforged.world.noise.NoiseMath;

class TerrainRidgeLayoutTest {
    private static final int SEED = 0x621974;
    private static final int SPACING = 1300;
    private static final float REACH = 2300.0F;
    private static final int CENTER_SEED = SEED + 7;
    private static final int CANDIDATE_SEED = SEED + 29;

    @Test
    void boundedSearchMatchesAnExhaustiveOracle() {
        TerrainRidgeLayout layout = new TerrainRidgeLayout(SEED, SPACING, REACH, this::influence);
        TerrainRidgeBuffer actual = new TerrainRidgeBuffer();
        TerrainRidgeBuffer expected = new TerrainRidgeBuffer();
        float[][] coordinates = {
                {0.0F, 0.0F},
                {-4096.25F, 8192.75F},
                {31777.0F, -25001.0F},
                {-240000.0F, -160000.0F},
                {750000.0F, 1250000.0F}
        };

        for (float[] coordinate : coordinates) {
            layout.sample(coordinate[0], coordinate[1], actual);
            exhaustive(coordinate[0], coordinate[1], expected);
            assertBufferEquals(expected, actual);
        }
        assertTrue(layout.searchRadius() <= TerrainRidgeLayout.MAX_SEARCH_RADIUS);
    }

    @Test
    void topThreeAreStableAcrossRepeatsAndThreads() throws Exception {
        TerrainRidgeLayout layout = new TerrainRidgeLayout(SEED, SPACING, REACH, this::influence);
        long[] expected = sampleBits(layout);
        assertArrayEquals(expected, sampleBits(layout));

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<long[]>> tasks = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                tasks.add(() -> sampleBits(layout));
            }
            for (Future<long[]> future : executor.invokeAll(tasks)) {
                assertArrayEquals(expected, future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void retainsAtMostThreeCandidatesInStrongestFirstOrder() {
        TerrainRidgeLayout layout = new TerrainRidgeLayout(SEED, SPACING, REACH, this::influence);
        TerrainRidgeBuffer output = new TerrainRidgeBuffer();
        for (int z = -12000; z <= 12000; z += 257) {
            for (int x = -12000; x <= 12000; x += 257) {
                layout.sample(x, z, output);
                assertTrue(output.candidateCount() <= TerrainRidgeBuffer.MAX_CANDIDATES);
                for (int candidate = 1; candidate < output.candidateCount(); candidate++) {
                    assertTrue(output.influence(candidate - 1) >= output.influence(candidate));
                }
            }
        }
    }

    private float influence(int anchorSeed, float centerX, float centerZ,
                            float rotationCos, float rotationSin,
                            float sampleX, float sampleZ) {
        float distance = (float) Math.sqrt(
                (sampleX - centerX) * (sampleX - centerX)
                        + (sampleZ - centerZ) * (sampleZ - centerZ));
        if (distance >= REACH) {
            return 0.0F;
        }
        float value = 1.0F - distance / REACH;
        return value * value * (3.0F - 2.0F * value);
    }

    private void exhaustive(float x, float z, TerrainRidgeBuffer output) {
        output.clear();
        int originX = floor(x / SPACING);
        int originZ = floor(z / SPACING);
        for (int dz = -12; dz <= 12; dz++) {
            for (int dx = -12; dx <= 12; dx++) {
                int cellX = originX + dx;
                int cellZ = originZ + dz;
                NoiseMath.Vec2f jitter = NoiseMath.cell(CENTER_SEED, cellX, cellZ);
                float centerX = (cellX + jitter.x() * TerrainRidgeLayout.CELL_JITTER) * SPACING;
                float centerZ = (cellZ + jitter.y() * TerrainRidgeLayout.CELL_JITTER) * SPACING;
                float deltaX = centerX - x;
                float deltaZ = centerZ - z;
                float distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
                if (distanceSquared >= REACH * REACH) {
                    continue;
                }
                int anchorSeed = NoiseMath.hash2D(CANDIDATE_SEED, cellX, cellZ);
                float influence = influence(anchorSeed, centerX, centerZ, 1.0F, 0.0F, x, z);
                if (influence <= 0.0F) {
                    continue;
                }
                output.insert(packCell(cellX, cellZ), anchorSeed, centerX, centerZ,
                        1.0F, 0.0F, distanceSquared, influence);
            }
        }
    }

    private static long[] sampleBits(TerrainRidgeLayout layout) {
        TerrainRidgeBuffer output = new TerrainRidgeBuffer();
        long[] result = new long[17 * 17 * TerrainRidgeBuffer.MAX_CANDIDATES];
        int index = 0;
        for (int z = -8192; z <= 8192; z += 1024) {
            for (int x = -8192; x <= 8192; x += 1024) {
                layout.sample(x, z, output);
                for (int candidate = 0; candidate < TerrainRidgeBuffer.MAX_CANDIDATES; candidate++) {
                    result[index++] = candidate < output.candidateCount()
                            ? output.anchorKey(candidate) ^ Float.floatToIntBits(output.influence(candidate))
                            : Long.MIN_VALUE;
                }
            }
        }
        return result;
    }

    private static void assertBufferEquals(TerrainRidgeBuffer expected, TerrainRidgeBuffer actual) {
        assertEquals(expected.candidateCount(), actual.candidateCount());
        for (int index = 0; index < expected.candidateCount(); index++) {
            assertEquals(expected.anchorKey(index), actual.anchorKey(index));
            assertEquals(expected.anchorSeed(index), actual.anchorSeed(index));
            assertEquals(expected.centerX(index), actual.centerX(index), 0.0F);
            assertEquals(expected.centerZ(index), actual.centerZ(index), 0.0F);
            assertEquals(expected.distanceSquared(index), actual.distanceSquared(index), 0.0F);
            assertEquals(expected.influence(index), actual.influence(index), 0.0F);
        }
    }

    private static int floor(float value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static long packCell(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
