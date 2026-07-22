package endterraforged.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Controls for region-scale cave graph generation.
 */
public record CaveNetworkConfig(int regionSize,
                                float networkDensity,
                                int chamberSpacing,
                                float branchingFactor,
                                float loopChance,
                                float maxSlope,
                                float minLandness) {

    public static final CaveNetworkConfig DEFAULT =
            new CaveNetworkConfig(1024, 0.35F, 384, 2.0F, 0.18F, 0.45F, 0.2F);

    private static final Codec<CaveNetworkConfig> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("region_size", DEFAULT.regionSize)
                    .forGetter(CaveNetworkConfig::regionSize),
            Codec.FLOAT.optionalFieldOf("network_density", DEFAULT.networkDensity)
                    .forGetter(CaveNetworkConfig::networkDensity),
            Codec.INT.optionalFieldOf("chamber_spacing", DEFAULT.chamberSpacing)
                    .forGetter(CaveNetworkConfig::chamberSpacing),
            Codec.FLOAT.optionalFieldOf("branching_factor", DEFAULT.branchingFactor)
                    .forGetter(CaveNetworkConfig::branchingFactor),
            Codec.FLOAT.optionalFieldOf("loop_chance", DEFAULT.loopChance)
                    .forGetter(CaveNetworkConfig::loopChance),
            Codec.FLOAT.optionalFieldOf("max_slope", DEFAULT.maxSlope)
                    .forGetter(CaveNetworkConfig::maxSlope),
            Codec.FLOAT.optionalFieldOf("min_landness", DEFAULT.minLandness)
                    .forGetter(CaveNetworkConfig::minLandness)
    ).apply(instance, instance.stable(CaveNetworkConfig::new)));

    public static final Codec<CaveNetworkConfig> CODEC = BASE_CODEC.flatXmap(
            CaveNetworkConfigValidator::validate,
            config -> DataResult.success(config));
}
