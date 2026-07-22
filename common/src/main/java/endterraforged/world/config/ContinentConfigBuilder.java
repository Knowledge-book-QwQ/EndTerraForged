package endterraforged.world.config;

import java.util.Objects;

import com.mojang.serialization.DataResult;

import endterraforged.world.noise.DistanceFunction;

/**
 * Mutable editing state for {@link ContinentConfig}. {@link #build()} uses
 * the same validator as the codec so GUI edits and imported JSON obey one
 * contract before reaching world generation.
 */
public final class ContinentConfigBuilder {

    private int islandsScale;
    private int continentScale;
    private DistanceFunction continentShape;
    private float continentJitter;
    private float continentSkipping;
    private float continentSizeVariance;
    private int continentNoiseOctaves;
    private float continentNoiseGain;
    private float continentNoiseLacunarity;
    private float featureSpread;
    private float islandRadius;
    private float islandScatter;
    private float riftThreshold;
    private float riftStrength;
    private int warpScale;
    private float warpStrength;
    private int outerContinentScale;
    private LandmassVolumeMode landmassVolumeMode;
    private int shelfThickness;
    private int shelfEdgeThickness;
    private ContinentCoastShape coastShape;
    private int coastScale;
    private float coastStrength;
    private float coastCellBlend;
    private ContinentAlgorithm continentAlgorithm;
    private ContinentBandsConfig continentBands;

    public ContinentConfigBuilder() {
        this(ContinentConfig.defaults());
    }

    public ContinentConfigBuilder(ContinentConfig source) {
        Objects.requireNonNull(source, "source");
        this.islandsScale = source.islandsScale();
        this.continentScale = source.continentScale();
        this.continentShape = source.continentShape();
        this.continentJitter = source.continentJitter();
        this.continentSkipping = source.continentSkipping();
        this.continentSizeVariance = source.continentSizeVariance();
        this.continentNoiseOctaves = source.continentNoiseOctaves();
        this.continentNoiseGain = source.continentNoiseGain();
        this.continentNoiseLacunarity = source.continentNoiseLacunarity();
        this.featureSpread = source.featureSpread();
        this.islandRadius = source.islandRadius();
        this.islandScatter = source.islandScatter();
        this.riftThreshold = source.riftThreshold();
        this.riftStrength = source.riftStrength();
        this.warpScale = source.warpScale();
        this.warpStrength = source.warpStrength();
        this.outerContinentScale = source.outerContinentScale();
        this.landmassVolumeMode = source.landmassVolumeMode();
        this.shelfThickness = source.shelfThickness();
        this.shelfEdgeThickness = source.shelfEdgeThickness();
        this.coastShape = source.coastShape();
        this.coastScale = source.coastScale();
        this.coastStrength = source.coastStrength();
        this.coastCellBlend = source.coastCellBlend();
        this.continentAlgorithm = source.continentAlgorithm();
        this.continentBands = source.continentBands();
    }

    public ContinentConfig build() {
        ContinentConfig config = new ContinentConfig(islandsScale, continentScale, continentShape,
                continentJitter, continentSkipping, continentSizeVariance,
                continentNoiseOctaves, continentNoiseGain, continentNoiseLacunarity,
                featureSpread, islandRadius, islandScatter, riftThreshold,
                riftStrength, warpScale, warpStrength, outerContinentScale,
                landmassVolumeMode, shelfThickness, shelfEdgeThickness,
                coastShape, coastScale, coastStrength, coastCellBlend, continentAlgorithm, continentBands);
        DataResult<ContinentConfig> result = ContinentConfigValidator.validate(config);
        return result.result().orElseThrow(() -> invalidState(result));
    }

    private static IllegalStateException invalidState(DataResult<?> result) {
        String message = result.error().map(error -> error.message()).orElse("unknown validation error");
        return new IllegalStateException("invalid continent config builder state: " + message);
    }

    public ContinentConfigBuilder reset() {
        ContinentConfig defaults = ContinentConfig.defaults();
        this.islandsScale = defaults.islandsScale();
        this.continentScale = defaults.continentScale();
        this.continentShape = defaults.continentShape();
        this.continentJitter = defaults.continentJitter();
        this.continentSkipping = defaults.continentSkipping();
        this.continentSizeVariance = defaults.continentSizeVariance();
        this.continentNoiseOctaves = defaults.continentNoiseOctaves();
        this.continentNoiseGain = defaults.continentNoiseGain();
        this.continentNoiseLacunarity = defaults.continentNoiseLacunarity();
        this.featureSpread = defaults.featureSpread();
        this.islandRadius = defaults.islandRadius();
        this.islandScatter = defaults.islandScatter();
        this.riftThreshold = defaults.riftThreshold();
        this.riftStrength = defaults.riftStrength();
        this.warpScale = defaults.warpScale();
        this.warpStrength = defaults.warpStrength();
        this.outerContinentScale = defaults.outerContinentScale();
        this.landmassVolumeMode = defaults.landmassVolumeMode();
        this.shelfThickness = defaults.shelfThickness();
        this.shelfEdgeThickness = defaults.shelfEdgeThickness();
        this.coastShape = defaults.coastShape();
        this.coastScale = defaults.coastScale();
        this.coastStrength = defaults.coastStrength();
        this.coastCellBlend = defaults.coastCellBlend();
        this.continentAlgorithm = defaults.continentAlgorithm();
        this.continentBands = defaults.continentBands();
        return this;
    }

    /**
     * Resets the builder to the recommended baseline for its currently selected
     * macro-continent algorithm.
     *
     * <p>Algorithm profiles are an editor-side explicit action. They are never
     * inferred during preset decoding, which keeps saved worlds stable across
     * releases.</p>
     */
    public ContinentConfigBuilder resetForCurrentAlgorithm() {
        ContinentAlgorithm algorithm = this.continentAlgorithm;
        reset();
        return applyRecommendedProfile(algorithm);
    }

