/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Cubic (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged.
 *
 * Pure-algorithm adaptation: drops the upstream codec surface while preserving
 * cubic value interpolation, octave accumulation, and range derivation.
 */
package endterraforged.world.noise;

/**
 * Smooth cubic value noise used by the dales and plateau terrain families.
 *
 * <p>The runtime is immutable and safe to share across world-generation workers.
 */
public record Cubic(float frequency, int octaves, float lacunarity, float gain,
                    float min, float max) implements Noise {

    public Cubic(float frequency, int octaves, float lacunarity, float gain) {
        this(frequency, octaves, lacunarity, gain,
                calculateBound(-0.75F, octaves, gain), calculateBound(0.75F, octaves, gain));
    }

    @Override
    public float compute(float x, float z, int seed) {
        x *= this.frequency;
        z *= this.frequency;

        float sum = sample(x, z, seed);
        float amplitude = 1.0F;
        for (int octave = 1; octave < this.octaves; octave++) {
            x *= this.lacunarity;
            z *= this.lacunarity;
            amplitude *= this.gain;
            sum += sample(x, z, seed + octave) * amplitude;
        }
        return NoiseMath.map(sum, this.min, this.max, this.max - this.min);
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

    public static float sample(float x, float z, int seed) {
        int x0 = NoiseMath.floor(x);
        int z0 = NoiseMath.floor(z);
        float xAlpha = x - x0;
        float zAlpha = z - z0;

        return NoiseMath.cubicLerp(
                NoiseMath.cubicLerp(
                        NoiseMath.valCoord2D(seed, x0 - 1, z0 - 1),
                        NoiseMath.valCoord2D(seed, x0, z0 - 1),
                        NoiseMath.valCoord2D(seed, x0 + 1, z0 - 1),
                        NoiseMath.valCoord2D(seed, x0 + 2, z0 - 1), xAlpha),
                NoiseMath.cubicLerp(
                        NoiseMath.valCoord2D(seed, x0 - 1, z0),
                        NoiseMath.valCoord2D(seed, x0, z0),
                        NoiseMath.valCoord2D(seed, x0 + 1, z0),
                        NoiseMath.valCoord2D(seed, x0 + 2, z0), xAlpha),
                NoiseMath.cubicLerp(
                        NoiseMath.valCoord2D(seed, x0 - 1, z0 + 1),
                        NoiseMath.valCoord2D(seed, x0, z0 + 1),
                        NoiseMath.valCoord2D(seed, x0 + 1, z0 + 1),
                        NoiseMath.valCoord2D(seed, x0 + 2, z0 + 1), xAlpha),
                NoiseMath.cubicLerp(
                        NoiseMath.valCoord2D(seed, x0 - 1, z0 + 2),
                        NoiseMath.valCoord2D(seed, x0, z0 + 2),
                        NoiseMath.valCoord2D(seed, x0 + 1, z0 + 2),
                        NoiseMath.valCoord2D(seed, x0 + 2, z0 + 2), xAlpha),
                zAlpha) * 0.44444445F;
    }

    private static float calculateBound(float signal, int octaves, float gain) {
        float amplitude = 1.0F;
        float value = signal;
        for (int i = 1; i < octaves; i++) {
            amplitude *= gain;
            value += signal * amplitude;
        }
        return value;
    }
}
