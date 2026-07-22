package endterraforged.world.config;

import java.util.Objects;

import com.mojang.serialization.DataResult;

/**
 * Mutable editing state for {@link ClimateConfig}. GUI screens use this builder
 * so live edits and preset JSON share the same validation contract.
 */
public final class ClimateConfigBuilder {

    private float climateRadius;
    private int temperatureScale;
    private int temperatureSeedOffset;
    private int moistureScale;
    private int moistureSeedOffset;
    private int windScale;
    private float perturbation;
    private float temperatureFalloff;
    private float temperatureMin;
    private float temperatureMax;
    private float temperatureBias;
    private float moistureFalloff;
    private float moistureMin;
    private float moistureMax;
    private float moistureBias;

    public ClimateConfigBuilder() {
        this(ClimateConfig.DEFAULT);
    }

    public ClimateConfigBuilder(ClimateConfig source) {
        load(source);
    }

    public ClimateConfig build() {
        ClimateConfig config = new ClimateConfig(
                climateRadius, temperatureScale, temperatureSeedOffset,
                moistureScale, moistureSeedOffset, windScale, perturbation,
                temperatureFalloff,
                temperatureMin, temperatureMax, temperatureBias,
                moistureFalloff,
                moistureMin, moistureMax, moistureBias);
        DataResult<ClimateConfig> result = ClimateConfigValidator.validate(config);
        return result.result().orElseThrow(() -> invalidState(result));
    }

    public ClimateConfigBuilder reset() {
        load(ClimateConfig.DEFAULT);
        return this;
    }

    private void load(ClimateConfig source) {
        Objects.requireNonNull(source, "source");
        this.climateRadius = source.climateRadius();
        this.temperatureScale = source.temperatureScale();
        this.temperatureSeedOffset = source.temperatureSeedOffset();
        this.moistureScale = source.moistureScale();
        this.moistureSeedOffset = source.moistureSeedOffset();
        this.windScale = source.windScale();
        this.perturbation = source.perturbation();
        this.temperatureFalloff = source.temperatureFalloff();
        this.temperatureMin = source.temperatureMin();
        this.temperatureMax = source.temperatureMax();
        this.temperatureBias = source.temperatureBias();
        this.moistureFalloff = source.moistureFalloff();
        this.moistureMin = source.moistureMin();
        this.moistureMax = source.moistureMax();
        this.moistureBias = source.moistureBias();
    }

    private static IllegalStateException invalidState(DataResult<?> result) {
        String message = result.error().map(error -> error.message()).orElse("unknown validation error");
        return new IllegalStateException("invalid climate config builder state: " + message);
    }

    public float climateRadius() {
        return climateRadius;
    }

    public int temperatureScale() {
        return temperatureScale;
    }

    public int temperatureSeedOffset() {
        return temperatureSeedOffset;
    }

    public int moistureScale() {
        return moistureScale;
    }

    public int moistureSeedOffset() {
        return moistureSeedOffset;
    }

    public int windScale() {
        return windScale;
    }

    public float perturbation() {
        return perturbation;
    }

    public float temperatureMin() {
        return temperatureMin;
    }

    public float temperatureFalloff() {
        return temperatureFalloff;
    }

    public float temperatureMax() {
        return temperatureMax;
    }

    public float temperatureBias() {
        return temperatureBias;
    }

    public float moistureMin() {
        return moistureMin;
    }

    public float moistureFalloff() {
        return moistureFalloff;
    }

    public float moistureMax() {
        return moistureMax;
    }

    public float moistureBias() {
        return moistureBias;
    }

    public ClimateConfigBuilder climateRadius(float climateRadius) {
        this.climateRadius = climateRadius;
        return this;
    }

    public ClimateConfigBuilder temperatureScale(int temperatureScale) {
        this.temperatureScale = temperatureScale;
        return this;
    }

    public ClimateConfigBuilder temperatureSeedOffset(int temperatureSeedOffset) {
        this.temperatureSeedOffset = temperatureSeedOffset;
        return this;
    }

    public ClimateConfigBuilder moistureScale(int moistureScale) {
        this.moistureScale = moistureScale;
        return this;
    }

    public ClimateConfigBuilder moistureSeedOffset(int moistureSeedOffset) {
        this.moistureSeedOffset = moistureSeedOffset;
        return this;
    }

    public ClimateConfigBuilder windScale(int windScale) {
        this.windScale = windScale;
        return this;
    }

    public ClimateConfigBuilder perturbation(float perturbation) {
        this.perturbation = perturbation;
        return this;
    }

    public ClimateConfigBuilder temperatureFalloff(float temperatureFalloff) {
        this.temperatureFalloff = temperatureFalloff;
        return this;
    }

    public ClimateConfigBuilder temperatureMin(float temperatureMin) {
        this.temperatureMin = temperatureMin;
        return this;
    }

    public ClimateConfigBuilder temperatureMax(float temperatureMax) {
        this.temperatureMax = temperatureMax;
        return this;
    }

    public ClimateConfigBuilder temperatureBias(float temperatureBias) {
        this.temperatureBias = temperatureBias;
        return this;
    }

    public ClimateConfigBuilder moistureFalloff(float moistureFalloff) {
        this.moistureFalloff = moistureFalloff;
        return this;
    }

    public ClimateConfigBuilder moistureMin(float moistureMin) {
        this.moistureMin = moistureMin;
        return this;
    }

    public ClimateConfigBuilder moistureMax(float moistureMax) {
        this.moistureMax = moistureMax;
        return this;
    }

    public ClimateConfigBuilder moistureBias(float moistureBias) {
        this.moistureBias = moistureBias;
        return this;
    }
}
