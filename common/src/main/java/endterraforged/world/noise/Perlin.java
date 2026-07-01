/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Perlin (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged.
 *
 * Pure-algorithm adaptation: drops the RecordCodecBuilder serialisation
 * surface; the fBm octave loop, the per-octave Perlin.sample, and the
 * min/max bound derivation are byte-identical to upstream so output stays
 * the same. Reuses the already-ported NoiseMath helpers.
 */
package endterraforged.world.noise;

/**
 * 2D Perlin gradient noise with fractal Brownian motion (fBm) octave stacking.
 *
 * <p><b>Faithfulness.</b> The four-corner gradient lerp in {@link #sample} and
 * the amplitude-weighted octave loop in {@link #compute} are byte-identical to
 * upstream. The {@code SIGNALS} table scales each octave's contribution so the
 * theoretical output bound matches the empirical bound, letting
 * {@link NoiseMath#map} normalise cleanly into {@code [0,1]}.</p>
 *
 * <p><b>Seed quirk (preserved).</b> {@link #compute} ignores its {@code seed}
 * parameter and offsets from the record's stored {@link #seed} instead — this
 * matches upstream, where each Perlin instance is constructed with a unique
 * seed from a {@code Seed} generator. Callers wanting a single world seed to
 * drive multiple Perlin instances must construct each with a derived seed
 * (e.g. {@code worldSeed + i * GOLDEN}).</p>
 *
 * <p><b>Thread safety:</b> immutable record; safe to query from multiple
 * threads.</p>
 *
 * @param seed          base seed; octave {@code i} samples at {@code seed + i}
 * @param frequency     spatial frequency applied to inputs before sampling
 * @param octaves       number of fBm octaves (clamped to {@code SIGNALS} length internally)
 * @param lacunarity    per-octave frequency multiplier (typically ~2.0)
 * @param gain          per-octave amplitude multiplier (typically ~0.5)
 * @param interpolation per-cell fraction curve (CURVE4 recommended)
 * @param min           derived lower bound for normalisation
 * @param max           derived upper bound for normalisation
 */
public record Perlin(int seed, float frequency, int octaves, float lacunarity, float gain,
                     Interpolation interpolation, float min, float max) implements Noise {

    /** Per-octave empirical signal strength, used to derive the output bound. */
    private static final float[] SIGNALS = new float[] {
            1.0F, 0.9F, 0.83F, 0.75F, 0.64F, 0.62F, 0.61F
    };

    /**
     * Convenience constructor that derives {@code min}/{@code max} from the
     * octave/gain combination.
     */
    public Perlin(int seed, float frequency, int octaves, float lacunarity, float gain, Interpolation interpolation) {
        this(seed, frequency, octaves, lacunarity, gain, interpolation, min(octaves, gain), max(octaves, gain));
    }

    @Override
    public float compute(float x, float z, int seed) {
        x *= this.frequency;
        z *= this.frequency;

        float sum = 0.0F;
        float amplitude = this.gain;
        for (int i = 0; i < this.octaves; ++i) {
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

    /**
     * Single-octave 2D Perlin sample at {@code (x, y)} (already frequency-scaled).
     * Exposed static so {@link PerlinRidge} can reuse the gradient core without
     * an instance.
     */
    public static float sample(float x, float y, int seed, Interpolation interpolation) {
        int x2 = NoiseMath.floor(x);
        int y2 = NoiseMath.floor(y);
        int x3 = x2 + 1;
        int y3 = y2 + 1;
        float xs = interpolation.apply(x - x2);
        float ys = interpolation.apply(y - y2);
        float xd0 = x - x2;
        float yd0 = y - y2;
        float xd2 = xd0 - 1.0F;
        float yd2 = yd0 - 1.0F;
        float xf0 = NoiseMath.lerp(NoiseMath.gradCoord2D(seed, x2, y2, xd0, yd0), NoiseMath.gradCoord2D(seed, x3, y2, xd2, yd0), xs);
        float xf2 = NoiseMath.lerp(NoiseMath.gradCoord2D(seed, x2, y3, xd0, yd2), NoiseMath.gradCoord2D(seed, x3, y3, xd2, yd2), xs);
        return NoiseMath.lerp(xf0, xf2, ys);
    }

    private static float min(int octaves, float gain) {
        return -max(octaves, gain);
    }

    private static float max(int octaves, float gain) {
        float signal = signal(octaves);
        float sum = 0.0F;
        float amp = gain;
        for (int i = 0; i < octaves; ++i) {
            sum += signal * amp;
            amp *= gain;
        }
        return sum;
    }

    private static float signal(int octaves) {
        int index = Math.min(octaves, SIGNALS.length - 1);
        return SIGNALS[index];
    }
}
