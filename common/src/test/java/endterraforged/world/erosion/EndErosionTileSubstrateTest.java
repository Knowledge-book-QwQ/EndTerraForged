package endterraforged.world.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.LongAdder;

import org.junit.jupiter.api.Test;

class EndErosionTileSubstrateTest {

    private static final long ALGORITHM_ID = 0x4554465F54494C45L;
    private static final long WORLD_SEED = 123456789L;
    private static final long OWNER = 0x13579BDF2468ACE0L;
    private static final int TERRAIN_VERSION = 4;
    private static final int TILE_COUNT = 4;

    @Test
    void keyUsesCanonicalFloorDivisionAtPositiveAndNegativeCoordinates() {
        EndErosionTileKey template = key(0, 0, OWNER);
        int coreWidth = template.coreWidthBlocks();
        int coreHeight = template.coreHeightBlocks();

        assertEquals(116, coreWidth);
        assertEquals(116, coreHeight);
        assertEquals(0, template.forBlock(0, 0).tileX());
        assertEquals(0, template.forBlock(coreWidth - 1, coreHeight - 1).tileX());
        assertEquals(1, template.forBlock(coreWidth, coreHeight).tileX());
        assertEquals(-1, template.forBlock(-1, -1).tileX());
        assertEquals(-2, template.forBlock(-coreWidth - 1, -coreHeight - 1).tileX());

        EndErosionTileKey negative = template.withTile(-1, -1);
        assertEquals(-116L, negative.originBlockX());
        assertEquals(-116L, negative.originBlockZ());
        assertEquals(-124L, negative.sampleOriginBlockX());
        assertEquals(-124L, negative.sampleOriginBlockZ());
    }

    @Test
    void keyCanRepresentBothPlannedCoreSizesWithoutChangingTheHaloRule() {
        EndErosionTileKey core128 = geometryKey(36, 36);
        EndErosionTileKey core256 = geometryKey(68, 68);

        assertEquals(128, core128.coreWidthBlocks());
        assertEquals(128, core128.coreHeightBlocks());
        assertEquals(256, core256.coreWidthBlocks());
        assertEquals(256, core256.coreHeightBlocks());
        assertEquals(ErosionFixture.HALO, core128.haloSamples());
        assertEquals(ErosionFixture.HALO, core256.haloSamples());
    }

    @Test
    void artifactOwnsEveryPrimitiveChannelAndIgnoresLaterFixtureMutation() {
        ErosionFixture fixture = ErosionFixture.create(ErosionFixture.Kind.RIDGE);
        EndErosionTile tile = new ErosionTileFixtureBuilder(fixture).build(key(2, -3, OWNER)).tile();
        int centre = tile.index(ErosionFixture.SIZE / 2, ErosionFixture.SIZE / 2);
        float originalRawTop = fixture.rawTopValues()[centre];
        long checksum = tile.checksum();

        assertEquals(originalRawTop * ErosionFixture.WORLD_HEIGHT_BLOCKS,
                tile.sourceTopBlocks(centre), 0.0F);
        assertEquals(fixture.landnessValues()[centre], tile.landness(centre), 0.0F);
        assertEquals(fixture.inlandnessValues()[centre], tile.inlandness(centre), 0.0F);
        assertEquals(fixture.outerActivationValues()[centre], tile.outerActivation(centre), 0.0F);
        assertEquals(fixture.roughnessValues()[centre], tile.roughness(centre), 0.0F);
        assertEquals(fixture.erosionResistanceValues()[centre],
                tile.erosionResistance(centre), 0.0F);
        assertEquals(fixture.availableThicknessValues()[centre],
                tile.availableThicknessBlocks(centre), 0.0F);
        assertEquals(fixture.ridgeInfluenceValues()[centre], tile.ridgeInfluence(centre), 0.0F);
        assertEquals(fixture.areaFamilyValues()[centre], tile.areaFamily(centre));
        assertEquals(fixture.terrainTagsValues()[centre], tile.terrainTags(centre));
        assertEquals((long) key(2, -3, OWNER).cellCount() * 41L, tile.primitiveBytes());

        fixture.rawTopValues()[centre] = 0.05F;
        assertEquals(originalRawTop * ErosionFixture.WORLD_HEIGHT_BLOCKS,
                tile.sourceTopBlocks(centre), 0.0F);
        assertEquals(checksum, tile.checksum());
    }

