/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Adapted from ReTerraForged's Populators and RegionVariantPopulator (MIT)
 * into EndTerraForged (LGPL-3.0-or-later). Lineage: TerraForged (dags) ->
 * ReTerraForged (raccoonman) -> EndTerraForged.
 *
 * End-specific adaptation: replaces Cell mutation, terrain registries, and
 * overworld ground composition with immutable scalar family fields selected by
 * ETF TerrainRegionLayout ownership ids.
 */
package endterraforged.world.heightmap;

import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.NoiseMath;
import endterraforged.world.noise.Noises;
import endterraforged.world.terrain.TerrainRegionFamily;

/**
 * Immutable construction-time catalogue of scalar AREA terrain families.
 *
 * <p>Each ownership region chooses exactly one family variant from its stable
 * packed region id. This prevents individual columns inside the same region
 * from changing morphology while retaining a bounded, allocation-free sample
 * path for density generation.</p>
 */
final class TerrainFamilyRuntime {
    private static final float PLAINS_RELIEF = 0.06F;
    private static final float HILLS_RELIEF = 0.16F;
    private static final float PLATEAU_RELIEF = 0.21F;
    private static final float PLAINS_ROUGHNESS = 0.18F;
    private static final float HILLS_ROUGHNESS = 0.58F;
    private static final float PLATEAU_ROUGHNESS = 0.42F;
    private static final float PLAINS_RESISTANCE = 0.28F;
    private static final float HILLS_RESISTANCE = 0.46F;
    private static final float PLATEAU_RESISTANCE = 0.78F;

    private final FamilyRuntime[] families;

    TerrainFamilyRuntime(TerrainConfig config, int seed) {
        this.families = new FamilyRuntime[TerrainRegionFamily.values().length];

        float coordinateScale = config.globalHorizontalScale()
                * (config.terrainRegionSize() / (float) TerrainConfig.DEFAULT.terrainRegionSize());
        int terrainSeed = seed + config.terrainSeedOffset();
        this.families[TerrainRegionFamily.PLAINS.ordinal()] = createPlains(
                terrainSeed + 200, coordinateScale, config.plains());
        this.families[TerrainRegionFamily.HILLS.ordinal()] = createHills(
                terrainSeed + 210, coordinateScale, config.hills());
        this.families[TerrainRegionFamily.PLATEAU.ordinal()] = createPlateau(
                terrainSeed + 220, coordinateScale, config.plateau());
    }

    float contribution(TerrainRegionFamily family, long regionId, int entryId, float x, float z) {
        FamilyRuntime runtime = this.families[family.ordinal()];
        return runtime == null ? 0.0F : runtime.contribution(regionId, entryId, x, z);
    }

    void sample(TerrainRegionFamily family, long regionId, int entryId,
                float x, float z, EndTerrainSignalBuffer output) {
        FamilyRuntime runtime = this.families[family.ordinal()];
        if (runtime == null) {
            output.set(0.0F, 0.0F, 0.0F, terrainTag(family));
            return;
        }
        runtime.sample(regionId, entryId, x, z, output);
    }

    static float roughness(TerrainRegionFamily family) {
        return switch (family) {
            case PLAINS -> PLAINS_ROUGHNESS;
            case HILLS -> HILLS_ROUGHNESS;
            case PLATEAU -> PLATEAU_ROUGHNESS;
            case MOUNTAINS, VOLCANO -> 0.58F;
        };
    }

    static float erosionResistance(TerrainRegionFamily family) {
        return switch (family) {
            case PLAINS -> PLAINS_RESISTANCE;
            case HILLS -> HILLS_RESISTANCE;
            case PLATEAU -> PLATEAU_RESISTANCE;
            case MOUNTAINS, VOLCANO -> 0.46F;
        };
    }

    private static FamilyRuntime createPlains(int seed, float coordinateScale, TerrainLayerConfig config) {
        if (!enabled(config)) {
            return null;
        }
        return new FamilyRuntime(seed, config, PLAINS_RELIEF, PLAINS_ROUGHNESS,
                PLAINS_RESISTANCE, terrainTag(TerrainRegionFamily.PLAINS), new Noise[] {
                makePlains(seed, coordinateScale),
                makeSteppe(seed + 101, coordinateScale)
        });
    }

