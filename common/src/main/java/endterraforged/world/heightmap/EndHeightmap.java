/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Informed by RTF's
 * Heightmap (MIT) — lineage TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged — but the composition is restructured:
 * RTF's Heightmap is a CellPopulator coupled to climate/rivers/biome and
 * packages vertical scale into each TerrainPopulator; here the continent is
 * a pure Noise node, the mountain layer is a pure [0,1] shape, and this class
 * owns the single composition + world-height scaling step.
 */
package endterraforged.world.heightmap;

import java.util.Objects;

import endterraforged.world.climate.ClimateModulator;
import endterraforged.world.config.ContinentConfig;
import endterraforged.world.config.ContinentCoastShape;
import endterraforged.world.config.ContinentAlgorithm;
import endterraforged.world.config.DimensionProfile;
import endterraforged.world.config.SeaMode;
import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.config.TerrainLayoutMode;
import endterraforged.world.config.TerrainShape;
import endterraforged.world.config.TopologyMode;
import endterraforged.world.continent.CompleteContinent;
import endterraforged.world.continent.Continent;
import endterraforged.world.continent.ContinentSignalBuffer;
import endterraforged.world.continent.ContinentSignals;
import endterraforged.world.continent.ContinentalShatteredContinent;
import endterraforged.world.continent.BandedContinent;
import endterraforged.world.continent.IslandsContinent;
import endterraforged.world.continent.OuterContinentsContinent;
import endterraforged.world.continent.RtfAdvancedContinent;
import endterraforged.world.continent.RtfMultiContinent;
import endterraforged.world.lake.EndLakeMap;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.Noises;
import endterraforged.world.noise.domain.Domain;
import endterraforged.world.noise.domain.Domains;
import endterraforged.world.river.EndRiverMap;
import endterraforged.world.terrain.TerrainRegionBuffer;

/**
 * The End dimension's master height field: composes the continent landness
 * with a mountain shape layer and scales the result to world height.
 *
 * <p><b>Composition.</b> The terrain field is {@code continent × mountains},
 * both in {@code [0,1]}. Where the continent says "no land" (landness 0), the
 * product is 0 — and thanks to {@code Multiply}'s zero short-circuit the
 * mountain layer is not even sampled there. Where the continent is solid, the
 * mountain shape passes through unattenuated, so peaks and valleys are driven
 * purely by the configured mountain recipe ({@link TerrainShape#SHATTERED_RIDGES}
 * by default).</p>
 *
 * <p><b>Vertical scaling.</b> The {@code [0,1]} terrain field is mapped to
 * world height as {@code surface + elevationRange × terrain}, where
 * {@code surface} and {@code elevationRange} come from {@link EndLevels}.
 * This puts the terrain floor at the dimension's reference surface (sea level
 * or island baseline, depending on {@link endterraforged.world.config.SeaMode})
 * and lets mountains rise to the world top. Everything <em>below</em> the
 * surface — seabed, void, NO_FLOOR carving — is a stage-2.6 post-process
 * concern; this class only owns the above-surface shape.</p>
 *
 * <p><b>Thread safety.</b> All fields are immutable after construction. The
 * continent and mountain noises are records / leaf types that are safe to
 * query from parallel chunk-gen threads.</p>
 */
public final class EndHeightmap {
    /** Horizontal distance, in blocks, between the centre and profile samples. */
    public static final float SURFACE_PROFILE_SAMPLE_DISTANCE = 4.0F;
    private static final ThreadLocal<ContinentSignalBuffer> CONTINENT_SIGNAL_SCRATCH =
            ThreadLocal.withInitial(ContinentSignalBuffer::new);
    private static final ThreadLocal<EndLandmassSignalBuffer> LANDMASS_SIGNAL_SCRATCH =
            ThreadLocal.withInitial(EndLandmassSignalBuffer::new);
    private static final ThreadLocal<TerrainRegionBuffer> TERRAIN_REGION_SCRATCH =
            ThreadLocal.withInitial(TerrainRegionBuffer::new);
    private static final ThreadLocal<EndTerrainSignalBuffer> TERRAIN_SIGNAL_SCRATCH =
            ThreadLocal.withInitial(EndTerrainSignalBuffer::new);