    @Test
    void maskBitsKeepProtectedAndArchipelagoSignalsDistinct() {
        ErosionFixture coast = ErosionFixture.create(ErosionFixture.Kind.COAST_THIN_SHELF);
        EndErosionTile coastTile = new ErosionTileFixtureBuilder(coast).build(key(0, 0, OWNER)).tile();
        for (int index = 0; index < key(0, 0, OWNER).cellCount(); index++) {
            assertTrue(coastTile.erosionProtected(index));
            assertFalse(coastTile.archipelagoDominant(index));
        }

        ErosionFixture archipelago = ErosionFixture.create(ErosionFixture.Kind.ARCHIPELAGO_WINDOW);
        EndErosionTile archipelagoTile = new ErosionTileFixtureBuilder(archipelago)
                .build(key(0, 0, OWNER)).tile();
        boolean sawArchipelago = false;
        for (int index = 0; index < key(0, 0, OWNER).cellCount(); index++) {
            sawArchipelago |= archipelagoTile.archipelagoDominant(index);
        }
        assertTrue(sawArchipelago);
    }

    @Test
    void workerCacheReportsHitsEvictionsOwnerSwapsAndDuplicateBuilds() {
        BuildLedger ledger = new BuildLedger();
        ErosionTileFixtureBuilder builder = new ErosionTileFixtureBuilder(
                ErosionFixture.create(ErosionFixture.Kind.WATERSHED), ledger::record);
        EndErosionTileCache<EndErosionTile> cache = new EndErosionTileCache<>(2);
        EndErosionTileKey firstKey = key(0, 0, OWNER);
        EndErosionTileKey secondKey = key(1, 0, OWNER);
        EndErosionTileKey thirdKey = key(2, 0, OWNER);

        EndErosionTile first = cache.getOrBuild(firstKey, builder);
        assertSame(first, cache.getOrBuild(key(0, 0, OWNER), builder));
        cache.getOrBuild(secondKey, builder);
        cache.getOrBuild(thirdKey, builder);
        EndErosionTile rebuilt = cache.getOrBuild(firstKey, builder);
        assertEquals(first.checksum(), rebuilt.checksum());
        assertTrue(first != rebuilt);

        EndErosionTileKey otherOwner = key(0, 0, OWNER + 1L);
        cache.getOrBuild(otherOwner, builder);
        EndErosionTileCache.Metrics metrics = cache.metrics();
        assertEquals(6L, metrics.requests());
        assertEquals(1L, metrics.hits());
        assertEquals(5L, metrics.misses());
        assertEquals(5L, metrics.builds());
        assertEquals(2L, metrics.evictions());
        assertEquals(1L, metrics.ownerSwaps());
        assertEquals(1, cache.size());
        assertEquals(rebuilt.primitiveBytes(), metrics.currentResidentPrimitiveBytes());
        assertEquals(rebuilt.primitiveBytes() * 2L, metrics.peakResidentPrimitiveBytes());
        assertEquals(rebuilt.primitiveBytes(), metrics.peakBuildPrimitiveBytes());
        assertEquals(5L, ledger.totalBuilds());
        assertEquals(4L, ledger.distinctKeys());
        assertEquals(1L, ledger.duplicateBuilds());
    }

    @Test
    void failedBuildDoesNotPublishAPartialTile() {
        EndErosionTileCache<EndErosionTile> cache = new EndErosionTileCache<>(2);
        EndErosionTileKey key = key(0, 0, OWNER);

        assertThrows(IllegalStateException.class,
                () -> cache.getOrBuild(key, ignored -> {
                    throw new IllegalStateException("synthetic build failure");
                }));

        EndErosionTileCache.Metrics metrics = cache.metrics();
        assertEquals(1L, metrics.requests());
        assertEquals(1L, metrics.misses());
        assertEquals(0L, metrics.builds());
        assertEquals(0, cache.size());
        assertEquals(0L, metrics.currentResidentPrimitiveBytes());
    }

