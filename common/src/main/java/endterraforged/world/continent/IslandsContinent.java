/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). RTF's island generation
 * (MIT) lives inside a CellPopulator coupled to biome/climate selectors; this
 * is a standalone Noise that emits a discrete-island landness field, reusable
 * for any topology that wants scattered floating landmasses.
 */
package endterraforged.world.continent;

import endterraforged.world.noise.DistanceFunction;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.NoiseMath;
import endterraforged.world.noise.domain.Domain;

/**
 * {@link endterraforged.world.config.TopologyMode#ISLANDS} continent: discrete floating islands separated
 * by void, closest in spirit to vanilla End outer islands but more dramatic.
 *
 * <p>One coherent 3x3 worley scan per sample finds the nearest feature point
 * <em>and</em> its owning cell, so the per-island existence gate and size
 * jitter — both derived from the winning cell's hash — stay perfectly in sync
 * with the falloff. Composing this from separate {@code Worley} modules would
 * give each its own scan and therefore a desynchronised gate (an island could
 * vanish mid-falloff); the single scan is what makes the shape coherent.</p>
 *
 * <p>Output landness is {@code [0,1]}: {@code 1} at an island's feature point,
 * falling smoothly to {@code 0} at the island radius, and exactly {@code 0}
 * in cells gated out by {@link #scatter}. The EndHeightmap multiplies the
 * mountain layer by this, so islands naturally taper to a rim.</p>
 *
 * <p><b>Faithfulness vs RTF.</b> The cell scan reuses {@link NoiseMath#cell}
 * and mirrors {@link endterraforged.world.noise.Worley}'s 3x3 neighbourhood,
 * so feature-point placement is byte-identical to upstream worley. The gate,
 * the per-island size jitter and the hermite falloff are EndTerraForged
 * originals — RTF has no equivalent decoupled island shape.</p>
 *
 * @param frequency    island-cell frequency ({@code 1/scale}); higher = smaller, denser cells
 * @param distance     in-cell feature-point spread in {@code (0,1]}; {@code 1.0} roams the whole cell
 * @param islandRadius falloff radius in cell units (squared-distance space is rooted first);
 *                     landness reaches 0 at this distance from the feature point
 * @param scatter      per-cell existence threshold in {@code [0,1]}; a cell hosts an island
 *                     only if its hash {@code >= scatter}. Higher = fewer islands. {@code 0} = every cell
 * @param warp         coordinate warp applied before the scan, to break the square cell grid;
 *                     pass {@link endterraforged.world.noise.domain.Domains#identity()} for no warp
 */
public record IslandsContinent(float frequency, float distance, float islandRadius,
                               float scatter, Domain warp) implements Continent {

    @Override
    public float compute(float x, float z, int seed) {
        // Domain-warp the sample point first so the cell grid is deformed and
        // islands don't sit on a perfect lattice.
        float wx = this.warp.getX(x, z, seed) * this.frequency;
        float wz = this.warp.getZ(x, z, seed) * this.frequency;

        int xi = NoiseMath.floor(wx);
        int yi = NoiseMath.floor(wz);
        int cellX = xi;
        int cellY = yi;
        float nearest = Float.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int cx = xi + dx;
                int cy = yi + dy;
                NoiseMath.Vec2f vec = NoiseMath.cell(seed, cx, cy);
                float deltaX = cx + vec.x() * this.distance - wx;
                float deltaY = cy + vec.y() * this.distance - wz;
                float dist = DistanceFunction.EUCLIDEAN.apply(deltaX, deltaY);
                if (dist < nearest) {
                    nearest = dist;
                    cellX = cx;
                    cellY = cy;
                }
            }
        }

        // Per-cell existence gate: a cell hosts an island only if its hash
        // clears `scatter`. This turns the uniform grid into a scatter.
        float gate = value01(seed, cellX, cellY);
        if (gate < this.scatter) {
            return 0.0F;
        }

        // Per-island radius jitter so neighbouring islands vary in size.
        float jitter = value01(seed + 0x9E3779B1, cellX, cellY);
        float radius = this.islandRadius * (0.5F + jitter);
        if (radius <= 0.0F) {
            return 0.0F;
        }

        // EUCLIDEAN returns squared distance — root it for a true radial falloff.
        float t = NoiseMath.clamp((float) Math.sqrt(nearest) / radius, 0.0F, 1.0F);
        // 1 at the feature point, 0 at the radius, smooth in between.
        return 1.0F - NoiseMath.interpHermite(t);
    }

    @Override
    public float minValue() {
        return 0.0F;
    }

    @Override
    public float maxValue() {
        return 1.0F;
    }

    @Override
    public Noise mapAll(Visitor visitor) {
        return visitor.apply(new IslandsContinent(this.frequency, this.distance, this.islandRadius,
                this.scatter, this.warp.mapAll(visitor)));
    }

    /** Maps {@link NoiseMath#valCoord2D} (native {@code [-1,1]}) to clamped {@code [0,1]}. */
    private static float value01(int seed, int x, int y) {
        return NoiseMath.clamp((NoiseMath.valCoord2D(seed, x, y) + 1.0F) * 0.5F, 0.0F, 1.0F);
    }
}
