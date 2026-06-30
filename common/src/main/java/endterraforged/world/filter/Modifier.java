/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged (MIT) into EndTerraForged (LGPL-3.0-or-later).
 * Lineage: TerraForged (dags) -> ReTerraForged (raccoonman) -> EndTerraForged.
 *
 * Simplified from upstream: the original Modifier.modify default also scaled
 * erosion by terrain type and river proximity, which would pull in the Terrain
 * and river subsystems before they are ported. Stage 1 only needs height-band
 * scaling, so the terrain/river coupling is dropped here and will be revisited
 * once those subsystems exist.
 */
package endterraforged.world.filter;

import endterraforged.world.cell.Cell;

/**
 * Scales how strongly the erosion filter affects a cell, as a function of that
 * cell's height.
 *
 * <p>The erosion {@code Factory} builds a {@link #range(float, float)} modifier
 * bound to the dimension's ground band, so droplets only erode near the
 * surface rather than carving through the full height range. This keeps the
 * sea-level / island-baseline coupling localised to where the modifier is
 * constructed, not inside the erosion algorithm itself.</p>
 */
public interface Modifier {
    /**
     * Strength multiplier in {@code [0,1]} for a cell of the given height.
     * Returning {@code 0} fully protects the cell; {@code 1} applies full force.
     */
    float getValueModifier(float value);

    /**
     * Convenience wrapper applying the modifier to a cell's height. Kept as a
     * single hop so callers don't all reach into {@code cell.height}.
     */
    default float modify(Cell cell, float value) {
        return getValueModifier(cell.height) * value;
    }

    /** Logical inversion of the modifier ({@code 1 - f(v)}). */
    default Modifier invert() {
        return v -> 1.0F - this.getValueModifier(v);
    }

    /**
     * Linear ramp: {@code 0} below {@code min}, {@code 1} above {@code max},
     * linear in between. Used to confine erosion to a ground band.
     */
    static Modifier range(float minValue, float maxValue) {
        final float min = minValue;
        final float max = maxValue;
        final float range = maxValue - minValue;
        return new Modifier() {
            @Override
            public float getValueModifier(float value) {
                if (value > max) {
                    return 1.0F;
                }
                if (value < min) {
                    return 0.0F;
                }
                return (value - min) / range;
            }
        };
    }
}
