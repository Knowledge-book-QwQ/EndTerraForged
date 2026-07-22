package endterraforged.world.continent;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.ContinentConfig;
import endterraforged.world.noise.Noise;

class RtfAdvancedContinentTest {

    private static final int ROOT_SEED = 123456789;
    private static final float[][] FIXTURE_COORDINATES = {
            {0.0F, 0.0F},
            {1024.5F, -2048.25F},
            {-4096.75F, 8192.5F},
            {31777.0F, -25001.125F},
            {-240000.5F, -160000.25F},
            {750000.0F, 1250000.0F}
    };
    private static final int[] EXPECTED_EDGE_BITS = {
            0, 1046574668, 0, 0, 0, 1060399441
    };
    private static final int[] EXPECTED_ID_BITS = {
            1042088706, 1040851522, 0, 0, 0, 1062818410
    };
    private static final int[] EXPECTED_DISTANCE_BITS = {
            983215344, 1039450287, 1036809113, 1049559435, 1033275783, 1044612980
    };
    private static final int[] EXPECTED_CENTERS = {
            5915, 4727, 1809, -8999, -6675, 14992,
            30827, -19556, -246246, -162132, 746336, 1253864
    };
    private static final boolean[] EXPECTED_SKIPPED = {
            false, false, true, true, true, false
    };

    private static ContinentConfig r9_3_6Defaults() {
        return ContinentConfig.rtfMultiDefaults();
    }

    private static RtfAdvancedContinent continent() {
        return new RtfAdvancedContinent(ROOT_SEED, r9_3_6Defaults());
    }

    @Test
    void matchesR9_3_6AndR9_6GoldenFixture() {
        RtfAdvancedContinent continent = continent();
        AdvancedContinentSignalBuffer sample = new AdvancedContinentSignalBuffer();
        int[] edgeBits = new int[FIXTURE_COORDINATES.length];
        int[] idBits = new int[FIXTURE_COORDINATES.length];
        int[] distanceBits = new int[FIXTURE_COORDINATES.length];
        int[] centers = new int[FIXTURE_COORDINATES.length * 2];
        boolean[] skipped = new boolean[FIXTURE_COORDINATES.length];
        for (int i = 0; i < FIXTURE_COORDINATES.length; i++) {
            float[] coordinate = FIXTURE_COORDINATES[i];
            continent.sampleAdvanced(coordinate[0], coordinate[1], sample);
            edgeBits[i] = Float.floatToIntBits(sample.edge());
            idBits[i] = Float.floatToIntBits(sample.continentId());
            distanceBits[i] = Float.floatToIntBits(sample.distance());
            centers[i * 2] = sample.centerX();
            centers[i * 2 + 1] = sample.centerZ();
            skipped[i] = sample.skipped();
        }
        assertArrayEquals(EXPECTED_EDGE_BITS, edgeBits);
        assertArrayEquals(EXPECTED_ID_BITS, idBits);
        assertArrayEquals(EXPECTED_DISTANCE_BITS, distanceBits);
        assertArrayEquals(EXPECTED_CENTERS, centers);
        assertArrayEquals(EXPECTED_SKIPPED, skipped);
    }

    @Test
    void outputIsFiniteAndWithinUnitRangeAtNegativeAndRemoteCoordinates() {
        RtfAdvancedContinent continent = continent();
        AdvancedContinentSignalBuffer sample = new AdvancedContinentSignalBuffer();
        for (float[] coordinate : FIXTURE_COORDINATES) {
            continent.sampleAdvanced(coordinate[0], coordinate[1], sample);
            assertTrue(Float.isFinite(sample.edge()));
            assertTrue(sample.edge() >= 0.0F && sample.edge() <= 1.0F);
            assertTrue(Float.isFinite(sample.distance()));
            assertTrue(sample.distance() >= 0.0F);
        }
    }

    @Test
    void defaultContinentIsNeverSkipped() {
        AdvancedContinentSignalBuffer sample = new AdvancedContinentSignalBuffer();
        continent().sampleAdvanced(0.0F, 0.0F, sample);
        assertFalse(sample.skipped());
    }

    @Test
    void skippedSampleClearsPreviousIdentityAndEdge() {
        RtfAdvancedContinent continent = continent();
        AdvancedContinentSignalBuffer sample = new AdvancedContinentSignalBuffer();
        continent.sampleAdvanced(0.0F, 0.0F, sample);
        assertFalse(sample.skipped());
        assertTrue(sample.continentId() > 0.0F);

        continent.sampleAdvanced(-4096.75F, 8192.5F, sample);
        assertTrue(sample.skipped());
        assertEquals(0.0F, sample.edge(), 0.0F);
        assertEquals(0.0F, sample.continentId(), 0.0F);
    }

