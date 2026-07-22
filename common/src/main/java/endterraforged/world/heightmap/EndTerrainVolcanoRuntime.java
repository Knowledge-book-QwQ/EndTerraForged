/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Adapted from ReTerraForged's CompactTerrainEnvelope, VolcanoFootprint and
 * VolcanoProfile (MIT) for EndTerraForged (LGPL-3.0-or-later).
 *
 * EndTerraForged changes:
 * - replaces mutable Cell mutation with immutable scalar relief
 * - retains only finite footprint, flank, rim and crater geometry
 * - removes lava, surface material, biome, river, registry, pooling and
 *   executor coupling
 */
package endterraforged.world.heightmap;

import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.NoiseMath;
import endterraforged.world.noise.Noises;
import endterraforged.world.terrain.TerrainRegionBuffer;

/**
 * Immutable compact volcano morphology for one REGION_PLANNED volcano owner.
 *
 * <p>The runtime creates a finite rotated edifice with a bounded flank, raised
 * rim and depressed summit basin. It only produces terrain relief: actual
 * lava, surface palette, biome choice and flow placement belong to later
 * dedicated world-generation stages.</p>
 */
final class EndTerrainVolcanoRuntime {
    static final float ASPECT_RATIO = 1.35F;

    private static final float FOOTPRINT_RADIUS_FACTOR = 0.34F;
    private static final float EDGE_BLEND = 0.24F;
    private static final float MAX_RELIEF = 0.46F;
    private static final int VOLCANO_SEED_OFFSET = 250;

    private final float radius;
    private final float radiusX;
    private final float radiusZ;
    private final float relief;
    private final Noise detail;

    EndTerrainVolcanoRuntime(TerrainConfig terrain, int seed) {
        TerrainLayerConfig config = terrain.volcano();
        int regionSize = Math.max(128, Math.round(terrain.terrainRegionSize() * config.horizontalScale()));
        this.radius = regionSize * FOOTPRINT_RADIUS_FACTOR;
        float axisScale = (float) Math.sqrt(ASPECT_RATIO);
        this.radiusX = this.radius * axisScale;
        this.radiusZ = this.radius / axisScale;
        this.relief = MAX_RELIEF * config.weight() * config.baseScale() * config.verticalScale();
        int terrainSeed = seed + terrain.terrainSeedOffset();
        this.detail = Noises.perlin(terrainSeed + VOLCANO_SEED_OFFSET,
                Math.max(24, Math.round(regionSize * 0.11F)), 2);
    }

    float contribution(TerrainRegionBuffer region) {
        float distance = normalizedDistance(region.centerX(), region.centerZ(), region.orientation(),
                region.sampleX(), region.sampleZ());
        float influence = influence(distance);
        region.setPhysicalInfluence(influence);
        return contribution(region.regionId(), distance, influence, region.sampleX(), region.sampleZ());
    }

    float contribution(long regionId, float centerX, float centerZ, float orientation,
                       float sampleX, float sampleZ) {
        float distance = normalizedDistance(centerX, centerZ, orientation, sampleX, sampleZ);
        return contribution(regionId, distance, influence(distance), sampleX, sampleZ);
    }

    float influence(TerrainRegionBuffer region) {
        return influence(normalizedDistance(region.centerX(), region.centerZ(), region.orientation(),
                region.sampleX(), region.sampleZ()));
    }

    float influence(long regionId, float centerX, float centerZ, float orientation,
                    float sampleX, float sampleZ) {
        return influence(normalizedDistance(centerX, centerZ, orientation, sampleX, sampleZ));
    }

    private float contribution(long regionId, float distance, float influence, float sampleX, float sampleZ) {
        if (influence <= 0.0F || this.relief <= 0.0F) {
            return 0.0F;
        }
        float body = bodyProfile(distance);
        float crater = 1.0F - smoothstep(0.08F, 0.23F, distance);
        float rim = smoothstep(0.10F, 0.23F, distance)
                * (1.0F - smoothstep(0.23F, 0.46F, distance));
        float profile = Math.max(0.0F, body - crater * 0.30F + rim * 0.22F);
        float roughness = 0.88F + Math.clamp(this.detail.compute(sampleX, sampleZ, seed(regionId)), 0.0F, 1.0F)
                * 0.12F;
        return influence * profile * roughness * this.relief;
    }

    private float normalizedDistance(float centerX, float centerZ, float orientation,
                                     float sampleX, float sampleZ) {
        float deltaX = sampleX - centerX;
        float deltaZ = sampleZ - centerZ;
        float cosine = (float) Math.cos(orientation);
        float sine = (float) Math.sin(orientation);
        float along = deltaX * cosine + deltaZ * sine;
        float across = -deltaX * sine + deltaZ * cosine;
        return (float) Math.sqrt(along * along / (this.radiusX * this.radiusX)
                + across * across / (this.radiusZ * this.radiusZ));
    }

    private static float influence(float distance) {
        if (!Float.isFinite(distance) || distance >= 1.0F) {
            return 0.0F;
        }
        float inner = 1.0F - EDGE_BLEND;
        if (distance <= inner) {
            return 1.0F;
        }
        return 1.0F - smoothstep(inner, 1.0F, distance);
    }

    private static float bodyProfile(float distance) {
        float remaining = Math.clamp(1.0F - distance, 0.0F, 1.0F);
        float powered = (float) Math.pow(remaining, 1.35F);
        float smooth = remaining * remaining * (3.0F - 2.0F * remaining);
        return powered * 0.72F + smooth * 0.28F;
    }

    private static float smoothstep(float lower, float upper, float value) {
        float alpha = Math.clamp((value - lower) / Math.max(1.0E-6F, upper - lower), 0.0F, 1.0F);
        return alpha * alpha * (3.0F - 2.0F * alpha);
    }

    private static int seed(long regionId) {
        return NoiseMath.hash2D(VOLCANO_SEED_OFFSET, (int) (regionId >>> 32), (int) regionId);
    }
}
