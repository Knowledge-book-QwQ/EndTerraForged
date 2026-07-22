package endterraforged.world.config;

import java.util.Objects;

import com.mojang.serialization.DataResult;

/**
 * Mutable editing state for {@link CaveNetworkConfig}.
 */
public final class CaveNetworkConfigBuilder {

    private int regionSize;
    private float networkDensity;
    private int chamberSpacing;
    private float branchingFactor;
    private float loopChance;
    private float maxSlope;
    private float minLandness;

    public CaveNetworkConfigBuilder() {
        this(CaveNetworkConfig.DEFAULT);
    }

    public CaveNetworkConfigBuilder(CaveNetworkConfig source) {
        load(source);
    }

    public CaveNetworkConfig build() {
        CaveNetworkConfig config = new CaveNetworkConfig(regionSize, networkDensity,
                chamberSpacing, branchingFactor, loopChance, maxSlope, minLandness);
        DataResult<CaveNetworkConfig> result = CaveNetworkConfigValidator.validate(config);
        return result.result().orElseThrow(() -> invalidState(result));
    }

    public CaveNetworkConfigBuilder reset() {
        load(CaveNetworkConfig.DEFAULT);
        return this;
    }

    private void load(CaveNetworkConfig source) {
        Objects.requireNonNull(source, "source");
        this.regionSize = source.regionSize();
        this.networkDensity = source.networkDensity();
        this.chamberSpacing = source.chamberSpacing();
        this.branchingFactor = source.branchingFactor();
        this.loopChance = source.loopChance();
        this.maxSlope = source.maxSlope();
        this.minLandness = source.minLandness();
    }

    private static IllegalStateException invalidState(DataResult<?> result) {
        String message = result.error().map(error -> error.message()).orElse("unknown validation error");
        return new IllegalStateException("invalid cave network config builder state: " + message);
    }

    public int regionSize() {
        return regionSize;
    }

    public float networkDensity() {
        return networkDensity;
    }

    public int chamberSpacing() {
        return chamberSpacing;
    }

    public float branchingFactor() {
        return branchingFactor;
    }

    public float loopChance() {
        return loopChance;
    }

    public float maxSlope() {
        return maxSlope;
    }

    public float minLandness() {
        return minLandness;
    }

    public CaveNetworkConfigBuilder regionSize(int value) {
        this.regionSize = value;
        return this;
    }

    public CaveNetworkConfigBuilder networkDensity(float value) {
        this.networkDensity = value;
        return this;
    }

    public CaveNetworkConfigBuilder chamberSpacing(int value) {
        this.chamberSpacing = value;
        return this;
    }

    public CaveNetworkConfigBuilder branchingFactor(float value) {
        this.branchingFactor = value;
        return this;
    }

    public CaveNetworkConfigBuilder loopChance(float value) {
        this.loopChance = value;
        return this;
    }

    public CaveNetworkConfigBuilder maxSlope(float value) {
        this.maxSlope = value;
        return this;
    }

    public CaveNetworkConfigBuilder minLandness(float value) {
        this.minLandness = value;
        return this;
    }
}
