/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Levels (MIT) into EndTerraForged (LGPL-3.0-or-later).
 * Lineage: TerraForged (dags) -> ReTerraForged (raccoonman) -> EndTerraForged.
 *
 * EndTerraForged change: upstream Levels is built from (worldHeight, seaLevel)
 * and assumes a sea. Here the reference surface is taken from a
 * DimensionProfile, so the same math works for SeaMode.NONE (baseline =
 * island floor) and for sea modes (baseline = sea level), without the
 * algorithm layer having to know which.
 */
package endterraforged.world.heightmap;

import endterraforged.util.NoiseUtil;
import endterraforged.world.config.DimensionProfile;

/**
 * Normalised level math for a dimension: converts between world Y and the
 * {@code [0,1]} height units used throughout the heightfield pipeline.
 *
 * <p>Two reference points matter to consumers: {@code surface} (the waterline
 * in sea modes, or the island baseline in {@link endterraforged.world.config.SeaMode#NONE})
 * and {@code ground} (one block above the surface, where sub-aerial terrain
 * begins). Erosion's height-band {@link endterraforged.world.filter.Modifier}
 * is anchored to {@code ground}, so a single {@code EndLevels} instance makes
 * the same erosion filter work regardless of whether the End is flooded.</p>
 *
 * <p><b>Thread safety:</b> all fields are primitives set at construction;
 * safe to share across parallel tile generators.</p>
 */
public class EndLevels {
    public final int worldHeight;
    public final float unit;

    /** World Y of the reference surface (sea level or island baseline). */
    public final int surfaceY;
    /** World Y of the first sub-surface block (surfaceY - 1), clamped to the world. */
    public final int surfaceFillY;
    /** World Y of the first above-surface block (surfaceY + 1), clamped to the world. */
    public final int groundY;

    /** Normalised surface height in {@code [0,1]} world units. */
    public final float surface;
    /** Normalised ground height in {@code [0,1]} world units. */
    public final float ground;
    /** Normalised span from the surface up to the world top; inverse of {@link #surface}. */
    public final float elevationRange;

    public EndLevels(DimensionProfile profile) {
        this.worldHeight = Math.max(1, profile.worldHeight());
        this.unit = NoiseUtil.div(1, this.worldHeight);
        this.surfaceY = profile.surfaceY();
        // mirror upstream's "fill to Y-1 / ground starts at Y+1" convention,
        // clamped so a degenerate profile cannot produce out-of-range indices.
        this.surfaceFillY = Math.min(this.surfaceY - 1, this.worldHeight);
        this.groundY = Math.min(this.surfaceY + 1, this.worldHeight);
        this.surface = NoiseUtil.div(this.surfaceFillY, this.worldHeight);
        this.ground = NoiseUtil.div(this.groundY, this.worldHeight);
        this.elevationRange = 1.0F - this.surface;
    }

    /** Converts a normalised {@code [0,1]} height to a world Y. */
    public int scale(float value) {
        return (int) (value * this.worldHeight);
    }

    /** Converts a world Y to a normalised {@code [0,1]} height. */
    public float scale(int level) {
        return NoiseUtil.div(level, this.worldHeight);
    }

    /**
     * Elevation of a normalised height above the surface, in {@code [0,1]}.
     * Returns {@code 0} at or below the surface so underwater/below-baseline
     * terrain contributes no elevation.
     */
    public float elevation(float value) {
        if (value <= this.surface) {
            return 0.0F;
        }
        return (value - this.surface) / this.elevationRange;
    }

    /**
     * Elevation of a world Y above the surface, in {@code [0,1]}.
     * Returns {@code 0} at or below {@link #surfaceFillY}.
     */
    public float elevation(int y) {
        if (y <= this.surfaceFillY) {
            return 0.0F;
        }
        return this.scale(y - this.surfaceFillY) / this.elevationRange;
    }

    /** Normalised height {@code surfaceFillY + amount} blocks above the surface fill line. */
    public float surface(int amount) {
        return NoiseUtil.div(this.surfaceFillY + amount, this.worldHeight);
    }

    /** Normalised height {@code groundY + amount} blocks above the ground line. */
    public float ground(int amount) {
        return NoiseUtil.div(this.groundY + amount, this.worldHeight);
    }
}
