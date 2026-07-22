package endterraforged.world.level.biome;

import net.minecraft.util.Mth;

import endterraforged.world.config.BiomeLayoutConfig;
import endterraforged.world.config.BiomeVariantBlendConfig;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.Noises;

/**
 * Runtime biome ring layout with prebuilt noise graphs.
 */
public record EndBiomeLayout(int mainIslandRadius,
                             float radialCoefficient,
                             float highlandThreshold,
                             float midlandFloor,
                             float biomeEdgeStrength,
                             float biomeWarpStrength,
                             float outerNoiseThreshold,
                             Noise ringNoise,
                             Noise warpNoiseX,
                             Noise warpNoiseZ,
                             Noise outerNoise,
                             Noise fracNoiseX,
                             Noise fracNoiseZ) {

    private static final float FALLOFF_MIN = -100.0F;
    private static final float FALLOFF_MAX = 80.0F;
    private static final int RING_NOISE_SEED = 1337;
    private static final int OUTER_NOISE_SEED = 1338;
    private static final int WARP_X_SEED = 1341;
    private static final int WARP_Z_SEED = 1342;
    private static final int FRAC_X_SEED = 1339;
    private static final int FRAC_Z_SEED = 1340;

    public static final EndBiomeLayout DEFAULT =
            fromConfig(BiomeLayoutConfig.DEFAULT);

    public static EndBiomeLayout fromConfig(BiomeLayoutConfig config) {
        return new EndBiomeLayout(
                config.mainIslandRadius(),
                config.radialCoefficient(),
                config.highlandThreshold(),
                config.midlandFloor(),
                config.biomeEdgeStrength(),
                config.biomeWarpStrength(),
                config.outerNoiseThreshold(),
                Noises.simplex(RING_NOISE_SEED, config.biomeEdgeScale(),
                        config.biomeEdgeOctaves(), config.biomeEdgeLacunarity(),
                        config.biomeEdgeGain()),
                Noises.simplex(WARP_X_SEED, config.biomeWarpScale(), 2),
                Noises.simplex(WARP_Z_SEED, config.biomeWarpScale(), 2),
                Noises.simplex(OUTER_NOISE_SEED, config.outerNoiseScale(),
                        config.outerNoiseOctaves()),
                variantBlendNoise(FRAC_X_SEED, config.variantBlendConfig()),
                variantBlendNoise(FRAC_Z_SEED, config.variantBlendConfig()));
    }

    public Ring ringAt(int x, int z) {
        float sampleX = warpedX(x, z);
        float sampleZ = warpedZ(x, z);
        if (sampleX * sampleX + sampleZ * sampleZ < mainIslandRadius * mainIslandRadius) {
            return Ring.END;
        }
        float dist = (float) Math.sqrt(sampleX * sampleX + sampleZ * sampleZ);
        float falloff = 100.0F - dist * radialCoefficient;
        falloff = Mth.clamp(falloff, FALLOFF_MIN, FALLOFF_MAX);
        float perturbed = falloff + ringNoise.compute(sampleX, sampleZ, 0) * biomeEdgeStrength;

        if (perturbed > highlandThreshold) {
            return Ring.HIGHLANDS;
        }
        if (perturbed >= midlandFloor) {
            return Ring.MIDLANDS;
        }
        return outerNoise.compute(sampleX, sampleZ, 0) > outerNoiseThreshold
                ? Ring.ISLANDS
                : Ring.BARRENS;
    }

    public float fracX(int x, int z) {
        float sampleX = warpedX(x, z);
        float sampleZ = warpedZ(x, z);
        return fracNoiseX.compute(sampleX, sampleZ, 0);
    }

    public float fracZ(int x, int z) {
        float sampleX = warpedX(x, z);
        float sampleZ = warpedZ(x, z);
        return fracNoiseZ.compute(sampleX, sampleZ, 0);
    }

    private float warpedX(int x, int z) {
        if (biomeWarpStrength == 0.0F) {
            return x;
        }
        return x + warpNoiseX.compute(x, z, 0) * biomeWarpStrength;
    }

    private float warpedZ(int x, int z) {
        if (biomeWarpStrength == 0.0F) {
            return z;
        }
        return z + warpNoiseZ.compute(x, z, 0) * biomeWarpStrength;
    }

    private static Noise variantBlendNoise(int seed, BiomeVariantBlendConfig config) {
        return Noises.map(Noises.simplex(seed, config.scale(), config.octaves()), 0.0F, 1.0F);
    }

    public enum Ring {
        END,
        HIGHLANDS,
        MIDLANDS,
        ISLANDS,
        BARRENS
    }
}
