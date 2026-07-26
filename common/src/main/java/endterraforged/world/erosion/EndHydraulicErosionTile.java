/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Hydraulic semantics are adapted from ReTerraForged R9.3.6/R9.6 (MIT)
 * into EndTerraForged (LGPL-3.0-or-later). See NOTICE.md for the exact
 * source boundary and ETF-specific deterministic tile changes.
 */
package endterraforged.world.erosion;

import java.util.Objects;

/** Immutable output artifact for one test-only hydraulic erosion tile. */
final class EndHydraulicErosionTile implements EndErosionTileArtifact {

    private static final int FLOAT_CHANNELS = 5;

    record Stats(int droplets,
                 int steps,
                 int brushWrites,
                 int coreSteps,
                 int haloSteps,
                 int stationaryStops,
                 int boundaryStops,
                 int lifetimeStops,
                 float erodedBlocks,
                 float depositedBlocks,
                 float exportedSedimentBlocks) {
    }

    private final EndErosionTileKey key;
    private final float[] finalTopBlocks;
    private final float[] deltaBlocks;
    private final float[] erosionStrength;
    private final float[] drainagePotential;
    private final float[] activation;
    private final Stats stats;

    private EndHydraulicErosionTile(EndErosionTileKey key,
                                    float[] finalTopBlocks,
                                    float[] deltaBlocks,
                                    float[] erosionStrength,
                                    float[] drainagePotential,
                                    float[] activation,
                                    Stats stats) {
        this.key = Objects.requireNonNull(key, "key");
        int cells = key.cellCount();
        this.finalTopBlocks = requireFinite(finalTopBlocks, cells, "finalTopBlocks");
        this.deltaBlocks = requireFinite(deltaBlocks, cells, "deltaBlocks");
        this.erosionStrength = requireFinite(erosionStrength, cells, "erosionStrength");
        this.drainagePotential = requireFinite(
                drainagePotential, cells, "drainagePotential");
        this.activation = requireFinite(activation, cells, "activation");
        this.stats = Objects.requireNonNull(stats, "stats");
    }

    static EndHydraulicErosionTile fromOwnedArrays(EndErosionTileKey key,
                                                    float[] finalTopBlocks,
                                                    float[] deltaBlocks,
                                                    float[] erosionStrength,
                                                    float[] drainagePotential,
                                                    float[] activation,
                                                    Stats stats) {
        return new EndHydraulicErosionTile(key, finalTopBlocks, deltaBlocks,
                erosionStrength, drainagePotential, activation, stats);
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

    Stats stats() {
        return this.stats;
    }

    @Override
    public long primitiveBytes() {
        return (long) this.key.cellCount() * FLOAT_CHANNELS * Float.BYTES;
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

    private static long mix(long hash, long value) {
        return (hash ^ value) * 0x100000001B3L;
    }
}
