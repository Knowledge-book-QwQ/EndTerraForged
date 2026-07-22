package endterraforged.world.config;

import java.util.Objects;

import com.mojang.serialization.DataResult;

/** Mutable editing state for {@link ContinentBandsConfig}. */
public final class ContinentBandsConfigBuilder {

    private boolean enabled;
    private float voidOuterThreshold;
    private float shelfThreshold;
    private float rimThreshold;
    private float coastThreshold;
    private float inlandThreshold;

    public ContinentBandsConfigBuilder() {
        this(ContinentBandsConfig.DEFAULT);
    }

    public ContinentBandsConfigBuilder(ContinentBandsConfig source) {
        load(source);
    }

    public ContinentBandsConfigBuilder load(ContinentBandsConfig source) {
        Objects.requireNonNull(source, "source");
        this.enabled = source.enabled();
        this.voidOuterThreshold = source.voidOuterThreshold();
        this.shelfThreshold = source.shelfThreshold();
        this.rimThreshold = source.rimThreshold();
        this.coastThreshold = source.coastThreshold();
        this.inlandThreshold = source.inlandThreshold();
        return this;
    }

    public ContinentBandsConfig build() {
        ContinentBandsConfig config = new ContinentBandsConfig(this.enabled, this.voidOuterThreshold,
                this.shelfThreshold, this.rimThreshold, this.coastThreshold, this.inlandThreshold);
        DataResult<ContinentBandsConfig> result = ContinentBandsConfigValidator.validate(config);
        return result.result().orElseThrow(() -> new IllegalStateException(
                "invalid continent bands builder state: "
                        + result.error().map(error -> error.message()).orElse("unknown error")));
    }

    public ContinentBandsConfigBuilder reset() {
        return load(ContinentBandsConfig.DEFAULT);
    }

    public boolean enabled() {
        return this.enabled;
    }

    public float voidOuterThreshold() {
        return this.voidOuterThreshold;
    }

    public float shelfThreshold() {
        return this.shelfThreshold;
    }

    public float rimThreshold() {
        return this.rimThreshold;
    }

    public float coastThreshold() {
        return this.coastThreshold;
    }

    public float inlandThreshold() {
        return this.inlandThreshold;
    }

    public ContinentBandsConfigBuilder enabled(boolean value) {
        this.enabled = value;
        return this;
    }

    public ContinentBandsConfigBuilder voidOuterThreshold(float value) {
        this.voidOuterThreshold = value;
        return this;
    }

    public ContinentBandsConfigBuilder shelfThreshold(float value) {
        this.shelfThreshold = value;
        return this;
    }

    public ContinentBandsConfigBuilder rimThreshold(float value) {
        this.rimThreshold = value;
        return this;
    }

    public ContinentBandsConfigBuilder coastThreshold(float value) {
        this.coastThreshold = value;
        return this;
    }

    public ContinentBandsConfigBuilder inlandThreshold(float value) {
        this.inlandThreshold = value;
        return this;
    }
}