    private final Continent continent;
    private final Noise mountains;
    private final Noise terrain;
    private final EndTerrainComposer terrainComposer;
    private final EndTerrainRegionComposer terrainRegionComposer;
    private final EndTerrainEligibilityPolicy terrainEligibilityPolicy;
    private final EndLevels levels;
    private final SeaMode seaMode;
    private final EndLandmassVolume landmassVolume;
    private final float globalVerticalScale;
    private final float globalHorizontalScale;
    private final float terrainRegionScale;
    private final float terrainLayerAmplitudeScale;
    private final float terrainLayerHorizontalScale;
    private final boolean terrainUsesWorldCoordinates;
    private final boolean continentBandsActive;
    private final EndArchipelagoMask archipelagoMask;
    private final EndArchipelagoRelief archipelagoRelief;
    private final boolean archipelagoActive;
    /**
     * Optional river post-processor. When set, {@link #getHeight} runs the
     * river carver over the raw terrain. May be {@code null}.
     */
    private final EndRiverMap riverMap;

    /**
     * Optional lake post-processor. Runs after the river carver in
     * {@link #getHeight}, so a lake basin is carved on top of any river
     * valley — a river running through a lake carves into the lake floor, not
     * the raw terrain. May be {@code null}.
     */
    private final EndLakeMap lakeMap;

    /**
     * Optional climate modulator. Runs first in the {@link #getHeight} chain
     * (before rivers/lakes), so rivers carve the climate-modulated terrain.
     * May be {@code null} — when absent, getHeight is purely terrain → river
     * → lake.
     */
    private final ClimateModulator climateModulator;

    /**
     * Builds an EndHeightmap from a dimension profile, using the configured
     * {@link TerrainShape} as the mountain layer.
     *
     * @param profile the dimension shape (sea level, topology, height range)
     * @param seed    the world seed; drives both the mountain recipe and the
     *                continent cell layout. The same seed must be passed to
     *                {@link #getHeight} / {@link #getLandness} at query time.
     */
    public EndHeightmap(DimensionProfile profile, int seed) {
        this(profile, seed, buildMountains(profile, seed), null, null, null);
    }

    /**
     * Builds an EndHeightmap with an explicit mountain layer. Package-private:
     * lets tests (and future preset code) swap the mountain recipe without
     * touching the continent / levels wiring.
     *
     * @param profile   the dimension shape
     * @param seed      the world seed, used to build the continent warp
     * @param mountains a {@code [0,1]} mountain height-field (e.g.
     *                  {@link EndMountains#mountains1} or {@link EndMountains#mountains2})
     */
    EndHeightmap(DimensionProfile profile, int seed, Noise mountains) {
        this(profile, seed, mountains, null, null, null);
    }

    /**
     * Full constructor with optional climate / river / lake post-processors.
     * All other constructors delegate here. Package-private: the public seams
     * for adding post-processors are {@link #withClimate},
     * {@link #withRivers(EndRiverMap)}, {@link #withLakes(EndLakeMap)}.
     */
    EndHeightmap(DimensionProfile profile, int seed, Noise mountains,
                 ClimateModulator climateModulator, EndRiverMap riverMap, EndLakeMap lakeMap) {
        this.levels = new EndLevels(profile);
        this.continent = buildContinent(profile.topologyMode(), profile.continentConfig(), seed);
        this.landmassVolume = new EndLandmassVolume(profile.continentConfig(), this.levels, seed);
        this.mountains = mountains;
        this.terrain = Noises.mul(continent, mountains);
        this.seaMode = profile.seaMode();
        TerrainConfig terrainConfig = profile.terrainConfig();
        this.terrainComposer = new EndTerrainComposer(terrainConfig, seed);
        this.terrainRegionComposer = terrainConfig.terrainLayoutMode() == TerrainLayoutMode.REGION_PLANNED
                ? new EndTerrainRegionComposer(terrainConfig, seed)
                : null;
        this.terrainEligibilityPolicy = this.terrainRegionComposer != null
                ? new EndTerrainEligibilityPolicy(profile.topologyMode(), this.landmassVolume)
                : null;
        this.globalVerticalScale = terrainConfig.globalVerticalScale();
        this.globalHorizontalScale = terrainConfig.globalHorizontalScale();
        this.terrainRegionScale = terrainConfig.terrainRegionSize()
                / (float) TerrainConfig.DEFAULT.terrainRegionSize();
        TerrainLayerConfig mountainLayer = terrainConfig.mountains();
        this.terrainLayerAmplitudeScale =
                mountainLayer.weight() * mountainLayer.baseScale() * mountainLayer.verticalScale();
        this.terrainLayerHorizontalScale = Math.max(0.01F, mountainLayer.horizontalScale());
        this.terrainUsesWorldCoordinates = usesWorldCoordinates(
                this.globalHorizontalScale, this.terrainRegionScale, this.terrainLayerHorizontalScale);
        this.continentBandsActive = profile.topologyMode() == TopologyMode.OUTER_CONTINENTS
                && profile.continentConfig().continentAlgorithm().supportsContinentBands()
                && profile.continentConfig().continentBands().enabled();
        this.archipelagoActive = isArchipelagoActive(profile);
        this.archipelagoMask = this.archipelagoActive
                ? new EndArchipelagoMask(seed) : EndArchipelagoMask.DISABLED;
        this.archipelagoRelief = this.archipelagoActive
                ? new EndArchipelagoRelief(seed, profile.continentConfig().continentScale())
                : EndArchipelagoRelief.DISABLED;
        this.climateModulator = climateModulator;
        this.riverMap = riverMap;
        this.lakeMap = lakeMap;
    }

