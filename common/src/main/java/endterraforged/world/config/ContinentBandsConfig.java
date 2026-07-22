package endterraforged.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * End-specific macro-continent bands applied after a continent algorithm has
 * produced its raw landness field.
 *
 * <p>The five thresholds deliberately use End vocabulary rather than RTF's
 * ocean vocabulary. Enabled bands turn one raw field into a finite shelf
 * signal and a separate inland-relief signal. The disabled value preserves
 * presets written before format version 3 without silently reshaping them.</p>
 */
public record ContinentBandsConfig(boolean enabled,
                                   float voidOuterThreshold,
                                   float shelfThreshold,
                                   float rimThreshold,
                                   float coastThreshold,
                                   float inlandThreshold) {

    public static final ContinentBandsConfig DEFAULT = new ContinentBandsConfig(
            true, 0.10F, 0.25F, 0.327F, 0.448F, 0.502F);
    public static final ContinentBandsConfig LEGACY_PASSTHROUGH = new ContinentBandsConfig(
            false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

    private static final Codec<ContinentBandsConfig> BASE_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.optionalFieldOf("enabled", false)
                            .forGetter(ContinentBandsConfig::enabled),
                    Codec.FLOAT.optionalFieldOf("void_outer", LEGACY_PASSTHROUGH.voidOuterThreshold)
                            .forGetter(ContinentBandsConfig::voidOuterThreshold),
                    Codec.FLOAT.optionalFieldOf("shelf", LEGACY_PASSTHROUGH.shelfThreshold)
                            .forGetter(ContinentBandsConfig::shelfThreshold),
                    Codec.FLOAT.optionalFieldOf("rim", LEGACY_PASSTHROUGH.rimThreshold)
                            .forGetter(ContinentBandsConfig::rimThreshold),
                    Codec.FLOAT.optionalFieldOf("coast", LEGACY_PASSTHROUGH.coastThreshold)
                            .forGetter(ContinentBandsConfig::coastThreshold),
                    Codec.FLOAT.optionalFieldOf("inland", LEGACY_PASSTHROUGH.inlandThreshold)
                            .forGetter(ContinentBandsConfig::inlandThreshold)
            ).apply(instance, ContinentBandsConfig::new));

    public static final Codec<ContinentBandsConfig> CODEC = BASE_CODEC.flatXmap(
            ContinentBandsConfigValidator::validate,
            DataResult::success);

    /** Maps an algorithm's raw {@code [0,1]} landness to finite shelf strength. */
    public float landness(float rawLandness) {
        if (!this.enabled) {
            return clamp01(rawLandness);
        }
        float shelf = smoothstep(rawLandness, this.voidOuterThreshold, this.shelfThreshold);
        float rim = smoothstep(rawLandness, this.shelfThreshold, this.rimThreshold);
        return shelf * (0.35F + 0.65F * rim);
    }

    /**
     * Maps raw landness to a low-relief coast and then to full inland relief.
     * This keeps the shelf readable without turning every continent rim into a
     * perfectly flat platform.
     */
    public float inlandness(float rawLandness) {
        if (!this.enabled) {
            return 1.0F;
        }
        float coast = smoothstep(rawLandness, this.rimThreshold, this.coastThreshold);
        float inland = smoothstep(rawLandness, this.coastThreshold, this.inlandThreshold);
        return coast * (0.18F + 0.82F * inland);
    }

    static float smoothstep(float value, float start, float end) {
        float alpha = clamp01((value - start) / (end - start));
        return alpha * alpha * (3.0F - 2.0F * alpha);
    }

    private static float clamp01(float value) {
        return Math.clamp(value, 0.0F, 1.0F);
    }
}
