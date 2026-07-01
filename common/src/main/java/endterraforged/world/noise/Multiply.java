/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Multiply (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged. Pure-algorithm adaptation: Codec dropped.
 */
package endterraforged.world.noise;

/**
 * Multiplies two noise fields pointwise: {@code input1 * input2}, with a
 * short-circuit that returns {@code 0} when {@code input1} is exactly zero
 * (avoids sampling {@code input2} for the common masking case).
 *
 * <p><b>Range caveat (faithful to upstream).</b> {@code minValue}/{@code
 * maxValue} are reported as {@code min1*min2} and {@code max1*max2}, which is
 * only correct when both inputs are non-negative. The makeMountains recipes
 * feed {@code [0,1]}-normalised noises (Perlin/Simplex/Worley post-{@link
 * NoiseMath#map}) into {@code Multiply}, so the bound holds there; composing
 * signed-range inputs would under-report the true extent. Not guarded to keep
 * output byte-identical to upstream.</p>
 *
 * <p>Immutable record, thread-safe.</p>
 *
 * @param input1 the mask / scalar factor (sampled first)
 * @param input2 the masked field
 */
public record Multiply(Noise input1, Noise input2) implements Noise {

    @Override
    public float compute(float x, float z, int seed) {
        float input1 = this.input1.compute(x, z, seed);
        return input1 != 0.0F ? input1 * this.input2.compute(x, z, seed) : 0.0F;
    }

    @Override
    public float minValue() {
        return this.input1.minValue() * this.input2.minValue();
    }

    @Override
    public float maxValue() {
        return this.input1.maxValue() * this.input2.maxValue();
    }

    @Override
    public Noise mapAll(Visitor visitor) {
        return visitor.apply(new Multiply(this.input1.mapAll(visitor), this.input2.mapAll(visitor)));
    }
}