    /**
     * Returns a new EndHeightmap with the same continent / mountains / levels
     * and the same river / lake post-processors, but with the given climate
     * modulator attached. The original is unchanged (immutable). Pass
     * {@code null} to detach climate. Climate runs first in getHeight (before
     * rivers/lakes), so rivers carve the climate-modulated terrain.
     */
    public EndHeightmap withClimate(ClimateModulator climateModulator) {
        return new EndHeightmap(this.levels, this.continent, this.mountains, this.terrain,
                this.terrainComposer, this.terrainRegionComposer,
                this.terrainEligibilityPolicy,
                this.seaMode, this.landmassVolume, this.globalVerticalScale, this.globalHorizontalScale,
                this.terrainRegionScale, this.terrainLayerAmplitudeScale, this.terrainLayerHorizontalScale,
                this.continentBandsActive, this.archipelagoMask, this.archipelagoRelief,
                this.archipelagoActive,
                climateModulator, this.riverMap, this.lakeMap);
    }

    /**
     * Returns a new EndHeightmap with the same continent / mountains / levels
     * and the same lake post-processor, but with the given river post-processor
     * attached. The original is unchanged (immutable). Pass {@code null} to
     * detach rivers.
     */
    public EndHeightmap withRivers(EndRiverMap riverMap) {
        return new EndHeightmap(this.levels, this.continent, this.mountains, this.terrain,
                this.terrainComposer, this.terrainRegionComposer,
                this.terrainEligibilityPolicy,
                this.seaMode, this.landmassVolume, this.globalVerticalScale, this.globalHorizontalScale,
                this.terrainRegionScale, this.terrainLayerAmplitudeScale, this.terrainLayerHorizontalScale,
                this.continentBandsActive, this.archipelagoMask, this.archipelagoRelief,
                this.archipelagoActive,
                this.climateModulator, riverMap, this.lakeMap);
    }

    /**
     * Returns a new EndHeightmap with the same continent / mountains / levels
     * and the same river post-processor, but with the given lake post-processor
     * attached. The original is unchanged (immutable). Pass {@code null} to
     * detach lakes. Lakes run after rivers in {@link #getHeight}.
     */
    public EndHeightmap withLakes(EndLakeMap lakeMap) {
        return new EndHeightmap(this.levels, this.continent, this.mountains, this.terrain,
                this.terrainComposer, this.terrainRegionComposer,
                this.terrainEligibilityPolicy,
                this.seaMode, this.landmassVolume, this.globalVerticalScale, this.globalHorizontalScale,
                this.terrainRegionScale, this.terrainLayerAmplitudeScale, this.terrainLayerHorizontalScale,
                this.continentBandsActive, this.archipelagoMask, this.archipelagoRelief,
                this.archipelagoActive,
                this.climateModulator, this.riverMap, lakeMap);
    }

    private EndHeightmap(EndLevels levels, Continent continent, Noise mountains,
                          Noise terrain, EndTerrainComposer terrainComposer,
                          EndTerrainRegionComposer terrainRegionComposer,
                          EndTerrainEligibilityPolicy terrainEligibilityPolicy,
                          SeaMode seaMode, EndLandmassVolume landmassVolume,
                         float globalVerticalScale, float globalHorizontalScale,
                          float terrainRegionScale, float terrainLayerAmplitudeScale,
                           float terrainLayerHorizontalScale,
                           boolean continentBandsActive,
                            EndArchipelagoMask archipelagoMask,
                           EndArchipelagoRelief archipelagoRelief,
                           boolean archipelagoActive,
                           ClimateModulator climateModulator,
                         EndRiverMap riverMap, EndLakeMap lakeMap) {
        this.levels = levels;
        this.continent = continent;
        this.mountains = mountains;
        this.terrain = terrain;
        this.terrainComposer = terrainComposer;
        this.terrainRegionComposer = terrainRegionComposer;
        this.terrainEligibilityPolicy = terrainEligibilityPolicy;
        this.seaMode = seaMode;
        this.landmassVolume = landmassVolume;
        this.globalVerticalScale = globalVerticalScale;
        this.globalHorizontalScale = globalHorizontalScale;
        this.terrainRegionScale = terrainRegionScale;
        this.terrainLayerAmplitudeScale = terrainLayerAmplitudeScale;
        this.terrainLayerHorizontalScale = terrainLayerHorizontalScale;
        this.terrainUsesWorldCoordinates = usesWorldCoordinates(
                globalHorizontalScale, terrainRegionScale, terrainLayerHorizontalScale);
        this.continentBandsActive = continentBandsActive;
        this.archipelagoMask = Objects.requireNonNull(archipelagoMask, "archipelagoMask");
        this.archipelagoRelief = Objects.requireNonNull(archipelagoRelief, "archipelagoRelief");
        this.archipelagoActive = archipelagoActive;
        this.climateModulator = climateModulator;
        this.riverMap = riverMap;
        this.lakeMap = lakeMap;
    }

