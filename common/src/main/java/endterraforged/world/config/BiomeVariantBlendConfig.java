package endterraforged.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Scalar controls for climate-variant blending inside biome cells.
 */
public record BiomeVariantBlendConfig(int scale, int octaves) {

    public static final BiomeVariantBlendConfig DEFAULT =
            new BiomeVariantBlendConfig(50, 2);

    private static final Codec<BiomeVariantBlendConfig> BASE_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("scale", DEFAULT.scale)
                            .forGetter(BiomeVariantBlendConfig::scale),
                    Codec.INT.optionalFieldOf("octaves", DEFAULT.octaves)
                            .forGetter(BiomeVariantBlendConfig::octaves)
            ).apply(instance, instance.stable(BiomeVariantBlendConfig::new)));

    public static final Codec<BiomeVariantBlendConfig> CODEC = BASE_CODEC.flatXmap(
            BiomeVariantBlendConfigValidator::validate,
            config -> DataResult.success(config));
}
