package endterraforged.world.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.management.ManagementFactory;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class EndBoundedFlowErosionPerformanceTest {

    private static final int WARMUP_BUILDS = 3;
    private static final int COLD_128_BUILDS = 8;
    private static final int COLD_256_BUILDS = 5;
    private static final int WARM_HITS = 256;
    private static final int CACHE_SLOTS = 16;
    private static final long FLOOD_SCRATCH_BYTES = 1_176L;

    @Test
    void records128And256ColdWarmLatencyAndPrimitiveBudgets() {
        Observation core128 = observe(128, COLD_128_BUILDS);
        Observation core256 = observe(256, COLD_256_BUILDS);

        assertTrue(core128.checksum() != 0L);
        assertTrue(core256.checksum() != 0L);
        assertEquals(89L * core128.cells() + FLOOD_SCRATCH_BYTES,
                core128.peakBuildPrimitiveBytes());
        assertEquals(89L * core256.cells() + FLOOD_SCRATCH_BYTES,
                core256.peakBuildPrimitiveBytes());
        assertEquals(21L * core128.cells(), core128.outputPrimitiveBytes());
        assertEquals(21L * core256.cells(), core256.outputPrimitiveBytes());
        assertEquals(27L * core128.cells() + FLOOD_SCRATCH_BYTES,
                core128.scratchPrimitiveBytes());
        assertEquals(27L * core256.cells() + FLOOD_SCRATCH_BYTES,
                core256.scratchPrimitiveBytes());

        long sixWorkerBudget = 6L * (core128.peakResidentPrimitiveBytes()
                + core128.scratchPrimitiveBytes() + core128.runtimePrimitiveBytes());
        System.out.printf(
                "[perf] p47BoundedFlow core128 cold p50/p95 %.3f/%.3f ms; "
                        + "warm p50/p95 %.1f/%.1f ns; output %,d, scratch %,d, "
                        + "build peak %,d, 16-slot resident %,d, 6-worker estimate %,d bytes; "
                        + "queries %,d, heap push/pop %,d/%,d, routed/split %,d/%,d, "
                        + "transfers %,d, incision %,d, terminal/truncated %.1f/%.1f, "
                        + "cut %.3f, max spill %.3f; checksum %d%n",
                core128.coldP50Nanos() / 1_000_000.0D,
                core128.coldP95Nanos() / 1_000_000.0D,
                (double) core128.warmP50Nanos(), (double) core128.warmP95Nanos(),
                core128.outputPrimitiveBytes(), core128.scratchPrimitiveBytes(),
                core128.peakBuildPrimitiveBytes(), core128.peakResidentPrimitiveBytes(),
                sixWorkerBudget, core128.stats().priorityQueries(),
                core128.stats().heapPushes(), core128.stats().heapPops(),
                core128.stats().routedCells(), core128.stats().splitCells(),
                core128.stats().flowTransfers(), core128.stats().incisionCells(),
                core128.stats().terminalExportArea(), core128.stats().truncatedArea(),
                core128.stats().totalCutBlocks(),
                core128.stats().maximumSpillDepthBlocks(), core128.checksum());
        System.out.printf(
                "[perf] p47BoundedFlow core256 cold p50/p95 %.3f/%.3f ms; "
                        + "output %,d, scratch %,d, build peak %,d; queries %,d, "
                        + "heap push/pop %,d/%,d, routed/split %,d/%,d, transfers %,d, "
                        + "incision %,d, terminal/truncated %.1f/%.1f, cut %.3f, "
                        + "max spill %.3f; checksum %d%n",
                core256.coldP50Nanos() / 1_000_000.0D,
                core256.coldP95Nanos() / 1_000_000.0D,
                core256.outputPrimitiveBytes(), core256.scratchPrimitiveBytes(),
                core256.peakBuildPrimitiveBytes(), core256.stats().priorityQueries(),
                core256.stats().heapPushes(), core256.stats().heapPops(),
                core256.stats().routedCells(), core256.stats().splitCells(),
                core256.stats().flowTransfers(), core256.stats().incisionCells(),
                core256.stats().terminalExportArea(), core256.stats().truncatedArea(),
                core256.stats().totalCutBlocks(),
                core256.stats().maximumSpillDepthBlocks(), core256.checksum());
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
            EndErosionTileKey key = EndBoundedFlowErosionRuntimeTest.key(128, 0, 0);
            EndBoundedFlowErosionRuntime runtime = new EndBoundedFlowErosionRuntime();
            EndBoundedFlowErosionTileBuilder builder = builder(key, runtime);
            for (int index = 0; index < WARMUP_BUILDS; index++) {
                builder.build(key);
            }

            long threadId = Thread.currentThread().threadId();
            long beforeCold = allocationBean.getThreadAllocatedBytes(threadId);
            long checksum = 0L;
            for (int index = 0; index < COLD_128_BUILDS; index++) {
                checksum += builder.build(key).tile().checksum();
            }
            long coldBytes = allocationBean.getThreadAllocatedBytes(threadId) - beforeCold;

            EndErosionTileCache<EndBoundedFlowErosionTile> cache = new EndErosionTileCache<>(1);
            EndBoundedFlowErosionTile cached = cache.getOrBuild(key, builder);
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
                    "[perf] p47BoundedFlowAllocation core128 cold %,d bytes/build; "
                            + "warm %.3f bytes/hit%n",
                    coldBytes / COLD_128_BUILDS, warmBytes / (double) WARM_HITS);
        } finally {
            if (restoreDisabled) {
                allocationBean.setThreadAllocatedMemoryEnabled(false);
            }
        }
    }

    private static Observation observe(int coreBlocks, int coldBuilds) {
        EndErosionTileKey template = EndBoundedFlowErosionRuntimeTest.key(coreBlocks, 0, 0);
        EndBoundedFlowErosionRuntime runtime = new EndBoundedFlowErosionRuntime();
        EndBoundedFlowErosionTileBuilder builder = builder(template, runtime);
        for (int index = 0; index < WARMUP_BUILDS; index++) {
            builder.build(template);
        }

        long[] coldNanos = new long[coldBuilds];
        EndBoundedFlowErosionTile last = null;
        long checksum = 0L;
        long peakBuild = 0L;
        for (int index = 0; index < coldNanos.length; index++) {
            long start = System.nanoTime();
            EndErosionTileCache.BuildResult<EndBoundedFlowErosionTile> result =
                    builder.build(template);
            coldNanos[index] = System.nanoTime() - start;
            last = result.tile();
            peakBuild = Math.max(peakBuild, result.peakPrimitiveBytes());
            checksum += last.checksum();
        }

        EndErosionTileCache<EndBoundedFlowErosionTile> cache =
                new EndErosionTileCache<>(CACHE_SLOTS);
        EndErosionTileKey[] keys = new EndErosionTileKey[CACHE_SLOTS];
        for (int index = 0; index < keys.length; index++) {
            keys[index] = template.withTile(index - 8, 7 - index);
            cache.getOrBuild(keys[index], builder);
        }
        long[] warmNanos = new long[WARM_HITS];
        for (int index = 0; index < warmNanos.length; index++) {
            long start = System.nanoTime();
            EndBoundedFlowErosionTile tile = cache.getOrBuild(keys[index & 15], builder);
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

    private static EndBoundedFlowErosionTileBuilder builder(
            EndErosionTileKey key,
            EndBoundedFlowErosionRuntime runtime) {
        ErosionWorldFixtureBuilder input = new ErosionWorldFixtureBuilder(
                ErosionWorldFixtureBuilder.Kind.FLOW_FIELD, 128.0F, 128.0F);
        return new EndBoundedFlowErosionTileBuilder(input, runtime, key.cellCount());
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
                               EndBoundedFlowErosionTile.Stats stats,
                               long checksum) {
    }
}
