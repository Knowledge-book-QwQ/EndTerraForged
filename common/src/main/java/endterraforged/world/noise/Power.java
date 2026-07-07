/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Power (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged. Pure-algorithm adaptation: Codec dropped.
 */
package endterraforged.world.noise;

/**
 * Raises a noise field to a constant power: {@code pow(input, power)} via
 * {@link NoiseMath#pow(float, float)} (which delegates to {@code Math.pow}).
 *
 * <p>Used in makeMountains2 as the final {@code pow 1.1} sharpening step that
 * steepens ridges without clamping. {@code minValue}/{@code maxValue} mirror
 * the input range — exact for non-negative inputs and {@code power >= 0}, the
 * only regime the recipes use.</p>
 *
 * <p>Immutable record, thread-safe.</p>
 *
 * @param input  the field being exponentiated
 * @param power  the exponent (e.g. {@code 1.1F})
 */
public record Power(Noise input, float power) implements Noise {

    @Override
    public float compute(float x, float z, int seed) {
        return NoiseMath.pow(this.input.compute(x, z, seed), this.power);
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
        return visitor.apply(new Power(this.input.mapAll(visitor), this.power));
    }
}
