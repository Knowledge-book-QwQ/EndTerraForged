/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged R9.3.6/R9.6 (MIT):
 * raccoonman.reterraforged.world.worldgen.cell.continent.advanced.AdvancedContinentGenerator
 *
 * EndTerraForged changes:
 * - removed GeneratorContext, Cell, Resource and river-cache coupling
 * - exposes caller-owned primitive signals instead of mutating a pooled Cell
 * - keeps central-End protection, End bands and finite volume outside this class
 */
package endterraforged.world.continent;

import java.util.Objects;

import endterraforged.world.config.ContinentConfig;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.NoiseMath;
import endterraforged.world.noise.Noises;
import endterraforged.world.noise.domain.Domain;
import endterraforged.world.noise.domain.Domains;

/**
 * RTF's advanced Voronoi continent field adapted to ETF's immutable runtime.
 *
 * <p>The implementation preserves the R9.3.6/R9.6 seed sequence, two-pass
 * Voronoi search, perpendicular-bisector distance, per-cell size variance,
 * corrected centre and cliff/bay coast shaping. It is wired into the internal
 * heightmap and preview assembly for integration tests, but remains unavailable
 * to persisted presets and the editor until P4.0 client compatibility and the
 * complete P4.1 performance gates pass.</p>
 */
public final class RtfAdvancedContinent implements Continent {

    private static final float CENTER_CORRECTION = 0.35F;
    private static final float SHALLOW_OCEAN = 0.25F;
    private static final float INLAND = 0.502F;

    private final int continentSeed;
    private final int skippingSeed;
    private final int varianceSeed;
    private final float frequency;
    private final float jitter;
    private final float skipThreshold;
    private final float variance;
    private final Domain warp;
    private final Noise cliffNoise;
    private final Noise bayNoise;

    /**
     * Builds the pure advanced continent field from the RTF-compatible ETF
     * configuration subset.
     */
    public RtfAdvancedContinent(int rootSeed, ContinentConfig config) {
        this(
                rootSeed,
                rootSeed + 1,
                rootSeed + 2,
                1.0F / (config.continentScale() * 4.0F),
                config.continentJitter(),
                config.continentSkipping(),
                config.continentSizeVariance(),
                buildWarp(rootSeed + 3, config),
                buildCliffNoise(rootSeed + 5, config.continentScale()),
                buildBayNoise(rootSeed + 6));
    }

    private RtfAdvancedContinent(int continentSeed,
                                 int skippingSeed,
                                 int varianceSeed,
                                 float frequency,
                                 float jitter,
                                 float skipThreshold,
                                 float variance,
                                 Domain warp,
                                 Noise cliffNoise,
                                 Noise bayNoise) {
        this.continentSeed = continentSeed;
        this.skippingSeed = skippingSeed;
        this.varianceSeed = varianceSeed;
        this.frequency = frequency;
        this.jitter = jitter;
        this.skipThreshold = skipThreshold;
        this.variance = variance;
        this.warp = Objects.requireNonNull(warp, "warp");
        this.cliffNoise = Objects.requireNonNull(cliffNoise, "cliffNoise");
        this.bayNoise = Objects.requireNonNull(bayNoise, "bayNoise");
    }

    @Override
    public float compute(float x, float z, int ignoredSeed) {
        return sample(x, z, null, null);
    }

    @Override
    public void sampleSignals(float x, float z, int ignoredSeed, ContinentSignalBuffer output) {
        Objects.requireNonNull(output, "output");
        sample(x, z, output, null);
    }

    /**
     * Samples the complete upstream diagnostics without allocating a value
     * object. The supplied buffer belongs to the caller.
     */
    public void sampleAdvanced(float x, float z, AdvancedContinentSignalBuffer output) {
        Objects.requireNonNull(output, "output");
        sample(x, z, null, output);
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
        return visitor.apply(new RtfAdvancedContinent(
                this.continentSeed,
                this.skippingSeed,
                this.varianceSeed,
                this.frequency,
                this.jitter,
                this.skipThreshold,
                this.variance,
                this.warp.mapAll(visitor),
                this.cliffNoise.mapAll(visitor),
                this.bayNoise.mapAll(visitor)));
    }

