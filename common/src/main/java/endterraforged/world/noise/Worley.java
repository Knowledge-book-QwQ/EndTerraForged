/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Worley (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged.
 *
 * Pure-algorithm adaptation: drops the MapCodec/DFU serialisation surface
 * (a stage-3 bridge concern); the cell-noise sampling core (compute, sample,
 * min/max bound derivation) is byte-identical to upstream so output stays the
 * same. Reuses the already-ported NoiseMath.cell / valCoord2D / map helpers,
 * which are themselves byte-faithful to upstream's NoiseUtil.
 */
package endterraforged.world.noise;

/**
 * Worley (cell) noise: tiles the plane with a square grid of feature points,
 * and at each query returns a value derived from the <em>nearest</em> feature
 * point.
 *
 * <p>The feature point inside each integer cell is {@link NoiseMath#cell} — a
 * hash-indexed entry from the precomputed {@code CELL_2D} table, so the field
 * is deterministic and reproducible. The returned value depends on
 * {@link CellFunction}: a per-cell hash ({@link CellFunction#CELL_VALUE}), a
 * secondary noise sampled at the feature point ({@link CellFunction#NOISE_LOOKUP}),
 * or the raw distance ({@link CellFunction#DISTANCE}).</p>
 *
 * <p>Output is normalised into {@code [0,1]} against the per-variant
 * {@code [min, max]} bound, except {@link CellFunction#NOISE_LOOKUP} which
 * passes the lookup's own range through unchanged.</p>
 *
 * <p><b>Faithfulness.</b> The 3x3 neighbourhood scan, the
 * {@code cx + vec.x*distance - x} delta, the {@link DistanceFunction} ranking
 * and the {@link CellFunction#mapValue} normalisation are all byte-identical
 * to upstream. {@code distance} defaults to {@code 1.0F} in the common case
 * (feature point anywhere in the cell); lower values shrink the feature-point
 * cloud toward cell centres.</p>
 *
 * <p><b>Thread safety:</b> immutable record; the {@code lookup} noise must
 * itself be thread-safe. Safe to query from multiple threads.</p>
 */
public record Worley(float frequency, float distance, CellFunction cellFunction,
                     DistanceFunction distanceFunction, Noise lookup,
                     float min, float max) implements Noise {

    /**
     * @param frequency        spatial frequency (higher = smaller cells)
     * @param distance         in-cell feature-point spread in {@code (0,1]};
     *                         {@code 1.0} lets the point roam the whole cell
     * @param cellFunction     what to derive from the winning cell
     * @param distanceFunction how to measure query-to-feature distance
     * @param lookup           secondary noise, used only by {@link CellFunction#NOISE_LOOKUP};
     *                         ignored (may be {@code null}) for other variants
     */
    public Worley(float frequency, float distance, CellFunction cellFunction,
                  DistanceFunction distanceFunction, Noise lookup) {
        this(frequency, distance, cellFunction, distanceFunction, lookup,
                min(cellFunction, lookup), max(cellFunction, lookup));
    }

    @Override
    public float compute(float x, float z, int seed) {
        x *= this.frequency;
        z *= this.frequency;
        float value = sample(x, z, seed, this.distance, this.cellFunction, this.distanceFunction, this.lookup);
        return this.cellFunction.mapValue(value, this.min, this.max, this.max - this.min);
    }

    @Override
    public float minValue() {
        return 0.0F;
    }

    @Override
    public float maxValue() {
        return 1.0F;
    }

    @Override
    public Noise mapAll(Visitor visitor) {
        // Leaf w.r.t. the cell kernel, but the NOISE_LOOKUP variant carries a
        // child noise — recurse into it so a tree-wide visitor reaches it.
        Noise lookup = this.lookup == null ? null : this.lookup.mapAll(visitor);
        return visitor.apply(new Worley(this.frequency, this.distance, this.cellFunction,
                this.distanceFunction, lookup, this.min, this.max));
    }

    /**
     * Samples the worley field at {@code (x, z)} (already frequency-scaled).
     * Exposed static so other cell-noise consumers (e.g. a future volcano
     * populator) can reuse the 3x3 scan without an instance.
     */
    public static float sample(float x, float y, int seed, float distance,
                               CellFunction cellFunction, DistanceFunction distanceFunction, Noise lookup) {
        int xi = NoiseMath.floor(x);
        int yi = NoiseMath.floor(y);
        int cellX = xi;
        int cellY = yi;
        NoiseMath.Vec2f vec2f = null;
        float nearest = Float.MAX_VALUE;
        for (int dy = -1; dy <= 1; ++dy) {
            for (int dx = -1; dx <= 1; ++dx) {
                int cx = xi + dx;
                int cy = yi + dy;
                NoiseMath.Vec2f vec = NoiseMath.cell(seed, cx, cy);
                float deltaX = cx + vec.x() * distance - x;
                float deltaY = cy + vec.y() * distance - y;
                float dist = distanceFunction.apply(deltaX, deltaY);
                if (dist < nearest) {
                    nearest = dist;
                    vec2f = vec;
                    cellX = cx;
                    cellY = cy;
                }
            }
        }
        return cellFunction.apply(seed, cellX, cellY, nearest, vec2f, lookup);
    }

    private static float min(CellFunction function, Noise lookup) {
        if (function == CellFunction.NOISE_LOOKUP) {
            return lookup.minValue();
        }
        return -1.0F;
    }

    private static float max(CellFunction function, Noise lookup) {
        if (function == CellFunction.NOISE_LOOKUP) {
            return lookup.maxValue();
        }
        if (function == CellFunction.DISTANCE) {
            return 0.25F;
        }
        return 1.0F;
    }
}
