package endterraforged.world.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.management.ManagementFactory;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class EndHydraulicErosionPerformanceTest {

    private static final int WARMUP_BUILDS = 4;
    private static final int COLD_128_BUILDS = 12;
    private static final int COLD_256_BUILDS = 8;
    private static final int WARM_HITS = 256;
    private static final int CACHE_SLOTS = 16;

    @Test
    void records128And256ColdWarmLatencyAndPrimitiveBudgets() {
        Observation core128 = observe(128, COLD_128_BUILDS);
        Observation core256 = observe(256, COLD_256_BUILDS);

        assertTrue(core128.checksum() != 0L);
        assertTrue(core256.checksum() != 0L);
        assertEquals(77L * core128.cells(), core128.peakBuildPrimitiveBytes());
        assertEquals(77L * core256.cells(), core256.peakBuildPrimitiveBytes());
        assertEquals(20L * core128.cells(), core128.outputPrimitiveBytes());
        assertEquals(20L * core256.cells(), core256.outputPrimitiveBytes());
        assertEquals(16L * core128.cells(), core128.scratchPrimitiveBytes());
        assertEquals(16L * core256.cells(), core256.scratchPrimitiveBytes());

        long sixWorkerBudget = 6L * (core128.peakResidentPrimitiveBytes()
                + core128.scratchPrimitiveBytes() + core128.runtimePrimitiveBytes());
        System.out.printf(
                "[perf] p47Hydraulic core128 cold p50/p95 %.3f/%.3f ms; "
                        + "warm p50/p95 %.1f/%.1f ns; output %,d, scratch %,d, "
                        + "build peak %,d, 16-slot resident %,d, 6-worker estimate %,d bytes; "
                        + "droplets %,d, steps %,d, brush writes %,d, core/halo %,d/%,d, "
                        + "stops stationary/boundary/lifetime %,d/%,d/%,d; checksum %d%n",
                core128.coldP50Nanos() / 1_000_000.0D,
                core128.coldP95Nanos() / 1_000_000.0D,
                (double) core128.warmP50Nanos(),
                (double) core128.warmP95Nanos(),
                core128.outputPrimitiveBytes(), core128.scratchPrimitiveBytes(),
                core128.peakBuildPrimitiveBytes(), core128.peakResidentPrimitiveBytes(),
                sixWorkerBudget, core128.stats().droplets(), core128.stats().steps(),
                core128.stats().brushWrites(), core128.stats().coreSteps(),
                core128.stats().haloSteps(), core128.stats().stationaryStops(),
                core128.stats().boundaryStops(), core128.stats().lifetimeStops(),
                core128.checksum());
        System.out.printf(
                "[perf] p47Hydraulic core256 cold p50/p95 %.3f/%.3f ms; "
                        + "output %,d, scratch %,d, build peak %,d; droplets %,d, steps %,d, "
                        + "brush writes %,d, core/halo %,d/%,d, "
                        + "stops stationary/boundary/lifetime %,d/%,d/%,d; checksum %d%n",
                core256.coldP50Nanos() / 1_000_000.0D,
                core256.coldP95Nanos() / 1_000_000.0D,
                core256.outputPrimitiveBytes(), core256.scratchPrimitiveBytes(),
                core256.peakBuildPrimitiveBytes(), core256.stats().droplets(),
                core256.stats().steps(), core256.stats().brushWrites(),
                core256.stats().coreSteps(), core256.stats().haloSteps(),
                core256.stats().stationaryStops(), core256.stats().boundaryStops(),
                core256.stats().lifetimeStops(), core256.checksum());
    }

    @Test
    void recordsColdBuildAndWarmHitAllocation() {
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
            EndErosionTileKey key = EndHydraulicErosionRuntimeTest.key(128, 0, 0);
            EndHydraulicErosionRuntime runtime = new EndHydraulicErosionRuntime();
            EndHydraulicErosionTileBuilder builder = builder(key, runtime);
            for (int index = 0; index < WARMUP_BUILDS; index++) {
                builder.build(key.withTile(index, -index));
            }

            long threadId = Thread.currentThread().threadId();
            long beforeCold = allocationBean.getThreadAllocatedBytes(threadId);
            long checksum = 0L;
            for (int index = 0; index < COLD_128_BUILDS; index++) {
                checksum += builder.build(key.withTile(index, 1 - index)).tile().checksum();
            }
            long coldBytes = allocationBean.getThreadAllocatedBytes(threadId) - beforeCold;

            EndErosionTileCache<EndHydraulicErosionTile> cache = new EndErosionTileCache<>(1);
            EndHydraulicErosionTile cached = cache.getOrBuild(key, builder);
            long beforeWarm = allocationBean.getThreadAllocatedBytes(threadId);
            for (int index = 0; index < WARM_HITS; index++) {
                checksum += cache.getOrBuild(key, builder).checksum();
            }
            long warmBytes = allocationBean.getThreadAllocatedBytes(threadId) - beforeWarm;

            assertTrue(checksum != 0L);
            assertTrue(coldBytes > 0L);
            assertTrue(warmBytes >= 0L);
            assertEquals(cached.primitiveBytes(), cache.metrics().peakResidentPrimitiveBytes());
            System.out.printf(
                    "[perf] p47HydraulicAllocation core128 cold %,d bytes/build; "
                            + "warm %.3f bytes/hit%n",
                    coldBytes / COLD_128_BUILDS, warmBytes / (double) WARM_HITS);
        } finally {
            if (restoreDisabled) {
                allocationBean.setThreadAllocatedMemoryEnabled(false);
            }
        }
    }

    private static Observation observe(int coreBlocks, int coldBuilds) {
        EndErosionTileKey template = EndHydraulicErosionRuntimeTest.key(coreBlocks, 0, 0);
        EndHydraulicErosionRuntime runtime = new EndHydraulicErosionRuntime();
        EndHydraulicErosionTileBuilder builder = builder(template, runtime);
        for (int index = 0; index < WARMUP_BUILDS; index++) {
            builder.build(template.withTile(index, -index));
        }

        long[] coldNanos = new long[coldBuilds];
        EndHydraulicErosionTile last = null;
        long checksum = 0L;
        long peakBuild = 0L;
        for (int index = 0; index < coldNanos.length; index++) {
            EndErosionTileKey key = template.withTile(index - coldNanos.length / 2, index);
            long start = System.nanoTime();
            EndErosionTileCache.BuildResult<EndHydraulicErosionTile> result = builder.build(key);
            coldNanos[index] = System.nanoTime() - start;
            last = result.tile();
            peakBuild = Math.max(peakBuild, result.peakPrimitiveBytes());
            checksum += last.checksum();
        }

        EndErosionTileCache<EndHydraulicErosionTile> cache =
                new EndErosionTileCache<>(CACHE_SLOTS);
        EndErosionTileKey[] keys = new EndErosionTileKey[CACHE_SLOTS];
        for (int index = 0; index < keys.length; index++) {
            keys[index] = template.withTile(index - 8, 7 - index);
            cache.getOrBuild(keys[index], builder);
        }
        long[] warmNanos = new long[WARM_HITS];
        for (int index = 0; index < warmNanos.length; index++) {
            long start = System.nanoTime();
            EndHydraulicErosionTile tile = cache.getOrBuild(keys[index & 15], builder);
            warmNanos[index] = System.nanoTime() - start;
            checksum += tile.checksum();
        }

        Arrays.sort(coldNanos);
        Arrays.sort(warmNanos);
        return new Observation(
                template.cellCount(), percentile(coldNanos, 0.50D),
                percentile(coldNanos, 0.95D), percentile(warmNanos, 0.50D),
                percentile(warmNanos, 0.95D), last.primitiveBytes(),
                builder.scratchPrimitiveBytes(), runtime.primitiveBytes(), peakBuild,
                cache.metrics().peakResidentPrimitiveBytes(), last.stats(), checksum);
    }

    private static EndHydraulicErosionTileBuilder builder(
            EndErosionTileKey key,
            EndHydraulicErosionRuntime runtime) {
        HydraulicWorldFixtureBuilder input = new HydraulicWorldFixtureBuilder(
                HydraulicWorldFixtureBuilder.Kind.LONG_PLANE, 128.0F, 128.0F);
        return new EndHydraulicErosionTileBuilder(input, runtime, key.cellCount());
    }

    private static long percentile(long[] sorted, double percentile) {
        int index = (int) Math.ceil(sorted.length * percentile) - 1;
        return sorted[Math.clamp(index, 0, sorted.length - 1)];
    }

    private record Observation(int cells,
                               long coldP50Nanos,
                               long coldP95Nanos,
                               long warmP50Nanos,
                               long warmP95Nanos,
                               long outputPrimitiveBytes,
                               long scratchPrimitiveBytes,
                               long runtimePrimitiveBytes,
                               long peakBuildPrimitiveBytes,
                               long peakResidentPrimitiveBytes,
                               EndHydraulicErosionTile.Stats stats,
                               long checksum) {
    }
}
