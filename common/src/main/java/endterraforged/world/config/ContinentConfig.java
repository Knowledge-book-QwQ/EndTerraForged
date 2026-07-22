/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later).
 */
package endterraforged.world.config;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import endterraforged.world.noise.DistanceFunction;

/**
 * Tunable macro-landmass parameters used by the continent modules and GUI
 * preview.
 *
 * <p>The record keeps topology-specific values together so switching between
 * complete continent, shattered continent and islands does not discard the
 * user's tuning. All values are immutable and safe to share across parallel
 * chunk generation.</p>
 */
public record ContinentConfig(int islandsScale,
                              int continentScale,
                              DistanceFunction continentShape,
                              float continentJitter,
                              float continentSkipping,
                              float continentSizeVariance,
                              int continentNoiseOctaves,
                              float continentNoiseGain,
                              float continentNoiseLacunarity,
                              float featureSpread,
                              float islandRadius,
                              float islandScatter,
                              float riftThreshold,
                              float riftStrength,
                              int warpScale,
                              float warpStrength,
                              int outerContinentScale,
                              LandmassVolumeMode landmassVolumeMode,
                              int shelfThickness,
                              int shelfEdgeThickness,
                              ContinentCoastShape coastShape,
                              int coastScale,
                              float coastStrength,
                              float coastCellBlend,
                              ContinentAlgorithm continentAlgorithm,
                              ContinentBandsConfig continentBands) {

    public ContinentConfig {
        Objects.requireNonNull(continentShape, "continentShape");
        Objects.requireNonNull(landmassVolumeMode, "landmassVolumeMode");
        Objects.requireNonNull(coastShape, "coastShape");
        Objects.requireNonNull(continentAlgorithm, "continentAlgorithm");
        Objects.requireNonNull(continentBands, "continentBands");
    }

    private static final ContinentConfig DEFAULTS = new ContinentConfig(
            400, 800, DistanceFunction.EUCLIDEAN, 1.0F, 0.25F, 0.25F,
            5, 0.26F, 4.33F, 1.0F, 0.6F, 0.5F, 0.75F, 0.85F,
            300, 40.0F, 4096, LandmassVolumeMode.FLOATING_SHELF, 160, 48,
            ContinentCoastShape.ORGANIC, 1200, 0.24F, 0.60F, ContinentAlgorithm.LEGACY_RADIAL,
            ContinentBandsConfig.DEFAULT);

    private static final ContinentConfig RTF_MULTI_DEFAULTS = new ContinentConfig(
            400, 3000, DistanceFunction.EUCLIDEAN, 0.7F, 0.25F, 0.25F,
            5, 0.26F, 4.33F, 1.0F, 0.6F, 0.5F, 0.75F, 0.85F,
            300, 40.0F, 4096, LandmassVolumeMode.FLOATING_SHELF, 160, 48,
            ContinentCoastShape.ORGANIC, 1200, 0.24F, 0.60F, ContinentAlgorithm.RTF_MULTI,
            ContinentBandsConfig.DEFAULT);

    private static final ContinentConfig LEGACY_DEFAULTS = new ContinentConfig(
            400, 800, DistanceFunction.EUCLIDEAN, 1.0F, 0.25F, 0.25F,
            5, 0.26F, 4.33F, 1.0F, 0.6F, 0.5F, 0.75F, 0.85F,
            300, 40.0F, 4096, LandmassVolumeMode.LEGACY_COLUMN, 160, 48,
            ContinentCoastShape.RADIAL_LEGACY, 1200, 0.24F, 0.60F, ContinentAlgorithm.LEGACY_RADIAL,
            ContinentBandsConfig.LEGACY_PASSTHROUGH);

    /**
     * Source compatibility constructor for code that predates continent band
     * persistence. New in-memory construction adopts the current bands;
     * persisted pre-v3 JSON is handled separately by {@link #CODEC}.
     */
    public ContinentConfig(int islandsScale, int continentScale, DistanceFunction continentShape,
                           float continentJitter, float continentSkipping, float continentSizeVariance,
                           int continentNoiseOctaves, float continentNoiseGain,
                           float continentNoiseLacunarity, float featureSpread, float islandRadius,
                           float islandScatter, float riftThreshold, float riftStrength,
                           int warpScale, float warpStrength, int outerContinentScale,
                           LandmassVolumeMode landmassVolumeMode, int shelfThickness,
                           int shelfEdgeThickness, ContinentCoastShape coastShape, int coastScale,
                           float coastStrength, float coastCellBlend,
                           ContinentAlgorithm continentAlgorithm) {
        this(islandsScale, continentScale, continentShape, continentJitter, continentSkipping,
                continentSizeVariance, continentNoiseOctaves, continentNoiseGain,
                continentNoiseLacunarity, featureSpread, islandRadius, islandScatter,
                riftThreshold, riftStrength, warpScale, warpStrength, outerContinentScale,
                landmassVolumeMode, shelfThickness, shelfEdgeThickness, coastShape, coastScale,
                coastStrength, coastCellBlend, continentAlgorithm, ContinentBandsConfig.DEFAULT);
    }

    /**
     * Compatibility constructor for presets created before organic coast
     * controls were introduced. It deliberately preserves the old radial
     * contour instead of silently reshaping existing worlds.
     */
    public ContinentConfig(int islandsScale, int continentScale, DistanceFunction continentShape,
                           float continentJitter, float continentSkipping, float continentSizeVariance,
                           int continentNoiseOctaves, float continentNoiseGain,
                           float continentNoiseLacunarity, float featureSpread, float islandRadius,
                           float islandScatter, float riftThreshold, float riftStrength,
                           int warpScale, float warpStrength, int outerContinentScale,
                           LandmassVolumeMode landmassVolumeMode, int shelfThickness,
                           int shelfEdgeThickness) {
        this(islandsScale, continentScale, continentShape, continentJitter, continentSkipping,
                continentSizeVariance, continentNoiseOctaves, continentNoiseGain,
                continentNoiseLacunarity, featureSpread, islandRadius, islandScatter,
                riftThreshold, riftStrength, warpScale, warpStrength, outerContinentScale,
                landmassVolumeMode, shelfThickness, shelfEdgeThickness,
                ContinentCoastShape.RADIAL_LEGACY, legacyDefaults().coastScale,
                legacyDefaults().coastStrength, legacyDefaults().coastCellBlend,
                ContinentAlgorithm.LEGACY_RADIAL, ContinentBandsConfig.DEFAULT);
    }

    /**
     * Compatibility constructor for presets created before outer continents
     * gained an independent macro scale.
     */
    public ContinentConfig(int islandsScale, int continentScale, DistanceFunction continentShape,
                           float continentJitter, float continentSkipping, float continentSizeVariance,
                           int continentNoiseOctaves, float continentNoiseGain,
                           float continentNoiseLacunarity, float featureSpread, float islandRadius,
                           float islandScatter, float riftThreshold, float riftStrength,
                           int warpScale, float warpStrength) {
        this(islandsScale, continentScale, continentShape, continentJitter, continentSkipping,
                continentSizeVariance, continentNoiseOctaves, continentNoiseGain,
                continentNoiseLacunarity, featureSpread, islandRadius, islandScatter,
                riftThreshold, riftStrength, warpScale, warpStrength,
                legacyDefaults().outerContinentScale,
                legacyDefaults().landmassVolumeMode,
                legacyDefaults().shelfThickness,
                legacyDefaults().shelfEdgeThickness,
                legacyDefaults().coastShape,
                legacyDefaults().coastScale,
                legacyDefaults().coastStrength,
                legacyDefaults().coastCellBlend,
                ContinentAlgorithm.LEGACY_RADIAL, ContinentBandsConfig.DEFAULT);
    }

    /**
     * Compatibility constructor for presets created before finite landmass
     * volumes were introduced.
     */
    public ContinentConfig(int islandsScale, int continentScale, DistanceFunction continentShape,
                           float continentJitter, float continentSkipping, float continentSizeVariance,
                           int continentNoiseOctaves, float continentNoiseGain,
                           float continentNoiseLacunarity, float featureSpread, float islandRadius,
                           float islandScatter, float riftThreshold, float riftStrength,
                           int warpScale, float warpStrength, int outerContinentScale) {
        this(islandsScale, continentScale, continentShape, continentJitter, continentSkipping,
                continentSizeVariance, continentNoiseOctaves, continentNoiseGain,
                continentNoiseLacunarity, featureSpread, islandRadius, islandScatter,
                riftThreshold, riftStrength, warpScale, warpStrength, outerContinentScale,
                legacyDefaults().landmassVolumeMode,
                legacyDefaults().shelfThickness,
                legacyDefaults().shelfEdgeThickness,
                legacyDefaults().coastShape,
                legacyDefaults().coastScale,
                legacyDefaults().coastStrength,
                legacyDefaults().coastCellBlend,
                ContinentAlgorithm.LEGACY_RADIAL, ContinentBandsConfig.DEFAULT);
    }

    /**
     * Compatibility constructor for presets created before continent algorithm
     * selection was introduced. Those worlds retain the legacy radial field.
     */
    public ContinentConfig(int islandsScale, int continentScale, DistanceFunction continentShape,
                           float continentJitter, float continentSkipping, float continentSizeVariance,
                           int continentNoiseOctaves, float continentNoiseGain,
                           float continentNoiseLacunarity, float featureSpread, float islandRadius,
                           float islandScatter, float riftThreshold, float riftStrength,
                           int warpScale, float warpStrength, int outerContinentScale,
                           LandmassVolumeMode landmassVolumeMode, int shelfThickness,
                           int shelfEdgeThickness, ContinentCoastShape coastShape, int coastScale,
                           float coastStrength, float coastCellBlend) {
        this(islandsScale, continentScale, continentShape, continentJitter, continentSkipping,
                continentSizeVariance, continentNoiseOctaves, continentNoiseGain,
                continentNoiseLacunarity, featureSpread, islandRadius, islandScatter,
                riftThreshold, riftStrength, warpScale, warpStrength, outerContinentScale,
                landmassVolumeMode, shelfThickness, shelfEdgeThickness, coastShape, coastScale,
                coastStrength, coastCellBlend, ContinentAlgorithm.LEGACY_RADIAL,
                ContinentBandsConfig.DEFAULT);
    }

    private static final Codec<DistanceFunction> DISTANCE_FUNCTION_CODEC = Codec.STRING.flatXmap(
            name -> {
                try {
                    return DataResult.success(DistanceFunction.valueOf(name));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown DistanceFunction: " + name);
                }
            },
            value -> DataResult.success(value.name()));

    private static final MapCodec<LegacyValues> LEGACY_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.INT.optionalFieldOf("islands_scale", defaults().islandsScale)
                            .forGetter(LegacyValues::islandsScale),
                    Codec.INT.optionalFieldOf("continent_scale", defaults().continentScale)
                            .forGetter(LegacyValues::continentScale),
                    DISTANCE_FUNCTION_CODEC.optionalFieldOf("continent_shape", defaults().continentShape)
                            .forGetter(LegacyValues::continentShape),
                    Codec.FLOAT.optionalFieldOf("continent_jitter", defaults().continentJitter)
                            .forGetter(LegacyValues::continentJitter),
                    Codec.FLOAT.optionalFieldOf("continent_skipping", defaults().continentSkipping)
                            .forGetter(LegacyValues::continentSkipping),
                    Codec.FLOAT.optionalFieldOf("continent_size_variance", defaults().continentSizeVariance)
                            .forGetter(LegacyValues::continentSizeVariance),
                    Codec.INT.optionalFieldOf("continent_noise_octaves", defaults().continentNoiseOctaves)
                            .forGetter(LegacyValues::continentNoiseOctaves),
                    Codec.FLOAT.optionalFieldOf("continent_noise_gain", defaults().continentNoiseGain)
                            .forGetter(LegacyValues::continentNoiseGain),
                    Codec.FLOAT.optionalFieldOf("continent_noise_lacunarity", defaults().continentNoiseLacunarity)
                            .forGetter(LegacyValues::continentNoiseLacunarity),
                    Codec.FLOAT.optionalFieldOf("feature_spread", defaults().featureSpread)
                            .forGetter(LegacyValues::featureSpread),
                    Codec.FLOAT.optionalFieldOf("island_radius", defaults().islandRadius)
                            .forGetter(LegacyValues::islandRadius),
                    Codec.FLOAT.optionalFieldOf("island_scatter", defaults().islandScatter)
                            .forGetter(LegacyValues::islandScatter),
                    Codec.FLOAT.optionalFieldOf("rift_threshold", defaults().riftThreshold)
                            .forGetter(LegacyValues::riftThreshold),
                    Codec.FLOAT.optionalFieldOf("rift_strength", defaults().riftStrength)
                            .forGetter(LegacyValues::riftStrength),
                    Codec.INT.optionalFieldOf("warp_scale", defaults().warpScale)
                            .forGetter(LegacyValues::warpScale),
                    Codec.FLOAT.optionalFieldOf("warp_strength", defaults().warpStrength)
                            .forGetter(LegacyValues::warpStrength)
            ).apply(instance, instance.stable(LegacyValues::new))
    );

    private static final Codec<ContinentConfig> BASE_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    LEGACY_CODEC.forGetter(ContinentConfig::legacyValues),
                    Codec.INT.optionalFieldOf("outer_continent_scale", defaults().outerContinentScale)
                            .forGetter(ContinentConfig::outerContinentScale),
                    LandmassVolumeMode.CODEC.optionalFieldOf("volume_mode",
                                    LandmassVolumeMode.LEGACY_COLUMN)
                            .forGetter(ContinentConfig::landmassVolumeMode),
                    Codec.INT.optionalFieldOf("shelf_thickness", defaults().shelfThickness)
                            .forGetter(ContinentConfig::shelfThickness),
                    Codec.INT.optionalFieldOf("shelf_edge_thickness", defaults().shelfEdgeThickness)
                            .forGetter(ContinentConfig::shelfEdgeThickness),
                    coastShapeCodec().optionalFieldOf("coast_shape", ContinentCoastShape.RADIAL_LEGACY)
                            .forGetter(ContinentConfig::coastShape),
                    Codec.INT.optionalFieldOf("coast_scale", defaults().coastScale)
                            .forGetter(ContinentConfig::coastScale),
                    Codec.FLOAT.optionalFieldOf("coast_strength", defaults().coastStrength)
                            .forGetter(ContinentConfig::coastStrength),
                    Codec.FLOAT.optionalFieldOf("coast_cell_blend", defaults().coastCellBlend)
                            .forGetter(ContinentConfig::coastCellBlend),
                    continentAlgorithmCodec().optionalFieldOf("algorithm", ContinentAlgorithm.LEGACY_RADIAL)
                            .forGetter(ContinentConfig::continentAlgorithm),
                    ContinentBandsConfig.CODEC.optionalFieldOf("bands",
                                    ContinentBandsConfig.LEGACY_PASSTHROUGH)
                            .forGetter(ContinentConfig::continentBands)
            ).apply(instance, (legacy, outerScale, volumeMode, shelfThickness, shelfEdgeThickness,
                               coastShape, coastScale, coastStrength, coastCellBlend, continentAlgorithm,
                               continentBands) ->
                    legacy.withCoast(outerScale, volumeMode, shelfThickness, shelfEdgeThickness,
                            coastShape, coastScale, coastStrength, coastCellBlend, continentAlgorithm,
                            continentBands))
    );

    /** Codec with decode-time validation for hand-edited preset files. */
    public static final Codec<ContinentConfig> CODEC = BASE_CODEC.flatXmap(
            ContinentConfigValidator::validate,
            config -> DataResult.success(config));

    /** Canonical defaults for editable continent assembly and preview. */
    public static ContinentConfig defaults() {
        return DEFAULTS;
    }

    /**
     * Recommended editable baseline for {@link ContinentAlgorithm#RTF_MULTI}.
     *
     * <p>This is intentionally a separate profile rather than the global
     * default: loading an existing preset must never silently reinterpret its
     * macro-continent scale merely because it selects a different algorithm.</p>
     */
    public static ContinentConfig rtfMultiDefaults() {
        return RTF_MULTI_DEFAULTS;
    }

    /** Defaults used when decoding a preset authored before finite volumes existed. */
    public static ContinentConfig legacyDefaults() {
        return LEGACY_DEFAULTS;
    }

    private LegacyValues legacyValues() {
        return new LegacyValues(islandsScale, continentScale, continentShape, continentJitter,
                continentSkipping, continentSizeVariance, continentNoiseOctaves, continentNoiseGain,
                continentNoiseLacunarity, featureSpread, islandRadius, islandScatter, riftThreshold,
                riftStrength, warpScale, warpStrength);
    }

    private static Codec<ContinentCoastShape> coastShapeCodec() {
        return Codec.STRING.flatXmap(
                name -> {
                    try {
                        return DataResult.success(ContinentCoastShape.valueOf(name));
                    } catch (IllegalArgumentException e) {
                        return DataResult.error(() -> "Unknown ContinentCoastShape: " + name);
                    }
                },
                value -> DataResult.success(value.name()));
    }

    private static Codec<ContinentAlgorithm> continentAlgorithmCodec() {
        return Codec.STRING.flatXmap(
                name -> {
                    try {
                        return DataResult.success(ContinentAlgorithm.valueOf(name));
                    } catch (IllegalArgumentException e) {
                        return DataResult.error(() -> "Unknown ContinentAlgorithm: " + name);
                    }
                },
                value -> DataResult.success(value.name()));
    }

    private record LegacyValues(int islandsScale, int continentScale, DistanceFunction continentShape,
                                float continentJitter, float continentSkipping,
                                float continentSizeVariance, int continentNoiseOctaves,
                                float continentNoiseGain, float continentNoiseLacunarity,
                                float featureSpread, float islandRadius, float islandScatter,
                                float riftThreshold, float riftStrength, int warpScale,
                                float warpStrength) {
        private ContinentConfig withCoast(int outerContinentScale,
                                          LandmassVolumeMode landmassVolumeMode,
                                          int shelfThickness,
                                          int shelfEdgeThickness,
                                          ContinentCoastShape coastShape,
                                          int coastScale,
                                          float coastStrength,
                                          float coastCellBlend,
                                          ContinentAlgorithm continentAlgorithm,
                                          ContinentBandsConfig continentBands) {
            return new ContinentConfig(islandsScale, continentScale, continentShape, continentJitter,
                    continentSkipping, continentSizeVariance, continentNoiseOctaves, continentNoiseGain,
                    continentNoiseLacunarity, featureSpread, islandRadius, islandScatter,
                    riftThreshold, riftStrength, warpScale, warpStrength, outerContinentScale,
                    landmassVolumeMode, shelfThickness, shelfEdgeThickness,
                    coastShape, coastScale, coastStrength, coastCellBlend, continentAlgorithm,
                    continentBands);
        }
    }
}
