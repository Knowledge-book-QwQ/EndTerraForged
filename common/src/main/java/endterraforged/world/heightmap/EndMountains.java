/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged (LGPL-3.0-or-later). The mountain <em>shape recipes</em> below
 * are ported faithful-to-upstream from ReTerraForged's Populators (MIT):
 * lineage TerraForged (dags) -> ReTerraForged (raccoonman) -> EndTerraForged.
 * The serialisation/packaging surface (TerrainPopulator, Cell fields, cache2d,
 * vertical-scale mul) is intentionally dropped — EndHeightmap owns the
 * composition and scaling, so these factories return a pure [0,1] height field.
 */
package endterraforged.world.heightmap;

import endterraforged.world.noise.DistanceFunction;
import endterraforged.world.noise.EdgeFunction;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.Noises;

/**
 * Mountain height-field recipes for the End, ported from RTF's
 * {@code Populators.makeMountains*}.
 *
 * <p>Each factory returns a {@link Noise} in {@code [0,1]} describing a
 * mountain height-field (independent of continent / sea level). The EndHeightmap
 * multiplies this by the continent landness and scales to world height, so the
 * recipe stays a pure shape — exactly as upstream's {@code height} noise is a
 * pure shape before {@code TerrainPopulator} packages it with a ground level.</p>
 *
 * <p><b>Faithfulness.</b> The leaf choices, scales, octaves, lacunarity/gain,
 * the {@code mul(cell, 1.2) -> clamp} saturation trick, the warp strength and
 * the {@code pow(1.1)} sharpening are all byte-faithful to upstream. The only
 * deviations are cosmetic: RTF draws each leaf's seed from a {@code Seed} cursor
 * ({@code seed.next()}); we pass explicit offsets ({@code seed}, {@code seed+1},
 * ...) which is deterministic and equivalent. RTF's final
 * {@code Noises.mul(height, 0.645F * verticalScale)} vertical scaling is
 * <em>not</em> applied here — that is an EndHeightmap concern, not a shape
 * concern, and the End wants mountains that can reach full world height.</p>
 *
 * <p><b>Deferred.</b> {@code makeMountains3} requires {@code advancedTerrace},
 * which is not yet ported; it will be added when the End wants terraced
 * mountains. {@code makeFancy} (thermal-erosion noise) is also deferred.</p>
 */
public final class EndMountains {

    /** Upstream constant: MOUNTAINS_H, the non-legacy horizontal scale for mountains1. */
    public static final int MOUNTAINS1_SCALE = 610;
    /** Upstream constant: MOUNTAINS3_H, the cell scale for mountains3's worleyEdge. */
    public static final int MOUNTAINS3_SCALE = 600;

    private EndMountains() {
    }

    /**
     * RTF {@code makeMountains} (MOUNTAINS_1): a ridged-multifractal perlin
     * field, subtly modulated by a low-amplitude perlin scaler and domain-warped.
     * Smooth rolling ridges — the gentler of the two recipes.
     *
     * <pre>
     * height = perlinRidge(seed, MOUNTAINS1_SCALE, 4, 2.35, 1.15)
     *        * alpha(perlin(seed+1, 24, 4), 0.075)
     * warpPerlin(height, seed+2, 350, 1, 150)
     * </pre>
     */
    public static Noise mountains1(int seed) {
        Noise height = Noises.perlinRidge(seed, MOUNTAINS1_SCALE, 4, 2.35F, 1.15F);
        Noise scaler = Noises.alpha(Noises.perlin(seed + 1, 24, 4), 0.075F);
        height = Noises.mul(height, scaler);
        height = Noises.warpPerlin(height, seed + 2, 350, 1, 150.0F);
        return height;
    }

    /**
     * RTF {@code makeMountains2} (MOUNTAINS_2): a warped worley-edge ridge field,
     * saturated by {@code mul(1.2)->clamp} so the upper band plateaus, then shaped
     * by a faint perlin blur and a larger-scale ridged surface, finally sharpened.
     * This is the shattered-ridge mountain — the realistic, broken-spine look the
     * End wants.
     *
     * <pre>
     * cell   = clamp(worleyEdge(seed, 360, DISTANCE_2, EUCLIDEAN) * 1.2, 0, 1)
     * cell   = warpPerlin(cell, seed+1, 200, 2, 100)
     * blur   = alpha(perlin(seed+2, 10, 1), 0.025)
     * surface= alpha(perlinRidge(seed+3, 125, 4), 0.37)
     * height = pow(clamp(cell) * blur * surface, 1.1)
     * </pre>
     */
    public static Noise mountains2(int seed) {
        Noise cell = Noises.worleyEdge(seed, 360, EdgeFunction.DISTANCE_2, DistanceFunction.EUCLIDEAN);
        cell = Noises.mul(cell, 1.2F);
        cell = Noises.clamp(cell, 0.0F, 1.0F);
        cell = Noises.warpPerlin(cell, seed + 1, 200, 2, 100.0F);

        Noise blur = Noises.alpha(Noises.perlin(seed + 2, 10, 1), 0.025F);
        Noise surface = Noises.alpha(Noises.perlinRidge(seed + 3, 125, 4), 0.37F);

        Noise height = Noises.clamp(cell, 0.0F, 1.0F);
        height = Noises.mul(height, blur);
        height = Noises.mul(height, surface);
        height = Noises.pow(height, 1.1F);
        return height;
    }
}
