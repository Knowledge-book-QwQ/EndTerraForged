/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged R9.3.6/R9.6 (MIT):
 * raccoonman.reterraforged.world.worldgen.cell.continent.simple.ContinentGenerator
 *
 * EndTerraForged changes:
 * - removed GeneratorContext, Cell, Resource and river-cache coupling
 * - exposes the pure macro landness field consumed by EndHeightmap
 * - keeps central-End protection and finite-volume handling outside this class
 */
package endterraforged.world.continent;

import java.util.Objects;

import endterraforged.world.config.ContinentConfig;
import endterraforged.world.noise.DistanceFunction;
import endterraforged.world.noise.EdgeFunction;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.NoiseMath;
import endterraforged.world.noise.Noises;
import endterraforged.world.noise.domain.Domain;
import endterraforged.world.noise.domain.Domains;

/**
 * RTF's low-cost Voronoi macro-continent field adapted to ETF's pure
 * {@code [0,1]} landness contract.
 *
 * <p>The implementation preserves R9.3.6/R9.6's seed advancement, two-stage
 * domain warp, 3x3 feature-point scan, {@link EdgeFunction#DISTANCE_2_DIV}
 * mapping and shape multiplier. It is immutable and does not allocate during
 * sampling, so it is safe for parallel chunk generation.</p>
 */
public final class RtfMultiContinent implements Continent {

    private static final float CLAMP_MIN = 0.2F;
    private static final float CLAMP_MAX = 1.0F;
    private static final float CLAMP_RANGE = CLAMP_MAX - CLAMP_MIN;
    private static final float INLAND_THRESHOLD = 0.502F;

    private final int seed;
    private final float frequency;
    private final DistanceFunction distanceFunction;
    private final float offsetAlpha;
    private final Domain warp;
    private final Noise shape;

    /**
     * Creates the RTF multi-continent field from the compatible ETF parameter
     * subset. The root seed order deliberately mirrors RTF's {@code Seed#next}
     * sequence, including its overlapping simplex/perlin seed slots.
     */
    public RtfMultiContinent(int seed, ContinentConfig config) {
        this(
                seed,
                1.0F / (config.continentScale() * 4.0F),
                config.continentShape(),
                config.continentJitter(),
                buildWarp(seed, config.continentScale()),
                buildShape(seed, config.continentScale()));
    }

    private RtfMultiContinent(int seed, float frequency, DistanceFunction distanceFunction, float offsetAlpha,
                              Domain warp, Noise shape) {
        this.seed = seed;
        this.frequency = frequency;
        this.distanceFunction = Objects.requireNonNull(distanceFunction, "distanceFunction");
        this.offsetAlpha = offsetAlpha;
        this.warp = Objects.requireNonNull(warp, "warp");
        this.shape = Objects.requireNonNull(shape, "shape");
    }

    @Override
    public float compute(float x, float z, int ignoredSeed) {
        return computeLandness(x, z, null);
    }

    @Override
    public void sampleSignals(float x, float z, int ignoredSeed, ContinentSignalBuffer output) {
        Objects.requireNonNull(output, "output");
        computeLandness(x, z, output);
    }

    private float computeLandness(float x, float z, ContinentSignalBuffer output) {
        float px = this.warp.getX(x, z, 0) * this.frequency;
        float pz = this.warp.getZ(x, z, 0) * this.frequency;
        int originX = NoiseMath.floor(px);
        int originZ = NoiseMath.floor(pz);
        float nearest = Float.MAX_VALUE;
        float secondNearest = Float.MAX_VALUE;
        int ownerX = originX;
        int ownerZ = originZ;
        float ownerPointX = px;
        float ownerPointZ = pz;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int cellX = originX + dx;
                int cellZ = originZ + dz;
                NoiseMath.Vec2f feature = NoiseMath.cell(this.seed, cellX, cellZ);
                float featureX = cellX + feature.x() * this.offsetAlpha;
                float featureZ = cellZ + feature.y() * this.offsetAlpha;
                float distance = this.distanceFunction.apply(featureX - px, featureZ - pz);
                if (distance < nearest) {
                    secondNearest = nearest;
                    nearest = distance;
                    ownerX = cellX;
                    ownerZ = cellZ;
                    ownerPointX = featureX;
                    ownerPointZ = featureZ;
                } else if (distance < secondNearest) {
                    secondNearest = distance;
                }
            }
        }

        float edge = edgeValue(nearest, secondNearest);
        float landness = edge * shapeAt(x, z, edge);
        if (output != null) {
            output.setIdentified(
                    edge,
                    landness,
                    1.0F,
                    Continent.packId(ownerX, ownerZ),
                    (int) (ownerPointX / this.frequency),
                    (int) (ownerPointZ / this.frequency));
        }
        return landness;
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
        return visitor.apply(new RtfMultiContinent(
                this.seed,
                this.frequency,
                this.distanceFunction,
                this.offsetAlpha,
                this.warp.mapAll(visitor),
                this.shape.mapAll(visitor)));
    }

    private static Domain buildWarp(int seed, int continentScale) {
        Domain warp = Domains.domainPerlin(seed + 1, 20, 2, 20.0F);
        return Domains.compound(warp,
                Domains.domainSimplex(seed + 2, continentScale / 2, 3, continentScale / 2.0F));
    }

    private static Noise buildShape(int seed, int continentScale) {
        return Noises.clamp(Noises.add(Noises.simplex(seed + 3, continentScale * 2, 1), 0.65F), 0.0F, 1.0F);
    }

    private static float edgeValue(float nearest, float secondNearest) {
        EdgeFunction edgeFunction = EdgeFunction.DISTANCE_2_DIV;
        float value = edgeFunction.apply(nearest, secondNearest);
        value = 1.0F - NoiseMath.map(value, edgeFunction.min(), edgeFunction.max(), edgeFunction.range());
        if (value <= CLAMP_MIN) {
            return 0.0F;
        }
        if (value >= CLAMP_MAX) {
            return 1.0F;
        }
        return (value - CLAMP_MIN) / CLAMP_RANGE;
    }

    private float shapeAt(float x, float z, float edgeValue) {
        if (edgeValue >= INLAND_THRESHOLD) {
            return 1.0F;
        }
        return this.shape.compute(x, z, 0) * (edgeValue / INLAND_THRESHOLD);
    }
}
