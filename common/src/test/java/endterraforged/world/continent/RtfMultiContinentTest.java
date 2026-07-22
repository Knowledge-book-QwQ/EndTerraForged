package endterraforged.world.continent;

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

import endterraforged.world.config.ContinentAlgorithm;
import endterraforged.world.config.ContinentConfig;
import endterraforged.world.config.ContinentConfigBuilder;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.Perlin;

class RtfMultiContinentTest {

    private static final int ROOT_SEED = 123456789;
    private static final float[][] FIXTURE_COORDINATES = {
            {0.0F, 0.0F},
            {1024.5F, -2048.25F},
            {-4096.75F, 8192.5F},
            {31777.0F, -25001.125F},
            {-240000.5F, -160000.25F},
            {750000.0F, 1250000.0F}
    };
    // R9.3.6 ContinentGenerator with the documented defaults and ROOT_SEED.
    // Values are Float.floatToIntBits so this fixture stays independent of an
    // external RTF jar while detecting seed or composite-warp drift exactly.
    private static final int[] R9_3_6_GOLDEN_BITS = {
            0, 1044199456, 1009492574, 1058470254, 1032008532, 1057727727
    };

    private static ContinentConfig r9_3_6Defaults() {
        return new ContinentConfigBuilder(ContinentConfig.defaults())
                .continentScale(3000)
                .continentJitter(0.7F)
                .continentSkipping(0.25F)
                .continentSizeVariance(0.25F)
                .continentNoiseOctaves(5)
                .continentNoiseGain(0.26F)
                .continentNoiseLacunarity(4.33F)
                .continentAlgorithm(ContinentAlgorithm.RTF_MULTI)
                .build();
    }

    private static RtfMultiContinent continent() {
        return new RtfMultiContinent(ROOT_SEED, r9_3_6Defaults());
    }

    @Test
    void r9_3_6GoldenFixtureMatchesBitForBit() {
        assertArrayEquals(R9_3_6_GOLDEN_BITS, sampleBits(continent(), FIXTURE_COORDINATES));
    }

    @Test
    void outputIsFiniteAndWithinUnitRangeAtNegativeAndRemoteCoordinates() {
        RtfMultiContinent continent = continent();
        for (float[] coordinate : FIXTURE_COORDINATES) {
            float value = continent.compute(coordinate[0], coordinate[1], 0);
            assertTrue(Float.isFinite(value), "landness must be finite");
            assertTrue(value >= 0.0F && value <= 1.0F, "landness out of range: " + value);
        }
    }

    @Test
    void signalSamplingRetainsGoldenLandnessWithoutAllocatingARecord() {
        RtfMultiContinent continent = continent();
        ContinentSignalBuffer signals = new ContinentSignalBuffer();
        for (float[] coordinate : FIXTURE_COORDINATES) {
            continent.sampleSignals(coordinate[0], coordinate[1], 0, signals);
            assertEquals(Float.floatToIntBits(continent.compute(coordinate[0], coordinate[1], 0)),
                    Float.floatToIntBits(signals.landness()));
            assertTrue(signals.edge() >= 0.0F && signals.edge() <= 1.0F);
            assertEquals(1.0F, signals.inlandness(), 0.0F);
            assertTrue(signals.identified());
            long continentId = signals.continentId();
            int centerX = signals.centerX();
            int centerZ = signals.centerZ();
            continent.sampleSignals(coordinate[0], coordinate[1], 0, signals);
            assertEquals(continentId, signals.continentId());
            assertEquals(centerX, signals.centerX());
            assertEquals(centerZ, signals.centerZ());
        }
    }

    @Test
    void repeatedAndReorderedSamplingIsBitStable() {
        RtfMultiContinent continent = continent();
        int[] forward = sampleBits(continent, FIXTURE_COORDINATES);
        int[] reverse = new int[FIXTURE_COORDINATES.length];
        for (int i = FIXTURE_COORDINATES.length - 1; i >= 0; i--) {
            reverse[i] = Float.floatToIntBits(continent.compute(
                    FIXTURE_COORDINATES[i][0], FIXTURE_COORDINATES[i][1], 0));
        }
        assertArrayEquals(forward, reverse);
        assertArrayEquals(forward, sampleBits(continent, FIXTURE_COORDINATES));
    }

    @Test
    void concurrentSamplingMatchesSingleThreadReference() throws Exception {
        RtfMultiContinent continent = continent();
        int[] expected = sampleBits(continent, FIXTURE_COORDINATES);
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
    void subBlockSamplesStayContinuousAcrossCellEdges() {
        RtfMultiContinent continent = continent();
        for (int i = -128; i <= 128; i++) {
            float x = i * 93.75F;
            float before = continent.compute(x - 0.01F, 8192.5F, 0);
            float after = continent.compute(x + 0.01F, 8192.5F, 0);
            assertTrue(Math.abs(after - before) < 0.05F,
                    "landness should remain continuous around x=" + x);
        }
    }

    @Test
    void mapAllReachesCompositeWarpDrivers() {
        RtfMultiContinent continent = continent();
        int[] perlinLeaves = {0};
        Noise mapped = continent.mapAll(noise -> {
            if (noise instanceof Perlin) {
                perlinLeaves[0]++;
            }
            return noise;
        });

        assertTrue(perlinLeaves[0] >= 4, "composite warp must expose both Perlin drivers");
        assertTrue(mapped instanceof RtfMultiContinent);
    }

    private static int[] sampleBits(RtfMultiContinent continent, float[][] coordinates) {
        int[] values = new int[coordinates.length];
        for (int i = 0; i < coordinates.length; i++) {
            values[i] = Float.floatToIntBits(continent.compute(coordinates[i][0], coordinates[i][1], 0));
        }
        return values;
    }
}