    private float sample(float worldX, float worldZ,
                         ContinentSignalBuffer signals,
                         AdvancedContinentSignalBuffer advanced) {
        float warpedX = this.warp.getX(worldX, worldZ, 0);
        float warpedZ = this.warp.getZ(worldX, worldZ, 0);
        float x = warpedX * this.frequency;
        float z = warpedZ * this.frequency;
        int originX = NoiseMath.floor(x);
        int originZ = NoiseMath.floor(z);
        int ownerX = originX;
        int ownerZ = originZ;
        float ownerPointX = x;
        float ownerPointZ = z;
        float nearest = Float.MAX_VALUE;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int cellX = originX + dx;
                int cellZ = originZ + dz;
                NoiseMath.Vec2f feature = NoiseMath.cell(this.continentSeed, cellX, cellZ);
                float featureX = cellX + feature.x() * this.jitter;
                float featureZ = cellZ + feature.y() * this.jitter;
                float distance = NoiseMath.dist2(x, z, featureX, featureZ);
                if (distance < nearest) {
                    ownerPointX = featureX;
                    ownerPointZ = featureZ;
                    ownerX = cellX;
                    ownerZ = cellZ;
                    nearest = distance;
                }
            }
        }

        nearest = Float.MAX_VALUE;
        float neighbourSumX = 0.0F;
        float neighbourSumZ = 0.0F;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int cellX = ownerX + dx;
                int cellZ = ownerZ + dz;
                if (cellX == ownerX && cellZ == ownerZ) {
                    continue;
                }
                NoiseMath.Vec2f feature = NoiseMath.cell(this.continentSeed, cellX, cellZ);
                float featureX = cellX + feature.x() * this.jitter;
                float featureZ = cellZ + feature.y() * this.jitter;
                float distance = distanceToBisector(
                        x, z, ownerPointX, ownerPointZ, featureX, featureZ);
                neighbourSumX += featureX;
                neighbourSumZ += featureZ;
                if (distance < nearest) {
                    nearest = distance;
                }
            }
        }

        float continentDistance = NoiseMath.sqrt(nearest);
        int centerX = correctedCenter(ownerPointX, neighbourSumX * 0.125F);
        int centerZ = correctedCenter(ownerPointZ, neighbourSumZ * 0.125F);
        boolean skipped = shouldSkip(ownerX, ownerZ);
        float continentId = skipped ? 0.0F : cellValue(this.continentSeed, ownerX, ownerZ);
        float edge = skipped ? 0.0F
                : distanceValue(warpedX, warpedZ, ownerX, ownerZ, nearest);

        if (signals != null) {
            if (skipped) {
                signals.set(edge, edge, 1.0F);
            } else {
                signals.setIdentified(
                        edge,
                        edge,
                        1.0F,
                        Continent.packId(ownerX, ownerZ),
                        centerX,
                        centerZ);
            }
        }
        if (advanced != null) {
            advanced.set(edge, continentId, continentDistance, centerX, centerZ, skipped);
        }
        return edge;
    }

    private float distanceValue(float warpedX, float warpedZ,
                                int cellX, int cellZ, float distance) {
        if (this.variance > 0.0F && !isDefaultContinent(cellX, cellZ)) {
            float sizeValue = cellValue(this.varianceSeed, cellX, cellZ);
            float sizeModifier = NoiseMath.map(sizeValue, 0.0F, this.variance, this.variance);
            distance *= sizeModifier;
        }
        distance = NoiseMath.sqrt(distance);
        distance = NoiseMath.map(distance, 0.05F, 0.25F, 0.2F);
        distance = coastalDistanceValue(warpedX, warpedZ, distance);
        if (distance < INLAND && distance >= SHALLOW_OCEAN) {
            distance = coastalDistanceValue(warpedX, warpedZ, distance);
        }
        return Math.clamp(distance, 0.0F, 1.0F);
    }

    private float coastalDistanceValue(float warpedX, float warpedZ, float distance) {
        if (distance > SHALLOW_OCEAN && distance < INLAND) {
            float alpha = distance / INLAND;
            float cliff = this.cliffNoise.compute(warpedX, warpedZ, 0);
            distance = NoiseMath.lerp(distance * cliff, distance, alpha);
            if (distance < SHALLOW_OCEAN) {
                distance = SHALLOW_OCEAN * this.bayNoise.compute(warpedX, warpedZ, 0);
            }
        }
        return distance;
    }

    private int correctedCenter(float point, float neighbourAverage) {
        return (int) (NoiseMath.lerp(point, neighbourAverage, CENTER_CORRECTION) / this.frequency);
    }

    private boolean shouldSkip(int cellX, int cellZ) {
        return this.skipThreshold > 0.0F
                && !isDefaultContinent(cellX, cellZ)
                && cellValue(this.skippingSeed, cellX, cellZ) < this.skipThreshold;
    }

    private static boolean isDefaultContinent(int cellX, int cellZ) {
        return cellX == 0 && cellZ == 0;
    }

    private static float cellValue(int seed, int cellX, int cellZ) {
        return 0.5F + NoiseMath.valCoord2D(seed, cellX, cellZ) * 0.5F;
    }

    private static float distanceToBisector(float x, float z,
                                            float ownerX, float ownerZ,
                                            float neighbourX, float neighbourZ) {
        float midpointX = (ownerX + neighbourX) * 0.5F;
        float midpointZ = (ownerZ + neighbourZ) * 0.5F;
        float deltaX = neighbourX - ownerX;
        float deltaZ = neighbourZ - ownerZ;
        return distanceToLine(
                x, z, midpointX, midpointZ,
                midpointX - deltaZ, midpointZ + deltaX);
    }

    private static float distanceToLine(float x, float z,
                                        float lineAX, float lineAZ,
                                        float lineBX, float lineBZ) {
        float deltaX = lineBX - lineAX;
        float deltaZ = lineBZ - lineAZ;
        float projection = ((x - lineAX) * deltaX + (z - lineAZ) * deltaZ)
                / (deltaX * deltaX + deltaZ * deltaZ);
        float projectedX = lineAX + deltaX * projection;
        float projectedZ = lineAZ + deltaZ * projection;
        return NoiseMath.dist2(x, z, projectedX, projectedZ);
    }

    private static Domain buildWarp(int firstWarpSeed, ContinentConfig config) {
        int tectonicScale = config.continentScale() * 4;
        int warpScale = NoiseMath.round(tectonicScale * 0.225F);
        float strength = NoiseMath.round(tectonicScale * 0.33F);
        return Domains.domain(
                Noises.perlin2(
                        firstWarpSeed,
                        warpScale,
                        config.continentNoiseOctaves(),
                        config.continentNoiseLacunarity(),
                        config.continentNoiseGain()),
                Noises.perlin2(
                        firstWarpSeed + 1,
                        warpScale,
                        config.continentNoiseOctaves(),
                        config.continentNoiseLacunarity(),
                        config.continentNoiseGain()),
                Noises.constant(strength));
    }

    private static Noise buildCliffNoise(int seed, int continentScale) {
        return Noises.map(
                Noises.clamp(Noises.simplex2(seed, continentScale / 2, 2), 0.1F, 0.25F),
                0.0F,
                1.0F);
    }

    private static Noise buildBayNoise(int seed) {
        return Noises.add(Noises.mul(Noises.simplex(seed, 100, 1), 0.1F), 0.9F);
    }
}
