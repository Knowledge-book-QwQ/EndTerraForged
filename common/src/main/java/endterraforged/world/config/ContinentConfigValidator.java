package endterraforged.world.config;

import com.mojang.serialization.DataResult;

/**
 * Range checks for {@link ContinentConfig}. Kept separate from the record so
 * tests and codecs can reuse the same fail-fast messages.
 */
public final class ContinentConfigValidator {

    private static final int MIN_SCALE = 64;
    private static final int MAX_SCALE = 4096;
    public static final int MIN_OUTER_CONTINENT_SCALE = 1024;
    public static final int MAX_OUTER_CONTINENT_SCALE = 16384;
    public static final int MIN_SHELF_THICKNESS = 16;
    public static final int MAX_SHELF_THICKNESS = 2048;
    public static final int MIN_COAST_SCALE = 128;
    public static final int MAX_COAST_SCALE = 8192;
    public static final float MAX_COAST_STRENGTH = 0.45F;
    private static final int MIN_OCTAVES = 1;
    private static final int MAX_OCTAVES = 12;

    private ContinentConfigValidator() {
    }

    public static DataResult<ContinentConfig> validate(ContinentConfig config) {
        if (config == null) {
            return DataResult.error(() -> "continent config must not be null");
        }
        if (config.continentShape() == null) {
            return DataResult.error(() -> "continent.continent_shape must not be null");
        }
        DataResult<ContinentConfig> scale = validateScale("continent.islands_scale", config.islandsScale(), config);
        if (scale.error().isPresent()) return scale;
        scale = validateScale("continent.continent_scale", config.continentScale(), config);
        if (scale.error().isPresent()) return scale;
        DataResult<ContinentConfig> unit = validateUnit("continent.continent_jitter", config.continentJitter(), config);
        if (unit.error().isPresent()) return unit;
        unit = validateUnit("continent.continent_skipping", config.continentSkipping(), config);
        if (unit.error().isPresent()) return unit;
        unit = validateUnit("continent.continent_size_variance", config.continentSizeVariance(), config);
        if (unit.error().isPresent()) return unit;
        if (config.continentNoiseOctaves() < MIN_OCTAVES || config.continentNoiseOctaves() > MAX_OCTAVES) {
            return DataResult.error(() -> "continent.continent_noise_octaves must be in [" + MIN_OCTAVES
                    + ", " + MAX_OCTAVES + "], got " + config.continentNoiseOctaves());
        }
        unit = validateUnit("continent.continent_noise_gain", config.continentNoiseGain(), config);
        if (unit.error().isPresent()) return unit;
        if (!Float.isFinite(config.continentNoiseLacunarity())
                || config.continentNoiseLacunarity() <= 0.0F
                || config.continentNoiseLacunarity() > 10.5F) {
            return DataResult.error(() -> "continent.continent_noise_lacunarity must be in (0, 10.5], got "
                    + config.continentNoiseLacunarity());
        }
        unit = validateUnit("continent.feature_spread", config.featureSpread(), config);
        if (unit.error().isPresent()) return unit;
        unit = validateUnit("continent.island_radius", config.islandRadius(), config);
        if (unit.error().isPresent()) return unit;
        unit = validateUnit("continent.island_scatter", config.islandScatter(), config);
        if (unit.error().isPresent()) return unit;
        unit = validateUnit("continent.rift_threshold", config.riftThreshold(), config);
        if (unit.error().isPresent()) return unit;
        unit = validateUnit("continent.rift_strength", config.riftStrength(), config);
        if (unit.error().isPresent()) return unit;
        scale = validateScale("continent.warp_scale", config.warpScale(), config);
        if (scale.error().isPresent()) return scale;
        if (config.outerContinentScale() < MIN_OUTER_CONTINENT_SCALE
                || config.outerContinentScale() > MAX_OUTER_CONTINENT_SCALE) {
            return DataResult.error(() -> "continent.outer_continent_scale must be in ["
                    + MIN_OUTER_CONTINENT_SCALE + ", " + MAX_OUTER_CONTINENT_SCALE + "], got "
                    + config.outerContinentScale());
        }
        if (config.landmassVolumeMode() == null) {
            return DataResult.error(() -> "continent.volume_mode must not be null");
        }
        if (config.coastShape() == null) {
            return DataResult.error(() -> "continent.coast_shape must not be null");
        }
        if (config.continentAlgorithm() == null) {
            return DataResult.error(() -> "continent.algorithm must not be null");
        }
        if (!config.continentAlgorithm().isImplemented()) {
            return DataResult.error(() -> "continent.algorithm is not implemented in this release: "
                    + config.continentAlgorithm());
        }
        DataResult<ContinentBandsConfig> bands = ContinentBandsConfigValidator.validate(config.continentBands());
        if (bands.error().isPresent()) {
            String message = bands.error().orElseThrow().message();
            return DataResult.error(() -> message);
        }
        if (config.coastScale() < MIN_COAST_SCALE || config.coastScale() > MAX_COAST_SCALE) {
            return DataResult.error(() -> "continent.coast_scale must be in [" + MIN_COAST_SCALE
                    + ", " + MAX_COAST_SCALE + "], got " + config.coastScale());
        }
        if (!Float.isFinite(config.coastStrength())
                || config.coastStrength() < 0.0F
                || config.coastStrength() > MAX_COAST_STRENGTH) {
            return DataResult.error(() -> "continent.coast_strength must be in [0, "
                    + MAX_COAST_STRENGTH + "], got " + config.coastStrength());
        }
        unit = validateUnit("continent.coast_cell_blend", config.coastCellBlend(), config);
        if (unit.error().isPresent()) return unit;
        if (config.shelfThickness() < MIN_SHELF_THICKNESS
                || config.shelfThickness() > MAX_SHELF_THICKNESS) {
            return DataResult.error(() -> "continent.shelf_thickness must be in ["
                    + MIN_SHELF_THICKNESS + ", " + MAX_SHELF_THICKNESS + "], got "
                    + config.shelfThickness());
        }
        if (config.shelfEdgeThickness() < MIN_SHELF_THICKNESS
                || config.shelfEdgeThickness() > config.shelfThickness()) {
            return DataResult.error(() -> "continent.shelf_edge_thickness must be in ["
                    + MIN_SHELF_THICKNESS + ", shelf_thickness], got "
                    + config.shelfEdgeThickness());
        }
        if (!Float.isFinite(config.warpStrength())
                || config.warpStrength() < 0.0F
                || config.warpStrength() > 512.0F) {
            return DataResult.error(() -> "continent.warp_strength must be in [0, 512], got " + config.warpStrength());
        }
        return DataResult.success(config);
    }

    private static DataResult<ContinentConfig> validateScale(String name, int value, ContinentConfig config) {
        if (value < MIN_SCALE || value > MAX_SCALE) {
            return DataResult.error(() -> name + " must be in [" + MIN_SCALE + ", "
                    + MAX_SCALE + "], got " + value);
        }
        return DataResult.success(config);
    }

    private static DataResult<ContinentConfig> validateUnit(String name, float value, ContinentConfig config) {
        if (!Float.isFinite(value) || value < 0.0F || value > 1.0F) {
            return DataResult.error(() -> name + " must be in [0, 1], got " + value);
        }
        return DataResult.success(config);
    }
}
