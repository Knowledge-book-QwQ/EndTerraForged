/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's PerlinRidge (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged.
 *
 * Pure-algorithm adaptation: drops the RecordCodecBuilder serialisation
 * surface; the ridged-multifractal octave loop, the spectral-weight
 * precomputation and the max-bound derivation are byte-identical to upstream.
 */
package endterraforged.world.noise;

/**
 * Ridged multifractal noise built on {@link Perlin}.
 *
 * <p>This is the spine of RTF's {@code makeMountains1} ({@code perlinRidge(seed,
 * 410, 4, 2.35, 1.15)}) and the surface detail layer in
 * {@code makeMountains2/3} ({@code perlinRidge(seed, 125, 4)}). The ridging
 * transform {@code (1 - |signal|)^2} folds the Perlin field so its zero
 * crossings become sharp crests, and the per-octave {@code weight = signal *
 * amp} feedback makes higher octaves concentrate along the lower-frequency
 * ridges — together they produce the realistic broken-spine mountain silhouette
 * that fBm alone cannot.</p>
 *
 * <p><b>Faithfulness.</b> The octave loop, the {@code spectralWeights}
 * precompute ({@code pow(f, -1)} per octave) and the max-bound walk are all
 * byte-identical to upstream. Octaves are clamped to 30 in the compact
 * constructor (upstream behaviour) to stay within the spectral-weights array.</p>
 *
 * <p><b>Thread safety:</b> immutable record; the {@code spectralWeights} array
 * is assigned once at construction and never mutated afterwards, so concurrent
 * reads are safe.</p>
 *
 * @param frequency        spatial frequency
 * @param octaves          number of fBm octaves (clamped to 30)
 * @param lacunarity       per-octave frequency multiplier
 * @param gain             per-octave amplitude multiplier
 * @param interpolation    per-cell fraction curve passed through to {@link Perlin#sample}
 * @param spectralWeights  per-octave {@code pow(f, -1)} weights, precomputed once
 * @param min              always 0 (ridged output is non-negative)
 * @param max              derived upper bound for normalisation
 */
public record PerlinRidge(float frequency, int octaves, float lacunarity, float gain,
                          Interpolation interpolation, float[] spectralWeights, float min, float max) implements Noise {

    public PerlinRidge(float frequency, int octaves, float lacunarity, float gain, Interpolation interpolation) {
        this(frequency, octaves, lacunarity, gain, interpolation, calculateSpectralWeights(octaves, lacunarity));
    }

    private PerlinRidge(float frequency, int octaves, float lacunarity, float gain, Interpolation interpolation, float[] spectralWeights) {
        this(frequency, octaves, lacunarity, gain, interpolation, spectralWeights, 0.0F, calculateMaxBound(spectralWeights, gain));
    }

    /** Clamps octaves to the spectral-weights length, matching upstream. */
    public PerlinRidge {
        octaves = Math.min(octaves, 30);
    }

    @Override
    public float compute(float x, float z, int seed) {
        x *= this.frequency;
        z *= this.frequency;
        float amp = 2.0F;
        float value = 0.0F;
        float weight = 1.0F;
        for (int octave = 0; octave < this.octaves; ++octave) {
            float signal = Perlin.sample(x, z, seed + octave, this.interpolation);
            signal = 1.0F - Math.abs(signal);
            signal *= signal;
            signal *= weight;
            weight = signal * amp;
            weight = NoiseMath.clamp(weight, 0.0F, 1.0F);
            value += signal * this.spectralWeights[octave];
            x *= this.lacunarity;
            z *= this.lacunarity;
            amp *= this.gain;
        }
        return NoiseMath.map(value, this.min, this.max, Math.abs(this.max - this.min));
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

    @Override
    public boolean equals(Object o) {
        return o instanceof PerlinRidge other
                && other.frequency == this.frequency
                && other.octaves == this.octaves
                && other.lacunarity == this.lacunarity
                && other.gain == this.gain
                && other.interpolation.equals(this.interpolation);
    }

    @Override
    public int hashCode() {
        return Float.floatToIntBits(frequency)
                ^ Float.floatToIntBits(lacunarity)
                ^ Float.floatToIntBits(gain)
                ^ octaves
                ^ interpolation.hashCode();
    }

    private static float[] calculateSpectralWeights(int octaves, float lacunarity) {
        float frequency = 1.0F;
        float[] spectralWeights = new float[octaves];
        for (int i = 0; i < spectralWeights.length; i++) {
            spectralWeights[i] = NoiseMath.pow(frequency, -1.0F);
            frequency *= lacunarity;
        }
        return spectralWeights;
    }

    private static float calculateMaxBound(float[] spectralWeights, float gain) {
        float amp = 2.0F;
        float value = 0.0F;
        float weight = 1.0F;
        for (int curOctave = 0; curOctave < spectralWeights.length; ++curOctave) {
            float noise = 1.0F;
            noise *= weight;
            weight = noise * amp;
            weight = Math.min(1.0F, Math.max(0.0F, weight));
            value += noise * spectralWeights[curOctave];
            amp *= gain;
        }
        return value;
    }
}
