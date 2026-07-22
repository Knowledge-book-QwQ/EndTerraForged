package endterraforged.world.config;

import com.mojang.serialization.DataResult;

/**
 * Range checks for global terrain shaping controls.
 */
public final class TerrainConfigValidator {

    private TerrainConfigValidator() {
    }

    public static DataResult<TerrainConfig> validate(TerrainConfig config) {
        if (config == null) {
            return DataResult.error(() -> "terrain config must not be null");
        }
        if (config.terrainShape() == null) {
            return DataResult.error(() -> "terrain.terrain_shape must not be null");
        }
        if (config.terrainLayoutMode() == null) {
            return DataResult.error(() -> "terrain.terrain_layout_mode must not be null");
        }
        if (config.terrainRegionSize() < 125 || config.terrainRegionSize() > 5000) {
            return DataResult.error(() -> "terrain.terrain_region_size must be in [125, 5000], got "
                    + config.terrainRegionSize());
        }
        DataResult<TerrainConfig> layers = validateLayers(config);
        if (layers.error().isPresent()) {
            return layers;
        }
        DataResult<TerrainConfig> layout = validateLayout(config);
        if (layout.error().isPresent()) {
            return layout;
        }
        DataResult<TerrainConfig> vertical = validateRange(
                "terrain.global_vertical_scale", config.globalVerticalScale(), 0.01F, 1.0F, config);
        if (vertical.error().isPresent()) {
            return vertical;
        }
        DataResult<TerrainConfig> horizontal = validateRange(
                "terrain.global_horizontal_scale", config.globalHorizontalScale(), 0.01F, 5.0F, config);
        if (horizontal.error().isPresent()) {
            return horizontal;
        }
        return validateRange(
                "terrain.terrain_blend_range", config.terrainBlendRange(), 0.0F, 1.0F, config);
    }

    private static DataResult<TerrainConfig> validateLayout(TerrainConfig config) {
        if (config.terrainLayoutMode() != TerrainLayoutMode.REGION_PLANNED) {
            return DataResult.success(config);
        }
        if (!areaEnabled(config.plains())
                && !areaEnabled(config.hills())
                && !areaEnabled(config.plateau())) {
            return DataResult.error(() -> "terrain.terrain_layout_mode=REGION_PLANNED requires at least one "
                    + "enabled AREA layer: plains, hills or plateau");
        }
        if (areaEnabled(config.volcano())) {
            return DataResult.error(() -> "terrain.volcano is not supported by REGION_PLANNED; "
                    + "COMPACT placement remains frozen until its independent runtime is validated");
        }
        return DataResult.success(config);
    }

    private static DataResult<TerrainConfig> validateLayers(TerrainConfig config) {
        DataResult<TerrainLayerConfig> plains = TerrainLayerConfig.validate(config.plains());
        if (plains.error().isPresent()) {
            return DataResult.error(() -> "terrain.plains invalid: " + plains.error().get().message());
        }
        DataResult<TerrainLayerConfig> hills = TerrainLayerConfig.validate(config.hills());
        if (hills.error().isPresent()) {
            return DataResult.error(() -> "terrain.hills invalid: " + hills.error().get().message());
        }
        DataResult<TerrainLayerConfig> plateau = TerrainLayerConfig.validate(config.plateau());
        if (plateau.error().isPresent()) {
            return DataResult.error(() -> "terrain.plateau invalid: " + plateau.error().get().message());
        }
        DataResult<TerrainLayerConfig> mountains = TerrainLayerConfig.validate(config.mountains());
        if (mountains.error().isPresent()) {
            return DataResult.error(() -> "terrain.mountains invalid: " + mountains.error().get().message());
        }
        DataResult<TerrainLayerConfig> volcano = TerrainLayerConfig.validate(config.volcano());
        if (volcano.error().isPresent()) {
            return DataResult.error(() -> "terrain.volcano invalid: " + volcano.error().get().message());
        }
        return DataResult.success(config);
    }

    private static boolean areaEnabled(TerrainLayerConfig config) {
        return config.weight() > 0.0F
                && config.baseScale() > 0.0F
                && config.verticalScale() > 0.0F;
    }

    private static DataResult<TerrainConfig> validateRange(String name, float value,
                                                           float min, float max,
                                                           TerrainConfig config) {
        if (!Float.isFinite(value) || value < min || value > max) {
            return DataResult.error(() -> name + " must be in [" + min + ", " + max + "], got " + value);
        }
        return DataResult.success(config);
    }
}
