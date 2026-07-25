package endterraforged.world.erosion;

import java.util.Arrays;
import java.util.Objects;

/**
 * Fixed-radius thermal/talus relaxation candidate for P4.7 comparison.
 *
 * <p>The runtime is immutable, stateless and thread-safe. Each synchronous
 * pass reads one height grid and publishes into a separate grid, so scan order
 * cannot feed partially updated heights back into the same pass.</p>
 */
final class EndBoundedThermalRuntime {

    static final int PASSES = 2;
    static final int REQUIRED_HALO = PASSES;

    private static final float MIN_LANDNESS = 0.12F;
    private static final float MIN_INLANDNESS = 0.15F;
    private static final float MIN_THICKNESS_BLOCKS = 8.0F;
    private static final float TALUS_BLOCKS_PER_BLOCK = 1.0F;
    private static final float MAX_TRANSFER_BLOCKS_PER_PASS = 2.0F;
    static final float MAX_EXPORT_BLOCKS = PASSES * MAX_TRANSFER_BLOCKS_PER_PASS;

    void apply(int width,
               int height,
               float worldHeightBlocks,
               float sampleDistanceBlocks,
               float[] sourceTop,
               float[] landness,
               float[] inlandness,
               float[] outerActivation,
               float[] erosionResistance,
               float[] availableThicknessBlocks,
               boolean[] erosionMasked,
               boolean[] archipelagoDominant,
               EndBoundedThermalBuffer output) {
        int cells = validate(width, height, worldHeightBlocks, sampleDistanceBlocks,
                sourceTop, landness, inlandness, outerActivation, erosionResistance,
                availableThicknessBlocks, erosionMasked, archipelagoDominant, output);
        output.begin();
        float[] current = output.current();
        float[] budget = output.remainingExportBudget();
        double sourceMaterialBlocks = 0.0D;
        for (int index = 0; index < cells; index++) {
            float top = sourceTop[index];
            float thickness = availableThicknessBlocks[index];
            requireFinite(top, "sourceTop", index);
            requireFinite(landness[index], "landness", index);
            requireFinite(inlandness[index], "inlandness", index);
            requireFinite(outerActivation[index], "outerActivation", index);
            requireFinite(erosionResistance[index], "erosionResistance", index);
            requireFinite(thickness, "availableThicknessBlocks", index);

            float heightBlocks = Math.clamp(top, 0.0F, 1.0F) * worldHeightBlocks;
            current[index] = heightBlocks;
            sourceMaterialBlocks += heightBlocks;
            budget[index] = active(index, landness, inlandness, outerActivation,
                    thickness, erosionMasked, archipelagoDominant)
                    ? Math.min(MAX_EXPORT_BLOCKS,
                            Math.max(0.0F, thickness - MIN_THICKNESS_BLOCKS) * 0.25F)
                    : -1.0F;
        }

        float talusHeightBlocks = TALUS_BLOCKS_PER_BLOCK * sampleDistanceBlocks;
        for (int pass = 0; pass < PASSES; pass++) {
            current = output.current();
            float[] next = output.next();
            float[] delta = output.transferDelta();
            Arrays.fill(delta, 0, cells, 0.0F);
            for (int z = 1; z < height - 1; z++) {
                int row = z * width;
                for (int x = 1; x < width - 1; x++) {
                    int source = row + x;
                    float sourceBudget = budget[source];
                    if (sourceBudget <= 0.0F) {
                        continue;
                    }

                    int northIndex = source - width;
                    int westIndex = source - 1;
                    int eastIndex = source + 1;
                    int southIndex = source + width;
                    float sourceHeight = current[source];
                    float north = excess(sourceHeight, current[northIndex], talusHeightBlocks,
                            budget[northIndex]);
                    float west = excess(sourceHeight, current[westIndex], talusHeightBlocks,
                            budget[westIndex]);
                    float east = excess(sourceHeight, current[eastIndex], talusHeightBlocks,
                            budget[eastIndex]);
                    float south = excess(sourceHeight, current[southIndex], talusHeightBlocks,
                            budget[southIndex]);
                    float excessSum = north + west + east + south;
                    if (excessSum <= 0.0F) {
                        continue;
                    }

                    float maximumExcess = Math.max(Math.max(north, south), Math.max(west, east));
                    float activation = Math.clamp(outerActivation[source], 0.0F, 1.0F)
                            * Math.clamp(landness[source], 0.0F, 1.0F)
                            * Math.clamp(inlandness[source], 0.0F, 1.0F)
                            * (1.0F - Math.clamp(erosionResistance[source], 0.0F, 1.0F));
                    float move = Math.min(sourceBudget,
                            Math.min(MAX_TRANSFER_BLOCKS_PER_PASS, maximumExcess * 0.5F) * activation);
                    if (move <= 0.0F) {
                        continue;
                    }

                    int lastTarget = south > 0.0F ? southIndex
                            : east > 0.0F ? eastIndex
                            : west > 0.0F ? westIndex : northIndex;
                    float assigned = 0.0F;
                    assigned += distribute(delta, northIndex, lastTarget, north, excessSum, move);
                    assigned += distribute(delta, westIndex, lastTarget, west, excessSum, move);
                    assigned += distribute(delta, eastIndex, lastTarget, east, excessSum, move);
                    assigned += distribute(delta, southIndex, lastTarget, south, excessSum, move);
                    delta[lastTarget] += move - assigned;
                    delta[source] -= move;
                    budget[source] = sourceBudget - move;
                    output.recordTransfer(move);
                }
            }

            for (int index = 0; index < cells; index++) {
                next[index] = current[index] + delta[index];
            }
            output.swap();
        }

        double finalMaterialBlocks = 0.0D;
        current = output.current();
        for (int index = 0; index < cells; index++) {
            finalMaterialBlocks += current[index];
        }
        output.finish(sourceTop, worldHeightBlocks, cells,
                sourceMaterialBlocks, finalMaterialBlocks);
    }