    @Test
    void edgeIsContinuousAcrossBlockScaleNeighbours() {
        RtfAdvancedContinent continent = continent();
        for (int z = -24000; z <= 24000; z += 1200) {
            for (int x = -24000; x <= 24000; x += 1200) {
                float center = continent.compute(x, z, 0);
                float east = continent.compute(x + 1.0F, z, 0);
                float south = continent.compute(x, z + 1.0F, 0);
                assertTrue(Math.abs(center - east) < 0.01F,
                        "east neighbour discontinuity at " + x + "," + z);
                assertTrue(Math.abs(center - south) < 0.01F,
                        "south neighbour discontinuity at " + x + "," + z);
            }
        }
    }

    @Test
    void genericSignalSamplingRetainsRawAdvancedEdge() {
        RtfAdvancedContinent continent = continent();
        ContinentSignalBuffer generic = new ContinentSignalBuffer();
        AdvancedContinentSignalBuffer advanced = new AdvancedContinentSignalBuffer();
        for (float[] coordinate : FIXTURE_COORDINATES) {
            continent.sampleSignals(coordinate[0], coordinate[1], 0, generic);
            continent.sampleAdvanced(coordinate[0], coordinate[1], advanced);
            assertEquals(Float.floatToIntBits(continent.compute(coordinate[0], coordinate[1], 0)),
                    Float.floatToIntBits(generic.landness()));
            assertEquals(Float.floatToIntBits(generic.edge()),
                    Float.floatToIntBits(generic.landness()));
            assertEquals(1.0F, generic.inlandness(), 0.0F);
            assertEquals(!advanced.skipped(), generic.identified());
            if (generic.identified()) {
                assertEquals(advanced.centerX(), generic.centerX());
                assertEquals(advanced.centerZ(), generic.centerZ());
            } else {
                assertEquals(0L, generic.continentId());
                assertEquals(0, generic.centerX());
                assertEquals(0, generic.centerZ());
            }
        }
    }

    @Test
    void repeatedReorderedAndConcurrentSamplingIsBitStable() throws Exception {
        RtfAdvancedContinent continent = continent();
        int[] expected = sampleBits(continent, FIXTURE_COORDINATES);
        float[][] reverseCoordinates = FIXTURE_COORDINATES.clone();
        for (int left = 0, right = reverseCoordinates.length - 1; left < right; left++, right--) {
            float[] value = reverseCoordinates[left];
            reverseCoordinates[left] = reverseCoordinates[right];
            reverseCoordinates[right] = value;
        }
        int[] reverse = sampleBits(continent, reverseCoordinates);
        for (int i = 0; i < reverse.length / 2; i++) {
            int value = reverse[i];
            reverse[i] = reverse[reverse.length - 1 - i];
            reverse[reverse.length - 1 - i] = value;
        }
        assertArrayEquals(expected, reverse);
        assertArrayEquals(expected, sampleBits(continent, FIXTURE_COORDINATES));

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<int[]>> tasks = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                tasks.add(() -> sampleBits(continent, FIXTURE_COORDINATES));
            }
            for (Future<int[]> future : executor.invokeAll(tasks)) {
                assertArrayEquals(expected, future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void mapAllReachesWarpAndCoastNoiseLeaves() {
        RtfAdvancedContinent continent = continent();
        int[] perlinLeaves = {0};
        int[] simplexLeaves = {0};
        int[] simplex2Leaves = {0};
        Noise mapped = continent.mapAll(noise -> {
            String type = noise.getClass().getSimpleName();
            if ("Perlin2".equals(type)) {
                perlinLeaves[0]++;
            } else if ("Simplex".equals(type)) {
                simplexLeaves[0]++;
            } else if ("Simplex2".equals(type)) {
                simplex2Leaves[0]++;
            }
            return noise;
        });

        assertTrue(perlinLeaves[0] >= 2);
        assertTrue(simplexLeaves[0] >= 1);
        assertTrue(simplex2Leaves[0] >= 1);
        assertTrue(mapped instanceof RtfAdvancedContinent);
    }

    private static int[] sampleBits(RtfAdvancedContinent continent, float[][] coordinates) {
        int[] values = new int[coordinates.length];
        for (int i = 0; i < coordinates.length; i++) {
            values[i] = Float.floatToIntBits(continent.compute(
                    coordinates[i][0], coordinates[i][1], 0));
        }
        return values;
    }
}
