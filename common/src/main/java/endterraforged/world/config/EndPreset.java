/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). The serialisable,
 * per-world dimension profile for the End. Informed by RTF's MIT-licensed
 * preset model, while the End-specific topology, sea, floating-island,
 * continent, terrain, climate, biome, subsurface and erosion settings remain
 * EndTerraForged extensions.
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
 * that placeholder with this record, making the profile DFU-serialisable and
 * carrying erosion configuration so a world can ship its own preset and the
 * GUI can bind to its fields. The current default is the standard player
 * envelope; larger envelopes remain explicit preset requests.</p>
 *
 * <p><b>Fields.</b> The first seven components implement {@link DimensionProfile}
 * (world shape + the three orthogonal switches {@link SeaMode} /
 * {@link TopologyMode} / {@code floatingIslandsEnabled}). The embedded
 * {@link ContinentConfig} owns macro-landmass tuning for full continents,
 * shattered continents and islands; {@link TerrainConfig} owns global terrain
 * shaping; {@link ClimateConfig} owns temperature, moisture and wind fields;
 * {@link SubsurfaceConfig} owns underground carving controls; and
 * {@link ErosionConfig} owns surface droplet erosion. Other module-specific
 * defaults can still live beside their generators until they need preset-level
 * editing.</p>
 *
 * <p><b>Serialisation.</b> {@link #CODEC} is a {@link RecordCodecBuilder} over
 * the record components. Enum switches serialise by name via
 * {@code Codec.STRING.xmap}; {@link ErosionConfig} embeds its own codec. Every
 * field has a default in the codec, so a preset JSON may omit any subset and
 * inherit the End defaults — this is what lets partial preset files and the
 * future GUI work without spelling out every field.</p>
 *
 * <p><b>Thread safety.</b> A record of immutable primitives and immutable
 * config records; safe to share across parallel chunk-gen threads.</p>
 *
 * @param worldHeight           vertical size of the world in blocks
 * @param minY                  world Y of the lowest block
 * @param seaLevelY             world Y of sea level (meaningful only when
 *                              {@code seaMode} {@link SeaMode#hasSea()})
 * @param islandBaselineY       world Y of the no-sea reference surface
 * @param seaMode               how the dimension treats its sea level
 * @param topologyMode          macro shape of the landmass
 * @param floatingIslandsEnabled whether the extra floating-island overlay is on
 * @param continentConfig       macro-landmass tuning for continents, rifts and islands
 * @param terrainConfig         global vertical / horizontal terrain shaping
 * @param climateConfig         climate noise controls for biome/surface variation
 * @param biomeLayoutConfig     scalar controls for geometric biome rings
 * @param subsurfaceConfig      underground carving controls
 * @param erosionConfig         droplet-erosion tuning for the surface filter
 * @param formatVersion         persisted migration format, not an editor control
 */
public record EndPreset(int worldHeight, int minY, int seaLevelY, int islandBaselineY,
                        SeaMode seaMode, TopologyMode topologyMode,
                        boolean floatingIslandsEnabled,
                        ContinentConfig continentConfig,
                        TerrainConfig terrainConfig,
                         ClimateConfig climateConfig,
                         BiomeLayoutConfig biomeLayoutConfig,
                         SubsurfaceConfig subsurfaceConfig,
                         ErosionConfig erosionConfig,
                         int formatVersion) implements DimensionProfile {

    /** Standard player-facing vertical envelope bundled with the default End data pack. */
    public static final WorldVerticalBounds DEFAULT_WORLD_BOUNDS = new WorldVerticalBounds(-256, 512);
    public static final int LEGACY_FORMAT_VERSION = 0;
    public static final int CURRENT_FORMAT_VERSION = 3;

    public EndPreset(int worldHeight, int minY, int seaLevelY, int islandBaselineY,
                     SeaMode seaMode, TopologyMode topologyMode,
                     boolean floatingIslandsEnabled,
                     ContinentConfig continentConfig,
                     TerrainConfig terrainConfig,
                     ErosionConfig erosionConfig) {
        this(worldHeight, minY, seaLevelY, islandBaselineY, seaMode, topologyMode,
                floatingIslandsEnabled, continentConfig, terrainConfig,
                ClimateConfig.DEFAULT, BiomeLayoutConfig.DEFAULT,
                SubsurfaceConfig.DEFAULT, erosionConfig, CURRENT_FORMAT_VERSION);
    }

    public EndPreset(int worldHeight, int minY, int seaLevelY, int islandBaselineY,
                     SeaMode seaMode, TopologyMode topologyMode,
                     boolean floatingIslandsEnabled,
                     ContinentConfig continentConfig,
                     TerrainConfig terrainConfig,
                     ClimateConfig climateConfig,
                     ErosionConfig erosionConfig) {
        this(worldHeight, minY, seaLevelY, islandBaselineY, seaMode, topologyMode,
                floatingIslandsEnabled, continentConfig, terrainConfig,
                climateConfig, BiomeLayoutConfig.DEFAULT,
                SubsurfaceConfig.DEFAULT, erosionConfig, CURRENT_FORMAT_VERSION);
    }

    public EndPreset(int worldHeight, int minY, int seaLevelY, int islandBaselineY,
                     SeaMode seaMode, TopologyMode topologyMode,
                     boolean floatingIslandsEnabled,
                     ContinentConfig continentConfig,
                     TerrainConfig terrainConfig,
                     ClimateConfig climateConfig,
                     BiomeLayoutConfig biomeLayoutConfig,
                     ErosionConfig erosionConfig) {
        this(worldHeight, minY, seaLevelY, islandBaselineY, seaMode, topologyMode,
                floatingIslandsEnabled, continentConfig, terrainConfig,
                climateConfig, biomeLayoutConfig, SubsurfaceConfig.DEFAULT, erosionConfig,
                CURRENT_FORMAT_VERSION);
    }

    /**
     * Compatibility constructor for call sites that predate format-versioned
     * preset migration. New in-memory presets always use the current format.
     */
    public EndPreset(int worldHeight, int minY, int seaLevelY, int islandBaselineY,
                     SeaMode seaMode, TopologyMode topologyMode,
                     boolean floatingIslandsEnabled,
                     ContinentConfig continentConfig,
                     TerrainConfig terrainConfig,
                     ClimateConfig climateConfig,
                     BiomeLayoutConfig biomeLayoutConfig,
                     SubsurfaceConfig subsurfaceConfig,
                     ErosionConfig erosionConfig) {
        this(worldHeight, minY, seaLevelY, islandBaselineY, seaMode, topologyMode,
                floatingIslandsEnabled, continentConfig, terrainConfig, climateConfig,
                biomeLayoutConfig, subsurfaceConfig, erosionConfig, CURRENT_FORMAT_VERSION);
    }

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
     * DFU codec for {@link EndPreset}. Most fields have current defaults, but
     * migration-sensitive fields use their historical values when omitted so
     * existing compact JSON is never silently reinterpreted as new terrain.
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
                    TOPOLOGY_MODE_CODEC.optionalFieldOf("topology_mode", TopologyMode.ISLANDS)
                            .forGetter(EndPreset::topologyMode),
                    Codec.BOOL.optionalFieldOf("floating_islands", defaults().floatingIslandsEnabled)
                            .forGetter(EndPreset::floatingIslandsEnabled),
                    ContinentConfig.CODEC.optionalFieldOf("continent", ContinentConfig.legacyDefaults())
                            .forGetter(EndPreset::continentConfig),
                    TerrainConfig.CODEC.optionalFieldOf("terrain", defaults().terrainConfig)
                            .forGetter(EndPreset::terrainConfig),
                    ClimateConfig.CODEC.optionalFieldOf("climate", defaults().climateConfig)
                            .forGetter(EndPreset::climateConfig),
                    BiomeLayoutConfig.CODEC.optionalFieldOf("biome_layout", defaults().biomeLayoutConfig)
                            .forGetter(EndPreset::biomeLayoutConfig),
                    SubsurfaceConfig.CODEC.optionalFieldOf("subsurface", defaults().subsurfaceConfig)
                            .forGetter(EndPreset::subsurfaceConfig),
                    ErosionConfig.CODEC.optionalFieldOf("erosion", defaults().erosionConfig)
                            .forGetter(EndPreset::erosionConfig),
                    Codec.INT.optionalFieldOf("format_version", LEGACY_FORMAT_VERSION)
                            .forGetter(EndPreset::formatVersion)
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

    /** Returns the persisted vertical envelope requested by this preset. */
    public WorldVerticalBounds worldBounds() {
        return new WorldVerticalBounds(this.minY, this.worldHeight);
    }

    /**
     * Returns this preset with only its runtime world envelope replaced.
     *
     * <p>This is used after Minecraft has selected the actual dimension data.
     * It deliberately preserves all terrain, climate and underground settings;
     * callers must validate reference Y values against {@code bounds} before
     * using this method.</p>
     */
    public EndPreset withWorldBounds(WorldVerticalBounds bounds) {
        java.util.Objects.requireNonNull(bounds, "bounds");
        return new EndPreset(bounds.height(), bounds.minY(), this.seaLevelY,
                this.islandBaselineY, this.seaMode, this.topologyMode,
                this.floatingIslandsEnabled, this.continentConfig, this.terrainConfig,
                this.climateConfig, this.biomeLayoutConfig, this.subsurfaceConfig,
                this.erosionConfig, this.formatVersion);
    }

    /**
     * The default player-facing End envelope: 512 blocks from -256 to 255.
     * This keeps the surface near Y=0 with comparable space above and below
     * it while avoiding the eightfold per-column cost of the former 4064-block
     * default. Larger envelopes will be explicit creation-time data-pack
     * choices, not mutable editor fields.
     */
    public static EndPreset defaults() {
        return new EndPreset(DEFAULT_WORLD_BOUNDS.height(), DEFAULT_WORLD_BOUNDS.minY(), 0, 0,
                SeaMode.NONE, TopologyMode.OUTER_CONTINENTS, false,
                ContinentConfig.rtfMultiDefaults(),
                TerrainConfig.DEFAULT,
                ClimateConfig.DEFAULT,
                BiomeLayoutConfig.DEFAULT,
                SubsurfaceConfig.DEFAULT,
                ErosionConfig.DEFAULT,
                CURRENT_FORMAT_VERSION);
    }

    /**
     * Historical default used only when a preset omits {@code format_version}.
     * It prevents existing compact {@code {}} files from silently changing
     * their topology when the current default evolves.
     */
    public static EndPreset legacyDefaults() {
        return new EndPreset(DEFAULT_WORLD_BOUNDS.height(), DEFAULT_WORLD_BOUNDS.minY(), 0, 0,
                SeaMode.NONE, TopologyMode.ISLANDS, false,
                ContinentConfig.legacyDefaults(),
                TerrainConfig.DEFAULT,
                ClimateConfig.DEFAULT,
                BiomeLayoutConfig.DEFAULT,
                SubsurfaceConfig.DEFAULT,
                ErosionConfig.DEFAULT,
                LEGACY_FORMAT_VERSION);
    }
}