    /**
     * Final terrain height at {@code (x, z)}, with climate → rivers → lakes
     * applied if attached via {@link #withClimate} / {@link #withRivers} /
     * {@link #withLakes}.
     *
     * <p>Post-processors chain: raw terrain → climate modulator → river carver
     * → lake carver. Climate runs first so rivers/lakes carve the
     * climate-modulated surface. When none are attached, this is identical to
     * {@link #getTerrainHeight}.</p>
     *
     * <p>This is the value downstream consumers should query: EndDensity uses
     * it to decide column solidity, and the stage-3 DensityFunction bridge
     * will expose it as the dimension's {@code final_density}.</p>
     *
     * @return normalised height in {@code [surface, 1]} (post-climate, post-river, post-lake)
     */
    public float getHeight(float x, float z, int seed) {
        return getHeight(x, z, seed, 0.0F, 1.0F, false);
    }

    /**
     * Height path for density sampling when the continent gate is already
     * available for this exact world coordinate. Non-default horizontal
     * scaling keeps the original terrain path because it samples the
     * continent at transformed coordinates.
     */
    float getHeight(float x, float z, int seed, float landness) {
        return getHeight(x, z, seed, landness, getInlandness(x, z, seed), true);
    }

    /** Density-only fast path with both cached continent signals. */
    float getHeight(float x, float z, int seed, float landness, float inlandness) {
        return getHeight(x, z, seed, landness, inlandness, true);
    }

    /** Density path that reuses the complete cached continent signal. */
    float getHeight(float x, float z, int seed, ContinentSignalBuffer continentSignals) {
        Objects.requireNonNull(continentSignals, "continentSignals");
        return getHeight(x, z, seed, continentSignals.landness(), continentSignals.inlandness(), true);
    }

    /** Density path that reuses the complete cached mainland and archipelago signal. */
    public float getHeight(float x, float z, int seed, EndLandmassSignalBuffer landmassSignals) {
        Objects.requireNonNull(landmassSignals, "landmassSignals");
        float landness = landmassSignals.landness();
        float inlandness = landmassSignals.inlandness();
        float h = getTerrainHeightWithLandmass(x, z, seed, landmassSignals);
        if (this.climateModulator != null) {
            h = this.climateModulator.modulate(x, z, seed, this.levels, h);
        }
        if (this.riverMap != null) {
            h = this.riverMap.modifyHeight(x, z, seed, this, h, landness);
        }
        if (this.lakeMap != null) {
            h = this.lakeMap.modifyHeight(x, z, seed, this, h, landness);
        }
        return h;
    }

    private float getHeight(float x, float z, int seed, float landness, float inlandness,
                            boolean landnessKnown) {
        float h = landnessKnown
                ? getTerrainHeight(x, z, seed, landness, inlandness)
                : getTerrainHeight(x, z, seed);
        if (this.climateModulator != null) {
            h = this.climateModulator.modulate(x, z, seed, this.levels, h);
        }
        if (this.riverMap != null) {
            h = landnessKnown
                    ? this.riverMap.modifyHeight(x, z, seed, this, h, landness)
                    : this.riverMap.modifyHeight(x, z, seed, this, h);
        }
        if (this.lakeMap != null) {
            h = landnessKnown
                    ? this.lakeMap.modifyHeight(x, z, seed, this, h, landness)
                    : this.lakeMap.modifyHeight(x, z, seed, this, h);
        }
        return h;
    }

    /**
     * Raw terrain height at {@code (x, z)} — continent × mountains scaled to
     * world height, with <em>no</em> river carving. Used internally by
     * {@link EndRiverMap#modifyHeight} to sample source / terrain heights
     * without recursing through the carving step.
     *
     * <p>External callers should normally use {@link #getHeight}; this is
     * exposed for the river carver and for diagnostics that want to see the
     * pre-river shape.</p>
     *
     * @return normalised height in {@code [surface, 1]} (pre-river)
     */
    public float getTerrainHeight(float x, float z, int seed) {
        if (this.archipelagoActive) {
            EndLandmassSignalBuffer signals = LANDMASS_SIGNAL_SCRATCH.get();
            sampleLandmassSignals(x, z, seed, signals);
            return getTerrainHeightWithLandmass(x, z, seed, signals);
        }
        return getMainTerrainHeight(x, z, seed);
    }

