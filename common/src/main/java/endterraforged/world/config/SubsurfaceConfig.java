package endterraforged.world.config;

import java.util.Optional;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import endterraforged.world.cave.EndCaveGraphPreviewMask;
import endterraforged.world.cave.EndCavePreviewMask;
import endterraforged.world.heightmap.EndSubsurface;

/**
 * Serializable root for underground terrain modifiers.
 */
public record SubsurfaceConfig(AbyssPitConfig abyssPitConfig,
                               CaveTunnelConfig caveTunnelConfig,
                               CaveSystemConfig caveSystemConfig,
                               CaveNetworkConfig caveNetworkConfig,
                               CaveChamberConfig caveChamberConfig) {

    public static final SubsurfaceConfig DISABLED =
            new SubsurfaceConfig(AbyssPitConfig.DISABLED, CaveTunnelConfig.DISABLED,
                    CaveSystemConfig.DISABLED, CaveNetworkConfig.DEFAULT,
                    CaveChamberConfig.DEFAULT);
    public static final SubsurfaceConfig DEFAULT = DISABLED;

    public SubsurfaceConfig(AbyssPitConfig abyssPitConfig) {
        this(abyssPitConfig, CaveTunnelConfig.DEFAULT);
    }

    public SubsurfaceConfig(AbyssPitConfig abyssPitConfig,
                            CaveTunnelConfig caveTunnelConfig) {
        this(abyssPitConfig, caveTunnelConfig, CaveSystemConfig.DEFAULT,
                CaveNetworkConfig.DEFAULT, CaveChamberConfig.DEFAULT);
    }

    private static final Codec<SubsurfaceConfig> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AbyssPitConfig.CODEC.optionalFieldOf("abyss", DEFAULT.abyssPitConfig)
                    .forGetter(SubsurfaceConfig::abyssPitConfig),
            CaveTunnelConfig.CODEC.optionalFieldOf("caves", DEFAULT.caveTunnelConfig)
                    .forGetter(SubsurfaceConfig::caveTunnelConfig),
            CaveSystemConfig.CODEC.optionalFieldOf("cave_system", DEFAULT.caveSystemConfig)
                    .forGetter(SubsurfaceConfig::caveSystemConfig),
            CaveNetworkConfig.CODEC.optionalFieldOf("cave_network", DEFAULT.caveNetworkConfig)
                    .forGetter(SubsurfaceConfig::caveNetworkConfig),
            CaveChamberConfig.CODEC.optionalFieldOf("cave_chambers", DEFAULT.caveChamberConfig)
                    .forGetter(SubsurfaceConfig::caveChamberConfig)
    ).apply(instance, instance.stable(SubsurfaceConfig::new)));

    private static final Codec<SubsurfaceConfig> VALIDATED_CODEC = BASE_CODEC.flatXmap(
            SubsurfaceConfigValidator::validate,
            config -> DataResult.success(config));

    public static final Codec<SubsurfaceConfig> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<SubsurfaceConfig, T>> decode(DynamicOps<T> ops, T input) {
            DataResult<Optional<String>> previewOnlyField = previewOnlyField(ops, input);
            if (previewOnlyField.error().isPresent()) {
                return previewOnlyField.map(field -> null);
            }
            return previewOnlyField.result()
                    .flatMap(optional -> optional)
                    .map(field -> DataResult.<Pair<SubsurfaceConfig, T>>error(() ->
                            "preview-only subsurface field '" + field
                                    + "' is not a preset config; use the editor preview controls instead"))
                    .orElseGet(() -> VALIDATED_CODEC.decode(ops, input));
        }

        @Override
        public <T> DataResult<T> encode(SubsurfaceConfig input, DynamicOps<T> ops, T prefix) {
            return VALIDATED_CODEC.encode(input, ops, prefix);
        }
    };

    public EndSubsurface buildRuntime(int seed) {
        return EndSubsurface.fromConfig(this, seed);
    }

    public EndCavePreviewMask buildCavePreviewMask(int seed) {
        return EndCavePreviewMask.fromConfig(this, seed);
    }

    public EndCaveGraphPreviewMask buildCaveGraphPreviewMask(int seed) {
        return EndCaveGraphPreviewMask.fromConfig(this, seed);
    }

    private static <T> DataResult<Optional<String>> previewOnlyField(DynamicOps<T> ops, T input) {
        DataResult<MapLike<T>> mapResult = ops.getMap(input);
        if (mapResult.error().isPresent()) {
            return mapResult.map(map -> null);
        }
        MapLike<T> map = mapResult.result().orElseThrow();
        String[] fields = {
                "cave_liquids",
                "cave_water",
                "cave_lava",
                "preview_mode",
                "slice_axis",
                "slice_offset",
                "blocks_per_pixel"
        };
        for (String field : fields) {
            if (map.get(field) != null) {
                return DataResult.success(Optional.of(field));
            }
        }
        return DataResult.success(Optional.empty());
    }
}
