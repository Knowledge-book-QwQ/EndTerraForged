/*
 * Copyright (c) 2023 ReTerraForged
 *
 * EndTerraForged original (LGPL-3.0-or-later). No RTF equivalent — RTF's
 * climate affects biome selection, not terrain height directly. This module
 * is the End's stage-2.5b "climate as optional heightmap modulator": a thin
 * post-process layer that nudges terrain height based on EndClimate channels,
 * keeping the Noise tree untouched.
 */
package endterraforged.world.climate;

import endterraforged.world.heightmap.EndHeightmap;
import endterraforged.world.heightmap.EndLevels;
import endterraforged.world.noise.NoiseMath;

/**
 * Optional climate-driven terrain modulator: nudges the terrain height at
 * {@code (x, z)} based on {@link EndClimate} channels, applied as a
 * post-process on top of the raw continent × mountains field.
 *
 * <p><b>Model.</b> The modulator scales the terrain's <em>elevation above
 * surface</em> by a factor centred at 1.0:</p>
 * <pre>
 *   modulation = 1 + coldBoost * (1 - temperature) - wetErosion * moisture
 *   height = surface + (inputHeight - surface) * clamp(modulation, minScale, maxScale)
 * </pre>
 *
 * <p>Cold regions ({@code temperature → 0}) boost elevation (glacial buildup
 * imagery); wet regions ({@code moisture → 1}) lower it (erosion imagery).
 * Both are small amplitudes by default so the underlying continent/mountain
 * shape remains recognisable. The modulation is clamped to
 * {@code [minScale, maxScale]} so extreme climate samples cannot flatten or
 * double the terrain.</p>
 *
 * <p><b>Decoupling.</b> Reads only {@link EndClimate} channels and
 * {@link EndLevels#surface}; does not touch Continent landness, so it can be
 * queried on void columns too (where it is a no-op by contract — see
 * {@link #modulate}). Rivers/lakes run after this in the EndHeightmap chain,
 * so they carve the climate-modulated terrain.</p>
 *
 * <p><b>Thread safety.</b> Immutable record; safe from parallel chunk-gen.</p>
 *
 * @param climate      the climate field to sample
 * @param coldBoost    elevation boost per unit coldness (1 - temperature);
 *                     0 = no cold effect
 * @param wetErosion   elevation loss per unit moisture; 0 = no erosion effect
 * @param minScale     lower clamp on the modulation factor
 * @param maxScale     upper clamp on the modulation factor
 */
public record ClimateModulator(EndClimate climate, float coldBoost,
                               float wetErosion, float minScale, float maxScale) {

    /** Conservative defaults: subtle nudges, clamped to [0.85, 1.15]. */
    public static ClimateModulator defaults(EndClimate climate) {
        return new ClimateModulator(climate, 0.10F, 0.08F, 0.85F, 1.15F);
    }

    /**
     * Modulates the input height at {@code (x, z)}: scales the elevation
     * above {@code surface} by a climate-driven factor.
     *
     * <p>On void columns (where {@code inputHeight <= surface}), this is a
     * no-op — there is no elevation above surface to modulate, so the
     * modulator returns {@code inputHeight} unchanged. This keeps the
     * modulator safe to chain before rivers/lakes without punching holes
     * into the void threshold.</p>
     *
     * @param x            world X
     * @param z            world Z
     * @param seed         world seed
     * @param levels       the dimension's EndLevels (for surface reference)
     * @param inputHeight  the upstream height at {@code (x, z)}
     * @return modulated height; unchanged {@code inputHeight} on void / surface
     */
    public float modulate(float x, float z, int seed, EndLevels levels, float inputHeight) {
        // No elevation above surface → nothing to modulate. This also covers
        // void columns (where inputHeight == surface) and prevents the
        // modulator from pushing surface below itself.
        if (inputHeight <= levels.surface) {
            return inputHeight;
        }
        float temperature = climate.getTemperature(x, z, seed);
        float moisture = climate.getMoisture(x, z, seed);
        float modulation = 1.0F
                + coldBoost * (1.0F - temperature)
                - wetErosion * moisture;
        modulation = NoiseMath.clamp(modulation, minScale, maxScale);
        float elevation = inputHeight - levels.surface;
        // Clamp to [surface, 1.0]: the modulator must not push terrain above
        // the world ceiling (1.0) — that would create solid pillars in
        // EndDensity (heightNorm > 1.0 is never exceeded, so the whole column
        // turns solid). Also must not push below surface (handled by the early
        // return above, but double-guard here for safety).
        float result = levels.surface + elevation * modulation;
        return NoiseMath.clamp(result, levels.surface, 1.0F);
    }
}