    private static FamilyRuntime createHills(int seed, float coordinateScale, TerrainLayerConfig config) {
        if (!enabled(config)) {
            return null;
        }
        return new FamilyRuntime(seed, config, HILLS_RELIEF, HILLS_ROUGHNESS,
                HILLS_RESISTANCE, terrainTag(TerrainRegionFamily.HILLS), new Noise[] {
                makeHills1(seed, coordinateScale),
                makeHills2(seed + 101, coordinateScale)
        });
    }

    private static FamilyRuntime createPlateau(int seed, float coordinateScale, TerrainLayerConfig config) {
        if (!enabled(config)) {
            return null;
        }
        return new FamilyRuntime(seed, config, PLATEAU_RELIEF, PLATEAU_ROUGHNESS,
                PLATEAU_RESISTANCE, terrainTag(TerrainRegionFamily.PLATEAU), new Noise[] {
                makePlateau(seed, coordinateScale)
        });
    }

    private static Noise makeSteppe(int seed, float coordinateScale) {
        int scale = scale(250, coordinateScale);
        Noise erosion = Noises.alpha(Noises.perlin(seed, scale * 2, 3, 3.75F), 0.45F);
        Noise warpX = Noises.perlin(seed + 1, Math.max(8, scale / 4), 3, 3.0F);
        Noise warpZ = Noises.perlin(seed + 2, Math.max(8, scale / 4), 3, 3.0F);
        Noise height = Noises.perlin(seed + 3, scale, 1);
        height = Noises.mul(height, erosion);
        height = Noises.warp(height, warpX, warpZ, scale / 4.0F);
        height = Noises.warpPerlin(height, seed + 4, scale(256, coordinateScale), 1,
                200.0F * coordinateScale);
        return normalized(Noises.add(Noises.mul(height, 0.08F), -0.02F));
    }

    private static Noise makePlains(int seed, float coordinateScale) {
        int scale = scale(250, coordinateScale);
        Noise erosion = Noises.alpha(Noises.perlin(seed, scale * 2, 3, 3.75F), 0.45F);
        Noise warpX = Noises.perlin(seed + 1, Math.max(8, scale / 4), 3, 3.5F);
        Noise warpZ = Noises.perlin(seed + 2, Math.max(8, scale / 4), 3, 3.5F);
        Noise height = Noises.perlin(seed + 3, scale, 1);
        height = Noises.mul(height, erosion);
        height = Noises.warp(height, warpX, warpZ, scale / 4.0F);
        height = Noises.warpPerlin(height, seed + 4, scale(256, coordinateScale), 1,
                256.0F * coordinateScale);
        return normalized(Noises.add(Noises.mul(height, 0.15F), -0.02F));
    }

    private static Noise makeHills1(int seed, float coordinateScale) {
        Noise height = Noises.perlin(seed, scale(200, coordinateScale), 3);
        Noise scaler = Noises.alpha(Noises.billow(seed + 1, scale(400, coordinateScale), 3), 0.5F);
        height = Noises.mul(height, scaler);
        height = Noises.warpPerlin(height, seed + 2, scale(30, coordinateScale), 3,
                20.0F * coordinateScale);
        height = Noises.warpPerlin(height, seed + 3, scale(400, coordinateScale), 3,
                200.0F * coordinateScale);
        return normalized(Noises.mul(height, 0.6F));
    }

    private static Noise makeHills2(int seed, float coordinateScale) {
        Noise height = Noises.cubic(seed, scale(128, coordinateScale), 2);
        Noise fineScaler = Noises.alpha(Noises.perlin(seed + 1, scale(32, coordinateScale), 4), 0.075F);
        height = Noises.mul(height, fineScaler);
        height = Noises.warpPerlin(height, seed + 2, scale(30, coordinateScale), 3,
                20.0F * coordinateScale);
        height = Noises.warpPerlin(height, seed + 3, scale(400, coordinateScale), 3,
                200.0F * coordinateScale);
        Noise ridgeScaler = Noises.alpha(
                Noises.perlinRidge(seed + 4, scale(512, coordinateScale), 2), 0.8F);
        height = Noises.mul(height, ridgeScaler);
        return normalized(Noises.mul(height, 0.55F));
    }

