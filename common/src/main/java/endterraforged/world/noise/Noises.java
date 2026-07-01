/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Noises (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged.
 *
 * Pure-algorithm adaptation: drops the Codec/Registry bootstrap surface (a
 * stage-3 DFU bridge concern). The factory signatures, the scale->frequency
 * ({@code 1.0F / scale}) convention, the per-leaf seed-isolation strategy
 * (ShiftSeed for seed-sensitive leaves, direct seed for Perlin), and the
 * Noises.map identity short-circuit are all preserved so makeMountains recipes
 * assemble identically to upstream.
 *
 * Only the subset needed for the End-heightmap mountain layer is ported:
 * constant/zero/one, perlin/perlinRidge/simplex/simplex2, worley/worleyEdge,
 * shiftSeed, add/mul/pow/alpha/invert, map/clamp, warp/warpPerlin. Upstream's
 * perlin2/billow/cubic/sin/white/line/frequency/gradient/terrace/
 * advancedTerrace/blend/boost/steps/abs/threshold/min/max/cache/erosion/cell
 * factories are deferred until a consumer needs them.
 */
package endterraforged.world.noise;

import endterraforged.world.noise.domain.Domain;
import endterraforged.world.noise.domain.Domains;

/**
 * Factory for assembling {@link Noise} trees.
 *
 * <p>This is the API surface recipes like {@code makeMountains} build against:
 * a small set of static helpers that hide the {@code 1.0F/scale} frequency
 * conversion and the per-leaf seed-offset plumbing. Callers pass a world
 * {@code seed} and a spatial {@code scale} (in blocks); the factory returns a
 * fully-wired {@link Noise}.</p>
 *
 * <p><b>Seed handling.</b> {@link Perlin} stores its own seed and ignores the
 * one passed to {@code compute}, so {@link #perlin} passes {@code seed}
 * straight to its constructor. Every other leaf ({@link Simplex}, {@link
 * Simplex2}, {@link PerlinRidge}, {@link Worley}, {@link WorleyEdge}) is
 * seed-sensitive, so their factories wrap the instance in {@link ShiftSeed}
 * with the supplied offset — letting one world seed drive many independent
 * instances.</p>
 *
 * <p>Static, stateless, thread-safe.</p>
 */
public final class Noises {

    private Noises() {
    }

    // ----- constants -------------------------------------------------------

    public static Noise constant(float value) {
        return new Constant(value);
    }

    public static Noise zero() {
        return constant(0.0F);
    }

    public static Noise one() {
        return constant(1.0F);
    }

    // ----- gradient / ridge leaves ----------------------------------------

    public static Noise perlin(int seed, int scale, int octaves) {
        return perlin(seed, scale, octaves, 2.0F);
    }

    public static Noise perlin(int seed, int scale, int octaves, float lacunarity) {
        return perlin(seed, scale, octaves, lacunarity, 0.5F);
    }

    public static Noise perlin(int seed, int scale, int octaves, float lacunarity, float gain) {
        return new Perlin(seed, 1.0F / scale, octaves, lacunarity, gain, Interpolation.CURVE3);
    }

    public static Noise perlinRidge(int seed, int scale, int octaves) {
        return perlinRidge(seed, scale, octaves, 2.0F);
    }

    public static Noise perlinRidge(int seed, int scale, int octaves, float lacunarity) {
        return perlinRidge(seed, scale, octaves, lacunarity, 0.975F);
    }

    public static Noise perlinRidge(int seed, int scale, int octaves, float lacunarity, float gain) {
        return shiftSeed(new PerlinRidge(1.0F / scale, octaves, lacunarity, gain, Interpolation.CURVE3), seed);
    }

    public static Noise simplex(int seed, int scale, int octaves) {
        return simplex(seed, scale, octaves, 2.0F);
    }

    public static Noise simplex(int seed, int scale, int octaves, float lacunarity) {
        return simplex(seed, scale, octaves, lacunarity, 0.5F);
    }

    public static Noise simplex(int seed, int scale, int octaves, float lacunarity, float gain) {
        return shiftSeed(new Simplex(1.0F / scale, octaves, lacunarity, gain, Interpolation.CURVE3), seed);
    }

    public static Noise simplex2(int seed, int scale, int octaves) {
        return simplex2(seed, scale, octaves, 2.0F);
    }

    public static Noise simplex2(int seed, int scale, int octaves, float lacunarity) {
        return simplex2(seed, scale, octaves, lacunarity, 0.5F);
    }

    public static Noise simplex2(int seed, int scale, int octaves, float lacunarity, float gain) {
        return shiftSeed(new Simplex2(1.0F / scale, octaves, lacunarity, gain, Interpolation.CURVE3), seed);
    }

    // ----- cell leaves -----------------------------------------------------

    public static Noise worley(int seed, int scale) {
        return worley(seed, scale, CellFunction.CELL_VALUE, DistanceFunction.EUCLIDEAN, zero());
    }

    public static Noise worley(int seed, int scale, CellFunction cellFunction,
                               DistanceFunction distanceFunction, Noise lookup) {
        return shiftSeed(new Worley(1.0F / scale, 1.0F, cellFunction, distanceFunction, lookup), seed);
    }

    public static Noise worleyEdge(int seed, int scale) {
        return worleyEdge(seed, scale, EdgeFunction.DISTANCE_2, DistanceFunction.EUCLIDEAN);
    }

    public static Noise worleyEdge(int seed, int scale, EdgeFunction edgeFunction,
                                   DistanceFunction distanceFunction) {
        return shiftSeed(new WorleyEdge(1.0F / scale, 1.0F, edgeFunction, distanceFunction), seed);
    }

    // ----- seed plumbing ---------------------------------------------------

    public static Noise shiftSeed(Noise input, int seed) {
        return new ShiftSeed(input, seed);
    }

    // ----- arithmetic composers -------------------------------------------

    public static Noise add(Noise input1, float input2) {
        return add(input1, constant(input2));
    }

    public static Noise add(Noise input1, Noise input2) {
        return new Add(input1, input2);
    }

    public static Noise mul(Noise input1, float input2) {
        return mul(input1, constant(input2));
    }

    public static Noise mul(Noise input1, Noise input2) {
        return new Multiply(input1, input2);
    }

    public static Noise pow(Noise input, float power) {
        return new Power(input, power);
    }

    public static Noise alpha(Noise input, float alpha) {
        return alpha(input, constant(alpha));
    }

    public static Noise alpha(Noise input, Noise alpha) {
        return new Alpha(input, alpha);
    }

    public static Noise invert(Noise input) {
        return new Invert(input);
    }

    // ----- range shaping ---------------------------------------------------

    public static Noise map(Noise input, float from, float to) {
        return map(input, constant(from), constant(to));
    }

    public static Noise map(Noise input, Noise from, Noise to) {
        // Identity short-circuit: if `from`/`to` are constants matching the
        // input's own range, the remap is a no-op — return the input untouched
        // so no redundant Map node (and no divide-by-zero on a degenerate
        // alpha extent) is introduced.
        if (from.minValue() == from.maxValue() && from.minValue() == input.minValue()
                && to.minValue() == to.maxValue() && to.maxValue() == input.maxValue()) {
            return input;
        }
        return new Map(input, from, to);
    }

    public static Noise clamp(Noise input, float min, float max) {
        return clamp(input, constant(min), constant(max));
    }

    public static Noise clamp(Noise input, Noise min, Noise max) {
        return new Clamp(input, min, max);
    }

    // ----- domain warping --------------------------------------------------

    public static Noise warp(Noise input, Domain domain) {
        return new Warp(input, domain);
    }

    public static Noise warp(Noise input, Noise warpX, Noise warpZ, Noise distance) {
        return warp(input, Domains.domain(warpX, warpZ, distance));
    }

    public static Noise warp(Noise input, Noise warpX, Noise warpZ, float distance) {
        return warp(input, warpX, warpZ, constant(distance));
    }

    /**
     * Warps {@code input} by two perlin fields (offset by one seed) scaled by a
     * constant {@code strength}. The canonical makeMountains warp step.
     */
    public static Noise warpPerlin(Noise input, int seed, int scale, int octaves, float strength) {
        return warp(input,
                perlin(seed, scale, octaves),
                perlin(seed + 1, scale, octaves),
                strength);
    }
}
