/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). RTF (MIT) only models
 * a continuous continent+ocean; EndTerraForged adds an explicit topology
 * switch because the End is conceptually archipelago rather than mainland.
 */
package endterraforged.world.config;

/**
 * The macro shape of the End's landmass.
 *
 * <p>One of the three orthogonal switches (with {@link SeaMode} and
 * {@code FloatingIslands}). Selects which continent module the heightmap
 * assembles, which in turn determines whether the world reads as a shattered
 * supercontinent or a scatter of free-floating islands.</p>
 *
 * <p><b>Thread safety:</b> enum, inherently immutable and safe to share.</p>
 */
public enum TopologyMode {
    /**
     * A continuous "continent" height-field, cut by void/rift noise into a
     * shattered supercontinent. Visually closest to a torn-up End mainland.
     * Backed by a {@code ContinentalShatteredContinent} module (stage 2.3).
     */
    CONTINENTAL_SHATTERED,

    /**
     * Discrete floating islands separated by void. Closest to vanilla End
     * outer islands, but more dramatic. Backed by an {@code IslandsContinent}
     * module (stage 2.3) that emits an island-distance field.
     */
    ISLANDS
}