    /** Returns raw terrain top while reusing a caller-sampled landmass signal. */
    public float getTerrainHeight(float x, float z, int seed,
                                  EndLandmassSignalBuffer landmassSignals) {
        return getTerrainHeightWithLandmass(x, z, seed,
                Objects.requireNonNull(landmassSignals, "landmassSignals"));
    }

    /** Returns the mainland terrain top before attached archipelago composition. */
    public float getMainlandTerrainHeight(float x, float z, int seed) {
        return getMainTerrainHeight(x, z, seed);
    }

    private float getMainTerrainHeight(float x, float z, int seed) {
        if (this.terrainRegionComposer != null) {
            ContinentSignalBuffer signals = CONTINENT_SIGNAL_SCRATCH.get();
            sampleContinentSignals(x, z, seed, signals);
            return composeTerrainHeight(x, z, seed, 0.0F, signals.landness(), signals.inlandness());
        }
        float horizontalScale = this.globalHorizontalScale
                * this.terrainRegionScale
                * this.terrainLayerHorizontalScale;
        float sampleX = x / horizontalScale;
        float sampleZ = z / horizontalScale;
        ContinentSignalBuffer signals = CONTINENT_SIGNAL_SCRATCH.get();
        sampleContinentSignals(sampleX, sampleZ, seed, signals);
        float landness = signals.landness();
        float terrain = landness != 0.0F
                ? landness * this.mountains.compute(sampleX, sampleZ, seed)
                : 0.0F;
        return composeTerrainHeight(x, z, seed, terrain, landness, signals.inlandness());
    }

    private float getTerrainHeightWithLandmass(float x, float z, int seed,
                                               EndLandmassSignalBuffer landmassSignals) {
        ContinentSignalBuffer mainland = landmassSignals.continentSignals();
        float mainlandTop = getMainTerrainHeight(x, z, seed, mainland);
        if (!this.archipelagoActive || landmassSignals.archipelagoLandness() <= 0.0F) {
            return mainlandTop;
        }
        float archipelagoTop = this.archipelagoRelief.top(x, z, this.levels,
                landmassSignals.archipelagoSignals());
        return Math.max(mainlandTop, archipelagoTop);
    }

    private float getMainTerrainHeight(float x, float z, int seed,
                                       ContinentSignalBuffer signals) {
        if (this.terrainRegionComposer != null) {
            return composeTerrainHeight(x, z, seed, 0.0F, signals.landness(), signals.inlandness());
        }
        if (!this.terrainUsesWorldCoordinates) {
            return getMainTerrainHeight(x, z, seed);
        }
        float terrain = signals.landness() != 0.0F
                ? signals.landness() * this.mountains.compute(x, z, seed)
                : 0.0F;
        return composeTerrainHeight(x, z, seed, terrain, signals.landness(), signals.inlandness());
    }

    private float getTerrainHeight(float x, float z, int seed, float landness, float inlandness) {
        if (this.terrainRegionComposer != null) {
            return composeTerrainHeight(x, z, seed, 0.0F, landness, inlandness);
        }
        if (!this.terrainUsesWorldCoordinates) {
            return getTerrainHeight(x, z, seed);
        }
        float terrain = landness != 0.0F
                ? landness * this.mountains.compute(x, z, seed)
                : 0.0F;
        return composeTerrainHeight(x, z, seed, terrain, landness, inlandness);
    }

    private float composeTerrainHeight(float x, float z, int seed, float terrain, float landness,
                                       float inlandness) {
        float reliefEnvelope = this.continentBandsActive
                ? this.terrainComposer.reliefEnvelope(inlandness)
                : 1.0F;
        float terrainHeight = Math.clamp(
                terrain
                        * this.globalVerticalScale
                        * this.terrainLayerAmplitudeScale
                        * reliefEnvelope,
                0.0F, 1.0F);
        terrainHeight += auxiliaryContribution(x, z, seed, landness, inlandness)
                * this.landmassVolume.edgeFade(landness)
                * reliefEnvelope;
        terrainHeight = Math.clamp(terrainHeight, 0.0F, 1.0F);
        return this.levels.surface + this.levels.elevationRange * terrainHeight;
    }

