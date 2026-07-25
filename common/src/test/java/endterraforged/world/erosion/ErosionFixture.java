package endterraforged.world.erosion;

import java.util.List;

/**
 * Canonical primitive input artifact for P4.7 erosion candidate comparisons.
 *
 * <p>The fixture is deliberately test-only. It contains no Minecraft or
 * runtime references, so every candidate sees the same world-space height
 * field, gate channels, halo and derivative stencil.</p>
 */
final class ErosionFixture {

    static final int SIZE = 33;
    static final int HALO = 2;
    static final float WORLD_HEIGHT_BLOCKS = 512.0F;
    static final float SAMPLE_DISTANCE_BLOCKS = 4.0F;

    enum Kind {
        FLAT,
        PLANE,
        PARABOLOID,
        ISOLATED_SPIKE,
        RIDGE,
        PLATEAU_EDGE,
        CLOSED_BASIN,
        WATERSHED,
        COAST_THIN_SHELF,
        ARCHIPELAGO_WINDOW
    }

    private final Kind kind;
    private final float[] rawTop;
    private final float[] landness;
    private final float[] inlandness;
    private final float[] outerActivation;
    private final float[] roughness;
    private final float[] erosionResistance;
    private final float[] availableThicknessBlocks;
    private final float[] ridgeInfluence;
    private final int[] areaFamily;
    private final int[] terrainTags;
    private final boolean[] erosionMasked;
    private final boolean[] archipelagoDominant;

    private ErosionFixture(Kind kind, float[] rawTop, float[] landness,
                           float[] inlandness, float[] outerActivation, float[] roughness,
                           float[] erosionResistance, float[] availableThicknessBlocks,
                           float[] ridgeInfluence, int[] areaFamily, int[] terrainTags,
                           boolean[] erosionMasked, boolean[] archipelagoDominant) {
        this.kind = kind;
        this.rawTop = rawTop;
        this.landness = landness;
        this.inlandness = inlandness;
        this.outerActivation = outerActivation;
        this.roughness = roughness;
        this.erosionResistance = erosionResistance;
        this.availableThicknessBlocks = availableThicknessBlocks;
        this.ridgeInfluence = ridgeInfluence;
        this.areaFamily = areaFamily;
        this.terrainTags = terrainTags;
        this.erosionMasked = erosionMasked;
        this.archipelagoDominant = archipelagoDominant;
    }

    static List<ErosionFixture> standardSet() {
        return List.of(
                create(Kind.FLAT),
                create(Kind.PLANE),
                create(Kind.PARABOLOID),
                create(Kind.ISOLATED_SPIKE),
                create(Kind.RIDGE),
                create(Kind.PLATEAU_EDGE),
                create(Kind.CLOSED_BASIN),
                create(Kind.WATERSHED),
                create(Kind.COAST_THIN_SHELF),
                create(Kind.ARCHIPELAGO_WINDOW));
    }