    public int islandsScale() { return islandsScale; }
    public int continentScale() { return continentScale; }
    public DistanceFunction continentShape() { return continentShape; }
    public float continentJitter() { return continentJitter; }
    public float continentSkipping() { return continentSkipping; }
    public float continentSizeVariance() { return continentSizeVariance; }
    public int continentNoiseOctaves() { return continentNoiseOctaves; }
    public float continentNoiseGain() { return continentNoiseGain; }
    public float continentNoiseLacunarity() { return continentNoiseLacunarity; }
    public float featureSpread() { return featureSpread; }
    public float islandRadius() { return islandRadius; }
    public float islandScatter() { return islandScatter; }
    public float riftThreshold() { return riftThreshold; }
    public float riftStrength() { return riftStrength; }
    public int warpScale() { return warpScale; }
    public float warpStrength() { return warpStrength; }
    public int outerContinentScale() { return outerContinentScale; }
    public LandmassVolumeMode landmassVolumeMode() { return landmassVolumeMode; }
    public int shelfThickness() { return shelfThickness; }
    public int shelfEdgeThickness() { return shelfEdgeThickness; }
    public ContinentCoastShape coastShape() { return coastShape; }
    public int coastScale() { return coastScale; }
    public float coastStrength() { return coastStrength; }
    public float coastCellBlend() { return coastCellBlend; }
    public ContinentAlgorithm continentAlgorithm() { return continentAlgorithm; }
    public ContinentBandsConfig continentBands() { return continentBands; }

    public ContinentConfigBuilder islandsScale(int value) { this.islandsScale = value; return this; }
    public ContinentConfigBuilder continentScale(int value) { this.continentScale = value; return this; }
    public ContinentConfigBuilder continentShape(DistanceFunction value) { this.continentShape = Objects.requireNonNull(value); return this; }
    public ContinentConfigBuilder continentJitter(float value) { this.continentJitter = value; return this; }
    public ContinentConfigBuilder continentSkipping(float value) { this.continentSkipping = value; return this; }
    public ContinentConfigBuilder continentSizeVariance(float value) { this.continentSizeVariance = value; return this; }
    public ContinentConfigBuilder continentNoiseOctaves(int value) { this.continentNoiseOctaves = value; return this; }
    public ContinentConfigBuilder continentNoiseGain(float value) { this.continentNoiseGain = value; return this; }
    public ContinentConfigBuilder continentNoiseLacunarity(float value) { this.continentNoiseLacunarity = value; return this; }
    public ContinentConfigBuilder featureSpread(float value) { this.featureSpread = value; return this; }
    public ContinentConfigBuilder islandRadius(float value) { this.islandRadius = value; return this; }
    public ContinentConfigBuilder islandScatter(float value) { this.islandScatter = value; return this; }
    public ContinentConfigBuilder riftThreshold(float value) { this.riftThreshold = value; return this; }
    public ContinentConfigBuilder riftStrength(float value) { this.riftStrength = value; return this; }
    public ContinentConfigBuilder warpScale(int value) { this.warpScale = value; return this; }
    public ContinentConfigBuilder warpStrength(float value) { this.warpStrength = value; return this; }
    public ContinentConfigBuilder outerContinentScale(int value) { this.outerContinentScale = value; return this; }
    public ContinentConfigBuilder landmassVolumeMode(LandmassVolumeMode value) { this.landmassVolumeMode = Objects.requireNonNull(value); return this; }
    public ContinentConfigBuilder shelfThickness(int value) { this.shelfThickness = value; return this; }
    public ContinentConfigBuilder shelfEdgeThickness(int value) { this.shelfEdgeThickness = value; return this; }
    public ContinentConfigBuilder coastShape(ContinentCoastShape value) { this.coastShape = Objects.requireNonNull(value); return this; }
    public ContinentConfigBuilder coastScale(int value) { this.coastScale = value; return this; }
    public ContinentConfigBuilder coastStrength(float value) { this.coastStrength = value; return this; }
    public ContinentConfigBuilder coastCellBlend(float value) { this.coastCellBlend = value; return this; }
    public ContinentConfigBuilder continentAlgorithm(ContinentAlgorithm value) {
        this.continentAlgorithm = Objects.requireNonNull(value);
        return this;
    }

    /**
     * Applies the documented baseline for a macro-continent algorithm.
     *
     * <p>Only an explicit editor action should call this method. Normal algorithm
     * assignment deliberately preserves the current values so programmatic
     * callers can construct custom cross-algorithm presets.</p>
     */
    public ContinentConfigBuilder applyRecommendedProfile(ContinentAlgorithm value) {
        ContinentAlgorithm algorithm = Objects.requireNonNull(value);
        if (algorithm != ContinentAlgorithm.RTF_MULTI) {
            return continentAlgorithm(algorithm);
        }

        ContinentConfig defaults = ContinentConfig.rtfMultiDefaults();
        this.continentScale = defaults.continentScale();
        this.continentShape = defaults.continentShape();
        this.continentJitter = defaults.continentJitter();
        this.continentSkipping = defaults.continentSkipping();
        this.continentSizeVariance = defaults.continentSizeVariance();
        this.continentNoiseOctaves = defaults.continentNoiseOctaves();
        this.continentNoiseGain = defaults.continentNoiseGain();
        this.continentNoiseLacunarity = defaults.continentNoiseLacunarity();
        this.continentAlgorithm = algorithm;
        return this;
    }

    public ContinentConfigBuilder continentBands(ContinentBandsConfig value) {
        this.continentBands = Objects.requireNonNull(value);
        return this;
    }
}
