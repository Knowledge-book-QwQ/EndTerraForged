/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Add (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged. Pure-algorithm adaptation: Codec dropped.
 */
package endterraforged.world.noise;

/**
 * Sums two noise fields pointwise: {@code input1 + input2}.
 *
 * <p>The output range is the sum of the input ranges, which is exact for
 * addition regardless of sign — unlike {@link Multiply}, no non-negativity
 * assumption is required.</p>
 *
 * <p>Immutable record, thread-safe.</p>
 *
 * @param input1 first addend
 * @param input2 second addend
 */
public record Add(Noise input1, Noise input2) implements Noise {

    @Override
    public float compute(float x, float z, int seed) {
        return this.input1.compute(x, z, seed) + this.input2.compute(x, z, seed);
    }

    @Override
    public float minValue() {
        return this.input1.minValue() + this.input2.minValue();
    }

    @Override
    public float maxValue() {
        return this.input1.maxValue() + this.input2.maxValue();
    }

    @Override
    public Noise mapAll(Visitor visitor) {
        return visitor.apply(new Add(this.input1.mapAll(visitor), this.input2.mapAll(visitor)));
    }
}
