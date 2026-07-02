/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Informed by The Aether's
 * distance-field floating-island concept (LGPL-3.0, ideas only — no code
 *搬运) and by vanilla `minecraft:floating_islands` noise settings, but the
 * shape model is rewritten: a 2D worley cell grid defines island centres,
 * each cell hosts at most one island whose 2D radial falloff is intersected
 * with a vertical Gaussian to give a lens-shaped solid region floating in
 * the void.
 */
package endterraforged.world.floatingislands;

import endterraforged.world.heightmap.EndLevels;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.NoiseMath;
import endterraforged.world.noise.Noises;

/**
 * The End's standalone floating-island layer: a 3D solidity field that
 * produces lens-shaped islands floating in the void, independent of the
 * main terrain ({@link endterraforged.world.heightmap.EndHeightmap}).
 *
 * <p><b>Layout.</b> A worley cell grid (size {@link #cellSize}) defines
 * candidate island centres. Each cell hosts an island with probability
 * {@link #islandChance}; the centre is the cell midpoint plus a small
 * hash-driven jitter. A 3×3 neighbourhood scan per sample finds the
 * nearest island centre, so islands from adjacent cells can overlap at
 * borders (compound silhouettes).</p>
 *
 * <p><b>Shape model.</b> For the nearest island centre, the 2D radial
 * distance {@code r = sqrt(dx²+dz²)} is mapped through a hermite falloff:
 * inside {@link #coreRadius} the island is full-strength, between
 * {@code coreRadius} and {@link #shellRadius} it tapers to zero. The
 * vertical dimension uses a Gaussian centred on {@link #centerY} with
 * standard deviation {@link #verticalScale}: at the island's vertical
 * centre the lens is thickest, tapering to zero above and below. The
 * final solidity is {@code radialFalloff × verticalGaussian} — a 3D lens
 * that is flat near {@code centerY} and pinches out at the top/bottom and
 * at the horizontal rim.</p>
 *
 * <p><b>Output.</b> Returns {@code 1.0} (solid) inside the lens, tapering
 * to {@code 0.0} (void) outside. The stage-3.6 {@code DensityFunction}
 * wrapper ORs this with the main terrain density so islands appear where
 * the main terrain is void — a true layer, not a replacement.</p>
 *
 * <p><b>Decoupling from main terrain.</b> This field does NOT read
 * {@link endterraforged.world.heightmap.EndHeightmap} — the floating-island
 * layer is independent by design (ROADMAP §2.3: "在主地形之外额外生成独立
 * 的悬浮小岛"). The {@code floatingIslandsEnabled} flag lives on
 * {@link endterraforged.world.config.DimensionProfile}; when false, the
 * Mixin does not apply this layer at all.</p>
 *
 * <p><b>Thread safety.</b> Immutable record; the worley scan is stateless.
 * Safe to query from parallel chunk-gen threads.</p>
 *
 * @param cellSize      worley cell size in blocks; smaller = denser island grid
 * @param islandChance  fraction of cells that host an island, in {@code [0,1]}
 * @param coreRadius    full-strength horizontal radius in blocks
 * @param shellRadius   total horizontal radius in blocks (falloff tapers to 0)
 * @param centerY       world Y of the island's vertical centre
 * @param verticalScale Gaussian std-dev in blocks; smaller = thinner lens
 */
public record FloatingIslandsField(float cellSize, float islandChance,
                                   float coreRadius, float shellRadius,
                                   float centerY, float verticalScale) {

    /** End-tuned defaults: sparse, modest islands floating around Y=120. */
    public static FloatingIslandsField defaults() {
        return new FloatingIslandsField(500.0F, 0.35F, 18.0F, 55.0F, 120.0F, 22.0F);
    }

    /**
     * Solidity at world block {@code (x, worldY, z)} under {@code seed}.
     *
     * @param x       world X
     * @param worldY  world Y (block-aligned)
     * @param z       world Z
     * @param seed    world seed (must be consistent across calls for stable layout)
     * @return {@code 1.0} inside the lens, tapering to {@code 0.0} outside;
     *         {@code 0.0} if no island is near or the cell hosts none
     */
    public float solidity(float x, float worldY, float z, int seed) {
        // Degenerate config guard — mirrors EndRiverMap/EndLakeMap.
        if (cellSize <= 0.0F) {
            return 0.0F;
        }

        float[] centre = nearestIslandCentre(x, z, seed);
        if (centre == null) {
            return 0.0F;
        }

        float dx = x - centre[0];
        float dz = z - centre[1];
        float r = (float) Math.sqrt(dx * dx + dz * dz);
        float radial = radialFalloff(r);
        if (radial <= 0.0F) {
            return 0.0F;
        }

        float dy = worldY - centerY;
        // Gaussian: exp(-dy²/(2σ²)). At dy=0 → 1.0; at dy=±2σ → ~0.135.
        float vertical = (float) Math.exp(-(dy * dy) / (2.0F * verticalScale * verticalScale));

        return NoiseMath.clamp(radial * vertical, 0.0F, 1.0F);
    }

    /**
     * Horizontal radial falloff: 1 inside {@link #coreRadius}, hermite
     * taper to 0 at {@link #shellRadius}, 0 beyond.
     */
    float radialFalloff(float r) {
        if (r >= shellRadius) {
            return 0.0F;
        }
        if (r <= coreRadius) {
            return 1.0F;
        }
        float t = (r - coreRadius) / (shellRadius - coreRadius);
        return 1.0F - NoiseMath.interpHermite(t);
    }

    /**
     * Scans the 3×3 worley neighbourhood of {@code (x, z)} for the nearest
     * island centre. Returns {@code null} if no cell in the neighbourhood
     * hosts an island.
     */
    private float[] nearestIslandCentre(float x, float z, int seed) {
        float invCell = 1.0F / cellSize;
        int cellX = NoiseMath.floor(x * invCell);
        int cellZ = NoiseMath.floor(z * invCell);

        float nearestCx = 0.0F;
        float nearestCz = 0.0F;
        float nearestDist = Float.MAX_VALUE;
        boolean found = false;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;
                if (!hasIsland(seed, cx, cz)) {
                    continue;
                }
                float jitterX = NoiseMath.valCoord2D(seed + 0x9E3779B1, cx, cz) * 0.18F * cellSize;
                float jitterZ = NoiseMath.valCoord2D(seed + 0x85EBCA77, cx, cz) * 0.18F * cellSize;
                float centreX = (cx + 0.5F) * cellSize + jitterX;
                float centreZ = (cz + 0.5F) * cellSize + jitterZ;
                float ddx = x - centreX;
                float ddz = z - centreZ;
                float dist = ddx * ddx + ddz * ddz;
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearestCx = centreX;
                    nearestCz = centreZ;
                    found = true;
                }
            }
        }

        if (!found) {
            return null;
        }
        return new float[]{nearestCx, nearestCz};
    }

    /**
     * Whether the worley cell at {@code (cx, cz)} hosts an island, derived
     * from a per-cell hash. {@code islandChance=0} → never; {@code 1} → always.
     */
    private boolean hasIsland(int seed, int cx, int cz) {
        float h = NoiseMath.clamp(
                (NoiseMath.valCoord2D(seed ^ 0x7A3C5F11, cx, cz) + 1.0F) * 0.5F,
                0.0F, 1.0F);
        return h < islandChance;
    }
}
