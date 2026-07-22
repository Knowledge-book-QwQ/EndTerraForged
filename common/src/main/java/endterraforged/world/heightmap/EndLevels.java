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
    public final int minY;
    public final int worldHeight;
    public final int maxYExclusive;
    public final int maxY;
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
        this.minY = profile.minY();
        this.worldHeight = Math.max(1, profile.worldHeight());
        this.maxYExclusive = Math.addExact(this.minY, this.worldHeight);
        this.maxY = this.maxYExclusive - 1;
        this.unit = NoiseUtil.div(1, this.worldHeight);
        this.surfaceY = Math.clamp(profile.surfaceY(), this.minY, this.maxY);
        // mirror upstream's "fill to Y-1 / ground starts at Y+1" convention,
        // clamped so a degenerate profile cannot produce out-of-range indices.
        this.surfaceFillY = Math.clamp(this.surfaceY - 1, this.minY, this.maxY);
        this.groundY = Math.clamp(this.surfaceY + 1, this.minY, this.maxY);
        this.surface = scale(this.surfaceFillY);
        this.ground = scale(this.groundY);
        this.elevationRange = 1.0F - this.surface;
    }

    /** Converts a normalised {@code [0,1]} height to a world Y. */
    public int scale(float value) {
        return this.minY + (int) (value * this.worldHeight);
    }

    /** Converts a world Y to a normalised {@code [0,1]} height. */
    public float scale(int level) {
        return NoiseUtil.div(level - this.minY, this.worldHeight);
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
        return NoiseUtil.div(y - this.surfaceFillY, this.worldHeight) / this.elevationRange;
    }

    /** Normalised height {@code surfaceFillY + amount} blocks above the surface fill line. */
    public float surface(int amount) {
        return scale(this.surfaceFillY + amount);
    }

    /** Normalised height {@code groundY + amount} blocks above the ground line. */
    public float ground(int amount) {
        return scale(this.groundY + amount);
    }
}
