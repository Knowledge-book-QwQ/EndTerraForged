/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's CellFunction (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged.
 *
 * Pure-algorithm adaptation: drops the StringRepresentable/Codec serialisation
 * surface; the cell-value transforms and the per-variant mapValue overrides
 * are unchanged.
 */
package endterraforged.world.noise;

/**
 * What {@link Worley} returns for the winning cell: a per-cell hash value, a
 * noise lookup at the feature point, or the raw distance.
 *
 * <p>{@link #mapValue} controls how the raw {@code apply} output is normalised
 * into {@code [0,1]} (or, for {@link #NOISE_LOOKUP}, left untouched since the
 * lookup already carries its own range). Preserved verbatim from upstream.</p>
 */
public enum CellFunction {
    /** Returns a hash of the winning cell's integer coords — flat per-cell tiles. */
    CELL_VALUE {
        @Override
        public float apply(int seed, int xc, int yc, float distance, NoiseMath.Vec2f vec2f, Noise lookup) {
            return NoiseMath.valCoord2D(seed, xc, yc);
        }
    },
    /**
     * Samples {@code lookup} at the feature point — lets a second noise field
     * vary smoothly per cell. Output is <em>not</em> normalised (the lookup
     * owns its range), so {@link #mapValue} is the identity here.
     */
    NOISE_LOOKUP {
        @Override
        public float apply(int seed, int xc, int yc, float distance, NoiseMath.Vec2f vec2f, Noise lookup) {
            return lookup.compute(xc + vec2f.x(), yc + vec2f.y(), seed);
        }

        @Override
        public float mapValue(float value, float min, float max, float range) {
            return value;
        }
    },
    /**
     * Returns {@code distance - 1} — concentric falloff from cell centres.
     * {@link #mapValue} collapses to 0 because Worley's distance variant is
     * consumed only via its edge sibling ({@link WorleyEdge}).
     */
    DISTANCE {
        @Override
        public float apply(int seed, int xc, int yc, float distance, NoiseMath.Vec2f vec2f, Noise lookup) {
            return distance - 1.0F;
        }

        @Override
        public float mapValue(float value, float min, float max, float range) {
            return 0.0F;
        }
    };

    /**
     * Produces the raw value for the winning cell.
     *
     * @param seed     world/dimension seed
     * @param xc        winning cell's integer X
     * @param yc        winning cell's integer Y
     * @param distance  distance from the query point to the cell's feature point
     * @param vec2f     the feature point's in-cell offset (already selected as nearest)
     * @param lookup    secondary noise source, used only by {@link #NOISE_LOOKUP}
     */
    public abstract float apply(int seed, int xc, int yc, float distance, NoiseMath.Vec2f vec2f, Noise lookup);

    /**
     * Normalises the raw {@code apply} output into the module's output range.
     * Default is upstream's {@code NoiseUtil.map}; {@link #NOISE_LOOKUP} and
     * {@link #DISTANCE} override.
     */
    public float mapValue(float value, float min, float max, float range) {
        return NoiseMath.map(value, min, max, range);
    }
}
