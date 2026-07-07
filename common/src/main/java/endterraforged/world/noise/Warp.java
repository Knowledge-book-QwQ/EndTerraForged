/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Warp (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged. Pure-algorithm adaptation: Codec dropped.
 */
package endterraforged.world.noise;

import endterraforged.world.noise.domain.Domain;

/**
 * Applies a {@link Domain} warp to a noise field: samples {@code input} at the
 * domain-warped coordinates rather than the caller's raw {@code (x, z)}.
 *
 * <p>This is the composer that turns a plain ridge field into a flowing,
 * non-grid-aligned ridge — the {@code warpPerlin(perlinRidge(...), ...)} step
 * in RTF's makeMountains2. The output range is unchanged ({@code input}'s own
 * range); warping relocates features, it does not rescale them.</p>
 *
 * <p>Immutable record, thread-safe (assuming {@code input} and {@code domain}
 * are).</p>
 *
 * @param input  the noise field to sample
 * @param domain the coordinate-rewriting layer
 */
public record Warp(Noise input, Domain domain) implements Noise {

    @Override
    public float compute(float x, float z, int seed) {
        return this.input.compute(
                this.domain.getX(x, z, seed),
                this.domain.getZ(x, z, seed),
                seed);
    }

    @Override
    public float minValue() {
        return this.input.minValue();
    }

    @Override
    public float maxValue() {
        return this.input.maxValue();
    }

    @Override
    public Noise mapAll(Visitor visitor) {
        return visitor.apply(new Warp(this.input.mapAll(visitor), this.domain.mapAll(visitor)));
    }
}
