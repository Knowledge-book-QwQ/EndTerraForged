/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged (MIT) into EndTerraForged (LGPL-3.0-or-later).
 * Lineage: TerraForged (dags) -> ReTerraForged (raccoonman) -> EndTerraForged.
 * Original MIT notice retained per license terms.
 */
package endterraforged.util;

/**
 * A fast, deterministic pseudo-random number generator based on a splittable
 * gamma-mixing scheme (ported verbatim from ReTerraForged so that seed
 * sequences stay byte-for-byte compatible with upstream worldgen).
 *
 * <p><b>Thread safety:</b> <i>not thread-safe</i>. Instances hold mutable
 * {@code seed}/{@code gamma} state and mutate it on every draw. Each thread
 * (or each tile/chunk in the parallel generator) must own its own instance.
 * Sharing a single instance across threads would corrupt both the stream and
 * the caller's invariants.</p>
 *
 * <p><b>Determinism:</b> given the same {@code (seed, gamma)} pair, the
 * sequence of outputs is fixed and reproducible. This is relied upon by the
 * erosion filter and tile generator so that worldgen is stable across reloads.</p>
 *
 * <p><b>Gamma handling asymmetry (upstream behaviour, preserved verbatim):</b>
 * the constructor stores {@code gamma} as-is, while {@link #seed(long, long)}
 * re-mixes it via {@code mixGamma}. As a result the two-arg {@code seed(...)}
 * does <i>not</i> replay the original stream; the single-arg {@link #seed(long)}
 * (which leaves gamma untouched) does. Callers in the worldgen pipeline rely on
 * the two-arg form exactly because it re-derives gamma from a per-tile seed.</p>
 */
public class FastRandom {
    private static final long DEFAULT_GAMMA = -7046029254386353131L;

    private long seed;
    private long gamma;

    public FastRandom() {
        this(System.currentTimeMillis(), DEFAULT_GAMMA);
    }

    public FastRandom(long seed) {
        this(seed, DEFAULT_GAMMA);
    }

    public FastRandom(long seed, long gamma) {
        this.seed = seed;
        this.gamma = gamma;
    }

    public FastRandom seed(long seed) {
        this.seed = seed;
        return this;
    }

    public FastRandom seed(long seed, long gamma) {
        this.seed = seed;
        this.gamma = mixGamma(gamma);
        return this;
    }

    public FastRandom gamma(long gamma) {
        this.gamma = gamma;
        return this;
    }

    public int nextInt() {
        return mix32(this.nextSeed());
    }

    public int nextInt(int bound) {
        int r = mix32(this.nextSeed());
        int m = bound - 1;
        if ((bound & m) == 0x0) {
            r &= m;
        } else {
            for (int u = r >>> 1; u + m - (r = u % bound) < 0; u = mix32(this.nextSeed()) >>> 1) {
                // rejection-sampling loop; mirrors java.util.Random semantics
            }
        }
        return r;
    }

    public float nextFloat() {
        return (mix32(this.nextSeed()) >>> 8) * 5.9604645E-8f;
    }

    public boolean nextBoolean() {
        return mix32(this.nextSeed()) < 0;
    }

    private long nextSeed() {
        return this.seed += this.gamma;
    }

    private static int mix32(long z) {
        z = (z ^ z >>> 33) * 7109453100751455733L;
        return (int)((z ^ z >>> 28) * -3808689974395783757L >>> 32);
    }

    private static long mixGamma(long z) {
        z = (z ^ z >>> 33) * -49064778989728563L;
        z = (z ^ z >>> 33) * -4265267296055464877L;
        z = ((z ^ z >>> 33) | 0x1L);
        int n = Long.bitCount(z ^ z >>> 1);
        return (n < 24) ? (z ^ 0xAAAAAAAAAAAAAAAAL) : z;
    }
}
