/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). The serialisable,
 * per-world dimension profile for the End — supersedes the stage-3.2
 * EndDefaults placeholder. Informed by RTF's preset model (MIT), lineage
 * TerraForged (dags) -> ReTerraForged (raccoonman) -> EndTerraForged, but
 * the End-specific three-axis (topology × sea × floating-islands) shape and
 * the erosion-config embedding are EndTerraForged extensions.
 */
package endterraforged.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import endterraforged.world.filter.ErosionConfig;

/**
 * The End's serialisable dimension profile: the single source of truth for the
 * dimension's shape and erosion tuning, loadable from JSON and consumed by the
 * worldgen pipeline through the {@link DimensionProfile} interface.
 *
 * <p><b>Role.</b> Stage 3.2 shipped {@code EndDefaults} as a hardcoded
 * placeholder so the MC-integration Mixin had a concrete profile to build an
 * {@link endterraforged.world.heightmap.EndHeightmap} from. Stage 5.1 replaces
 * that placeholder with this record: same default values (so production terrain
 * is unchanged), but DFU-serialisable and carrying the erosion config, so a
 * world can ship its own preset and the stage-5 GUI can bind to its fields.</p>
 *
 * <p><b>Fields.</b> The first seven components implement {@link DimensionProfile}
 * (world shape + the three orthogonal switches {@link SeaMode} /
 * {@link TopologyMode} / {@code floatingIslandsEnabled}). The eighth,
 * {@code erosionConfig}, is the stage-5.1 addition the ROADMAP calls out
 * ("暴露 ... 侵蚀参数"). Deeper tuning (continent cell scale, river density,
 * climate radius, ...) still lives in module {@code defaults()} factories and
 * is deferred to a later 5.x sub-step; this preset owns only what the ROADMAP
 * lists for 5.1.</p>
 *
 * <p><b>Serialisation.</b> {@link #CODEC} is a {@link RecordCodecBuilder} over
 * the eight components. Enum switches serialise by name via
 * {@code Codec.STRING.xmap}; {@link ErosionConfig} embeds its own codec. Every
 * field has a default in the codec, so a preset JSON may omit any subset and
 * inherit the End defaults — this is what lets partial preset files and the
 * future GUI work without spelling out every field.</p>
 *
 * <p><b>Thread safety.</b> A record of immutable primitives + an immutable
 * {@link ErosionConfig}; safe to share across parallel chunk-gen threads.</p>
 *
 * @param worldHeight           vertical size of the world in blocks
 * @param minY                  world Y of the lowest block
 * @param seaLevelY             world Y of sea level (meaningful only when
 *                              {@code seaMode} {@link SeaMode#hasSea()})
 * @param islandBaselineY       world Y of the no-sea reference surface
 * @param seaMode               how the dimension treats its sea level
 * @param topologyMode          macro shape of the landmass
 * @param floatingIslandsEnabled whether the extra floating-island overlay is on
 * @param erosionConfig         droplet-erosion tuning for the surface filter
 */