    private static boolean usesWorldCoordinates(float globalHorizontalScale,
                                                float terrainRegionScale,
                                                float terrainLayerHorizontalScale) {
        return globalHorizontalScale == 1.0F
                && terrainRegionScale == 1.0F
                && terrainLayerHorizontalScale == 1.0F;
    }

    /**
     * Runtime auxiliary terrain selected at {@code (x, z)}, or
     * {@link EndTerrainLayer#NONE} when all auxiliary layers are disabled.
     *
     * <p>This is primarily a preview/diagnostics hook. Terrain generation still
     * consumes the scalar height returned by {@link #getTerrainHeight}.</p>
     */
    public EndTerrainLayer auxiliaryTerrainAt(float x, float z, int seed) {
        if (this.terrainRegionComposer != null) {
            ContinentSignalBuffer signals = CONTINENT_SIGNAL_SCRATCH.get();
            sampleContinentSignals(x, z, seed, signals);
            return this.terrainRegionComposer.selectedLayer(x, z, TERRAIN_REGION_SCRATCH.get(),
                    signals.landness(), signals.inlandness(), this.terrainEligibilityPolicy);
        }
        return this.terrainComposer.selectedLayer(x, z, seed);
    }

    /**
     * Runtime auxiliary terrain blend at {@code (x, z)}. This mirrors the
     * contribution path used by {@link #getTerrainHeight} and is intended for
     * previews/diagnostics that need to visualise smooth terrain-layer borders.
     */
    public EndTerrainBlend auxiliaryTerrainBlendAt(float x, float z, int seed) {
        if (this.terrainRegionComposer != null) {
            ContinentSignalBuffer signals = CONTINENT_SIGNAL_SCRATCH.get();
            sampleContinentSignals(x, z, seed, signals);
            return this.terrainRegionComposer.selectedBlend(x, z, TERRAIN_REGION_SCRATCH.get(),
                    signals.landness(), signals.inlandness(), this.terrainEligibilityPolicy);
        }
        return this.terrainComposer.selectedBlend(x, z, seed);
    }

    /**
     * Samples the family-level terrain channels at {@code (x, z)} without
     * allocating. The returned height channel is the same auxiliary scalar
     * consumed by {@link #getTerrainHeight}; the other channels are runtime
     * inputs for future erosion and content consumers.
     *
     * @param x world X
     * @param z world Z
     * @param seed world seed namespace
     * @param output caller-owned destination buffer
     */
    public void sampleTerrainSignals(float x, float z, int seed, EndTerrainSignalBuffer output) {
        if (output == null) {
            throw new NullPointerException("output");
        }
        if (this.terrainRegionComposer != null) {
            ContinentSignalBuffer signals = CONTINENT_SIGNAL_SCRATCH.get();
            sampleContinentSignals(x, z, seed, signals);
            this.terrainRegionComposer.sampleSignals(x, z, seed, TERRAIN_REGION_SCRATCH.get(), output,
                    signals.landness(), signals.inlandness(), this.terrainEligibilityPolicy);
            return;
        }
        EndTerrainLayer layer = this.terrainComposer.selectedLayer(x, z, seed);
        float height = this.terrainComposer.auxiliaryContribution(x, z, seed);
        output.set(height, roughness(layer), resistance(layer), layer == EndTerrainLayer.NONE
                ? 0 : 1 << layer.ordinal());
    }

    /**
     * Samples a caller-owned raw surface profile without changing the default
     * height or density path.
     *
     * <p>The centre and four cardinal neighbours use the same raw top query
     * as {@link #getTerrainHeight(float, float, int)}. The method performs
     * four additional height samples only when explicitly called, making it a
     * suitable input seam for later erosion, content and diagnostic systems
     * without imposing a five-point cost on worldgen density sampling.</p>
     *
     * @param x world X
     * @param z world Z
     * @param seed world seed namespace
     * @param output caller-owned destination buffer
     */
    public void sampleTerrainProfile(float x, float z, int seed, EndTerrainProfileBuffer output) {
        if (output == null) {
            throw new NullPointerException("output");
        }
        EndTerrainSignalBuffer signals = TERRAIN_SIGNAL_SCRATCH.get();
        sampleTerrainSignals(x, z, seed, signals);

        float distance = SURFACE_PROFILE_SAMPLE_DISTANCE;
        float centre = getTerrainHeight(x, z, seed);
        float east = getTerrainHeight(x + distance, z, seed);
        float west = getTerrainHeight(x - distance, z, seed);
        float south = getTerrainHeight(x, z + distance, seed);
        float north = getTerrainHeight(x, z - distance, seed);
        // Raw terrain tops are normalized; restore world-block units before
        // deriving gradient signals so identical physical terrain remains
        // identical across world-height presets.
        float worldHeight = this.levels.worldHeight;
        float dx = (east - west) * worldHeight / (2.0F * distance);
        float dz = (south - north) * worldHeight / (2.0F * distance);
        float gradient = (float) Math.sqrt(dx * dx + dz * dz);
        float slope = gradient / (1.0F + gradient);
        float laplacian = (east + west + south + north - 4.0F * centre)
                * worldHeight / (distance * distance);
        float curvature = laplacian / (1.0F + Math.abs(laplacian));
        output.set(centre, worldHeight, slope, curvature, signals);
    }

