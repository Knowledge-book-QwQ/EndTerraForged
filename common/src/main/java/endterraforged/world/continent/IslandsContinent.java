/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). RTF's island generation
 * (MIT) lives inside a CellPopulator coupled to biome/climate selectors; this
 * is a standalone Noise that emits a discrete-island landness field, reusable
 * for any topology that wants scattered floating landmasses.
 */
package endterraforged.world.continent;

import endterraforged.world.config.ContinentCoastShape;
import endterraforged.world.noise.Constant;
import endterraforged.world.noise.DistanceFunction;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.NoiseMath;
import endterraforged.world.noise.domain.Domain;

/**
 * {@link endterraforged.world.config.TopologyMode#ISLANDS} continent: discrete
 * floating islands separated by void.
 *
 * <p>A single 3x3 cell scan finds both the nearest feature point and its owning
 * cell, so the per-island existence gate and size jitter stay in sync with the
 * falloff. The scan follows R9.6's continent-cell approach while the exposed
 * parameters mirror R9.3.6's continent tuning surface.</p>
 *
 * @param frequency        island-cell frequency ({@code 1/scale})
 * @param distance         local feature-point spread in cell units
 * @param distanceFunction distance metric used to rank feature points
 * @param jitter           random offset amount around the cell centre
 * @param skipping         extra per-cell skip probability
 * @param sizeVariance     per-island radius variation in {@code [0,1]}
 * @param islandRadius     falloff radius in cell units
 * @param scatter          base per-cell existence threshold in {@code [0,1]}
 * @param warp             coordinate warp applied before the scan
 * @param coastShape       legacy radial or organic cell-boundary coastline
 * @param coastNoise       normalized multi-octave coast field
 * @param coastStrength    signed coast displacement in normalized distance space
 * @param coastCellBlend   contribution of the cell-boundary coastline
 */
public record IslandsContinent(float frequency, float distance, DistanceFunction distanceFunction,
                               float jitter, float skipping, float sizeVariance,
                               float islandRadius, float scatter, Domain warp,
                               ContinentCoastShape coastShape, Noise coastNoise,
                               float coastStrength, float coastCellBlend) implements Continent {

    /**
     * Retains the historical radial island behavior for direct callers and old
     * preset migration paths.
     */
    public IslandsContinent(float frequency, float distance, DistanceFunction distanceFunction,
                            float jitter, float skipping, float sizeVariance,
                            float islandRadius, float scatter, Domain warp) {
        this(frequency, distance, distanceFunction, jitter, skipping, sizeVariance, islandRadius, scatter,
                warp, ContinentCoastShape.RADIAL_LEGACY, new Constant(0.5F), 0.0F, 0.0F);
    }

    @Override
    public float compute(float x, float z, int seed) {
        float wx = this.warp.getX(x, z, seed) * this.frequency;
        float wz = this.warp.getZ(x, z, seed) * this.frequency;

        int xi = NoiseMath.floor(wx);
        int yi = NoiseMath.floor(wz);
        int cellX = xi;
        int cellY = yi;
        float nearest = Float.MAX_VALUE;
        float secondNearest = Float.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int cx = xi + dx;
                int cy = yi + dy;
                NoiseMath.Vec2f vec = NoiseMath.cell(seed, cx, cy);
                float deltaX = cellFeature(cx, vec.x()) - wx;
                float deltaY = cellFeature(cy, vec.y()) - wz;
                float dist = this.distanceFunction.apply(deltaX, deltaY);
                if (dist < nearest) {
                    secondNearest = nearest;
                    nearest = dist;
                    cellX = cx;
                    cellY = cy;
                } else if (dist < secondNearest) {
                    secondNearest = dist;
                }
            }
        }

        float gate = value01(seed, cellX, cellY);
        if (gate < Math.max(this.scatter, this.skipping)) {
            return 0.0F;
        }

        float radiusNoise = value01(seed + 0x9E3779B1, cellX, cellY);
        float radiusScale = 1.0F + (radiusNoise - 0.5F) * 2.0F * this.sizeVariance;
        float radius = this.islandRadius * Math.max(0.1F, radiusScale);
        if (radius <= 0.0F) {
            return 0.0F;
        }

        float radialDistance = distanceToRadiusSpace(nearest) / radius;
        float t = radialDistance;
        if (this.coastShape == ContinentCoastShape.ORGANIC) {
            float nearestDistance = distanceToRadiusSpace(nearest);
            float secondDistance = distanceToRadiusSpace(secondNearest);
            float cellDistance = nearestDistance / Math.max(secondDistance, 1.0E-5F);
            float coastOffset = (this.coastNoise.compute(x, z, seed) * 2.0F - 1.0F) * this.coastStrength;
            t = NoiseMath.lerp(radialDistance, cellDistance, this.coastCellBlend) - coastOffset;
        }
        t = NoiseMath.clamp(t, 0.0F, 1.0F);
        return 1.0F - NoiseMath.interpHermite(t);
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
        return visitor.apply(new IslandsContinent(this.frequency, this.distance, this.distanceFunction,
                this.jitter, this.skipping, this.sizeVariance, this.islandRadius,
                this.scatter, this.warp.mapAll(visitor), this.coastShape,
                this.coastNoise.mapAll(visitor), this.coastStrength, this.coastCellBlend));
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
}
