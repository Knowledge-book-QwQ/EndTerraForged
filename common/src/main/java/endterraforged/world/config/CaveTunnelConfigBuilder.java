package endterraforged.world.config;

import java.util.Objects;

import com.mojang.serialization.DataResult;

/**
 * Mutable editing state for {@link CaveTunnelConfig}.
 */
public final class CaveTunnelConfigBuilder {

    private boolean enabled;
    private float entranceProbability;
    private float cheeseDepthOffset;
    private float cheeseProbability;
    private float spaghettiProbability;
    private float noodleProbability;

    public CaveTunnelConfigBuilder() {
        this(CaveTunnelConfig.DEFAULT);
    }

    public CaveTunnelConfigBuilder(CaveTunnelConfig source) {
        load(source);
    }

    public CaveTunnelConfig build() {
        CaveTunnelConfig config = new CaveTunnelConfig(enabled, entranceProbability,
                cheeseDepthOffset, cheeseProbability, spaghettiProbability,
                noodleProbability);
        DataResult<CaveTunnelConfig> result = CaveTunnelConfigValidator.validate(config);
        return result.result().orElseThrow(() -> invalidState(result));
    }

    public CaveTunnelConfigBuilder reset() {
        load(CaveTunnelConfig.DEFAULT);
        return this;
    }

    private void load(CaveTunnelConfig source) {
        Objects.requireNonNull(source, "source");
        this.enabled = source.enabled();
        this.entranceProbability = source.entranceProbability();
        this.cheeseDepthOffset = source.cheeseDepthOffset();
        this.cheeseProbability = source.cheeseProbability();
        this.spaghettiProbability = source.spaghettiProbability();
        this.noodleProbability = source.noodleProbability();
    }

    private static IllegalStateException invalidState(DataResult<?> result) {
        String message = result.error().map(error -> error.message()).orElse("unknown validation error");
        return new IllegalStateException("invalid cave tunnel config builder state: " + message);
    }

    public boolean enabled() {
        return enabled;
    }

    public float entranceProbability() {
        return entranceProbability;
    }

    public float cheeseDepthOffset() {
        return cheeseDepthOffset;
    }

    public float cheeseProbability() {
        return cheeseProbability;
    }

    public float spaghettiProbability() {
        return spaghettiProbability;
    }

    public float noodleProbability() {
        return noodleProbability;
    }

    public CaveTunnelConfigBuilder enabled(boolean value) {
        this.enabled = value;
        return this;
    }

    public CaveTunnelConfigBuilder entranceProbability(float value) {
        this.entranceProbability = value;
        return this;
    }

    public CaveTunnelConfigBuilder cheeseDepthOffset(float value) {
        this.cheeseDepthOffset = value;
        return this;
    }

    public CaveTunnelConfigBuilder cheeseProbability(float value) {
        this.cheeseProbability = value;
        return this;
    }

    public CaveTunnelConfigBuilder spaghettiProbability(float value) {
        this.spaghettiProbability = value;
        return this;
    }

    public CaveTunnelConfigBuilder noodleProbability(float value) {
        this.noodleProbability = value;
        return this;
    }
}
