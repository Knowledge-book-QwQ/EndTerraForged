/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's CurveFunction (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged.
 *
 * Pure-algorithm adaptation: drops the Codec/RTFBuiltInRegistries
 * serialisation surface; the single {@code apply} method is unchanged.
 */
package endterraforged.world.noise;

/**
 * A 1D curve applied to an interpolation fraction (or any {@code [0,1]} value)
 * to shape gradient-noise smoothness.
 *
 * <p>Implemented by {@link Interpolation} (LINEAR / CURVE3 / CURVE4) and, in
 * upstream, by SCurve / Terrace variants. Kept as a separate interface so a
 * noise module can accept any curve without depending on a specific enum.</p>
 */
public interface CurveFunction {

    /**
     * Maps {@code f} (nominally in {@code [0,1]}) through the curve.
     *
     * @param f the input fraction
     * @return the curved fraction, in {@code [0,1]} for the standard variants
     */
    float apply(float f);
}
