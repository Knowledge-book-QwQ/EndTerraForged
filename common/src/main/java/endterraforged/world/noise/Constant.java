/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Constant (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged. Pure-algorithm adaptation: Codec dropped.
 */
package endterraforged.world.noise;

/**
 * A noise field that returns the same scalar everywhere. The canonical leaf
 * used to feed constant parameters into composers (e.g. {@link Clamp#min},
 * {@link Alpha#alpha}, {@link Map#from}/{@link Map#to}).
 *
 * <p>{@code minValue == maxValue == value}, so a {@link Constant} is its own
 * degenerate range — downstream {@link NoiseMath#map} normalisation against it
 * would divide by zero, which is why the {@code Noises.map} factory short-
 * circuits when {@code from}/{@code to} collapse to a constant matching the
 * input range.</p>
 *
 * <p>Immutable record, thread-safe.</p>
 *
 * @param value the constant scalar this field evaluates to
 */
public record Constant(float value) implements Noise {

    @Override
    public float compute(float x, float z, int seed) {
        return this.value;
    }

    @Override
    public float minValue() {
        return this.value;
    }

    @Override
    public float maxValue() {
        return this.value;
    }

    @Override
    public Noise mapAll(Visitor visitor) {
        return visitor.apply(this);
    }
}
