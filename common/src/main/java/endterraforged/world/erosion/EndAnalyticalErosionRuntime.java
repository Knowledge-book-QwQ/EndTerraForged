package endterraforged.world.erosion;

import java.util.Objects;

import endterraforged.world.heightmap.EndTerrainProfileBuffer;

/**
 * Stateless local analytical erosion baseline for P4.7 candidate comparison.
 *
 * <p>The runtime is immutable and thread-safe. It consumes one already-sampled
 * raw profile and writes into caller-owned storage; it does not own world state,
 * scratch arrays, tiles, executors or caches. This first slice intentionally
 * produces a bounded local cut and drainage diagnostic, not hydraulic sediment
 * transport or carved drainage geometry.</p>
 */
public final class EndAnalyticalErosionRuntime {

    private static final float MIN_LANDNESS = 0.12F;
    private static final float MIN_THICKNESS_BLOCKS = 8.0F;
    private static final float MAX_CUT_BLOCKS = 12.0F;

    /** Applies the baseline to a sampled terrain profile. */
    public void apply(EndTerrainProfileBuffer profile,
                      float landness,
                      float inlandness,
                      float outerActivation,
                      float availableThicknessBlocks,
                      boolean archipelagoDominant,
                      EndAnalyticalErosionBuffer output) {
        Objects.requireNonNull(profile, "profile");
        apply(profile.rawTop(), profile.worldHeightBlocks(), profile.slope(),
                profile.curvature(), profile.roughness(),
                profile.erosionResistance(), landness, inlandness, outerActivation,
                availableThicknessBlocks, archipelagoDominant, output);
    }

    /**
     * Applies the same baseline to primitive fixture channels.
     *
     * <p>Package-private so the test-only canonical fixture can compare
     * algorithms without fabricating Minecraft profile objects.</p>
     */
    void apply(float rawTop,
               float worldHeightBlocks,
               float slope,
               float curvature,
               float roughness,
               float erosionResistance,
               float landness,
               float inlandness,
               float outerActivation,
               float availableThicknessBlocks,
               boolean archipelagoDominant,
               EndAnalyticalErosionBuffer output) {
        Objects.requireNonNull(output, "output");
        float safeTop = finiteOr(rawTop, 0.0F);
        if (!Float.isFinite(slope) || !Float.isFinite(curvature) || !Float.isFinite(roughness)
                || !Float.isFinite(erosionResistance)
                || !Float.isFinite(landness) || !Float.isFinite(inlandness)
                || !Float.isFinite(outerActivation)
                || !Float.isFinite(availableThicknessBlocks)
                || !Float.isFinite(worldHeightBlocks) || worldHeightBlocks <= 0.0F) {
            output.set(safeTop, 0.0F, 0.0F, 0.0F, 0.0F);
            return;
        }

        float activation = gateActivation(landness, inlandness, outerActivation,
                availableThicknessBlocks, archipelagoDominant);
        float drainagePotential = Math.clamp(curvature * 3.0F + Math.max(0.0F, slope) * 0.20F,
                0.0F, 1.0F);
        if (activation == 0.0F) {
            output.set(safeTop, 0.0F, 0.0F, drainagePotential, 0.0F);
            return;
        }

        float ridgeMask = Math.clamp(-curvature * 3.0F, 0.0F, 1.0F);
        float slopeFactor = Math.clamp(slope, 0.0F, 1.0F);
        float roughnessFactor = 0.35F + 0.65F * Math.clamp(roughness, 0.0F, 1.0F);
        float resistance = Math.clamp(erosionResistance, 0.0F, 1.0F);
        float strength = Math.clamp(activation * slopeFactor * roughnessFactor * (1.0F - resistance)
                * (1.0F - ridgeMask * 0.85F), 0.0F, 1.0F);
        float cutBlocks = Math.min(MAX_CUT_BLOCKS * strength,
                Math.max(0.0F, availableThicknessBlocks) * 0.25F);
        float delta = -Math.max(0.0F, cutBlocks / worldHeightBlocks);
        float top = Math.clamp(safeTop + delta, 0.0F, 1.0F);
        output.set(top, top - safeTop, strength, drainagePotential, activation);
    }

    private static float gateActivation(float landness,
                                        float inlandness,
                                        float outerActivation,
                                        float availableThicknessBlocks,
                                        boolean archipelagoDominant) {
        if (archipelagoDominant || outerActivation <= 0.0F
                || landness <= MIN_LANDNESS
                || availableThicknessBlocks < MIN_THICKNESS_BLOCKS) {
            return 0.0F;
        }
        return Math.clamp(outerActivation, 0.0F, 1.0F)
                * Math.clamp(landness, 0.0F, 1.0F)
                * Math.clamp(inlandness, 0.0F, 1.0F)
                * Math.clamp(availableThicknessBlocks / MAX_CUT_BLOCKS, 0.0F, 1.0F);
    }

    private static float finiteOr(float value, float fallback) {
        return Float.isFinite(value) ? Math.clamp(value, 0.0F, 1.0F) : fallback;
    }
}
