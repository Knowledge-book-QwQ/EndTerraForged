/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Perlin2 (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged.
 *
 * EndTerraForged adaptation:
 * - removed the Codec and registry bootstrap surface
 * - retained the octave loop, 24-gradient kernel and range derivation
 */
package endterraforged.world.noise;

/**
 * Perlin fBm variant using RTF's 24-direction gradient table.
 *
 * <p>The instance is immutable and safe for concurrent sampling. Like
 * {@link Perlin}, it owns its seed and ignores the seed passed to
 * {@link #compute(float, float, int)}.</p>
 */
public record Perlin2(int seed, float frequency, int octaves, float lacunarity, float gain,
                      Interpolation interpolation, float min, float max) implements Noise {

    private static final float[] SIGNALS = {
            1.0F, 0.9F, 0.83F, 0.75F, 0.64F, 0.62F, 0.61F
    };

    public Perlin2(int seed, float frequency, int octaves, float lacunarity, float gain,
                   Interpolation interpolation) {
        this(seed, frequency, octaves, lacunarity, gain, interpolation,
                min(octaves, gain), max(octaves, gain));
    }

    @Override
    public float compute(float x, float z, int ignoredSeed) {
        x *= this.frequency;
        z *= this.frequency;
        float sum = 0.0F;
        float amplitude = this.gain;
        for (int i = 0; i < this.octaves; i++) {
            sum += sample(x, z, this.seed + i, this.interpolation) * amplitude;
            x *= this.lacunarity;
            z *= this.lacunarity;
            amplitude *= this.gain;
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

    public static float sample(float x, float z, int seed, Interpolation interpolation) {
        int x0 = NoiseMath.floor(x);
        int z0 = NoiseMath.floor(z);
        int x1 = x0 + 1;
        int z1 = z0 + 1;
        float alphaX = interpolation.apply(x - x0);
        float alphaZ = interpolation.apply(z - z0);
        float deltaX0 = x - x0;
        float deltaZ0 = z - z0;
        float deltaX1 = deltaX0 - 1.0F;
        float deltaZ1 = deltaZ0 - 1.0F;
        float lower = NoiseMath.lerp(
                NoiseMath.gradCoord2D_24(seed, x0, z0, deltaX0, deltaZ0),
                NoiseMath.gradCoord2D_24(seed, x1, z0, deltaX1, deltaZ0),
                alphaX);
        float upper = NoiseMath.lerp(
                NoiseMath.gradCoord2D_24(seed, x0, z1, deltaX0, deltaZ1),
                NoiseMath.gradCoord2D_24(seed, x1, z1, deltaX1, deltaZ1),
                alphaX);
        return NoiseMath.lerp(lower, upper, alphaZ);
    }

    private static float min(int octaves, float gain) {
        return -max(octaves, gain);
    }

    private static float max(int octaves, float gain) {
        float signal = signal(octaves);
        float sum = 0.0F;
        float amplitude = gain;
        for (int i = 0; i < octaves; i++) {
            sum += signal * amplitude;
            amplitude *= gain;
        }
        return sum;
    }

    private static float signal(int octaves) {
        return SIGNALS[Math.min(octaves, SIGNALS.length - 1)];
    }
}
