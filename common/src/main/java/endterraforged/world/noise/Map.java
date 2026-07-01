/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Map (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged. Pure-algorithm adaptation: Codec dropped.
 */
package endterraforged.world.noise;

/**
 * Remaps a noise field's {@code [alphaMin, alphaMax]} extent onto a new
 * per-coordinate range {@code [from, to]}: normalises {@code alpha} to
 * {@code [0,1]}, then lerps {@code from -> to}.
 *
 * <p>This is the workhorse rescaler — e.g. {@code map(perlinRidge(...), -0.5,
 * 0.5)} centres a {@code [0,1]} ridge on zero for use as a domain-warp offset.
 * {@code minValue}/{@code maxValue} are {@code from.minValue()} and
 * {@code to.maxValue()}.</p>
 *
 * <p><b>Precondition (faithful to upstream).</b> {@code alpha.minValue()} must
 * be strictly less than {@code alpha.maxValue()}; a degenerate (constant)
 * {@code alpha} would divide by zero and yield {@code NaN}. The {@code
 * Noises.map} factory short-circuits the common identity case
 * ({@code from}/{@code to} constants matching the input's own range), but does
 * not guard a constant {@code alpha} — callers must supply a real varying
 * field. Not guarded here to keep output byte-identical to upstream.</p>
 *
 * <p>Immutable record, thread-safe.</p>
 *
 * @param alpha the field whose extent drives the remap
 * @param from  output value at {@code alpha == alpha.minValue()}
 * @param to    output value at {@code alpha == alpha.maxValue()}
 */
public record Map(Noise alpha, Noise from, Noise to) implements Noise {

    @Override
    public float compute(float x, float z, int seed) {
        float alphaMin = this.alpha.minValue();
        float alphaMax = this.alpha.maxValue();

        float value = this.alpha.compute(x, z, seed);
        float alpha = (value - alphaMin) / (alphaMax - alphaMin);
        float min = this.from.compute(x, z, seed);
        float max = this.to.compute(x, z, seed);
        return min + alpha * (max - min);
    }

    @Override
    public float minValue() {
        return this.from.minValue();
    }

    @Override
    public float maxValue() {
        return this.to.maxValue();
    }

    @Override
    public Noise mapAll(Visitor visitor) {
        return visitor.apply(new Map(
                this.alpha.mapAll(visitor),
                this.from.mapAll(visitor),
                this.to.mapAll(visitor)));
    }
}
