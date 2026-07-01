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
 * all, and whether the region below sea level is solid ground or void.</p>
 *
 * <p><b>Thread safety:</b> enum, inherently immutable and safe to share.</p>
 */
public enum SeaMode {
    /**
     * No sea. {@code seaLevel} is ignored; terrain below the island baseline is
     * void. Erosion is anchored to the island baseline instead of a waterline.
     * This is the most "vanilla-End-like" mode.
     */
    NONE,

    /**
     * Sea with a continuous floor. Sea level is honoured and the ground
     * extends below it as a seabed — the closest analogue to RTF's overworld
     * behaviour, transplanted into the End.
     */
    WITH_FLOOR,

    /**
     * Sea with no floor. Sea level is honoured as a surface, but anything below
     * it is void; land exists only above the waterline, so the result is
     * floating islands suspended over an endless sea. The most spectacular /
     * surreal combination.
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
     * {@code true} when the dimension has a continuous solid floor below the
     * reference surface (i.e. a seabed). {@code WITH_FLOOR} only; {@code NONE}
     * and {@code NO_FLOOR} both leave void below the surface — {@code NONE}
     * because islands float over nothing, {@code NO_FLOOR} because the sea
     * has no bed.
     *
     * <p>This is the single switch {@code EndDensity} uses to decide whether
     * to keep filling solid below the surface or to carve void.</p>
     */
    public boolean hasFloor() {
        return this == WITH_FLOOR;
    }
}
