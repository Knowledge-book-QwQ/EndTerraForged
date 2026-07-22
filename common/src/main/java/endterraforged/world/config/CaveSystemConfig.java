package endterraforged.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Global controls for the spectacle-first cave system.
 */
public record CaveSystemConfig(boolean enabled,
                               int seedOffset,
                               int depthStart,
                               int depthEnd,
                               float spectacleBias,
                               float connectivity,
                               float surfaceOpeningChance) {

    public static final CaveSystemConfig DISABLED =
            new CaveSystemConfig(false, 2400, 128, 1536, 0.75F, 0.65F, 0.03F);
    public static final CaveSystemConfig DEFAULT = DISABLED;

    private static final Codec<CaveSystemConfig> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("enabled", DEFAULT.enabled)
                    .forGetter(CaveSystemConfig::enabled),
            Codec.INT.optionalFieldOf("seed_offset", DEFAULT.seedOffset)
                    .forGetter(CaveSystemConfig::seedOffset),
            Codec.INT.optionalFieldOf("depth_start", DEFAULT.depthStart)
                    .forGetter(CaveSystemConfig::depthStart),
            Codec.INT.optionalFieldOf("depth_end", DEFAULT.depthEnd)
                    .forGetter(CaveSystemConfig::depthEnd),
            Codec.FLOAT.optionalFieldOf("spectacle_bias", DEFAULT.spectacleBias)
                    .forGetter(CaveSystemConfig::spectacleBias),
            Codec.FLOAT.optionalFieldOf("connectivity", DEFAULT.connectivity)
                    .forGetter(CaveSystemConfig::connectivity),
            Codec.FLOAT.optionalFieldOf("surface_opening_chance", DEFAULT.surfaceOpeningChance)
                    .forGetter(CaveSystemConfig::surfaceOpeningChance)
    ).apply(instance, instance.stable(CaveSystemConfig::new)));

    public static final Codec<CaveSystemConfig> CODEC = BASE_CODEC.flatXmap(
            CaveSystemConfigValidator::validate,
            config -> DataResult.success(config));
}