    private static Noise makePlateau(int seed, float coordinateScale) {
        Noise valley = Noises.invert(Noises.perlinRidge(seed, scale(500, coordinateScale), 1));
        valley = Noises.warpPerlin(valley, seed + 1, scale(100, coordinateScale), 1,
                150.0F * coordinateScale);
        valley = Noises.warpPerlin(valley, seed + 2, scale(20, coordinateScale), 1,
                15.0F * coordinateScale);

        Noise top = Noises.perlinRidge(seed + 3, scale(150, coordinateScale), 3, 2.45F);
        top = Noises.warpPerlin(top, seed + 4, scale(300, coordinateScale), 1,
                150.0F * coordinateScale);
        top = Noises.warpPerlin(top, seed + 5, scale(40, coordinateScale), 2,
                20.0F * coordinateScale);
        top = Noises.mul(top, 0.15F);

        Noise valleyScaler = Noises.map(Noises.clamp(valley, 0.02F, 0.1F), 0.0F, 1.0F);
        top = Noises.mul(top, valleyScaler);

        Noise surface = Noises.perlin(seed + 6, scale(20, coordinateScale), 3);
        surface = Noises.mul(surface, 0.05F);
        surface = Noises.warpPerlin(surface, seed + 7, scale(40, coordinateScale), 2,
                20.0F * coordinateScale);

        Noise cubic = Noises.add(Noises.mul(Noises.cubic(seed + 8, scale(500, coordinateScale), 1), 0.6F),
                0.3F);
        Noise valleyBase = Noises.add(Noises.mul(valley, cubic), top);
        Noise height = Noises.terrace(valleyBase, 0.9F, 0.15F, 0.35F, 0.4F, 4);
        return normalized(Noises.add(Noises.mul(height, 0.475F), surface));
    }

    private static Noise normalized(Noise input) {
        return Noises.clamp(Noises.map(input, 0.0F, 1.0F), 0.0F, 1.0F);
    }

    private static int scale(int baseScale, float coordinateScale) {
        return Math.max(8, Math.round(baseScale * coordinateScale));
    }

    private static boolean enabled(TerrainLayerConfig config) {
        return config.weight() > 0.0F
                && config.baseScale() > 0.0F
                && config.verticalScale() > 0.0F;
    }

    private static int terrainTag(TerrainRegionFamily family) {
        return 1 << family.ordinal();
    }

    private record FamilyRuntime(int variantSeed, TerrainLayerConfig config, float relief,
                                 float roughness, float erosionResistance,
                                 int terrainTags,
                                 Noise[] variants) {

        float contribution(long regionId, int entryId, float x, float z) {
            return sampleHeight(regionId, entryId, x, z)
                    * this.config.weight() * this.config.baseScale() * this.config.verticalScale();
        }

        void sample(long regionId, int entryId, float x, float z, EndTerrainSignalBuffer output) {
            float height = sampleHeight(regionId, entryId, x, z)
                    * this.config.weight() * this.config.baseScale() * this.config.verticalScale();
            output.set(height, this.roughness, this.erosionResistance,
                    this.terrainTags);
        }

        private float sampleHeight(long regionId, int entryId, float x, float z) {
            int index = variantIndex(this.variantSeed, regionId, entryId, this.variants.length);
            float sample = this.variants[index].compute(x, z, 0);
            return sample * this.relief;
        }

        private static int variantIndex(int seed, long regionId, int entryId, int count) {
            int regionHash = NoiseMath.hash2D(seed, (int) (regionId >>> 32), (int) regionId);
            return Math.floorMod(NoiseMath.hash2D(regionHash, entryId, 0), count);
        }

    }
}
