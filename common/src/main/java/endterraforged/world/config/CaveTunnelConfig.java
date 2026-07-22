package endterraforged.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Serializable scalar controls for the first cave-tunnel planning pass.
 *
 * <p>This config is intentionally not wired into production density carving
 * yet. It gives preset files, validators, builders and preview work a stable
 * contract before the more invasive 3D cave runtime is introduced.</p>
 */
public record CaveTunnelConfig(boolean enabled,
                               float entranceProbability,
                               float cheeseDepthOffset,
                               float cheeseProbability,
                               float spaghettiProbability,
                               float noodleProbability) {

    public static final CaveTunnelConfig DISABLED =
            new CaveTunnelConfig(false, 0.0F, 1.5625F, 0.0F, 0.0F, 0.0F);
    public static final CaveTunnelConfig DEFAULT = DISABLED;

    private static final Codec<CaveTunnelConfig> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("enabled", DEFAULT.enabled)
                    .forGetter(CaveTunnelConfig::enabled),
            Codec.FLOAT.optionalFieldOf("entrance_probability", DEFAULT.entranceProbability)
                    .forGetter(CaveTunnelConfig::entranceProbability),
            Codec.FLOAT.optionalFieldOf("cheese_depth_offset", DEFAULT.cheeseDepthOffset)
                    .forGetter(CaveTunnelConfig::cheeseDepthOffset),
            Codec.FLOAT.optionalFieldOf("cheese_probability", DEFAULT.cheeseProbability)
                    .forGetter(CaveTunnelConfig::cheeseProbability),
            Codec.FLOAT.optionalFieldOf("spaghetti_probability", DEFAULT.spaghettiProbability)
                    .forGetter(CaveTunnelConfig::spaghettiProbability),
            Codec.FLOAT.optionalFieldOf("noodle_probability", DEFAULT.noodleProbability)
                    .forGetter(CaveTunnelConfig::noodleProbability)
    ).apply(instance, instance.stable(CaveTunnelConfig::new)));

    public static final Codec<CaveTunnelConfig> CODEC = BASE_CODEC.flatXmap(
            CaveTunnelConfigValidator::validate,
            config -> DataResult.success(config));
}
