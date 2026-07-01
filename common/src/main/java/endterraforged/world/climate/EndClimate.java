/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). RTF (MIT) has no
 * direct equivalent — RTF climate is a dual-track system (self-hosted cell
 * climate + vanilla/TerraBlender MultiNoiseBiomeSource adapter) that depends
 * on a sea-land Continent for moisture modulation and on a MultiNoise biome
 * source for output. The End has neither: vanilla End uses the_end biome
 * source (geometric banding, no climate reads), and SeaMode.NONE has no sea.
 *
 * Therefore this module takes the "climate as independent Noise field" route
 * documented in ROADMAP stage 2.5a: temperature / moisture / wind are
 * self-contained [0,1] Noise nodes, decoupled from Continent / Levels. They
 * are queryable by downstream systems (stage 2.5b heightmap modulation,
 * stage 3.5 biome sub-type selection) but no consumer is required.
 */
package endterraforged.world.climate;

import endterraforged.world.noise.Noise;
import endterraforged.world.noise.Noises;

/**
 * The End's climate field: three independent {@code [0,1]} noise channels —
 * temperature, moisture, wind — that downstream systems can sample to vary
 * biome sub-types, surface rules, or heightmap modulation.
 *
 * <p><b>Temperature.</b> A latitude-like model adapted to the End's
 * radial-void layout: a base band that is hot near the world origin
 * ({@code (0,0)}) and cools toward {@link #climateRadius}. This mirrors how
 * vanilla End biomes band by distance from origin (small end islands → end
 * midlands → end barrens → end highlands), but as a continuous field rather
 * than discrete rings. A simplex perturbation breaks the perfect radial
 * symmetry so isotherms wiggle rather than form concentric circles.</p>
 *
 * <p><b>Moisture.</b> A standalone simplex field, independent of temperature,
 * so a hot-dry cell and a hot-wet cell can coexist (unlike a single-axis
 * temperature→moisture coupling). End moisture is not anchored to sea
 * proximity (RTF's {@code modifyMoisture(continentEdge)} is sea-coupled and
 * N/A here).</p>
 *
 * <p><b>Wind.</b> A standalone simplex field reserved for biome sub-type
 * selection and surface-rule variation (e.g. wind-exposed ridges get thinner
 * soil). No effect yet; included so the field is queryable when stage 3.5
 * needs it.</p>
 *
 * <p><b>Decoupling.</b> None of the three channels reads Continent landness,
 * EndLevels surface, or SeaMode. A consumer that wants "only on land" must
 * gate on {@link endterraforged.world.heightmap.EndHeightmap#getLandness}
 * itself. This keeps Climate a pure independent Noise module per the
 * ROADMAP 2.5a decision.</p>
 *
 * <p><b>Thread safety.</b> Immutable; the underlying {@link Noise} trees are
 * immutable. Safe to query from parallel chunk-gen threads.</p>
 *
 * @param seed             world seed (drives all three channels via seed
 *                         offsets so they are independent)
 * @param climateRadius    world-units radius over which temperature falls from
 *                         1 (origin) to 0 (edge); larger = gentler gradient
 * @param temperatureScale simplex perturbation scale in blocks; smaller =
 *                         tighter isotherm wiggle
 * @param moistureScale    simplex scale in blocks for the moisture field
 * @param windScale        simplex scale in blocks for the wind field
 * @param perturbation     amplitude of the simplex perturbation added to the
 *                         temperature base band, in {@code [0,1]}
 */
public record EndClimate(Noise temperature, Noise moisture, Noise wind,
                         float climateRadius, float perturbation) {

    /** Reasonable End-tuned defaults: 4000-block thermal radius, modest perturbation. */
    public static EndClimate defaults(int seed) {
        return new EndClimate(seed, 4000.0F, 600, 800, 1000, 0.25F);
    }

    /**
     * Full constructor: assembles the three noise channels.
     *
     * <p>Temperature = clamp(baseBand + simplexPerturbation, 0, 1), where
     * baseBand = 1 - distance(origin)/climateRadius (mapped to [0,1]) and
     * simplexPerturbation is a simplex in [-perturbation, +perturbation]. The
     * distance uses the warp-free radial distance; a small domain warp on the
     * perturbation field keeps the isotherms from looking too regular.</p>
     *
     * <p>Moisture and wind are simplex fields mapped to [0,1].</p>
     */
    public EndClimate(int seed, float climateRadius,
                      int temperatureScale, int moistureScale, int windScale,
                      float perturbation) {
        this(
                buildTemperature(seed, climateRadius, temperatureScale, perturbation),
                Noises.map(Noises.simplex(seed + 700, moistureScale, 4), 0.0F, 1.0F),
                Noises.map(Noises.simplex(seed + 800, windScale, 4), 0.0F, 1.0F),
                climateRadius,
                perturbation
        );
    }

    /**
     * Samples the temperature channel at {@code (x, z)}.
     *
     * @return temperature in {@code [0,1]} (1 = hot / near origin, 0 = cold / far rim)
     */
    public float getTemperature(float x, float z, int seed) {
        return temperature.compute(x, z, seed);
    }

    /**
     * Samples the moisture channel at {@code (x, z)}.
     *
     * @return moisture in {@code [0,1]} (1 = wet, 0 = dry); independent of temperature
     */
    public float getMoisture(float x, float z, int seed) {
        return moisture.compute(x, z, seed);
    }

    /**
     * Samples the wind channel at {@code (x, z)}.
     *
     * @return wind in {@code [0,1]} (1 = windy, 0 = calm); reserved for biome/surface variation
     */
    public float getWind(float x, float z, int seed) {
        return wind.compute(x, z, seed);
    }

    /**
     * Builds the temperature channel: base radial band + simplex perturbation,
     * clamped to {@code [0,1]}.
     *
     * <p>The base band is {@code 1 - dist/climateRadius}, computed analytically
     * (no Noise tree needed for the radial part — it is a pure function of
     * (x, z)). The perturbation is a warped simplex so isotherms wiggle
     * naturally. The sum is clamped to [0,1] to absorb perturbation overshoot
     * at the extremes.</p>
     */
    private static Noise buildTemperature(int seed, float climateRadius,
                                          int perturbScale, float perturbation) {
        // Perturbation: warped simplex in [-1,1], scaled to [-perturbation, +perturbation].
        Noise perturbField = Noises.warpPerlin(
                Noises.simplex(seed + 600, perturbScale, 4),
                seed + 601, perturbScale * 2, 3, 40.0F);
        Noise perturbScaled = Noises.mul(
                Noises.map(perturbField, -1.0F, 1.0F),
                perturbation);
        // Base band is a RadialBand Noise (computes 1 - dist/radius at sample
        // time). Add the perturbation, then clamp to [0,1].
        Noise base = new RadialBand(climateRadius);
        return Noises.clamp(Noises.add(base, perturbScaled), 0.0F, 1.0F);
    }
}
