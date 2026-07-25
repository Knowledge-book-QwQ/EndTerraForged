package endterraforged.world.erosion;

import java.util.Arrays;
import java.util.Objects;

/**
 * Worker-owned bounded cache used only by the P4.7 candidate harness.
 *
 * <p>The cache is mutable and not thread-safe. It deliberately has no shared
 * single-flight or executor; duplicate builds across workers remain visible to
 * the benchmark.</p>
 */
final class EndErosionTileCache {

    @FunctionalInterface
    interface Builder {
        BuildResult build(EndErosionTileKey key);
    }

    record BuildResult(EndErosionTile tile, long peakPrimitiveBytes) {
        BuildResult {
            Objects.requireNonNull(tile, "tile");
            if (peakPrimitiveBytes < tile.primitiveBytes()) {
                throw new IllegalArgumentException(
                        "peakPrimitiveBytes must include the published tile");
            }
        }
    }

    record Metrics(long requests,
                   long hits,
                   long misses,
                   long builds,
                   long evictions,
                   long ownerSwaps,
                   long currentResidentPrimitiveBytes,
                   long peakResidentPrimitiveBytes,
                   long peakBuildPrimitiveBytes) {
    }

    private final EndErosionTileKey[] keys;
    private final EndErosionTile[] tiles;
    private final long[] lastAccess;

    private boolean ownerSet;
    private long ownerFingerprint;
    private int size;
    private long accessClock;
    private long requests;
    private long hits;
    private long misses;
    private long builds;
    private long evictions;
    private long ownerSwaps;
    private long currentResidentPrimitiveBytes;
    private long peakResidentPrimitiveBytes;
    private long peakBuildPrimitiveBytes;

    EndErosionTileCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0, got " + capacity);
        }
        this.keys = new EndErosionTileKey[capacity];
        this.tiles = new EndErosionTile[capacity];
        this.lastAccess = new long[capacity];
    }

    EndErosionTile getOrBuild(EndErosionTileKey key, Builder builder) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(builder, "builder");
        ensureOwner(key.runtimeFingerprint());
        this.requests++;
        long access = ++this.accessClock;
        for (int slot = 0; slot < this.keys.length; slot++) {
            if (key.equals(this.keys[slot])) {
                this.hits++;
                this.lastAccess[slot] = access;
                return this.tiles[slot];
            }
        }

        this.misses++;
        BuildResult result = Objects.requireNonNull(builder.build(key), "build result");
        EndErosionTile tile = result.tile();
        if (!key.equals(tile.key())) {
            throw new IllegalArgumentException("builder returned a tile for a different key");
        }
        this.builds++;
        this.peakBuildPrimitiveBytes = Math.max(
                this.peakBuildPrimitiveBytes, result.peakPrimitiveBytes());

        int slot = selectSlot();
        EndErosionTile previous = this.tiles[slot];
        if (previous == null) {
            this.size++;
        } else {
            this.evictions++;
            this.currentResidentPrimitiveBytes -= previous.primitiveBytes();
        }
        this.keys[slot] = key;
        this.tiles[slot] = tile;
        this.lastAccess[slot] = access;
        this.currentResidentPrimitiveBytes += tile.primitiveBytes();
        this.peakResidentPrimitiveBytes = Math.max(
                this.peakResidentPrimitiveBytes, this.currentResidentPrimitiveBytes);
        return tile;
    }

    int capacity() {
        return this.keys.length;
    }

    int size() {
        return this.size;
    }

    Metrics metrics() {
        return new Metrics(this.requests, this.hits, this.misses, this.builds,
                this.evictions, this.ownerSwaps, this.currentResidentPrimitiveBytes,
                this.peakResidentPrimitiveBytes, this.peakBuildPrimitiveBytes);
    }

    private void ensureOwner(long runtimeFingerprint) {
        if (!this.ownerSet) {
            this.ownerSet = true;
            this.ownerFingerprint = runtimeFingerprint;
            return;
        }
        if (this.ownerFingerprint == runtimeFingerprint) {
            return;
        }
        this.ownerFingerprint = runtimeFingerprint;
        this.ownerSwaps++;
        Arrays.fill(this.keys, null);
        Arrays.fill(this.tiles, null);
        Arrays.fill(this.lastAccess, 0L);
        this.size = 0;
        this.currentResidentPrimitiveBytes = 0L;
    }

    private int selectSlot() {
        int leastRecentSlot = 0;
        long leastRecentAccess = Long.MAX_VALUE;
        for (int slot = 0; slot < this.tiles.length; slot++) {
            if (this.tiles[slot] == null) {
                return slot;
            }
            if (this.lastAccess[slot] < leastRecentAccess) {
                leastRecentAccess = this.lastAccess[slot];
                leastRecentSlot = slot;
            }
        }
        return leastRecentSlot;
    }
}
