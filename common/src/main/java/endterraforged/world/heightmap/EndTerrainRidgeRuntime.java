/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Adapted from ReTerraForged's RidgeTerrainEnvelope and
 * ShapeAwareTerrainComposer (MIT) for EndTerraForged
 * (LGPL-3.0-or-later).
 *
 * EndTerraForged changes:
 * - evaluates independent deterministic anchors instead of terrain owners
 * - emits immutable scalar relief and influence
 * - removes Cell, registry, water, river, pooling and executor coupling
 */
package endterraforged.world.heightmap;

import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.Noises;

/** Immutable morphology for finite curved ridge anchors. */
final class EndTerrainRidgeRuntime {
    static final float ASPECT_RATIO = 2.75F;

    private static final float LENGTH_FOOTPRINT = 0.58F;
    private static final float WIDTH_FOOTPRINT = 0.68F;
    private static final float APRON_SCALE = 1.25F;
    private static final float CURVATURE = 0.55F;
    private static final float EDGE_BLEND = 0.35F;
    private static final float MAX_RELIEF = 0.38F;
    private static final int RIDGE_SEED_OFFSET = 230;
    private static final int FIRST_CONTROL_SALT = 0x2D71A94B;
    private static final int SECOND_CONTROL_SALT = 0x58C3F1D7;

    private final float coreHalfLength;
    private final float outerHalfLength;
    private final float coreHalfWidth;
    private final float outerHalfWidth;
    private final float maxReach;
    private final float relief;
    private final Noise spine;
    private final Noise detail;

    EndTerrainRidgeRuntime(TerrainConfig terrain, int seed) {
        TerrainLayerConfig config = terrain.mountains();
        int regionSize = Math.max(128, Math.round(
                terrain.terrainRegionSize() * config.horizontalScale()));
        float axisScale = (float) Math.sqrt(ASPECT_RATIO);
        this.coreHalfLength = regionSize * axisScale * LENGTH_FOOTPRINT;
        this.coreHalfWidth = regionSize / axisScale * WIDTH_FOOTPRINT;
        this.outerHalfWidth = this.coreHalfWidth * APRON_SCALE;
        this.outerHalfLength = this.coreHalfLength + (this.outerHalfWidth - this.coreHalfWidth);
        float lateralReach = this.coreHalfWidth * CURVATURE
                + this.outerHalfWidth * EndRidgeTerrainEnvelope.MAX_ORGANIC_WIDTH_SCALE;
        this.maxReach = (float) Math.sqrt(
                this.outerHalfLength * this.outerHalfLength + lateralReach * lateralReach);
        this.relief = MAX_RELIEF * config.weight()
                * config.baseScale() * config.verticalScale();

        int terrainSeed = seed + terrain.terrainSeedOffset();
        this.spine = Noises.perlinRidge(terrainSeed + RIDGE_SEED_OFFSET,
                Math.max(32, Math.round(regionSize * 0.45F)), 2);
        this.detail = Noises.perlin(terrainSeed + RIDGE_SEED_OFFSET + 1,
                Math.max(16, Math.round(regionSize * 0.12F)), 2);
    }

    float maxReach() {
        return this.maxReach;
    }

    float influence(int anchorSeed,
                    float centerX, float centerZ,
                    float rotationCos, float rotationSin,
                    float sampleX, float sampleZ) {
        return EndRidgeTerrainEnvelope.influence(
                sampleX, sampleZ, centerX, centerZ,
                this.coreHalfLength, this.outerHalfLength,
                this.coreHalfWidth, this.outerHalfWidth,
                rotationCos, rotationSin,
                firstControl(anchorSeed), secondControl(anchorSeed), EDGE_BLEND);
    }

    float contribution(int anchorSeed,
                       float centerX, float centerZ,
                       float rotationCos, float rotationSin,
                       float sampleX, float sampleZ) {
        if (this.relief <= 0.0F) {
            return 0.0F;
        }
        float envelope = EndRidgeTerrainEnvelope.relief(
                sampleX, sampleZ, centerX, centerZ,
                this.coreHalfLength, this.outerHalfLength,
                this.coreHalfWidth, this.outerHalfWidth,
                rotationCos, rotationSin,
                firstControl(anchorSeed), secondControl(anchorSeed), EDGE_BLEND);
        if (envelope <= 0.0F) {
            return 0.0F;
        }

        float deltaX = sampleX - centerX;
        float deltaZ = sampleZ - centerZ;
        float localX = deltaX * rotationCos + deltaZ * rotationSin;
        float localZ = -deltaX * rotationSin + deltaZ * rotationCos;
        float broad = Math.clamp(this.spine.compute(localX, localZ, anchorSeed), 0.0F, 1.0F);
        float fine = Math.clamp(this.detail.compute(localX, localZ, anchorSeed), 0.0F, 1.0F);
        float profile = 0.52F + broad * 0.36F + fine * 0.12F;
        return envelope * profile * this.relief;
    }

    private static float firstControl(int anchorSeed) {
        return EndRidgeTerrainEnvelope.controlOffset(anchorSeed, FIRST_CONTROL_SALT, CURVATURE);
    }

    private static float secondControl(int anchorSeed) {
        return EndRidgeTerrainEnvelope.controlOffset(anchorSeed, SECOND_CONTROL_SALT, CURVATURE);
    }
}