    private float auxiliaryContribution(float x, float z, int seed, float landness, float inlandness) {
        if (this.terrainRegionComposer != null) {
            EndTerrainSignalBuffer signals = TERRAIN_SIGNAL_SCRATCH.get();
            this.terrainRegionComposer.sampleSignals(x, z, seed, TERRAIN_REGION_SCRATCH.get(), signals,
                    landness, inlandness, this.terrainEligibilityPolicy);
            return signals.height();
        }
        return this.terrainComposer.auxiliaryContribution(x, z, seed);
    }

    private static float roughness(EndTerrainLayer layer) {
        return switch (layer) {
            case PLAINS -> 0.18F;
            case HILLS -> 0.58F;
            case PLATEAU -> 0.42F;
            case MOUNTAINS -> 0.82F;
            case VOLCANO -> 0.90F;
            case NONE -> 0.0F;
        };
    }

    private static float resistance(EndTerrainLayer layer) {
        return switch (layer) {
            case PLAINS -> 0.28F;
            case HILLS -> 0.46F;
            case PLATEAU -> 0.78F;
            case MOUNTAINS -> 0.68F;
            case VOLCANO -> 0.34F;
            case NONE -> 0.0F;
        };
    }

    /** Returns final physical landness, including the controlled archipelago layer. */
    public float getLandness(float x, float z, int seed) {
        if (!this.archipelagoActive) {
            return this.continent.compute(x, z, seed);
        }
        EndLandmassSignalBuffer signals = LANDMASS_SIGNAL_SCRATCH.get();
        sampleLandmassSignals(x, z, seed, signals);
        return signals.landness();
    }

    /** Returns mainland landness without attached archipelago features. */
    public float getMainlandLandness(float x, float z, int seed) {
        return this.continent.compute(x, z, seed);
    }

    /** Returns a diagnostic snapshot of the continent signals at this world position. */
    public ContinentSignals getContinentSignals(float x, float z, int seed) {
        return this.continent.signalsAt(x, z, seed);
    }

    /** Returns the R2 inland-relief signal, or {@code 1} for legacy terrain paths. */
    public float getInlandness(float x, float z, int seed) {
        if (!this.continentBandsActive) {
            return 1.0F;
        }
        ContinentSignalBuffer signals = CONTINENT_SIGNAL_SCRATCH.get();
        sampleContinentSignals(x, z, seed, signals);
        return signals.inlandness();
    }

    /** Returns whether the experimental, non-persisted archipelago runtime is active. */
    public boolean archipelagoActive() {
        return this.archipelagoActive;
    }

    /** Returns the attached archipelago mask runtime. */
    public EndArchipelagoMask archipelagoMask() {
        return this.archipelagoMask;
    }

    /** Samples mainland and archipelago signals into caller-owned storage. */
    public void sampleLandmassSignals(float x, float z, int seed, EndLandmassSignalBuffer output) {
        Objects.requireNonNull(output, "output");
        output.resetDerived();
        sampleContinentSignals(x, z, seed, output.continentSignals());
        this.archipelagoMask.sample(x, z, seed, output.edge(), output.mainlandLandness(),
                output.archipelagoSignals());
        output.combine();
    }

    /** Writes continent signals into a caller-owned buffer without allocating. */
    void sampleContinentSignals(float x, float z, int seed, ContinentSignalBuffer output) {
        this.continent.sampleSignals(x, z, seed, output);
    }

    /** The {@link EndLevels} backing this heightmap's vertical scaling. */
    public EndLevels levels() {
        return this.levels;
    }

    /** The {@link SeaMode} of the backing dimension profile. */
    public SeaMode seaMode() {
        return this.seaMode;
    }

    /** The vertical volume policy paired with this heightmap's continent configuration. */
    public EndLandmassVolume landmassVolume() {
        return this.landmassVolume;
    }

    /** The composed terrain noise tree ({@code continent × mountains}). */
    public Noise terrain() {
        return this.terrain;
    }

    /** The continent module backing this heightmap. */
    public Continent continent() {
        return this.continent;
    }

    /**
     * The river post-processor attached via {@link #withRivers}, or
     * {@code null} if none. Callers that depend on the carved bed (e.g. the
     * stage-4.7 water placer) should check this and fail-fast rather than
     * silently read un-carved terrain.
     */
    public EndRiverMap riverMap() {
        return this.riverMap;
    }

