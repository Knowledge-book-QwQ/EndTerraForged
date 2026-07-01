/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Interpolation (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged.
 *
 * Pure-algorithm adaptation: drops the StringRepresentable/Codec serialisation
 * surface; the three curve implementations delegate to NoiseMath's
 * interpHermite / interpQuintic, which are byte-identical to upstream's
 * NoiseUtil equivalents.
 */
package endterraforged.world.noise;

/**
 * The interpolation curve applied to per-cell gradient fractions inside
 * {@link Perlin} (and other gradient noises).
 *
 * <p>{@link #LINEAR} is cheapest and visibly blocky; {@link #CURVE3}
 * (Hermite, {@code 3t^2 - 2t^3}) gives C1 continuity; {@link #CURVE4}
 * (quintic, {@code 6t^5 - 15t^4 + 10t^3}) gives C2 continuity and is the
 * upstream default for most terrain noises — it eliminates the faint grid
 * artefacts that CURVE3 still shows at high octaves.</p>
 */
public enum Interpolation implements CurveFunction {
    /** No curvature — raw linear lerp. Visible grid artefacts; rarely used. */
    LINEAR {
        @Override
        public float apply(float f) {
            return f;
        }
    },
    /** Hermite cubic {@code t*t*(3-2t)} — C1 continuous. */
    CURVE3 {
        @Override
        public float apply(float f) {
            return NoiseMath.interpHermite(f);
        }
    },
    /** Quintic {@code t^3*(t*(t*6-15)+10)} — C2 continuous, upstream default. */
    CURVE4 {
        @Override
        public float apply(float f) {
            return NoiseMath.interpQuintic(f);
        }
    }
}