    private static float distribute(float[] delta, int target, int lastTarget,
                                    float excess, float excessSum, float move) {
        if (excess <= 0.0F || target == lastTarget) {
            return 0.0F;
        }
        float amount = move * excess / excessSum;
        delta[target] += amount;
        return amount;
    }

    private static float excess(float sourceHeight, float targetHeight,
                                float talusHeightBlocks, float targetBudget) {
        if (targetBudget < 0.0F) {
            return 0.0F;
        }
        return Math.max(0.0F, sourceHeight - targetHeight - talusHeightBlocks);
    }

    private static boolean active(int index,
                                  float[] landness,
                                  float[] inlandness,
                                  float[] outerActivation,
                                  float thickness,
                                  boolean[] erosionMasked,
                                  boolean[] archipelagoDominant) {
        return !erosionMasked[index]
                && !archipelagoDominant[index]
                && outerActivation[index] > 0.0F
                && landness[index] > MIN_LANDNESS
                && inlandness[index] > MIN_INLANDNESS
                && thickness > MIN_THICKNESS_BLOCKS;
    }

    private static int validate(int width,
                                int height,
                                float worldHeightBlocks,
                                float sampleDistanceBlocks,
                                float[] sourceTop,
                                float[] landness,
                                float[] inlandness,
                                float[] outerActivation,
                                float[] erosionResistance,
                                float[] availableThicknessBlocks,
                                boolean[] erosionMasked,
                                boolean[] archipelagoDominant,
                                EndBoundedThermalBuffer output) {
        if (width < REQUIRED_HALO * 2 + 1 || height < REQUIRED_HALO * 2 + 1) {
            throw new IllegalArgumentException("thermal grid requires a " + REQUIRED_HALO
                    + "-cell halo, got " + width + "x" + height);
        }
        if (!Float.isFinite(worldHeightBlocks) || worldHeightBlocks <= 0.0F) {
            throw new IllegalArgumentException("worldHeightBlocks must be finite and > 0");
        }
        if (!Float.isFinite(sampleDistanceBlocks) || sampleDistanceBlocks <= 0.0F) {
            throw new IllegalArgumentException("sampleDistanceBlocks must be finite and > 0");
        }
        int cells = Math.multiplyExact(width, height);
        requireLength(sourceTop, cells, "sourceTop");
        requireLength(landness, cells, "landness");
        requireLength(inlandness, cells, "inlandness");
        requireLength(outerActivation, cells, "outerActivation");
        requireLength(erosionResistance, cells, "erosionResistance");
        requireLength(availableThicknessBlocks, cells, "availableThicknessBlocks");
        requireLength(erosionMasked, cells, "erosionMasked");
        requireLength(archipelagoDominant, cells, "archipelagoDominant");
        Objects.requireNonNull(output, "output");
        if (output.capacity() < cells) {
            throw new IllegalArgumentException("output capacity " + output.capacity()
                    + " is smaller than " + cells);
        }
        return cells;
    }

    private static void requireLength(float[] values, int cells, String name) {
        Objects.requireNonNull(values, name);
        if (values.length < cells) {
            throw new IllegalArgumentException(name + " length " + values.length
                    + " is smaller than " + cells);
        }
    }

    private static void requireLength(boolean[] values, int cells, String name) {
        Objects.requireNonNull(values, name);
        if (values.length < cells) {
            throw new IllegalArgumentException(name + " length " + values.length
                    + " is smaller than " + cells);
        }
    }

    private static void requireFinite(float value, String name, int index) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + '[' + index + "] must be finite");
        }
    }
}
