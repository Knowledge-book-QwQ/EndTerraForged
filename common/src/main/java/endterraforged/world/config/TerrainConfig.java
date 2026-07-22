package endterraforged.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Global terrain shaping controls inspired by RTF's terrain general settings.
 *
 * <p>{@code terrainBlendRange} controls cross-fading between adjacent
 * auxiliary terrain-layer regions. {@code 0.0} preserves the historical hard
 * selector, while {@code 1.0} uses the widest stable blend band allowed by the
 * neighboring layer weights.</p>
 */
public record TerrainConfig(int terrainSeedOffset, int terrainRegionSize,
                            float globalVerticalScale, float globalHorizontalScale,
                            float terrainBlendRange,
                            TerrainLayoutMode terrainLayoutMode,
                            TerrainShape terrainShape,
                            TerrainLayerConfig plains, TerrainLayerConfig hills,
                            TerrainLayerConfig plateau, TerrainLayerConfig mountains,
                            TerrainLayerConfig volcano) {

    public static final TerrainConfig DEFAULT = new TerrainConfig(
            0, 1200, 1.0F, 1.0F, 0.0F, TerrainLayoutMode.LEGACY_SELECTOR,
            TerrainShape.SHATTERED_RIDGES,
            TerrainLayerConfig.DISABLED, TerrainLayerConfig.DISABLED,
            TerrainLayerConfig.DISABLED, TerrainLayerConfig.DEFAULT,
            TerrainLayerConfig.DISABLED);

    public TerrainConfig(int terrainSeedOffset, int terrainRegionSize,
                         float globalVerticalScale, float globalHorizontalScale,
                         float terrainBlendRange,
                         TerrainShape terrainShape,
                         TerrainLayerConfig plains, TerrainLayerConfig hills,
                         TerrainLayerConfig plateau, TerrainLayerConfig mountains,
                         TerrainLayerConfig volcano) {
        this(terrainSeedOffset, terrainRegionSize,
                globalVerticalScale, globalHorizontalScale, terrainBlendRange,
                TerrainLayoutMode.LEGACY_SELECTOR,
                terrainShape, plains, hills, plateau, mountains, volcano);
    }

    public TerrainConfig(int terrainSeedOffset, int terrainRegionSize,
                         float globalVerticalScale, float globalHorizontalScale,
                         TerrainShape terrainShape,
                         TerrainLayerConfig plains, TerrainLayerConfig hills,
                         TerrainLayerConfig plateau, TerrainLayerConfig mountains,
                         TerrainLayerConfig volcano) {
        this(terrainSeedOffset, terrainRegionSize,
                globalVerticalScale, globalHorizontalScale, DEFAULT.terrainBlendRange,
                terrainShape, plains, hills, plateau, mountains, volcano);
    }

    public TerrainConfig(float globalVerticalScale, float globalHorizontalScale) {
        this(globalVerticalScale, globalHorizontalScale, TerrainShape.SHATTERED_RIDGES);
    }

    public TerrainConfig(float globalVerticalScale, float globalHorizontalScale, TerrainShape terrainShape) {
        this(DEFAULT.terrainSeedOffset, DEFAULT.terrainRegionSize,
                globalVerticalScale, globalHorizontalScale, terrainShape, TerrainLayerConfig.DEFAULT);
    }

    public TerrainConfig(int terrainSeedOffset, int terrainRegionSize,
                         float globalVerticalScale, float globalHorizontalScale,
                         TerrainShape terrainShape) {
        this(terrainSeedOffset, terrainRegionSize,
                globalVerticalScale, globalHorizontalScale, terrainShape, TerrainLayerConfig.DEFAULT);
    }

    public TerrainConfig(int terrainSeedOffset, int terrainRegionSize,
                         float globalVerticalScale, float globalHorizontalScale,
                         TerrainShape terrainShape, TerrainLayerConfig mountains) {
        this(terrainSeedOffset, terrainRegionSize,
                globalVerticalScale, globalHorizontalScale, terrainShape,
                TerrainLayerConfig.DISABLED, TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DISABLED, mountains, TerrainLayerConfig.DISABLED);
    }

    private static final Codec<TerrainConfig> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("terrain_seed_offset", DEFAULT.terrainSeedOffset)
                    .forGetter(TerrainConfig::terrainSeedOffset),
            Codec.INT.optionalFieldOf("terrain_region_size", DEFAULT.terrainRegionSize)
                    .forGetter(TerrainConfig::terrainRegionSize),
            Codec.FLOAT.optionalFieldOf("global_vertical_scale", DEFAULT.globalVerticalScale)
                    .forGetter(TerrainConfig::globalVerticalScale),
            Codec.FLOAT.optionalFieldOf("global_horizontal_scale", DEFAULT.globalHorizontalScale)
                    .forGetter(TerrainConfig::globalHorizontalScale),
            Codec.FLOAT.optionalFieldOf("terrain_blend_range", DEFAULT.terrainBlendRange)
                    .forGetter(TerrainConfig::terrainBlendRange),
            TerrainLayoutMode.CODEC.optionalFieldOf("terrain_layout_mode", DEFAULT.terrainLayoutMode)
                    .forGetter(TerrainConfig::terrainLayoutMode),
            TerrainShape.CODEC.optionalFieldOf("terrain_shape", DEFAULT.terrainShape)
                    .forGetter(TerrainConfig::terrainShape),
            TerrainLayerConfig.CODEC.optionalFieldOf("plains", DEFAULT.plains)
                    .forGetter(TerrainConfig::plains),
            TerrainLayerConfig.CODEC.optionalFieldOf("hills", DEFAULT.hills)
                    .forGetter(TerrainConfig::hills),
            TerrainLayerConfig.CODEC.optionalFieldOf("plateau", DEFAULT.plateau)
                    .forGetter(TerrainConfig::plateau),
            TerrainLayerConfig.CODEC.optionalFieldOf("mountains", DEFAULT.mountains)
                    .forGetter(TerrainConfig::mountains),
            TerrainLayerConfig.CODEC.optionalFieldOf("volcano", DEFAULT.volcano)
                    .forGetter(TerrainConfig::volcano)
    ).apply(instance, instance.stable(TerrainConfig::new)));

    public static final Codec<TerrainConfig> CODEC = BASE_CODEC.flatXmap(
            TerrainConfigValidator::validate,
            TerrainConfigValidator::validate);
}
