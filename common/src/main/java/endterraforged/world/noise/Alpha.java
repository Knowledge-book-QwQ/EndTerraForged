/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Alpha (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged. Pure-algorithm adaptation: Codec dropped.
 */
package endterraforged.world.noise;

/**
 * Blends {@code input} toward {@code 1.0} by a per-point factor:
 * {@code input * alpha + (1 - alpha)}.
 *
 * <p>This is the "scaler alpha" used throughout the makeMountains recipes —
 * e.g. {@code alpha(perlinRidge(...), 0.6)} lifts the ridge floor toward 1.0
 * by 40% while preserving peaks. When {@code alpha == 1} the output equals
 * {@code input}; when {@code alpha == 0} the output is pinned to {@code 1.0}.</p>
 *
 * <p><b>Range caveat (faithful to upstream).</b> {@code minValue}/{@code
 * maxValue} mirror {@code input}'s range, which assumes {@code alpha ∈ [0,1]}.
 * For {@code alpha == 0} the true output is the constant {@code 1.0}, so the
 * declared bound under-reports there; in practice alpha is a {@code [0,1]}
 * normalised noise so the bound holds. Not guarded to stay byte-identical to
 * upstream.</p>
 *
 * <p>Immutable record, thread-safe.</p>
 *
 * @param input the field being scaled
 * @param alpha blend factor in {@code [0,1]} (0 = pin to 1.0, 1 = passthrough)
 */
public record Alpha(Noise input, Noise alpha) implements Noise {

    @Override
    public float compute(float x, float z, int seed) {
        float input = this.input.compute(x, z, seed);
        float alpha = this.alpha.compute(x, z, seed);
        return input * alpha + (1.0F - alpha);
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
        return visitor.apply(new Alpha(this.input.mapAll(visitor), this.alpha.mapAll(visitor)));
    }
}
