/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later).
 */
package endterraforged.world.continent;

import endterraforged.world.noise.DistanceFunction;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.NoiseMath;
import endterraforged.world.noise.domain.Domain;

/**
 * {@link endterraforged.world.config.TopologyMode#CONTINENTAL_SHATTERED}
 * continent: a continuous supercontinent torn into pieces by void rifts.
 *
 * <p>The dual-nearest cell scan follows R9.6's continent generator shape:
 * domain-warped Voronoi cells, configurable distance metric, jittered feature
 * points and optional skipped cells. The rift thresholding is End-specific so
 * this topology still reads as broken End land rather than an overworld ocean
 * continent.</p>
 *
 * @param frequency        rift-cell frequency ({@code 1/scale})
 * @param distance         local feature-point spread in cell units
 * @param distanceFunction distance metric used to rank feature points
 * @param jitter           random offset amount around the cell centre
 * @param skipping         probability that a winning cell is treated as void-scored
 * @param sizeVariance     local rift-threshold variation in {@code [0,1]}
 * @param riftThreshold    boundary strength above which void carving starts
 * @param riftStrength     how far landness drops inside a rift
 * @param warp             coordinate warp applied before the scan
 */
public record ContinentalShatteredContinent(float frequency, float distance,
                                            DistanceFunction distanceFunction,
                                            float jitter, float skipping, float sizeVariance,
                                            float riftThreshold, float riftStrength,
                                            Domain warp) implements Continent {

    @Override
    public float compute(float x, float z, int seed) {
        if (this.riftStrength <= 0.0F || this.riftThreshold >= 1.0F) {
            return 1.0F;
        }

        float wx = this.warp.getX(x, z, seed) * this.frequency;
        float wz = this.warp.getZ(x, z, seed) * this.frequency;

        CellEdge edge = scanEdge(wx, wz, seed);
        float boundary = edge.boundary();
        if (value01(seed + 0x632BE5AB, edge.cellX(), edge.cellY()) < this.skipping) {
            boundary = Math.max(boundary, 0.9F);
        }

        float variance = value01(seed + 0x85157AF5, edge.cellX(), edge.cellY()) - 0.5F;
        float localThreshold = NoiseMath.clamp(
                this.riftThreshold + variance * 0.35F * this.sizeVariance,
                0.0F, 1.0F);
        float span = 1.0F - localThreshold;
        float rift = span > 0.0F
                ? NoiseMath.clamp((boundary - localThreshold) / span, 0.0F, 1.0F)
                : 0.0F;

        return 1.0F - this.riftStrength * rift;
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
        return visitor.apply(new ContinentalShatteredContinent(this.frequency, this.distance,
                this.distanceFunction, this.jitter, this.skipping, this.sizeVariance,
                this.riftThreshold, this.riftStrength, this.warp.mapAll(visitor)));
    }

    private CellEdge scanEdge(float x, float y, int seed) {
        int xi = NoiseMath.floor(x);
        int yi = NoiseMath.floor(y);
        int cellX = xi;
        int cellY = yi;
        float nearest1 = Float.MAX_VALUE;
        float nearest2 = Float.MAX_VALUE;
        for (int dy = -1; dy <= 1; ++dy) {
            for (int dx = -1; dx <= 1; ++dx) {
                int cx = xi + dx;
                int cy = yi + dy;
                NoiseMath.Vec2f vec = NoiseMath.cell(seed, cx, cy);
                float dist = this.distanceFunction.apply(
                        cellFeature(cx, vec.x()) - x,
                        cellFeature(cy, vec.y()) - y);
                if (dist < nearest1) {
                    nearest2 = nearest1;
                    nearest1 = dist;
                    cellX = cx;
                    cellY = cy;
                } else if (dist < nearest2) {
                    nearest2 = dist;
                }
            }
        }

        float d1 = distanceToRadiusSpace(nearest1);
        float d2 = distanceToRadiusSpace(nearest2);
        float boundary = d2 > 0.0F ? NoiseMath.clamp(1.0F - (d2 - d1) * 1.5F, 0.0F, 1.0F) : 0.0F;
        return new CellEdge(cellX, cellY, boundary);
    }

    private float cellFeature(int cell, float random) {
        float centred = 0.5F + (random - 0.5F) * this.jitter;
        return cell + 0.5F + (centred - 0.5F) * this.distance;
    }

    private float distanceToRadiusSpace(float distance) {
        if (this.distanceFunction == DistanceFunction.EUCLIDEAN) {
            return (float) Math.sqrt(distance);
        }
        return distance;
    }

    private static float value01(int seed, int x, int y) {
        return NoiseMath.clamp((NoiseMath.valCoord2D(seed, x, y) + 1.0F) * 0.5F, 0.0F, 1.0F);
    }

    private record CellEdge(int cellX, int cellY, float boundary) {
    }
}
