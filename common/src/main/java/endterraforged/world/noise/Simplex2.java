/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Simplex2 (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged.
 *
 * Pure-algorithm adaptation: drops the RecordCodecBuilder serialisation
 * surface; the fBm loop and the upstream-tuned 99.83685F scaler are
 * byte-identical. The only difference from {@link Simplex} is the scaler,
 * which produces a slightly hotter field — upstream uses Simplex2 as the
 * base for SimplexRidge (the ridge variant of mountain noise).
 */
package endterraforged.world.noise;

/**
 * 2D Simplex noise at a hotter scaler ({@code 99.83685F} vs {@link Simplex}'s
 * {@code 79.869484F}).
 *
 * <p>Upstream uses this as the base for {@link SimplexRidge}: the larger
 * scaler pushes more samples toward the kernel's saturation envelope, which
 * after {@code 1 - |n|} ridging yields crisper spines than SimplexRidge-on-
 * Simplex would.</p>
 *
 * <p><b>Faithfulness.</b> Everything except the dropped codec is
 * byte-identical to upstream, including the {@code SIGNALS} table (which
 * matches {@link Simplex}'s).</p>
 *
 * <p><b>Thread safety:</b> immutable record; safe to query from multiple
 * threads.</p>
 *
 * @param frequency     spatial frequency
 * @param octaves       number of fBm octaves
 * @param lacunarity    per-octave frequency multiplier
 * @param gain          per-octave amplitude multiplier
 * @param interpolation carried for API symmetry (kernel does not use it)
 * @param min           derived lower bound for normalisation
 * @param max           derived upper bound for normalisation
 */
public record Simplex2(float frequency, int octaves, float lacunarity, float gain,
                       Interpolation interpolation, float min, float max) implements Noise {

    private static final float[] SIGNALS = new float[] {
            1.0F, 0.989F, 0.81F, 0.781F, 0.708F, 0.702F, 0.696F
    };

    public Simplex2(float frequency, int octaves, float lacunarity, float gain, Interpolation interpolation) {
        this(frequency, octaves, lacunarity, gain, interpolation, -max(octaves, gain), max(octaves, gain));
    }

    @Override
    public float compute(float x, float z, int seed) {
        x *= this.frequency;
        z *= this.frequency;
        float sum = 0.0F;
        float amp = 1.0F;
        for (int i = 0; i < this.octaves; ++i) {
            sum += sample(x, z, seed + i) * amp;
            x *= this.lacunarity;
            z *= this.lacunarity;
            amp *= this.gain;
        }
        return NoiseMath.map(sum, this.min, this.max, (this.max - this.min));
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

    /** Single-octave sample at {@code (x, y)} with the hotter Simplex2 scaler. */
    public static float sample(float x, float y, int seed) {
        return Simplex.singleSimplex(x, y, seed, 99.83685F);
    }

    /**
     * Theoretical max of the fBm sum, used by {@link SimplexRidge} to derive
     * its normalisation bound. Exposed static because SimplexRidge needs it
     * without an instance.
     */
    public static float max(int octaves, float gain) {
        float signal = signal(octaves);
        float sum = 0.0F;
        float amp = 1.0F;
        for (int i = 0; i < octaves; ++i) {
            sum += amp * signal;
            amp *= gain;
        }
        return sum;
    }

    private static float signal(int octaves) {
        int index = Math.min(octaves, SIGNALS.length - 1);
        return SIGNALS[index];
    }
}
