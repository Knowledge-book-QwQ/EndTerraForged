package endterraforged.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Serializable scalar controls for vertical abyss pits.
 */
public record AbyssPitConfig(boolean enabled,
                             int seedOffset,
                             int pitScale,
                             int pitOctaves,
                             float pitLacunarity,
                             float pitGain,
                             float threshold,
                             float edgeFalloff,
                             int depth,
                             float depthCurve,
                             float minLandness) {

    public static final AbyssPitConfig DISABLED = new AbyssPitConfig(
            false, 1600, 900, 3, 2.0F, 0.5F,
            0.82F, 0.12F, 384, 1.0F, 0.25F);
    public static final AbyssPitConfig DEFAULT = DISABLED;

    public AbyssPitConfig(boolean enabled,
                          int seedOffset,
                          int pitScale,
                          float threshold,
                          float edgeFalloff,
                          int depth,
                          float minLandness) {
        this(enabled, seedOffset, pitScale,
                DEFAULT.pitOctaves, DEFAULT.pitLacunarity, DEFAULT.pitGain,
                threshold, edgeFalloff, depth, DEFAULT.depthCurve, minLandness);
    }

    private static final Codec<AbyssPitConfig> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("enabled", DEFAULT.enabled)
                    .forGetter(AbyssPitConfig::enabled),
            Codec.INT.optionalFieldOf("seed_offset", DEFAULT.seedOffset)
                    .forGetter(AbyssPitConfig::seedOffset),
            Codec.INT.optionalFieldOf("pit_scale", DEFAULT.pitScale)
                    .forGetter(AbyssPitConfig::pitScale),
            Codec.INT.optionalFieldOf("pit_octaves", DEFAULT.pitOctaves)
                    .forGetter(AbyssPitConfig::pitOctaves),
            Codec.FLOAT.optionalFieldOf("pit_lacunarity", DEFAULT.pitLacunarity)
                    .forGetter(AbyssPitConfig::pitLacunarity),
            Codec.FLOAT.optionalFieldOf("pit_gain", DEFAULT.pitGain)
                    .forGetter(AbyssPitConfig::pitGain),
            Codec.FLOAT.optionalFieldOf("threshold", DEFAULT.threshold)
                    .forGetter(AbyssPitConfig::threshold),
            Codec.FLOAT.optionalFieldOf("edge_falloff", DEFAULT.edgeFalloff)
                    .forGetter(AbyssPitConfig::edgeFalloff),
            Codec.INT.optionalFieldOf("depth", DEFAULT.depth)
                    .forGetter(AbyssPitConfig::depth),
            Codec.FLOAT.optionalFieldOf("depth_curve", DEFAULT.depthCurve)
                    .forGetter(AbyssPitConfig::depthCurve),
            Codec.FLOAT.optionalFieldOf("min_landness", DEFAULT.minLandness)
                    .forGetter(AbyssPitConfig::minLandness)
    ).apply(instance, instance.stable(AbyssPitConfig::new)));

    public static final Codec<AbyssPitConfig> CODEC = BASE_CODEC.flatXmap(
            AbyssPitConfigValidator::validate,
            config -> DataResult.success(config));
}
