package endterraforged.world.erosion;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class EndAnalyticalErosionPerformanceTest {

    private static final int WARMUP_PASSES = 32;
    private static final int MEASURE_PASSES = 128;
    private static final List<ErosionFixture> FIXTURES = ErosionFixture.standardSet();

    @Test
    void recordsCanonicalFixtureCost() {
        EndAnalyticalErosionRuntime runtime = new EndAnalyticalErosionRuntime();
        EndAnalyticalErosionBuffer output = new EndAnalyticalErosionBuffer();
        benchmark(runtime, output, WARMUP_PASSES);

        long start = System.nanoTime();
        long checksum = benchmark(runtime, output, MEASURE_PASSES);
        long elapsed = System.nanoTime() - start;
        long samples = (long) MEASURE_PASSES * FIXTURES.size()
                * interiorSize() * interiorSize();

        assertTrue(checksum != 0L, "DCE guard: analytical erosion checksum must be non-zero");
        System.out.printf("[perf] p47AnalyticalErosionFixture: %.1f ns/sample, checksum %d%n",
                elapsed / (double) samples, checksum);
    }

    private static long benchmark(EndAnalyticalErosionRuntime runtime,
                                  EndAnalyticalErosionBuffer output,
                                  int passes) {
        long checksum = 0L;
        int end = ErosionFixture.SIZE - ErosionFixture.HALO;
        for (int pass = 0; pass < passes; pass++) {
            for (ErosionFixture fixture : FIXTURES) {
                for (int z = ErosionFixture.HALO; z < end; z++) {
                    for (int x = ErosionFixture.HALO; x < end; x++) {
                        runtime.apply(fixture.rawTop(x, z), ErosionFixture.WORLD_HEIGHT_BLOCKS,
                                fixture.slope(x, z), fixture.curvature(x, z), 1.0F, 0.0F,
                                fixture.landness(x, z), fixture.inlandness(x, z), 1.0F,
                                fixture.availableThicknessBlocks(x, z),
                                fixture.archipelagoDominant(x, z), output);
                        checksum += Float.floatToIntBits(output.top());
                        checksum += Float.floatToIntBits(output.erosionStrength());
                        checksum += Float.floatToIntBits(output.drainagePotential());
                    }
                }
            }
        }
        return checksum;
    }

    private static int interiorSize() {
        return ErosionFixture.SIZE - ErosionFixture.HALO * 2;
    }
}
