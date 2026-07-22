package endterraforged.world.config;

import java.util.Objects;

import com.mojang.serialization.DataResult;

/**
 * Mutable editing state for {@link CaveSystemConfig}.
 */
public final class CaveSystemConfigBuilder {

    private boolean enabled;
    private int seedOffset;
    private int depthStart;
    private int depthEnd;
    private float spectacleBias;
    private float connectivity;
    private float surfaceOpeningChance;

    public CaveSystemConfigBuilder() {
        this(CaveSystemConfig.DEFAULT);
    }

    public CaveSystemConfigBuilder(CaveSystemConfig source) {
        load(source);
    }

    public CaveSystemConfig build() {
        CaveSystemConfig config = new CaveSystemConfig(enabled, seedOffset,
                depthStart, depthEnd, spectacleBias, connectivity, surfaceOpeningChance);
        DataResult<CaveSystemConfig> result = CaveSystemConfigValidator.validate(config);
        return result.result().orElseThrow(() -> invalidState(result));
    }

    public CaveSystemConfigBuilder reset() {
        load(CaveSystemConfig.DEFAULT);
        return this;
    }

    private void load(CaveSystemConfig source) {
        Objects.requireNonNull(source, "source");
        this.enabled = source.enabled();
        this.seedOffset = source.seedOffset();
        this.depthStart = source.depthStart();
        this.depthEnd = source.depthEnd();
        this.spectacleBias = source.spectacleBias();
        this.connectivity = source.connectivity();
        this.surfaceOpeningChance = source.surfaceOpeningChance();
    }

    private static IllegalStateException invalidState(DataResult<?> result) {
        String message = result.error().map(error -> error.message()).orElse("unknown validation error");
        return new IllegalStateException("invalid cave system config builder state: " + message);
    }

    public boolean enabled() {
        return enabled;
    }

    public int seedOffset() {
        return seedOffset;
    }

    public int depthStart() {
        return depthStart;
    }

    public int depthEnd() {
        return depthEnd;
    }

    public float spectacleBias() {
        return spectacleBias;
    }

    public float connectivity() {
        return connectivity;
    }

    public float surfaceOpeningChance() {
        return surfaceOpeningChance;
    }

    public CaveSystemConfigBuilder enabled(boolean value) {
        this.enabled = value;
        return this;
    }

    public CaveSystemConfigBuilder seedOffset(int value) {
        this.seedOffset = value;
        return this;
    }

    public CaveSystemConfigBuilder depthStart(int value) {
        this.depthStart = value;
        return this;
    }

    public CaveSystemConfigBuilder depthEnd(int value) {
        this.depthEnd = value;
        return this;
    }

    public CaveSystemConfigBuilder spectacleBias(float value) {
        this.spectacleBias = value;
        return this;
    }

    public CaveSystemConfigBuilder connectivity(float value) {
        this.connectivity = value;
        return this;
    }

    public CaveSystemConfigBuilder surfaceOpeningChance(float value) {
        this.surfaceOpeningChance = value;
        return this;
    }
}
