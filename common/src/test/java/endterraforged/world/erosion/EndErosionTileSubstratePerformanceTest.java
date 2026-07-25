package endterraforged.world.erosion;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.management.ManagementFactory;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class EndErosionTileSubstratePerformanceTest {

    private static final int WARMUP_BUILDS = 32;
    private static final int MEASURE_BUILDS = 64;
    private static final int MEASURE_HITS = 256;
    private static final long ALGORITHM_ID = 0x4554465F54494C45L;
    private static final long OWNER = 0x13579BDF2468ACE0L;
    private static final EndErosionTileKey[] KEYS = createKeys(16);

    @Test
    void recordsColdBuildWarmHitAndPrimitivePeaks() {
        ErosionTileFixtureBuilder builder = new ErosionTileFixtureBuilder(
                ErosionFixture.create(ErosionFixture.Kind.WATERSHED));
        for (int index = 0; index < WARMUP_BUILDS; index++) {
            builder.build(KEYS[index & 15]);
        }

        long[] coldNanos = new long[MEASURE_BUILDS];
        long checksum = 0L;
        EndErosionTileCache coldCache = null;
        for (int index = 0; index < coldNanos.length; index++) {
            coldCache = new EndErosionTileCache(1);
            long start = System.nanoTime();
            EndErosionTile tile = coldCache.getOrBuild(KEYS[index & 15], builder);
            coldNanos[index] = System.nanoTime() - start;
            checksum += tile.checksum();
        }

        EndErosionTileCache warmCache = new EndErosionTileCache(KEYS.length);
        for (EndErosionTileKey key : KEYS) {
            warmCache.getOrBuild(key, builder);
        }
        long[] warmNanos = new long[MEASURE_HITS];
        for (int index = 0; index < warmNanos.length; index++) {
            long start = System.nanoTime();
            EndErosionTile tile = warmCache.getOrBuild(KEYS[index & 15], builder);
            warmNanos[index] = System.nanoTime() - start;
            checksum += tile.checksum();
        }

        Arrays.sort(coldNanos);
        Arrays.sort(warmNanos);
        EndErosionTileCache.Metrics cold = coldCache.metrics();
        EndErosionTileCache.Metrics warm = warmCache.metrics();
        assertTrue(checksum != 0L, "DCE guard: tile checksum must be non-zero");
        assertTrue(cold.peakBuildPrimitiveBytes() > 0L);
        assertTrue(warm.peakResidentPrimitiveBytes() > cold.peakResidentPrimitiveBytes());
        System.out.printf(
                "[perf] p47TileSubstrate cold p50/p95 %.3f/%.3f ms; "
                        + "warm p50/p95 %.1f/%.1f ns; tile %,d primitive bytes; "
                        + "peak resident %,d bytes; checksum %d%n",
                percentile(coldNanos, 0.50D) / 1_000_000.0D,
                percentile(coldNanos, 0.95D) / 1_000_000.0D,
                (double) percentile(warmNanos, 0.50D),
                (double) percentile(warmNanos, 0.95D),
                cold.peakBuildPrimitiveBytes(), warm.peakResidentPrimitiveBytes(), checksum);
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
            ErosionTileFixtureBuilder builder = new ErosionTileFixtureBuilder(
                    ErosionFixture.create(ErosionFixture.Kind.WATERSHED));
            for (int index = 0; index < WARMUP_BUILDS; index++) {
                builder.build(KEYS[index & 15]);
            }
            long threadId = Thread.currentThread().threadId();
            long beforeCold = allocationBean.getThreadAllocatedBytes(threadId);
            long checksum = 0L;
            for (int index = 0; index < MEASURE_BUILDS; index++) {
                checksum += builder.build(KEYS[index & 15]).tile().checksum();
            }
            long coldBytes = allocationBean.getThreadAllocatedBytes(threadId) - beforeCold;

            EndErosionTileCache cache = new EndErosionTileCache(KEYS.length);
            for (EndErosionTileKey key : KEYS) {
                cache.getOrBuild(key, builder);
            }
            long beforeWarm = allocationBean.getThreadAllocatedBytes(threadId);
            for (int index = 0; index < MEASURE_HITS; index++) {
                checksum += cache.getOrBuild(KEYS[index & 15], builder).checksum();
            }
            long warmBytes = allocationBean.getThreadAllocatedBytes(threadId) - beforeWarm;

            assertTrue(checksum != 0L, "DCE guard: allocation checksum must be non-zero");
            assertTrue(coldBytes > 0L, "cold tile builds must allocate the immutable artifact");
            assertTrue(warmBytes >= 0L, "thread allocation counter must be monotonic");
            System.out.printf(
                    "[perf] p47TileSubstrateAllocation: cold %,d bytes/build; "
                            + "warm %.3f bytes/hit%n",
                    coldBytes / MEASURE_BUILDS, warmBytes / (double) MEASURE_HITS);
        } finally {
            if (restoreDisabled) {
                allocationBean.setThreadAllocatedMemoryEnabled(false);
            }
        }
    }

    private static long percentile(long[] sorted, double percentile) {
        int index = (int) Math.ceil(sorted.length * percentile) - 1;
        return sorted[Math.clamp(index, 0, sorted.length - 1)];
    }

    private static EndErosionTileKey[] createKeys(int count) {
        EndErosionTileKey[] keys = new EndErosionTileKey[count];
        for (int index = 0; index < keys.length; index++) {
            keys[index] = new EndErosionTileKey(
                    ALGORITHM_ID, 1, 123456789L, OWNER, -256,
                    (int) ErosionFixture.WORLD_HEIGHT_BLOCKS, 4,
                    index - 8, 7 - index, ErosionFixture.SIZE, ErosionFixture.SIZE,
                    ErosionFixture.HALO, (int) ErosionFixture.SAMPLE_DISTANCE_BLOCKS);
        }
        return keys;
    }
}
