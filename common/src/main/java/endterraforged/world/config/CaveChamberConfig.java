package endterraforged.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Controls for giant landmark chambers.
 */
public record CaveChamberConfig(float chamberProbability,
                                int minRadius,
                                int maxRadius,
                                float verticalStretch,
                                float floorBias,
                                float roughness) {

    public static final CaveChamberConfig DEFAULT =
            new CaveChamberConfig(0.45F, 48, 224, 1.6F, 0.35F, 0.55F);

    private static final Codec<CaveChamberConfig> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("chamber_probability", DEFAULT.chamberProbability)
                    .forGetter(CaveChamberConfig::chamberProbability),
            Codec.INT.optionalFieldOf("min_radius", DEFAULT.minRadius)
                    .forGetter(CaveChamberConfig::minRadius),
            Codec.INT.optionalFieldOf("max_radius", DEFAULT.maxRadius)
                    .forGetter(CaveChamberConfig::maxRadius),
            Codec.FLOAT.optionalFieldOf("vertical_stretch", DEFAULT.verticalStretch)
                    .forGetter(CaveChamberConfig::verticalStretch),
            Codec.FLOAT.optionalFieldOf("floor_bias", DEFAULT.floorBias)
                    .forGetter(CaveChamberConfig::floorBias),
            Codec.FLOAT.optionalFieldOf("roughness", DEFAULT.roughness)
                    .forGetter(CaveChamberConfig::roughness)
    ).apply(instance, instance.stable(CaveChamberConfig::new)));

    public static final Codec<CaveChamberConfig> CODEC = BASE_CODEC.flatXmap(
            CaveChamberConfigValidator::validate,
            config -> DataResult.success(config));
}
