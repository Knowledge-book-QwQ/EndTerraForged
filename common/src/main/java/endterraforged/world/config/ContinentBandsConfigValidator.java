package endterraforged.world.config;

import com.mojang.serialization.DataResult;

/** Range and ordering checks for {@link ContinentBandsConfig}. */
public final class ContinentBandsConfigValidator {

    private ContinentBandsConfigValidator() {
    }

    public static DataResult<ContinentBandsConfig> validate(ContinentBandsConfig config) {
        if (config == null) {
            return DataResult.error(() -> "continent.bands must not be null");
        }
        if (!config.enabled()) {
            if (config.equals(ContinentBandsConfig.LEGACY_PASSTHROUGH)) {
                return DataResult.success(config);
            }
            return DataResult.error(() -> "continent.bands disabled mode must use legacy passthrough values");
        }
        DataResult<ContinentBandsConfig> range = validateUnit(
                "continent.bands.void_outer", config.voidOuterThreshold(), config);
        if (range.error().isPresent()) {
            return range;
        }
        range = validateUnit("continent.bands.shelf", config.shelfThreshold(), config);
        if (range.error().isPresent()) {
            return range;
        }
        range = validateUnit("continent.bands.rim", config.rimThreshold(), config);
        if (range.error().isPresent()) {
            return range;
        }
        range = validateUnit("continent.bands.coast", config.coastThreshold(), config);
        if (range.error().isPresent()) {
            return range;
        }
        range = validateUnit("continent.bands.inland", config.inlandThreshold(), config);
        if (range.error().isPresent()) {
            return range;
        }
        if (!(config.voidOuterThreshold() < config.shelfThreshold()
                && config.shelfThreshold() < config.rimThreshold()
                && config.rimThreshold() < config.coastThreshold()
                && config.coastThreshold() < config.inlandThreshold())) {
            return DataResult.error(() -> "continent.bands thresholds must satisfy "
                    + "void_outer < shelf < rim < coast < inland");
        }
        return DataResult.success(config);
    }

    private static DataResult<ContinentBandsConfig> validateUnit(String name, float value,
                                                                  ContinentBandsConfig config) {
        if (!Float.isFinite(value) || value < 0.0F || value > 1.0F) {
            return DataResult.error(() -> name + " must be in [0, 1], got " + value);
        }
        return DataResult.success(config);
    }
}
