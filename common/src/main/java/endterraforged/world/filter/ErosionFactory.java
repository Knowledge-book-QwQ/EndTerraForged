/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original (LGPL-3.0-or-later). Replaces RTF's
 * WorldFilters/Erosion factory (MIT) which hardcoded
 * {@code Modifier.range(levels.ground, levels.ground(15))} against the
 * overworld sea level; here the same ground band is derived from a
 * DimensionProfile so it anchors to either sea level or island baseline.
 */
package endterraforged.world.filter;

import endterraforged.world.config.DimensionProfile;
import endterraforged.world.heightmap.EndLevels;

/**
 * Builds {@link Erosion} instances bound to a dimension's ground band.
 *
 * <p>The factory exists so the erosion algorithm itself stays free of
 * {@link DimensionProfile}/{@link EndLevels} knowledge: it only ever sees a
 * {@link Modifier}. The factory decides what height band that modifier
 * covers, derived from the profile's surface so the same {@code Erosion} class
 * erodes an End-with-a-sea (band above the waterline) and an End-with-no-sea
 * (band above the island baseline) identically.</p>
 *
 * <p>The default band is {@code [ground, ground+15]} blocks — the same 16-block
 * sub-aerial window RTF uses — expressed in normalised {@code [0,1]} units via
 * {@link EndLevels#ground(int)}.</p>
 *
 * <p><b>Thread safety:</b> stateless factory; safe to call from any thread.
 * The {@link Erosion} it produces is not thread-safe (see its javadoc).</p>
 */
public final class ErosionFactory {

    /** Default erosion band thickness above the ground line, in blocks. */
    public static final int DEFAULT_BAND_BLOCKS = 15;

    private ErosionFactory() {
    }

    /**
     * Builds an erosion filter whose strength ramps across the dimension's
     * default ground band.
     *
     * @param seed     world/dimension seed feeding droplet RNG
     * @param mapSize  tile edge length in cells (must match the {@link Filterable})
     * @param settings droplet tuning parameters
     * @param profile  dimension the filter will run against
     * @return a new, single-thread {@link Erosion} instance
     */
    public static Erosion create(int seed, int mapSize, ErosionConfig settings, DimensionProfile profile) {
        return create(seed, mapSize, settings, profile, DEFAULT_BAND_BLOCKS);
    }

    /**
     * Builds an erosion filter with an explicit ground-band thickness, for
     * presets that want a wider/narrower sub-aerial erosion window.
     */
    public static Erosion create(int seed, int mapSize, ErosionConfig settings,
                                 DimensionProfile profile, int bandBlocks) {
        EndLevels levels = new EndLevels(profile);
        // Ramp from the ground line up to ground+bandBlocks: full strength at
        // the surface, fading out above so high peaks keep their silhouette.
        Modifier modifier = Modifier.range(levels.ground(0), levels.ground(bandBlocks));
        return new Erosion(seed, mapSize, settings, modifier);
    }
}
