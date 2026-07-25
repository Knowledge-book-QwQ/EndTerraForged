package endterraforged.world.erosion;

import java.util.Objects;

/**
 * Immutable primitive input artifact shared by P4.7 tile candidates.
 *
 * <p>Construction transfers ownership of every array to this artifact. A
 * builder must not retain or mutate those arrays after publication.</p>
 */
final class EndErosionTile {

    static final byte MASK_EROSION_PROTECTED = 1;
    static final byte MASK_ARCHIPELAGO_DOMINANT = 1 << 1;

    private static final int FLOAT_CHANNELS = 8;
    private static final int INT_CHANNELS = 2;
    private static final int BYTE_CHANNELS = 1;

    private final EndErosionTileKey key;
    private final float[] sourceTopBlocks;
    private final float[] landness;
    private final float[] inlandness;
    private final float[] outerActivation;
    private final float[] roughness;
    private final float[] erosionResistance;
    private final float[] availableThicknessBlocks;
    private final float[] ridgeInfluence;
    private final int[] areaFamily;
    private final int[] terrainTags;
    private final byte[] masks;

    private EndErosionTile(EndErosionTileKey key,
                           float[] sourceTopBlocks,
                           float[] landness,
                           float[] inlandness,
                           float[] outerActivation,
                           float[] roughness,
                           float[] erosionResistance,
                           float[] availableThicknessBlocks,
                           float[] ridgeInfluence,
                           int[] areaFamily,
                           int[] terrainTags,
                           byte[] masks) {
        this.key = Objects.requireNonNull(key, "key");
        int cells = key.cellCount();
        this.sourceTopBlocks = requireLength(sourceTopBlocks, cells, "sourceTopBlocks");
        this.landness = requireLength(landness, cells, "landness");
        this.inlandness = requireLength(inlandness, cells, "inlandness");
        this.outerActivation = requireLength(outerActivation, cells, "outerActivation");
        this.roughness = requireLength(roughness, cells, "roughness");
        this.erosionResistance = requireLength(
                erosionResistance, cells, "erosionResistance");
        this.availableThicknessBlocks = requireLength(
                availableThicknessBlocks, cells, "availableThicknessBlocks");
        this.ridgeInfluence = requireLength(ridgeInfluence, cells, "ridgeInfluence");
        this.areaFamily = requireLength(areaFamily, cells, "areaFamily");
        this.terrainTags = requireLength(terrainTags, cells, "terrainTags");
        this.masks = requireLength(masks, cells, "masks");
        validateFinite(this.sourceTopBlocks, "sourceTopBlocks");
        validateFinite(this.landness, "landness");
        validateFinite(this.inlandness, "inlandness");
        validateFinite(this.outerActivation, "outerActivation");
        validateFinite(this.roughness, "roughness");
        validateFinite(this.erosionResistance, "erosionResistance");
        validateFinite(this.availableThicknessBlocks, "availableThicknessBlocks");
        validateFinite(this.ridgeInfluence, "ridgeInfluence");
    }

    static EndErosionTile fromOwnedArrays(EndErosionTileKey key,
                                           float[] sourceTopBlocks,
                                           float[] landness,
                                           float[] inlandness,
                                           float[] outerActivation,
                                           float[] roughness,
                                           float[] erosionResistance,
                                           float[] availableThicknessBlocks,
                                           float[] ridgeInfluence,
                                           int[] areaFamily,
                                           int[] terrainTags,
                                           byte[] masks) {
        return new EndErosionTile(key, sourceTopBlocks, landness, inlandness,
                outerActivation, roughness, erosionResistance,
                availableThicknessBlocks, ridgeInfluence, areaFamily, terrainTags, masks);
    }

    EndErosionTileKey key() {
        return this.key;
    }

    int index(int x, int z) {
        if (x < 0 || x >= this.key.sampleWidth() || z < 0 || z >= this.key.sampleHeight()) {
            throw new IndexOutOfBoundsException("sample " + x + ',' + z + " outside tile");
        }
        return z * this.key.sampleWidth() + x;
    }

    float sourceTopBlocks(int index) {
        return this.sourceTopBlocks[index];
    }

    float landness(int index) {
        return this.landness[index];
    }

    float inlandness(int index) {
        return this.inlandness[index];
    }

    float outerActivation(int index) {
        return this.outerActivation[index];
    }

    float roughness(int index) {
        return this.roughness[index];
    }

    float erosionResistance(int index) {
        return this.erosionResistance[index];
    }

    float availableThicknessBlocks(int index) {
        return this.availableThicknessBlocks[index];
    }

    float ridgeInfluence(int index) {
        return this.ridgeInfluence[index];
    }

    int areaFamily(int index) {
        return this.areaFamily[index];
    }

    int terrainTags(int index) {
        return this.terrainTags[index];
    }

    byte masks(int index) {
        return this.masks[index];
    }

    boolean erosionProtected(int index) {
        return (this.masks[index] & MASK_EROSION_PROTECTED) != 0;
    }

    boolean archipelagoDominant(int index) {
        return (this.masks[index] & MASK_ARCHIPELAGO_DOMINANT) != 0;
    }

    long primitiveBytes() {
        return (long) this.key.cellCount()
                * (FLOAT_CHANNELS * Float.BYTES
                + INT_CHANNELS * Integer.BYTES
                + BYTE_CHANNELS * Byte.BYTES);
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
            checksum = mix(checksum, Float.floatToIntBits(this.sourceTopBlocks[index]));
            checksum = mix(checksum, Float.floatToIntBits(this.landness[index]));
            checksum = mix(checksum, Float.floatToIntBits(this.inlandness[index]));
            checksum = mix(checksum, Float.floatToIntBits(this.outerActivation[index]));
            checksum = mix(checksum, Float.floatToIntBits(this.roughness[index]));
            checksum = mix(checksum, Float.floatToIntBits(this.erosionResistance[index]));
            checksum = mix(checksum, Float.floatToIntBits(this.availableThicknessBlocks[index]));
            checksum = mix(checksum, Float.floatToIntBits(this.ridgeInfluence[index]));
            checksum = mix(checksum, this.areaFamily[index]);
            checksum = mix(checksum, this.terrainTags[index]);
            checksum = mix(checksum, this.masks[index]);
        }
        return checksum;
    }

    private static float[] requireLength(float[] values, int length, String name) {
        Objects.requireNonNull(values, name);
        if (values.length != length) {
            throw new IllegalArgumentException(name + " length " + values.length
                    + " does not equal " + length);
        }
        return values;
    }

    private static int[] requireLength(int[] values, int length, String name) {
        Objects.requireNonNull(values, name);
        if (values.length != length) {
            throw new IllegalArgumentException(name + " length " + values.length
                    + " does not equal " + length);
        }
        return values;
    }

    private static byte[] requireLength(byte[] values, int length, String name) {
        Objects.requireNonNull(values, name);
        if (values.length != length) {
            throw new IllegalArgumentException(name + " length " + values.length
                    + " does not equal " + length);
        }
        return values;
    }

    private static void validateFinite(float[] values, String name) {
        for (int index = 0; index < values.length; index++) {
            if (!Float.isFinite(values[index])) {
                throw new IllegalArgumentException(name + '[' + index + "] must be finite");
            }
        }
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 0x100000001B3L;
    }
}
