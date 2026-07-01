/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's SimplexRidge (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged.
 *
 * Pure-algorithm adaptation: drops the RecordCodecBuilder serialisation
 * surface; the ridged-multifractal octave loop and the spectral-weight
 * precomputation are byte-identical to upstream.
 *
 * Bugfix vs upstream: upstream's SimplexRidge does NOT clamp {@code octaves}
 * to the spectral-weights array length (30), so an octaves count > 30 would
 * throw ArrayIndexOutOfBoundsException at compute time — PerlinRidge clamps
 * but SimplexRidge was missed. We add the same clamp here; for valid
 * octaves <= 30 the output is unchanged.
 */
package endterraforged.world.noise;

/**
 * Ridged multifractal noise built on {@link Simplex2}.
 *
 * <p>Mirrors {@link PerlinRidge} but samples {@link Simplex2} instead of
 * {@link Perlin}. Upstream uses this for the volcano-cone and some
 * shattered-ridge variants where Simplex's softer, less grid-aligned kernel
 * reads more naturally than Perlin's after ridging.</p>
 *
 * <p><b>Faithfulness.</b> The octave loop, the {@code offset - |signal|}
 * ridging (with {@code offset = 1.0}), the weight feedback and the
 * spectral-weight precompute are all byte-identical to upstream. The max bound
 * is delegated to {@link Simplex2#max(int, float)}.</p>
 *
 * <p><b>Bugfix.</b> Upstream's SimplexRidge allocates a 30-entry
 * {@code spectralWeights} array but does not clamp {@code octaves} (unlike
 * {@link PerlinRidge}, which clamps to 30 in its compact constructor). An
 * octaves count above 30 would therefore throw at compute time. We add the
 * same {@code Math.min(octaves, 30)} clamp here; for the upstream-typical
 * octaves (<=4) the output is byte-identical.</p>
 *
 * <p><b>Thread safety:</b> immutable record; safe to query from multiple
 * threads.</p>
 *
 * @param frequency        spatial frequency
 * @param octaves          number of fBm octaves (clamped to 30)
 * @param lacunarity       per-octave frequency multiplier
 * @param gain             per-octave amplitude multiplier
 * @param interpolation    carried for API symmetry (Simplex2 kernel does not use it)
 * @param spectralWeights  per-octave {@code pow(f, -1)} weights, precomputed once
 * @param min              always 0
 * @param max              derived upper bound for normalisation
 */
public record SimplexRidge(float frequency, int octaves, float lacunarity, float gain,
                           Interpolation interpolation, float[] spectralWeights, float min, float max) implements Noise {

    public SimplexRidge(float frequency, int octaves, float lacunarity, float gain, Interpolation interpolation) {
        this(frequency, octaves, lacunarity, gain, interpolation, calculateSpectralWeights(lacunarity));
    }

    private SimplexRidge(float frequency, int octaves, float lacunarity, float gain, Interpolation interpolation, float[] spectralWeights) {
        this(frequency, octaves, lacunarity, gain, interpolation, spectralWeights, 0.0F, Simplex2.max(octaves, gain));
    }

    /**
     * Clamps octaves to the spectral-weights length (30). <b>Bugfix vs
     * upstream</b>: upstream's SimplexRidge omitted this clamp, risking
     * ArrayIndexOutOfBoundsException for octaves > 30.
     */
    public SimplexRidge {
        octaves = Math.min(octaves, 30);
    }

    @Override
    public float compute(float x, float z, int seed) {
        x *= this.frequency;
        z *= this.frequency;
        float value = 0.0F;
        float weight = 1.0F;
        float offset = 1.0F;
        float amp = 2.0F;
        for (int octave = 0; octave < this.octaves; ++octave) {
            float signal = Simplex2.sample(x, z, seed + octave);
            signal = Math.abs(signal);
            signal = offset - signal;
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
        return o instanceof SimplexRidge other
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

    private static float[] calculateSpectralWeights(float lacunarity) {
        float frequency = 1.0F;
        float[] spectralWeights = new float[30];
        for (int i = 0; i < spectralWeights.length; i++) {
            spectralWeights[i] = NoiseMath.pow(frequency, -1.0F);
            frequency *= lacunarity;
        }
        return spectralWeights;
    }
}
