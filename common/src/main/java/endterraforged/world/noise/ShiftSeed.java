/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's ShiftSeed (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged. Pure-algorithm adaptation: Codec dropped.
 */
package endterraforged.world.noise;

/**
 * Adds a constant offset to the {@code seed} argument before delegating to
 * {@code input}: {@code input.compute(x, z, seed + shift)}.
 *
 * <p>This is how seed-sensitive leaves ({@link Simplex}, {@link Simplex2},
 * {@link PerlinRidge}, {@link SimplexRidge}, {@link Worley}, {@link
 * WorleyEdge}) gain per-instance seed isolation without storing their own seed
 * field: the {@code Noises} factory wraps each in a {@code ShiftSeed} carrying
 * a unique offset, so a single world seed can drive many independent instances.
 * {@link Perlin} is the exception — it stores its own seed and ignores the
 * passed value, so the factory passes the seed directly to its constructor
 * instead of wrapping.</p>
 *
 * <p>{@code minValue}/{@code maxValue} pass through unchanged. Immutable
 * record, thread-safe.</p>
 *
 * @param input the seed-sensitive leaf being offset
 * @param shift the constant added to the caller-supplied seed
 */
public record ShiftSeed(Noise input, int shift) implements Noise {

    @Override
    public float compute(float x, float z, int seed) {
        return this.input.compute(x, z, seed + this.shift);
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
        return visitor.apply(new ShiftSeed(this.input.mapAll(visitor), this.shift));
    }
}
