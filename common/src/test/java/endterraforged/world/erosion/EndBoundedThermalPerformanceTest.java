package endterraforged.world.erosion;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.management.ManagementFactory;

import org.junit.jupiter.api.Test;

class EndBoundedThermalPerformanceTest {

    private static final int WARMUP_PASSES = 32;
    private static final int MEASURE_PASSES = 128;
    private static final ErosionFixture[] FIXTURES =
            ErosionFixture.standardSet().toArray(ErosionFixture[]::new);

    @Test
    void recordsCanonicalFixtureCostAndScratchBytes() {
        EndBoundedThermalRuntime runtime = new EndBoundedThermalRuntime();
        EndBoundedThermalBuffer output = new EndBoundedThermalBuffer(
                ErosionFixture.SIZE * ErosionFixture.SIZE);
        benchmark(runtime, output, WARMUP_PASSES);

        long start = System.nanoTime();
        long checksum = benchmark(runtime, output, MEASURE_PASSES);
        long elapsed = System.nanoTime() - start;
        long outputCells = (long) MEASURE_PASSES * FIXTURES.length
                * interiorSize() * interiorSize();

        assertTrue(checksum != 0L, "DCE guard: bounded thermal checksum must be non-zero");
        System.out.printf("[perf] p47BoundedThermalFixture: %.1f ns/output cell, "
                        + "scratch %,d primitive bytes, checksum %d%n",
                elapsed / (double) outputCells, output.primitiveBytes(), checksum);
    }

    @Test
    void recordsWarmCurrentThreadAllocation() {
        java.lang.management.ThreadMXBean platformBean = ManagementFactory.getThreadMXBean();
        assumeTrue(platformBean instanceof com.sun.management.ThreadMXBean,
                "JVM does not expose per-thread allocation accounting");
        com.sun.management.ThreadMXBean allocationBean =
                (com.sun.management.ThreadMXBean) platformBean;
        assumeTrue(allocationBean.isThreadAllocatedMemorySupported(),
                "JVM does not support per-thread allocation accounting");

        boolean restoreDisabled = !allocationBean.isThreadAllocatedMemoryEnabled();
        if (restoreDisabled) {
            try {
                allocationBean.setThreadAllocatedMemoryEnabled(true);
            } catch (SecurityException | UnsupportedOperationException exception) {
                assumeTrue(false, () -> "Cannot enable per-thread allocation accounting: "
                        + exception.getMessage());
            }
        }

        try {
            EndBoundedThermalRuntime runtime = new EndBoundedThermalRuntime();
            EndBoundedThermalBuffer output = new EndBoundedThermalBuffer(
                    ErosionFixture.SIZE * ErosionFixture.SIZE);
            benchmark(runtime, output, WARMUP_PASSES);

            long threadId = Thread.currentThread().threadId();
            long before = allocationBean.getThreadAllocatedBytes(threadId);
            long checksum = benchmark(runtime, output, MEASURE_PASSES);
            long allocatedBytes = allocationBean.getThreadAllocatedBytes(threadId) - before;

            assertTrue(checksum != 0L, "DCE guard: allocation checksum must be non-zero");
            assertTrue(allocatedBytes >= 0L, "thread allocation counter must be monotonic");
            System.out.printf("[perf] p47BoundedThermalWarmAllocation: %,d bytes total, "
                            + "%.3f bytes/apply%n",
                    allocatedBytes, allocatedBytes / (double) (MEASURE_PASSES * FIXTURES.length));
        } finally {
            if (restoreDisabled) {
                allocationBean.setThreadAllocatedMemoryEnabled(false);
            }
        }
    }

    private static long benchmark(EndBoundedThermalRuntime runtime,
                                  EndBoundedThermalBuffer output,
                                  int passes) {
        long checksum = 0L;
        for (int pass = 0; pass < passes; pass++) {
            for (int fixtureIndex = 0; fixtureIndex < FIXTURES.length; fixtureIndex++) {
                ErosionFixture fixture = FIXTURES[fixtureIndex];
                runtime.apply(ErosionFixture.SIZE, ErosionFixture.SIZE,
                        ErosionFixture.WORLD_HEIGHT_BLOCKS, ErosionFixture.SAMPLE_DISTANCE_BLOCKS,
                        fixture.rawTopValues(), fixture.landnessValues(), fixture.inlandnessValues(),
                        fixture.outerActivationValues(), fixture.erosionResistanceValues(),
                        fixture.availableThicknessValues(), fixture.erosionMaskedValues(),
                        fixture.archipelagoDominantValues(), output);
                int centre = fixture.index(ErosionFixture.SIZE / 2, ErosionFixture.SIZE / 2);
                checksum += Float.floatToIntBits(output.top(centre));
                checksum += Float.floatToIntBits(output.movedMaterialBlocks());
                checksum += output.transferCount();
            }
        }
        return checksum;
    }

    private static int interiorSize() {
        return ErosionFixture.SIZE - ErosionFixture.HALO * 2;
    }
}
