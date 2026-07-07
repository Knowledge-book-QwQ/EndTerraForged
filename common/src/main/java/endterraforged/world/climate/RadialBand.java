/*
 * Copyright (c) 2023 ReTerraForged
 *
 * EndTerraForged original (LGPL-3.0-or-later). Pure leaf noise that outputs
 * a radial band: 1 at the world origin, 0 at distance == radius, linearly
 * interpolated between. Used by EndClimate for the temperature base band.
 */
package endterraforged.world.climate;

import endterraforged.world.noise.Noise;
import endterraforged.world.noise.NoiseMath;

/**
 * A radial-band leaf noise: outputs {@code 1 - dist((x,z), origin) / radius},
 * clamped to {@code [0,1]}. At the origin the value is 1; at distance
 * {@code radius} and beyond the value is 0.
 *
 * <p>Pure function of {@code (x, z)} — ignores {@code seed}. Used as the
 * base band of {@link EndClimate}'s temperature channel; a simplex
 * perturbation is added on top to break the perfect radial symmetry.</p>
 *
 * @param radius world-units radius over which the band falls from 1 to 0
 */
record RadialBand(float radius) implements Noise {

    @Override
    public float compute(float x, float z, int seed) {
        if (radius <= 0.0F) {
            // Degenerate: no thermal gradient, everywhere is "cold" (0). Avoids
            // div-by-zero (dist/0 = Inf/NaN) that would poison the climate
            // chain downstream.
            return 0.0F;
        }
        float dist = (float) Math.sqrt(x * x + z * z);
        float v = 1.0F - dist / radius;
        return NoiseMath.clamp(v, 0.0F, 1.0F);
    }

    @Override
    public float minValue() {
        return 0.0F;
    }

    @Override
    public float maxValue() {
        return 1.0F;
    }

    @Override
    public Noise mapAll(Visitor visitor) {
        return visitor.apply(this);
    }
}
