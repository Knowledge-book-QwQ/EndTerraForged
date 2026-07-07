/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Clamp (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged. Pure-algorithm adaptation: Codec dropped.
 */
package endterraforged.world.noise;

/**
 * Clamps a noise field pointwise into a per-coordinate range:
 * {@code clamp(input, min, max)} via {@link NoiseMath#clamp(float, float,
 * float)}.
 *
 * <p>Unlike a plain {@code clamp(input, constantMin, constantMax)}, the bounds
 * are themselves {@link Noise} fields, so the envelope can vary across the
 * world. In makeMountains2 a {@code worleyEdge * 1.2} field is clamped to
 * {@code [0, 1]} to cut off the ridge tails before warping.</p>
 *
 * <p>The declared output range is {@code [min.minValue(), max.maxValue()]} —
 * the tightest bound provable without sampling, exact when {@code min}/{@code
 * max} are constants.</p>
 *
 * <p>Immutable record, thread-safe.</p>
 *
 * @param input the field to clamp
 * @param min   lower-bound field
 * @param max   upper-bound field
 */
public record Clamp(Noise input, Noise min, Noise max) implements Noise {

    @Override
    public float compute(float x, float z, int seed) {
        return NoiseMath.clamp(
                this.input.compute(x, z, seed),
                this.min.compute(x, z, seed),
                this.max.compute(x, z, seed));
    }

    @Override
    public float minValue() {
        return this.min.minValue();
    }

    @Override
    public float maxValue() {
        return this.max.maxValue();
    }

    @Override
    public Noise mapAll(Visitor visitor) {
        return visitor.apply(new Clamp(
                this.input.mapAll(visitor),
                this.min.mapAll(visitor),
                this.max.mapAll(visitor)));
    }
}