    @Test
    void keyChecksumsDoNotDependOnRequestOrder() {
        ErosionTileFixtureBuilder builder = new ErosionTileFixtureBuilder(
                ErosionFixture.create(ErosionFixture.Kind.PARABOLOID));
        EndErosionTileKey[] keys = keys(OWNER);
        long forward = cacheChecksum(builder, keys);
        EndErosionTileKey[] reversed = Arrays.copyOf(keys, keys.length);
        reverse(reversed);
        long backward = cacheChecksum(builder, reversed);

        assertEquals(forward, backward);
        assertNotEquals(0L, forward);
    }

    @Test
    void oneTwoFourAndSixWorkersProduceIdenticalChecksumsAndMeasuredDuplicates()
            throws ExecutionException, InterruptedException {
        for (int workers : new int[]{1, 2, 4, 6}) {
            verifyWorkers(workers);
        }
    }

    private static void verifyWorkers(int workers)
            throws ExecutionException, InterruptedException {
        BuildLedger ledger = new BuildLedger();
        ErosionTileFixtureBuilder builder = new ErosionTileFixtureBuilder(
                ErosionFixture.create(ErosionFixture.Kind.CLOSED_BASIN), ledger::record);
        EndErosionTileKey[] keys = keys(OWNER);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<WorkerResult>> futures = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                int rotation = worker;
                futures.add(executor.submit(() -> workerRun(builder, keys, rotation)));
            }

            Long expectedChecksum = null;
            for (Future<WorkerResult> future : futures) {
                WorkerResult result = future.get();
                if (expectedChecksum == null) {
                    expectedChecksum = result.checksum();
                } else {
                    assertEquals(expectedChecksum.longValue(), result.checksum());
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

    private static WorkerResult workerRun(ErosionTileFixtureBuilder builder,
                                          EndErosionTileKey[] keys,
                                          int rotation) {
        EndErosionTileCache<EndErosionTile> cache = new EndErosionTileCache<>(TILE_COUNT);
        long checksum = 0L;
        for (int index = 0; index < keys.length; index++) {
            checksum += cache.getOrBuild(keys[(index + rotation) % keys.length], builder).checksum();
        }
        for (int index = keys.length - 1; index >= 0; index--) {
            checksum += cache.getOrBuild(keys[(index + rotation) % keys.length], builder).checksum();
        }
        return new WorkerResult(checksum, cache.metrics());
    }

    private static long cacheChecksum(ErosionTileFixtureBuilder builder,
                                      EndErosionTileKey[] keys) {
        EndErosionTileCache<EndErosionTile> cache = new EndErosionTileCache<>(keys.length);
        long checksum = 0L;
        for (EndErosionTileKey key : keys) {
            checksum += cache.getOrBuild(key, builder).checksum();
        }
        return checksum;
    }

    private static EndErosionTileKey[] keys(long owner) {
        EndErosionTileKey[] keys = new EndErosionTileKey[TILE_COUNT];
        for (int index = 0; index < keys.length; index++) {
            keys[index] = key(index - 2, 3 - index, owner);
        }
        return keys;
    }

    private static EndErosionTileKey key(int tileX, int tileZ, long owner) {
        return new EndErosionTileKey(
                ALGORITHM_ID, 1, WORLD_SEED, owner, -256,
                (int) ErosionFixture.WORLD_HEIGHT_BLOCKS, TERRAIN_VERSION,
                tileX, tileZ, ErosionFixture.SIZE, ErosionFixture.SIZE,
                ErosionFixture.HALO, (int) ErosionFixture.SAMPLE_DISTANCE_BLOCKS);
    }

    private static EndErosionTileKey geometryKey(int sampleWidth, int sampleHeight) {
        return new EndErosionTileKey(
                ALGORITHM_ID, 1, WORLD_SEED, OWNER, -256,
                (int) ErosionFixture.WORLD_HEIGHT_BLOCKS, TERRAIN_VERSION,
                0, 0, sampleWidth, sampleHeight, ErosionFixture.HALO,
                (int) ErosionFixture.SAMPLE_DISTANCE_BLOCKS);
    }

    private static void reverse(EndErosionTileKey[] keys) {
        for (int left = 0, right = keys.length - 1; left < right; left++, right--) {
            EndErosionTileKey key = keys[left];
            keys[left] = keys[right];
            keys[right] = key;
        }
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
