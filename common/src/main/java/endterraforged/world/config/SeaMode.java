/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later).
 * Informed by ReTerraForged's Levels/seaLevel handling (MIT), but the three-
 * state sea model is an EndTerraForged extension: RTF only ever has a sea,
 * whereas the End may optionally be flooded.
 */
package endterraforged.world.config;

/**
 * How the End dimension treats its sea level.
 *
 * <p>This is one of the three orthogonal switches (with {@link TopologyMode}
 * and {@code FloatingIslands}) that together describe the dimension's shape.
 * It controls whether {@code seaLevel} participates in height/level math at
 * all, how landmass columns treat the region below sea level, and whether
 * exterior columns receive a seabed. A finite
 * {@link LandmassVolumeMode#FLOATING_SHELF} still owns each continent's
 * underside; WITH_FLOOR adds a separate floor only outside those landmasses.</p>
 *
 * <p><b>Thread safety:</b> enum, inherently immutable and safe to share.</p>
 */
public enum SeaMode {
    /**
     * No sea. {@code seaLevel} is ignored; terrain below the island baseline is
     * void when {@link LandmassVolumeMode#LEGACY_COLUMN} is active. Erosion is
     * anchored to the island baseline instead of a waterline. This is the most
     * "vanilla-End-like" mode.
     */
    NONE,

    /**
     * Sea with a continuous exterior floor. Sea level is honoured and a
     * low-frequency seabed closes open-ocean columns below it.
     */
    WITH_FLOOR,

    /**
     * Sea with no floor. Sea level is honoured as a surface, but anything below
     * it remains negative density; the exterior fluid picker fills that space
     * with water while finite shelves keep their configured underside.
     */
    NO_FLOOR;

    /** {@code true} when {@code seaLevel} should participate in level math. */
    public boolean hasSea() {
        return this != NONE;
    }

    /** {@code true} when ground below sea level must be voided in post-processing. */
    public boolean voidsBelowSea() {
        return this == NO_FLOOR;
    }

    /**
     * {@code true} when the dimension has a continuous solid exterior seabed.
     * {@code WITH_FLOOR} only; {@code NONE} and {@code NO_FLOOR} leave exterior
     * density empty below the surface.
     *
     * <p>Finite landmasses retain their own underside independently.</p>
     */
    public boolean hasFloor() {
        return this == WITH_FLOOR;
    }
}
