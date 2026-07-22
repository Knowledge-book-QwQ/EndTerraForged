package endterraforged.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import endterraforged.world.level.biome.EndBiomeLayout;

/**
 * Serializable scalar controls for the End biome ring layout.
 */
public record BiomeLayoutConfig(int mainIslandRadius,
                                float radialCoefficient,
                                float highlandThreshold,
                                float midlandFloor,
                                int biomeEdgeScale,
                                int biomeEdgeOctaves,
                                float biomeEdgeLacunarity,
                                float biomeEdgeGain,
                                float biomeEdgeStrength,
                                int biomeWarpScale,
                                float biomeWarpStrength,
                                int outerNoiseScale,
                                int outerNoiseOctaves,
                                float outerNoiseThreshold,
                                BiomeVariantBlendConfig variantBlendConfig) {

    public static final BiomeLayoutConfig DEFAULT = new BiomeLayoutConfig(
            18, 8.0F, 40.0F, 0.0F,
            200, 4, 2.0F, 0.5F, 15.0F,
            300, 0.0F,
            400, 4, 0.2F,
            BiomeVariantBlendConfig.DEFAULT);

    public BiomeLayoutConfig(int mainIslandRadius,
                             float radialCoefficient,
                             float highlandThreshold,
                             float midlandFloor,
                             int biomeEdgeScale,
                             int biomeEdgeOctaves,
                             float biomeEdgeLacunarity,
                             float biomeEdgeGain,
                             float biomeEdgeStrength,
                             int outerNoiseScale,
                             int outerNoiseOctaves,
                             float outerNoiseThreshold) {
        this(mainIslandRadius, radialCoefficient, highlandThreshold, midlandFloor,
                biomeEdgeScale, biomeEdgeOctaves, biomeEdgeLacunarity,
                biomeEdgeGain, biomeEdgeStrength,
                DEFAULT.biomeWarpScale, DEFAULT.biomeWarpStrength,
                outerNoiseScale, outerNoiseOctaves, outerNoiseThreshold,
                DEFAULT.variantBlendConfig);
    }

    public BiomeLayoutConfig(int mainIslandRadius,
                             float radialCoefficient,
                             float highlandThreshold,
                             float midlandFloor,
                             int biomeEdgeScale,
                             int biomeEdgeOctaves,
                             float biomeEdgeLacunarity,
                             float biomeEdgeGain,
                             float biomeEdgeStrength,
                             int biomeWarpScale,
                             float biomeWarpStrength,
                             int outerNoiseScale,
                             int outerNoiseOctaves,
                             float outerNoiseThreshold) {
        this(mainIslandRadius, radialCoefficient, highlandThreshold, midlandFloor,
                biomeEdgeScale, biomeEdgeOctaves, biomeEdgeLacunarity,
                biomeEdgeGain, biomeEdgeStrength, biomeWarpScale, biomeWarpStrength,
                outerNoiseScale, outerNoiseOctaves, outerNoiseThreshold,
                DEFAULT.variantBlendConfig);
    }

    private static final Codec<BiomeLayoutConfig> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("main_island_radius", DEFAULT.mainIslandRadius)
                    .forGetter(BiomeLayoutConfig::mainIslandRadius),
            Codec.FLOAT.optionalFieldOf("radial_coefficient", DEFAULT.radialCoefficient)
                    .forGetter(BiomeLayoutConfig::radialCoefficient),
            Codec.FLOAT.optionalFieldOf("highland_threshold", DEFAULT.highlandThreshold)
                    .forGetter(BiomeLayoutConfig::highlandThreshold),
            Codec.FLOAT.optionalFieldOf("midland_floor", DEFAULT.midlandFloor)
                    .forGetter(BiomeLayoutConfig::midlandFloor),
            Codec.INT.optionalFieldOf("biome_edge_scale", DEFAULT.biomeEdgeScale)
                    .forGetter(BiomeLayoutConfig::biomeEdgeScale),
            Codec.INT.optionalFieldOf("biome_edge_octaves", DEFAULT.biomeEdgeOctaves)
                    .forGetter(BiomeLayoutConfig::biomeEdgeOctaves),
            Codec.FLOAT.optionalFieldOf("biome_edge_lacunarity", DEFAULT.biomeEdgeLacunarity)
                    .forGetter(BiomeLayoutConfig::biomeEdgeLacunarity),
            Codec.FLOAT.optionalFieldOf("biome_edge_gain", DEFAULT.biomeEdgeGain)
                    .forGetter(BiomeLayoutConfig::biomeEdgeGain),
            Codec.FLOAT.optionalFieldOf("biome_edge_strength", DEFAULT.biomeEdgeStrength)
                    .forGetter(BiomeLayoutConfig::biomeEdgeStrength),
            Codec.INT.optionalFieldOf("biome_warp_scale", DEFAULT.biomeWarpScale)
                    .forGetter(BiomeLayoutConfig::biomeWarpScale),
            Codec.FLOAT.optionalFieldOf("biome_warp_strength", DEFAULT.biomeWarpStrength)
                    .forGetter(BiomeLayoutConfig::biomeWarpStrength),
            Codec.INT.optionalFieldOf("outer_noise_scale", DEFAULT.outerNoiseScale)
                    .forGetter(BiomeLayoutConfig::outerNoiseScale),
            Codec.INT.optionalFieldOf("outer_noise_octaves", DEFAULT.outerNoiseOctaves)
                    .forGetter(BiomeLayoutConfig::outerNoiseOctaves),
            Codec.FLOAT.optionalFieldOf("outer_noise_threshold", DEFAULT.outerNoiseThreshold)
                    .forGetter(BiomeLayoutConfig::outerNoiseThreshold),
            BiomeVariantBlendConfig.CODEC.optionalFieldOf("variant_blend", DEFAULT.variantBlendConfig)
                    .forGetter(BiomeLayoutConfig::variantBlendConfig)
    ).apply(instance, instance.stable(BiomeLayoutConfig::new)));

    public static final Codec<BiomeLayoutConfig> CODEC = BASE_CODEC.flatXmap(
            BiomeLayoutConfigValidator::validate,
            config -> DataResult.success(config));

    public EndBiomeLayout buildRuntime() {
        return EndBiomeLayout.fromConfig(this);
    }
}
