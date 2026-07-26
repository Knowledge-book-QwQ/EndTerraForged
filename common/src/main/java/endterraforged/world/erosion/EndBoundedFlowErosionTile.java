package endterraforged.world.erosion;

import java.util.Objects;

/** Immutable output artifact for one test-only bounded flow erosion tile. */
final class EndBoundedFlowErosionTile implements EndErosionTileArtifact {

    private static final int FLOAT_CHANNELS = 5;
    private static final int BYTE_CHANNELS = 1;

    record Stats(int priorityQueries,
                 int heapPushes,
                 int heapPops,
                 int routedCells,
                 int splitCells,
                 int flowTransfers,
                 int incisionCells,
                 float terminalExportArea,
                 float truncatedArea,
                 float totalCutBlocks,
                 float maximumSpillDepthBlocks) {
    }

    private final EndErosionTileKey key;
    private final float[] finalTopBlocks;
    private final float[] deltaBlocks;
    private final float[] erosionStrength;
    private final float[] drainagePotential;
    private final float[] activation;
    private final byte[] dominantDirection;
    private final Stats stats;

    private EndBoundedFlowErosionTile(EndErosionTileKey key,
                                      float[] finalTopBlocks,
                                      float[] deltaBlocks,
                                      float[] erosionStrength,
                                      float[] drainagePotential,
                                      float[] activation,
                                      byte[] dominantDirection,
                                      Stats stats) {
        this.key = Objects.requireNonNull(key, "key");
        int cells = key.cellCount();
        this.finalTopBlocks = requireFinite(finalTopBlocks, cells, "finalTopBlocks");
        this.deltaBlocks = requireFinite(deltaBlocks, cells, "deltaBlocks");
        this.erosionStrength = requireFinite(erosionStrength, cells, "erosionStrength");
        this.drainagePotential = requireFinite(
                drainagePotential, cells, "drainagePotential");
        this.activation = requireFinite(activation, cells, "activation");
        this.dominantDirection = requireDirections(dominantDirection, cells);
        this.stats = Objects.requireNonNull(stats, "stats");
    }

    static EndBoundedFlowErosionTile fromOwnedArrays(
            EndErosionTileKey key,
            float[] finalTopBlocks,
            float[] deltaBlocks,
            float[] erosionStrength,
            float[] drainagePotential,
            float[] activation,
            byte[] dominantDirection,
            Stats stats) {
        return new EndBoundedFlowErosionTile(
                key, finalTopBlocks, deltaBlocks, erosionStrength,
                drainagePotential, activation, dominantDirection, stats);
    }

    @Override
    public EndErosionTileKey key() {
        return this.key;
    }

    int index(int x, int z) {
        if (x < 0 || x >= this.key.sampleWidth() || z < 0 || z >= this.key.sampleHeight()) {
            throw new IndexOutOfBoundsException("sample " + x + ',' + z + " outside tile");
        }
        return z * this.key.sampleWidth() + x;
    }

    float finalTopBlocks(int index) {
        return this.finalTopBlocks[index];
    }

    float deltaBlocks(int index) {
        return this.deltaBlocks[index];
    }

    float erosionStrength(int index) {
        return this.erosionStrength[index];
    }

    float drainagePotential(int index) {
        return this.drainagePotential[index];
    }

    float activation(int index) {
        return this.activation[index];
    }

    byte dominantDirection(int index) {
        return this.dominantDirection[index];
    }

    Stats stats() {
        return this.stats;
    }

    @Override
    public long primitiveBytes() {
        return (long) this.key.cellCount()
                * (FLOAT_CHANNELS * Float.BYTES + BYTE_CHANNELS);
    }

    long checksum() {
        long checksum = 0xCBF29CE484222325L;
        checksum = mix(checksum, this.key.algorithmId());
        checksum = mix(checksum, this.key.algorithmVersion());
        checksum = mix(checksum, this.key.worldSeed());
        checksum = mix(checksum, this.key.runtimeFingerprint());
        checksum = mix(checksum, this.key.minY());
        checksum = mix(checksum, this.key.worldHeight());
        checksum = mix(checksum, this.key.terrainVersion());
        checksum = mix(checksum, this.key.tileX());
        checksum = mix(checksum, this.key.tileZ());
        checksum = mix(checksum, this.key.sampleWidth());
        checksum = mix(checksum, this.key.sampleHeight());
        checksum = mix(checksum, this.key.haloSamples());
        checksum = mix(checksum, this.key.sampleDistanceBlocks());
        for (int index = 0; index < this.key.cellCount(); index++) {
            checksum = mix(checksum, Float.floatToIntBits(this.finalTopBlocks[index]));
            checksum = mix(checksum, Float.floatToIntBits(this.deltaBlocks[index]));
            checksum = mix(checksum, Float.floatToIntBits(this.erosionStrength[index]));
            checksum = mix(checksum, Float.floatToIntBits(this.drainagePotential[index]));
            checksum = mix(checksum, Float.floatToIntBits(this.activation[index]));
            checksum = mix(checksum, this.dominantDirection[index]);
        }
        return checksum;
    }

    private static float[] requireFinite(float[] values, int length, String name) {
        Objects.requireNonNull(values, name);
        if (values.length != length) {
            throw new IllegalArgumentException(name + " length " + values.length
                    + " does not equal " + length);
        }
        for (int index = 0; index < values.length; index++) {
            if (!Float.isFinite(values[index])) {
                throw new IllegalArgumentException(name + '[' + index + "] must be finite");
            }
        }
        return values;
    }

    private static byte[] requireDirections(byte[] values, int length) {
        Objects.requireNonNull(values, "dominantDirection");
        if (values.length != length) {
            throw new IllegalArgumentException("dominantDirection length " + values.length
                    + " does not equal " + length);
        }
        for (int index = 0; index < values.length; index++) {
            if (values[index] < 0 || values[index] > 8) {
                throw new IllegalArgumentException(
                        "dominantDirection[" + index + "] must be in [0,8]");
            }
        }
        return values;
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 0x100000001B3L;
    }
}
