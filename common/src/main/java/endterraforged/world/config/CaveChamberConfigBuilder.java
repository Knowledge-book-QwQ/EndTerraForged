package endterraforged.world.config;

import java.util.Objects;

import com.mojang.serialization.DataResult;

/**
 * Mutable editing state for {@link CaveChamberConfig}.
 */
public final class CaveChamberConfigBuilder {

    private float chamberProbability;
    private int minRadius;
    private int maxRadius;
    private float verticalStretch;
    private float floorBias;
    private float roughness;

    public CaveChamberConfigBuilder() {
        this(CaveChamberConfig.DEFAULT);
    }

    public CaveChamberConfigBuilder(CaveChamberConfig source) {
        load(source);
    }

    public CaveChamberConfig build() {
        CaveChamberConfig config = new CaveChamberConfig(chamberProbability,
                minRadius, maxRadius, verticalStretch, floorBias, roughness);
        DataResult<CaveChamberConfig> result = CaveChamberConfigValidator.validate(config);
        return result.result().orElseThrow(() -> invalidState(result));
    }

    public CaveChamberConfigBuilder reset() {
        load(CaveChamberConfig.DEFAULT);
        return this;
    }

    private void load(CaveChamberConfig source) {
        Objects.requireNonNull(source, "source");
        this.chamberProbability = source.chamberProbability();
        this.minRadius = source.minRadius();
        this.maxRadius = source.maxRadius();
        this.verticalStretch = source.verticalStretch();
        this.floorBias = source.floorBias();
        this.roughness = source.roughness();
    }

    private static IllegalStateException invalidState(DataResult<?> result) {
        String message = result.error().map(error -> error.message()).orElse("unknown validation error");
        return new IllegalStateException("invalid cave chamber config builder state: " + message);
    }

    public float chamberProbability() {
        return chamberProbability;
    }

    public int minRadius() {
        return minRadius;
    }

    public int maxRadius() {
        return maxRadius;
    }

    public float verticalStretch() {
        return verticalStretch;
    }

    public float floorBias() {
        return floorBias;
    }

    public float roughness() {
        return roughness;
    }

    public CaveChamberConfigBuilder chamberProbability(float value) {
        this.chamberProbability = value;
        return this;
    }

    public CaveChamberConfigBuilder minRadius(int value) {
        this.minRadius = value;
        return this;
    }

    public CaveChamberConfigBuilder maxRadius(int value) {
        this.maxRadius = value;
        return this;
    }

    public CaveChamberConfigBuilder verticalStretch(float value) {
        this.verticalStretch = value;
        return this;
    }

    public CaveChamberConfigBuilder floorBias(float value) {
        this.floorBias = value;
        return this;
    }

    public CaveChamberConfigBuilder roughness(float value) {
        this.roughness = value;
        return this;
    }
}