    static ErosionFixture create(Kind kind) {
        int cells = SIZE * SIZE;
        float[] rawTop = new float[cells];
        float[] landness = new float[cells];
        float[] inlandness = new float[cells];
        float[] outerActivation = new float[cells];
        float[] roughness = new float[cells];
        float[] erosionResistance = new float[cells];
        float[] availableThicknessBlocks = new float[cells];
        float[] ridgeInfluence = new float[cells];
        int[] areaFamily = new int[cells];
        int[] terrainTags = new int[cells];
        boolean[] erosionMasked = new boolean[cells];
        boolean[] archipelagoDominant = new boolean[cells];
        int centre = SIZE / 2;
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                float u = (x - centre) / (float) (SIZE - 1);
                float v = (z - centre) / (float) (SIZE - 1);
                float radius = (float) Math.sqrt(u * u + v * v);
                float top = height(kind, u, v, radius);
                float land = landness(kind, u, v, radius, top);
                int index = cellIndex(x, z);
                rawTop[index] = Math.clamp(top, 0.05F, 0.95F);
                landness[index] = Math.clamp(land, 0.0F, 1.0F);
                inlandness[index] = Math.clamp(1.0F - radius * 1.8F, 0.0F, 1.0F);
                outerActivation[index] = 1.0F;
                roughness[index] = roughness(kind);
                ridgeInfluence[index] = ridgeInfluence(kind, v);
                erosionResistance[index] = erosionResistance(
                        kind, radius, ridgeInfluence[index]);
                availableThicknessBlocks[index] = 4.0F + 156.0F * landness[index];
                areaFamily[index] = kind.ordinal() + 1;
                terrainTags[index] = terrainTags(kind);
                erosionMasked[index] = kind == Kind.COAST_THIN_SHELF;
                archipelagoDominant[index] = kind == Kind.ARCHIPELAGO_WINDOW
                        && islandSignal(u, v) > 0.42F;
            }
        }
        return new ErosionFixture(kind, rawTop, landness, inlandness, outerActivation,
                roughness, erosionResistance, availableThicknessBlocks,
                ridgeInfluence, areaFamily, terrainTags,
                erosionMasked, archipelagoDominant);
    }

    Kind kind() {
        return kind;
    }

    int size() {
        return SIZE;
    }

    int index(int x, int z) {
        return cellIndex(x, z);
    }

    float rawTop(int x, int z) {
        return rawTop[cellIndex(x, z)];
    }

    float landness(int x, int z) {
        return landness[cellIndex(x, z)];
    }

    float inlandness(int x, int z) {
        return inlandness[cellIndex(x, z)];
    }

    float outerActivation(int x, int z) {
        return outerActivation[cellIndex(x, z)];
    }

    float roughness(int x, int z) {
        return roughness[cellIndex(x, z)];
    }

    float erosionResistance(int x, int z) {
        return erosionResistance[cellIndex(x, z)];
    }

    float availableThicknessBlocks(int x, int z) {
        return availableThicknessBlocks[cellIndex(x, z)];
    }

    boolean archipelagoDominant(int x, int z) {
        return archipelagoDominant[cellIndex(x, z)];
    }

    float[] rawTopValues() {
        return rawTop;
    }

    float[] landnessValues() {
        return landness;
    }

    float[] inlandnessValues() {
        return inlandness;
    }

    float[] outerActivationValues() {
        return outerActivation;
    }

    float[] erosionResistanceValues() {
        return erosionResistance;
    }

    float[] availableThicknessValues() {
        return availableThicknessBlocks;
    }

    float[] roughnessValues() {
        return roughness;
    }

    float[] ridgeInfluenceValues() {
        return ridgeInfluence;
    }

    int[] areaFamilyValues() {
        return areaFamily;
    }

    int[] terrainTagsValues() {
        return terrainTags;
    }

    boolean[] erosionMaskedValues() {
        return erosionMasked;
    }

    boolean[] archipelagoDominantValues() {
        return archipelagoDominant;
    }

    float slope(int x, int z) {
        float dx = (rawTop(x + 1, z) - rawTop(x - 1, z))
                * WORLD_HEIGHT_BLOCKS / (2.0F * SAMPLE_DISTANCE_BLOCKS);
        float dz = (rawTop(x, z + 1) - rawTop(x, z - 1))
                * WORLD_HEIGHT_BLOCKS / (2.0F * SAMPLE_DISTANCE_BLOCKS);
        float gradient = (float) Math.sqrt(dx * dx + dz * dz);
        return gradient / (1.0F + gradient);
    }

    float curvature(int x, int z) {
        float laplacian = (rawTop(x + 1, z) + rawTop(x - 1, z)
                + rawTop(x, z + 1) + rawTop(x, z - 1) - 4.0F * rawTop(x, z))
                * WORLD_HEIGHT_BLOCKS / (SAMPLE_DISTANCE_BLOCKS * SAMPLE_DISTANCE_BLOCKS);
        return laplacian / (1.0F + Math.abs(laplacian));
    }

    long checksum(boolean reverse) {
        long checksum = 0L;
        int start = reverse ? SIZE - 1 : 0;
        int end = reverse ? -1 : SIZE;
        int step = reverse ? -1 : 1;
        for (int z = start; z != end; z += step) {
            for (int x = start; x != end; x += step) {
                int index = cellIndex(x, z);
                long cell = 0xCBF29CE484222325L;
                cell = (cell ^ index) * 0x100000001B3L;
                cell = (cell ^ Float.floatToIntBits(rawTop[index])) * 0x100000001B3L;
                cell = (cell ^ Float.floatToIntBits(landness[index])) * 0x100000001B3L;
                cell = (cell ^ Float.floatToIntBits(inlandness[index])) * 0x100000001B3L;
                cell = (cell ^ Float.floatToIntBits(outerActivation[index])) * 0x100000001B3L;
                cell = (cell ^ Float.floatToIntBits(roughness[index])) * 0x100000001B3L;
                cell = (cell ^ Float.floatToIntBits(erosionResistance[index]))
                        * 0x100000001B3L;
                cell = (cell ^ Float.floatToIntBits(availableThicknessBlocks[index]))
                        * 0x100000001B3L;
                cell = (cell ^ Float.floatToIntBits(ridgeInfluence[index]))
                        * 0x100000001B3L;
                cell = (cell ^ areaFamily[index]) * 0x100000001B3L;
                cell = (cell ^ terrainTags[index]) * 0x100000001B3L;
                cell = (cell ^ (erosionMasked[index] ? 1L : 0L)) * 0x100000001B3L;
                cell = (cell ^ (archipelagoDominant[index] ? 1L : 0L)) * 0x100000001B3L;
                checksum += cell;
            }
        }
        return checksum;
    }

    private static int cellIndex(int x, int z) {
        return z * SIZE + x;
    }

    private static float height(Kind kind, float u, float v, float radius) {
        return switch (kind) {
            case FLAT -> 0.50F;
            case PLANE -> 0.50F + 0.20F * u;
            case PARABOLOID -> 0.62F - 0.28F * (u * u + v * v);
            case ISOLATED_SPIKE -> 0.48F + 0.24F * Math.max(0.0F, 1.0F - radius * 3.2F);
            case RIDGE -> 0.48F + 0.22F * (float) Math.exp(-v * v * 42.0F)
                    * (0.70F + 0.30F * (float) Math.cos(u * Math.PI));
            case PLATEAU_EDGE -> 0.48F + 0.20F * (1.0F - smoothStep(0.28F, 0.42F, radius));
            case CLOSED_BASIN -> 0.64F - 0.22F * (float) Math.exp(-radius * radius * 22.0F);
            case WATERSHED -> 0.50F + 0.12F * Math.abs(u) - 0.10F * Math.abs(v);
            case COAST_THIN_SHELF -> 0.46F + 0.14F * smoothStep(-0.30F, 0.12F, u);
            case ARCHIPELAGO_WINDOW -> 0.44F + 0.28F * islandSignal(u, v);
        };
    }

    private static float landness(Kind kind, float u, float v, float radius, float top) {
        if (kind == Kind.COAST_THIN_SHELF) {
            return smoothStep(-0.42F, 0.18F, u) * (0.80F - 0.18F * Math.min(1.0F, radius));
        }
        if (kind == Kind.ARCHIPELAGO_WINDOW) {
            return Math.clamp((top - 0.44F) * 3.4F, 0.0F, 1.0F);
        }
        return Math.clamp(0.72F - radius * 0.42F, 0.0F, 1.0F);
    }

    private static float roughness(Kind kind) {
        return switch (kind) {
            case ISOLATED_SPIKE, RIDGE, WATERSHED -> 0.90F;
            case PARABOLOID, PLATEAU_EDGE, CLOSED_BASIN -> 0.60F;
            default -> 0.35F;
        };
    }

    private static float ridgeInfluence(Kind kind, float v) {
        return kind == Kind.RIDGE
                ? Math.clamp((float) Math.exp(-v * v * 42.0F), 0.0F, 1.0F)
                : 0.0F;
    }

    private static float erosionResistance(Kind kind, float radius, float ridgeInfluence) {
        return switch (kind) {
            case RIDGE -> ridgeInfluence;
            case PLATEAU_EDGE -> 1.0F - smoothStep(0.42F, 0.50F, radius);
            default -> 0.0F;
        };
    }

    private static int terrainTags(Kind kind) {
        return switch (kind) {
            case RIDGE -> 1;
            case PLATEAU_EDGE -> 1 << 1;
            case COAST_THIN_SHELF -> 1 << 2;
            case ARCHIPELAGO_WINDOW -> 1 << 3;
            default -> 0;
        };
    }

    private static float islandSignal(float u, float v) {
        float first = gaussian(u + 0.28F, v + 0.10F, 0.055F);
        float second = gaussian(u - 0.22F, v - 0.18F, 0.045F);
        float third = gaussian(u + 0.04F, v - 0.30F, 0.035F);
        return Math.max(first, Math.max(second, third));
    }

    private static float gaussian(float u, float v, float scale) {
        return (float) Math.exp(-(u * u + v * v) / scale);
    }

    private static float smoothStep(float edge0, float edge1, float value) {
        float t = Math.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
