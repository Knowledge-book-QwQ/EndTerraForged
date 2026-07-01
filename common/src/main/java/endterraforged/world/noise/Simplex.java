/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Simplex (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged.
 *
 * Pure-algorithm adaptation: drops the RecordCodecBuilder serialisation
 * surface; the fBm octave loop and the single_simplex kernel are
 * byte-identical to upstream. Reuses NoiseMath.gradCoord2D_24 / floor.
 */
package endterraforged.world.noise;

/**
 * 2D Simplex (OpenSimplex-style) noise with fBm octave stacking.
 *
 * <p>Unlike {@link Perlin}, Simplex uses the passed {@code seed} (offset per
 * octave as {@code seed + i}) rather than a stored seed — so a single world
 * seed drives the whole field. The triangular-grid kernel
 * ({@link #singleSimplex}) produces softer, less axis-aligned features than
 * Perlin's square grid, which is why upstream uses Simplex for moisture /
 * temperature fields and Perlin for terrain height.</p>
 *
 * <p><b>Faithfulness.</b> The {@code 0.36602542} / {@code 0.21132487} skew
 * constants, the {@code 0.5 - r^2} falloff, the 24-entry gradient table and
 * the {@code 79.869484} scaler are all byte-identical to upstream.</p>
 *
 * <p><b>Thread safety:</b> immutable record; safe to query from multiple
 * threads.</p>
 *
 * @param frequency     spatial frequency applied to inputs before sampling
 * @param octaves       number of fBm octaves
 * @param lacunarity    per-octave frequency multiplier
 * @param gain          per-octave amplitude multiplier
 * @param interpolation carried for API symmetry with {@link Perlin}; the
 *                      single-octave kernel does not use it (Simplex's kernel
 *                      is internally C1 continuous)
 * @param min           derived lower bound for normalisation
 * @param max           derived upper bound for normalisation
 */
public record Simplex(float frequency, int octaves, float lacunarity, float gain,
                      Interpolation interpolation, float min, float max) implements Noise {

    private static final float[] SIGNALS = new float[] {
            1.0F, 0.989F, 0.81F, 0.781F, 0.708F, 0.702F, 0.696F
    };

    public Simplex(float frequency, int octaves, float lacunarity, float gain, Interpolation interpolation) {
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

    /** Single-octave sample at {@code (x, y)} with upstream's Simplex scaler. */
    public static float sample(float x, float y, int seed) {
        return singleSimplex(x, y, seed, 79.869484F);
    }

    /**
     * The triangular-grid simplex kernel. Exposed static with an explicit
     * {@code scaler} so {@link Simplex2} can reuse it under a different scaler
     * (99.83685F upstream).
     */
    public static float singleSimplex(float x, float y, int seed, float scaler) {
        float t = (x + y) * 0.36602542F;
        int i = NoiseMath.floor(x + t);
        int j = NoiseMath.floor(y + t);
        t = (i + j) * 0.21132487F;
        float X0 = i - t;
        float Y0 = j - t;
        float x2 = x - X0;
        float y2 = y - Y0;
        int i2;
        int j2;
        if (x2 > y2) {
            i2 = 1;
            j2 = 0;
        } else {
            i2 = 0;
            j2 = 1;
        }
        float x3 = x2 - i2 + 0.21132487F;
        float y3 = y2 - j2 + 0.21132487F;
        float x4 = x2 - 1.0F + 0.42264974F;
        float y4 = y2 - 1.0F + 0.42264974F;
        t = 0.5F - x2 * x2 - y2 * y2;
        float n0;
        if (t < 0.0F) {
            n0 = 0.0F;
        } else {
            t *= t;
            n0 = t * t * NoiseMath.gradCoord2D_24(seed, i, j, x2, y2);
        }
        t = 0.5F - x3 * x3 - y3 * y3;
        float n2;
        if (t < 0.0F) {
            n2 = 0.0F;
        } else {
            t *= t;
            n2 = t * t * NoiseMath.gradCoord2D_24(seed, i + i2, j + j2, x3, y3);
        }
        t = 0.5F - x4 * x4 - y4 * y4;
        float n3;
        if (t < 0.0F) {
            n3 = 0.0F;
        } else {
            t *= t;
            n3 = t * t * NoiseMath.gradCoord2D_24(seed, i + 1, j + 1, x4, y4);
        }
        return scaler * (n0 + n2 + n3);
    }

    private static float max(int octaves, float gain) {
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
