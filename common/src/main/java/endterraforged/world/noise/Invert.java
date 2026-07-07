/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Invert (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged. Pure-algorithm adaptation: Codec dropped.
 */
package endterraforged.world.noise;

/**
 * Mirrors a noise field inside its declared range: {@code max - clamp(input,
 * min, max)}. The output keeps the same {@code [min, max]} extent, so an input
 * peaking at {@code max} maps to {@code min} and vice versa.
 *
 * <p>Useful for turning a "distance-to-cell-centre" field into a
 * "cell-centre-strength" field without rescaling. The internal {@link
 * NoiseMath#clamp} guards against inputs that drift outside their declared
 * range (e.g. from upstream composers with approximate bounds).</p>
 *
 * <p>Immutable record, thread-safe.</p>
 *
 * @param input the field to invert
 */
public record Invert(Noise input) implements Noise {

    @Override
    public float compute(float x, float z, int seed) {
        float min = this.input.minValue();
        float max = this.input.maxValue();
        return max - NoiseMath.clamp(this.input.compute(x, z, seed), min, max);
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
        return visitor.apply(new Invert(this.input.mapAll(visitor)));
    }
}
