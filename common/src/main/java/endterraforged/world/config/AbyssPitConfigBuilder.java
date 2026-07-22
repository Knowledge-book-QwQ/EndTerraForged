package endterraforged.world.config;

import java.util.Objects;

import com.mojang.serialization.DataResult;

/**
 * Mutable editing state for {@link AbyssPitConfig}.
 */
public final class AbyssPitConfigBuilder {

    private boolean enabled;
    private int seedOffset;
    private int pitScale;
    private int pitOctaves;
    private float pitLacunarity;
    private float pitGain;
    private float threshold;
    private float edgeFalloff;
    private int depth;
    private float depthCurve;
    private float minLandness;

    public AbyssPitConfigBuilder() {
        this(AbyssPitConfig.DEFAULT);
    }

    public AbyssPitConfigBuilder(AbyssPitConfig source) {
        load(source);
    }

    public AbyssPitConfig build() {
        AbyssPitConfig config = new AbyssPitConfig(enabled, seedOffset, pitScale,
                pitOctaves, pitLacunarity, pitGain, threshold, edgeFalloff,
                depth, depthCurve, minLandness);
        DataResult<AbyssPitConfig> result = AbyssPitConfigValidator.validate(config);
        return result.result().orElseThrow(() -> invalidState(result));
    }

    public AbyssPitConfigBuilder reset() {
        load(AbyssPitConfig.DEFAULT);
        return this;
    }

    private void load(AbyssPitConfig source) {
        Objects.requireNonNull(source, "source");
        this.enabled = source.enabled();
        this.seedOffset = source.seedOffset();
        this.pitScale = source.pitScale();
        this.pitOctaves = source.pitOctaves();
        this.pitLacunarity = source.pitLacunarity();
        this.pitGain = source.pitGain();
        this.threshold = source.threshold();
        this.edgeFalloff = source.edgeFalloff();
        this.depth = source.depth();
        this.depthCurve = source.depthCurve();
        this.minLandness = source.minLandness();
    }

    private static IllegalStateException invalidState(DataResult<?> result) {
        String message = result.error().map(error -> error.message()).orElse("unknown validation error");
        return new IllegalStateException("invalid abyss pit config builder state: " + message);
    }

    public boolean enabled() {
        return enabled;
    }

    public int seedOffset() {
        return seedOffset;
    }

    public int pitScale() {
        return pitScale;
    }

    public int pitOctaves() {
        return pitOctaves;
    }

    public float pitLacunarity() {
        return pitLacunarity;
    }

    public float pitGain() {
        return pitGain;
    }

    public float threshold() {
        return threshold;
    }

    public float edgeFalloff() {
        return edgeFalloff;
    }

    public int depth() {
        return depth;
    }

    public float depthCurve() {
        return depthCurve;
    }

    public float minLandness() {
        return minLandness;
    }

    public AbyssPitConfigBuilder enabled(boolean value) {
        this.enabled = value;
        return this;
    }

    public AbyssPitConfigBuilder seedOffset(int value) {
        this.seedOffset = value;
        return this;
    }

    public AbyssPitConfigBuilder pitScale(int value) {
        this.pitScale = value;
        return this;
    }

    public AbyssPitConfigBuilder pitOctaves(int value) {
        this.pitOctaves = value;
        return this;
    }

    public AbyssPitConfigBuilder pitLacunarity(float value) {
        this.pitLacunarity = value;
        return this;
    }

    public AbyssPitConfigBuilder pitGain(float value) {
        this.pitGain = value;
        return this;
    }

    public AbyssPitConfigBuilder threshold(float value) {
        this.threshold = value;
        return this;
    }

    public AbyssPitConfigBuilder edgeFalloff(float value) {
        this.edgeFalloff = value;
        return this;
    }

    public AbyssPitConfigBuilder depth(int value) {
        this.depth = value;
        return this;
    }

    public AbyssPitConfigBuilder depthCurve(float value) {
        this.depthCurve = value;
        return this;
    }

    public AbyssPitConfigBuilder minLandness(float value) {
        this.minLandness = value;
        return this;
    }
}
