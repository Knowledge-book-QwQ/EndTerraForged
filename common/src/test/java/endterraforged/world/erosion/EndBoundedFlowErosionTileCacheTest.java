package endterraforged.world.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.LongAdder;

import org.junit.jupiter.api.Test;

class EndBoundedFlowErosionTileCacheTest {

    private static final int TILE_COUNT = 4;

    @Test
    void outputCacheReportsHitsEvictionsAndOwnerSwaps() {
        EndErosionTileKey firstKey = EndBoundedFlowErosionRuntimeTest.key(128, 0, 0);
        EndBoundedFlowErosionTileBuilder builder = builder(firstKey, null);
        EndErosionTileCache<EndBoundedFlowErosionTile> cache = new EndErosionTileCache<>(2);

        EndBoundedFlowErosionTile first = cache.getOrBuild(firstKey, builder);
        assertSame(first, cache.getOrBuild(firstKey, builder));
        cache.getOrBuild(firstKey.withTile(1, 0), builder);
        cache.getOrBuild(firstKey.withTile(2, 0), builder);
        EndErosionTileKey otherOwner = new EndErosionTileKey(
                firstKey.algorithmId(), firstKey.algorithmVersion(), firstKey.worldSeed(),
                firstKey.runtimeFingerprint() + 1L, firstKey.minY(), firstKey.worldHeight(),
                firstKey.terrainVersion(), 0, 0, firstKey.sampleWidth(), firstKey.sampleHeight(),
                firstKey.haloSamples(), firstKey.sampleDistanceBlocks());
        cache.getOrBuild(otherOwner, builder(otherOwner, null));

        EndErosionTileCache.Metrics metrics = cache.metrics();
        assertEquals(5L, metrics.requests());
        assertEquals(1L, metrics.hits());
        assertEquals(4L, metrics.builds());
        assertEquals(1L, metrics.evictions());
        assertEquals(1L, metrics.ownerSwaps());
        assertEquals(1, cache.size());
    }

    @Test
    void oneTwoFourAndSixWorkersKeepChecksumsAndExposeDuplicateBuilds()
            throws ExecutionException, InterruptedException {
        for (int workers : new int[]{1, 2, 4, 6}) {
            verifyWorkers(workers);
        }
    }

    private static void verifyWorkers(int workers)
            throws ExecutionException, InterruptedException {
        BuildLedger ledger = new BuildLedger();
        EndErosionTileKey[] keys = keys();
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<WorkerResult>> futures = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                int rotation = worker;
                futures.add(executor.submit(() -> workerRun(keys, rotation, ledger)));
            }
            Long expected = null;
            for (Future<WorkerResult> future : futures) {
                WorkerResult result = future.get();
                if (expected == null) {
                    expected = result.checksum();
                } else {
                    assertEquals(expected.longValue(), result.checksum());
                }
                assertEquals(TILE_COUNT, result.metrics().builds());
                assertEquals(TILE_COUNT, result.metrics().hits());
                assertEquals(0L, result.metrics().evictions());
            }
        } finally {
            executor.shutdownNow();
        }
        assertEquals((long) workers * TILE_COUNT, ledger.totalBuilds());
        assertEquals(TILE_COUNT, ledger.distinctKeys());
        assertEquals((long) (workers - 1) * TILE_COUNT, ledger.duplicateBuilds());
    }

    private static WorkerResult workerRun(EndErosionTileKey[] keys,
                                          int rotation,
                                          BuildLedger ledger) {
        EndBoundedFlowErosionTileBuilder builder = builder(keys[0], ledger::record);
        EndErosionTileCache<EndBoundedFlowErosionTile> cache =
                new EndErosionTileCache<>(TILE_COUNT);
        long checksum = 0L;
        for (int index = 0; index < keys.length; index++) {
            checksum += cache.getOrBuild(
                    keys[(index + rotation) % keys.length], builder).checksum();
        }
        for (int index = keys.length - 1; index >= 0; index--) {
            checksum += cache.getOrBuild(
                    keys[(index + rotation) % keys.length], builder).checksum();
        }
        return new WorkerResult(checksum, cache.metrics());
    }

    private static EndBoundedFlowErosionTileBuilder builder(
            EndErosionTileKey key,
            ErosionWorldFixtureBuilder.BuildObserver observer) {
        ErosionWorldFixtureBuilder input = new ErosionWorldFixtureBuilder(
                ErosionWorldFixtureBuilder.Kind.LONG_PLANE,
                64.0F, 64.0F, true, observer);
        return new EndBoundedFlowErosionTileBuilder(
                input, new EndBoundedFlowErosionRuntime(), key.cellCount());
    }

    private static EndErosionTileKey[] keys() {
        EndErosionTileKey[] keys = new EndErosionTileKey[TILE_COUNT];
        for (int index = 0; index < keys.length; index++) {
            keys[index] = EndBoundedFlowErosionRuntimeTest.key(
                    128, index - 2, 3 - index);
        }
        return keys;
    }

    private record WorkerResult(long checksum, EndErosionTileCache.Metrics metrics) {
    }

    private static final class BuildLedger {
        private final LongAdder totalBuilds = new LongAdder();
        private final Map<EndErosionTileKey, Boolean> distinct = new ConcurrentHashMap<>();

        private void record(EndErosionTileKey key) {
            this.totalBuilds.increment();
            this.distinct.put(key, Boolean.TRUE);
        }

        private long totalBuilds() {
            return this.totalBuilds.sum();
        }

        private long distinctKeys() {
            return this.distinct.size();
        }

        private long duplicateBuilds() {
            return totalBuilds() - distinctKeys();
        }
    }
}