public record EndPreset(int worldHeight, int minY, int seaLevelY, int islandBaselineY,
                        SeaMode seaMode, TopologyMode topologyMode,
                        boolean floatingIslandsEnabled,
                        ErosionConfig erosionConfig) implements DimensionProfile {

    /**
     * String codec for the {@link SeaMode} switch (serialises by enum name).
     * Uses {@link Codec#flatXmap} so a typo'd mode name in a preset file
     * surfaces as a failed {@link DataResult} (which DFU reports cleanly) rather
     * than propagating {@link Enum#valueOf}'s {@link IllegalArgumentException}
     * out of {@code Codec.parse} — a raw exception would crash the worldgen
     * bootstrap instead of producing a readable "unknown SeaMode" error.
     */
    private static final Codec<SeaMode> SEA_MODE_CODEC = Codec.STRING.flatXmap(
            s -> {
                try {
                    return DataResult.success(SeaMode.valueOf(s));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown SeaMode: " + s);
                }
            },
            e -> DataResult.success(e.name())
    );

    /** String codec for the {@link TopologyMode} switch — see {@link #SEA_MODE_CODEC}. */
    private static final Codec<TopologyMode> TOPOLOGY_MODE_CODEC = Codec.STRING.flatXmap(
            s -> {
                try {
                    return DataResult.success(TopologyMode.valueOf(s));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown TopologyMode: " + s);
                }
            },
            e -> DataResult.success(e.name())
    );

    /**
     * DFU codec for {@link EndPreset}. Every field has a default equal to
     * {@link #defaults()}, so a preset JSON may omit any subset of fields and
     * still decode to a valid profile.
     *
     * <p>The builder is wrapped with {@link Codec#flatXmap} through
     * {@link EndPresetValidator#validate} on the decode side, so a
     * structurally-decoded preset with constraint-violating field values
     * (e.g. {@code "world_height": 100} which is not a multiple of 16,
     * or {@code "sea_level_y": 99999} which is outside the world's vertical
     * bounds) surfaces as a {@link DataResult#error} with a field-specific
     * message at decode time, rather than reaching the worldgen bootstrap
     * where vanilla's {@code DimensionType} constructor would throw a
     * confusing late-stage {@link IllegalArgumentException}. The encode side
     * is identity ({@link DataResult#success}) because a valid
     * {@link EndPreset} already satisfies the constraints by construction.</p>
     */
    private static final Codec<EndPreset> BASE_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.INT.optionalFieldOf("world_height", defaults().worldHeight)
                            .forGetter(EndPreset::worldHeight),
                    Codec.INT.optionalFieldOf("min_y", defaults().minY)
                            .forGetter(EndPreset::minY),
                    Codec.INT.optionalFieldOf("sea_level_y", defaults().seaLevelY)
                            .forGetter(EndPreset::seaLevelY),
                    Codec.INT.optionalFieldOf("island_baseline_y", defaults().islandBaselineY)
                            .forGetter(EndPreset::islandBaselineY),
                    SEA_MODE_CODEC.optionalFieldOf("sea_mode", defaults().seaMode)
                            .forGetter(EndPreset::seaMode),
                    TOPOLOGY_MODE_CODEC.optionalFieldOf("topology_mode", defaults().topologyMode)
                            .forGetter(EndPreset::topologyMode),
                    Codec.BOOL.optionalFieldOf("floating_islands", defaults().floatingIslandsEnabled)
                            .forGetter(EndPreset::floatingIslandsEnabled),
                    ErosionConfig.CODEC.optionalFieldOf("erosion", defaults().erosionConfig)
                            .forGetter(EndPreset::erosionConfig)
            ).apply(instance, instance.stable(EndPreset::new))
    );

    /**
     * Public {@link Codec} for {@link EndPreset}. Wraps {@link #BASE_CODEC}
     * with {@link EndPresetValidator#validate} via {@link Codec#flatXmap}
     * so decode-time constraint violations surface as {@link DataResult#error}
     * instead of crashing the worldgen bootstrap with a vanilla
     * {@link IllegalArgumentException} from inside {@code DimensionType}.
     */
    public static final Codec<EndPreset> CODEC = BASE_CODEC.flatXmap(
            EndPresetValidator::validate,
            preset -> DataResult.success(preset));

    /**
     * The End's canonical default preset: full RTF-matching height range
     * ({@code -2032..2032}, 4064 blocks), no sea, discrete islands, no extra
     * floating-island layer, classical droplet-erosion tuning. Matches the
     * values the stage-3.2 {@code EndDefaults} placeholder used, so switching
     * production to {@code EndPreset.defaults()} leaves terrain unchanged.
     */
    public static EndPreset defaults() {
        return new EndPreset(4064, -2032, 0, 0,
                SeaMode.NONE, TopologyMode.ISLANDS, false,
                ErosionConfig.DEFAULT);
    }
}
