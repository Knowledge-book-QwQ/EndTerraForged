/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's WorleyEdge (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged.
 *
 * Pure-algorithm adaptation: drops the MapCodec/DFU serialisation surface;
 * the dual-nearest cell scan and edge normalisation are byte-identical to
 * upstream. Reuses the already-ported NoiseMath.cell / map helpers.
 */
package endterraforged.world.noise;

/**
 * Worley <em>edge</em> noise: like {@link Worley} but combines the nearest
 * <em>and</em> second-nearest feature-point distances via an {@link EdgeFunction},
 * producing ridge/crease lines along cell boundaries.
 *
 * <p>This is the core of RTF's shattered-ridge mountain variants
 * ({@code MOUNTAINS_2}/{@code MOUNTAINS_3}): {@link EdgeFunction#DISTANCE_2}
 * with {@link DistanceFunction#EUCLIDEAN} yields sharp ridges tracing the
 * Voronoi cell borders, which — once warped and terraced — read as broken
 * mountain spines rather than the smooth billows of fBm.</p>
 *
 * <p>Output is normalised into {@code [0,1]} against the
 * {@link EdgeFunction}'s {@code [min, max]}.</p>
 *
 * <p><b>Faithfulness.</b> The 3x3 scan tracking both {@code nearest1} and
 * {@code nearest2}, the {@link EdgeFunction#apply} combination and the
 * {@code NoiseUtil.map(value, edge.min, edge.max, edge.range)} normalisation
 * are all byte-identical to upstream.</p>
 *
 * <p><b>Thread safety:</b> immutable record; safe to query from multiple
 * threads.</p>
 */
public record WorleyEdge(float frequency, float distance, EdgeFunction edgeFunction,
                         DistanceFunction distanceFunction) implements Noise {

    /**
     * Convenience constructor with the upstream-default feature-point spread
     * of {@code 1.0} (point roams the whole cell).
     */
    public WorleyEdge(float frequency, EdgeFunction edgeFunction, DistanceFunction distanceFunction) {
        this(frequency, 1.0F, edgeFunction, distanceFunction);
    }

    @Override
    public float compute(float x, float z, int seed) {
        x *= this.frequency;
        z *= this.frequency;
        float value = sample(x, z, seed, this.distance, this.edgeFunction, this.distanceFunction);
        return NoiseMath.map(value, this.edgeFunction.min(), this.edgeFunction.max(), this.edgeFunction.range());
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
        // Pure leaf — no child noise to recurse into.
        return visitor.apply(this);
    }

    /**
     * Samples the worley-edge field at {@code (x, z)} (already frequency-scaled).
     * Exposed static so other cell-edge consumers can reuse the dual-nearest
     * scan without an instance.
     */
    public static float sample(float x, float y, int seed, float distance,
                               EdgeFunction edgeFunction, DistanceFunction distanceFunc) {
        int xi = NoiseMath.floor(x);
        int yi = NoiseMath.floor(y);
        float nearest1 = Float.MAX_VALUE;
        float nearest2 = Float.MAX_VALUE;
        for (int dy = -1; dy <= 1; ++dy) {
            for (int dx = -1; dx <= 1; ++dx) {
                int cx = xi + dx;
                int cy = yi + dy;
                NoiseMath.Vec2f vec = NoiseMath.cell(seed, cx, cy);
                float deltaX = cx + vec.x() * distance - x;
                float deltaY = cy + vec.y() * distance - y;
                float dist = distanceFunc.apply(deltaX, deltaY);
                if (dist < nearest1) {
                    nearest2 = nearest1;
                    nearest1 = dist;
                } else if (dist < nearest2) {
                    nearest2 = dist;
                }
            }
        }
        return edgeFunction.apply(nearest1, nearest2);
    }
}
