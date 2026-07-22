/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Billow (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged.
 *
 * Pure-algorithm adaptation: drops the upstream codec surface while preserving
 * the billow octave loop, spectral weights, and output-range derivation.
 */
package endterraforged.world.noise;

/**
 * Billow multifractal noise for broad rolling terrain forms.
 *
 * <p>The runtime is immutable and safe to share across world-generation workers.
 */
public record Billow(float frequency, int octaves, float lacunarity, float gain,
                     Interpolation interpolation, float[] spectralWeights, float min, float max)
        implements Noise {

    public Billow(float frequency, int octaves, float lacunarity, float gain,
                  Interpolation interpolation) {
        this(frequency, octaves, lacunarity, gain, interpolation,
                calculateSpectralWeights(Math.min(octaves, 30), lacunarity));
    }

    private Billow(float frequency, int octaves, float lacunarity, float gain,
                   Interpolation interpolation, float[] spectralWeights) {
        this(frequency, Math.min(octaves, 30), lacunarity, gain, interpolation,
                spectralWeights, 0.0F, calculateMaxBound(spectralWeights, gain));
    }

    public Billow {
        octaves = Math.min(octaves, 30);
    }

    @Override
    public float compute(float x, float z, int seed) {
        x *= this.frequency;
        z *= this.frequency;
        float amplitude = 2.0F;
        float value = 0.0F;
        float weight = 1.0F;
        for (int octave = 0; octave < this.octaves; octave++) {
            float signal = Perlin.sample(x, z, seed + octave, this.interpolation);
            signal = 1.0F - Math.abs(signal);
            signal *= signal;
            signal *= weight;
            weight = Math.clamp(signal * amplitude, 0.0F, 1.0F);
            value += signal * this.spectralWeights[octave];
            x *= this.lacunarity;
            z *= this.lacunarity;
            amplitude *= this.gain;
        }
        return 1.0F - NoiseMath.map(value, this.min, this.max, Math.abs(this.max - this.min));
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

    private static float[] calculateSpectralWeights(int octaves, float lacunarity) {
        float frequency = 1.0F;
        float[] weights = new float[octaves];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = NoiseMath.pow(frequency, -1.0F);
            frequency *= lacunarity;
        }
        return weights;
    }

    private static float calculateMaxBound(float[] weights, float gain) {
        float amplitude = 2.0F;
        float value = 0.0F;
        float weight = 1.0F;
        for (float spectralWeight : weights) {
            float noise = weight;
            weight = Math.clamp(noise * amplitude, 0.0F, 1.0F);
            value += noise * spectralWeight;
            amplitude *= gain;
        }
        return value;
    }
}
