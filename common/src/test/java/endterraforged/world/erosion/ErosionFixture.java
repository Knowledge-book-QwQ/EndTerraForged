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
    private final float[] availableThicknessBlocks;
    private final boolean[] archipelagoDominant;

    private ErosionFixture(Kind kind, float[] rawTop, float[] landness,
                           float[] inlandness, float[] availableThicknessBlocks,
                           boolean[] archipelagoDominant) {
        this.kind = kind;
        this.rawTop = rawTop;
        this.landness = landness;
        this.inlandness = inlandness;
        this.availableThicknessBlocks = availableThicknessBlocks;
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
        float[] availableThicknessBlocks = new float[cells];
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
                availableThicknessBlocks[index] = 4.0F + 156.0F * landness[index];
                archipelagoDominant[index] = kind == Kind.ARCHIPELAGO_WINDOW
                        && islandSignal(u, v) > 0.42F;
            }
        }
        return new ErosionFixture(kind, rawTop, landness, inlandness,
                availableThicknessBlocks, archipelagoDominant);
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

    float availableThicknessBlocks(int x, int z) {
        return availableThicknessBlocks[cellIndex(x, z)];
    }

    boolean archipelagoDominant(int x, int z) {
        return archipelagoDominant[cellIndex(x, z)];
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
                cell = (cell ^ Float.floatToIntBits(availableThicknessBlocks[index]))
                        * 0x100000001B3L;
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