    /** The lake post-processor attached via {@link #withLakes}, or {@code null}. */
    public endterraforged.world.lake.EndLakeMap lakeMap() {
        return this.lakeMap;
    }

    /** The climate modulator attached via {@link #withClimate}, or {@code null}. */
    public endterraforged.world.climate.ClimateModulator climateModulator() {
        return this.climateModulator;
    }

    // ----- continent assembly ----------------------------------------------

    /**
     * Selects and wires the continent module for the dimension's topology.
     *
     * <p>Both topologies share a configurable perlin-driven domain warp that
     * deforms the cell grid so islands / rifts don't sit on a perfect lattice.
     * The warp drivers use seeds offset by 100 from the world seed to stay
     * independent of the mountain recipe's seed slots.</p>
     *
     * <p><b>Defaults.</b> The continent parameters come from
     * {@link ContinentConfig}, so the same preset drives production terrain
     * and the editor preview.</p>
     */
    private static Continent buildContinent(TopologyMode mode, ContinentConfig config, int seed) {
        Continent rtfContinent = buildRtfContinent(mode, config, seed);
        if (rtfContinent != null) {
            Continent continent = rtfContinent;
            if (config.continentBands().enabled()) {
                continent = new BandedContinent(continent, config.continentBands());
            }
            return new OuterContinentsContinent(continent);
        }
        Domain warp = Domains.domain(
                Noises.perlin(seed + 100, config.warpScale(), config.continentNoiseOctaves(),
                        config.continentNoiseLacunarity(), config.continentNoiseGain()),
                Noises.perlin(seed + 101, config.warpScale(), config.continentNoiseOctaves(),
                        config.continentNoiseLacunarity(), config.continentNoiseGain()),
                Noises.constant(config.warpStrength()));
        Noise coastNoise = config.coastShape() == ContinentCoastShape.ORGANIC
                ? Noises.perlin(seed + 201, config.coastScale(), config.continentNoiseOctaves(),
                config.continentNoiseLacunarity(), config.continentNoiseGain())
                : Noises.constant(0.5F);
        return switch (mode) {
            case CONTINENTAL -> new CompleteContinent(1.0F);
            case OUTER_CONTINENTS -> new OuterContinentsContinent(new IslandsContinent(
                    1.0F / config.outerContinentScale(), config.featureSpread(), config.continentShape(),
                    config.continentJitter(), config.continentSkipping(), config.continentSizeVariance(),
                    config.islandRadius(), config.islandScatter(), warp, config.coastShape(), coastNoise,
                    config.coastStrength(), config.coastCellBlend()));
            case ISLANDS -> new IslandsContinent(
                    1.0F / config.islandsScale(), config.featureSpread(), config.continentShape(),
                    config.continentJitter(), config.continentSkipping(), config.continentSizeVariance(),
                    config.islandRadius(), config.islandScatter(), warp, config.coastShape(), coastNoise,
                    config.coastStrength(), config.coastCellBlend());
            case CONTINENTAL_SHATTERED -> new ContinentalShatteredContinent(
                    1.0F / config.continentScale(), config.featureSpread(), config.continentShape(),
                    config.continentJitter(), config.continentSkipping(), config.continentSizeVariance(),
                    config.riftThreshold(), config.riftStrength(), warp);
        };
    }

    private static Continent buildRtfContinent(TopologyMode mode, ContinentConfig config, int seed) {
        if (mode != TopologyMode.OUTER_CONTINENTS) {
            return null;
        }
        return switch (config.continentAlgorithm()) {
            case RTF_MULTI -> new RtfMultiContinent(seed, config);
            case RTF_ADVANCED -> new RtfAdvancedContinent(seed, config);
            default -> null;
        };
    }

    private static boolean isArchipelagoActive(DimensionProfile profile) {
        return profile.topologyMode() == TopologyMode.OUTER_CONTINENTS
                && profile.continentConfig().continentAlgorithm() == ContinentAlgorithm.RTF_MULTI
                && profile.terrainConfig().terrainLayoutMode() == TerrainLayoutMode.REGION_PLANNED;
    }

    private static Noise buildMountains(DimensionProfile profile, int seed) {
        TerrainConfig terrainConfig = profile.terrainConfig();
        int terrainSeed = seed + terrainConfig.terrainSeedOffset();
        return switch (terrainConfig.terrainShape()) {
            case ROLLING_RIDGES -> EndMountains.mountains1(terrainSeed);
            case SHATTERED_RIDGES -> EndMountains.mountains2(terrainSeed);
        };
    }
}
