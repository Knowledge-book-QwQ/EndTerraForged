package endterraforged.world.config;

import com.mojang.serialization.DataResult;

/**
 * Range checks for scalar biome ring and edge controls.
 */
public final class BiomeLayoutConfigValidator {

    public static final int MIN_MAIN_ISLAND_RADIUS = 1;
    public static final int MAX_MAIN_ISLAND_RADIUS = 128;
    public static final float MIN_RADIAL_COEFFICIENT = 0.5F;
    public static final float MAX_RADIAL_COEFFICIENT = 32.0F;
    public static final float MIN_FALLOFF_THRESHOLD = -100.0F;
    public static final float MAX_FALLOFF_THRESHOLD = 80.0F;
    public static final int MIN_NOISE_SCALE = 1;
    public static final int MAX_NOISE_SCALE = 500;
    public static final int MIN_NOISE_OCTAVES = 1;
    public static final int MAX_NOISE_OCTAVES = 5;
    public static final float MIN_LACUNARITY = 0.01F;
    public static final float MAX_LACUNARITY = 10.5F;
    public static final float MIN_GAIN = 0.0F;
    public static final float MAX_GAIN = 5.5F;
    public static final float MIN_EDGE_STRENGTH = 1.0F;
    public static final float MAX_EDGE_STRENGTH = 500.0F;
    public static final float MIN_WARP_STRENGTH = 0.0F;
    public static final float MAX_WARP_STRENGTH = 500.0F;
    public static final float MIN_OUTER_THRESHOLD = -1.0F;
    public static final float MAX_OUTER_THRESHOLD = 1.0F;

    private BiomeLayoutConfigValidator() {
    }

    public static DataResult<BiomeLayoutConfig> validate(BiomeLayoutConfig config) {
        if (config == null) {
            return DataResult.error(() -> "biome layout config must not be null");
        }
        DataResult<BiomeLayoutConfig> mainIsland = validateIntRange(
                "biome_layout.main_island_radius", config.mainIslandRadius(),
                MIN_MAIN_ISLAND_RADIUS, MAX_MAIN_ISLAND_RADIUS, config);
        if (mainIsland.error().isPresent()) {
            return mainIsland;
        }
        DataResult<BiomeLayoutConfig> radial = validateRange(
                "biome_layout.radial_coefficient", config.radialCoefficient(),
                MIN_RADIAL_COEFFICIENT, MAX_RADIAL_COEFFICIENT, config);
        if (radial.error().isPresent()) {
            return radial;
        }
        DataResult<BiomeLayoutConfig> highland = validateRange(
                "biome_layout.highland_threshold", config.highlandThreshold(),
                MIN_FALLOFF_THRESHOLD, MAX_FALLOFF_THRESHOLD, config);
        if (highland.error().isPresent()) {
            return highland;
        }
        DataResult<BiomeLayoutConfig> midland = validateRange(
                "biome_layout.midland_floor", config.midlandFloor(),
                MIN_FALLOFF_THRESHOLD, MAX_FALLOFF_THRESHOLD, config);
        if (midland.error().isPresent()) {
            return midland;
        }
        if (config.midlandFloor() > config.highlandThreshold()) {
            return DataResult.error(() -> "biome_layout.midland_floor must be <= "
                    + "biome_layout.highland_threshold, got " + config.midlandFloor()
                    + " > " + config.highlandThreshold());
        }
        DataResult<BiomeLayoutConfig> edgeScale = validateIntRange(
                "biome_layout.biome_edge_scale", config.biomeEdgeScale(),
                MIN_NOISE_SCALE, MAX_NOISE_SCALE, config);
        if (edgeScale.error().isPresent()) {
            return edgeScale;
        }
        DataResult<BiomeLayoutConfig> edgeOctaves = validateIntRange(
                "biome_layout.biome_edge_octaves", config.biomeEdgeOctaves(),
                MIN_NOISE_OCTAVES, MAX_NOISE_OCTAVES, config);
        if (edgeOctaves.error().isPresent()) {
            return edgeOctaves;
        }
        DataResult<BiomeLayoutConfig> lacunarity = validateRange(
                "biome_layout.biome_edge_lacunarity", config.biomeEdgeLacunarity(),
                MIN_LACUNARITY, MAX_LACUNARITY, config);
        if (lacunarity.error().isPresent()) {
            return lacunarity;
        }
        DataResult<BiomeLayoutConfig> gain = validateRange(
                "biome_layout.biome_edge_gain", config.biomeEdgeGain(),
                MIN_GAIN, MAX_GAIN, config);
        if (gain.error().isPresent()) {
            return gain;
        }
        DataResult<BiomeLayoutConfig> strength = validateRange(
                "biome_layout.biome_edge_strength", config.biomeEdgeStrength(),
                MIN_EDGE_STRENGTH, MAX_EDGE_STRENGTH, config);
        if (strength.error().isPresent()) {
            return strength;
        }
        DataResult<BiomeLayoutConfig> warpScale = validateIntRange(
                "biome_layout.biome_warp_scale", config.biomeWarpScale(),
                MIN_NOISE_SCALE, MAX_NOISE_SCALE, config);
        if (warpScale.error().isPresent()) {
            return warpScale;
        }
        DataResult<BiomeLayoutConfig> warpStrength = validateRange(
                "biome_layout.biome_warp_strength", config.biomeWarpStrength(),
                MIN_WARP_STRENGTH, MAX_WARP_STRENGTH, config);
        if (warpStrength.error().isPresent()) {
            return warpStrength;
        }
        DataResult<BiomeLayoutConfig> outerScale = validateIntRange(
                "biome_layout.outer_noise_scale", config.outerNoiseScale(),
                MIN_NOISE_SCALE, MAX_NOISE_SCALE, config);
        if (outerScale.error().isPresent()) {
            return outerScale;
        }
        DataResult<BiomeLayoutConfig> outerOctaves = validateIntRange(
                "biome_layout.outer_noise_octaves", config.outerNoiseOctaves(),
                MIN_NOISE_OCTAVES, MAX_NOISE_OCTAVES, config);
        if (outerOctaves.error().isPresent()) {
            return outerOctaves;
        }
        DataResult<BiomeLayoutConfig> outerThreshold = validateRange("biome_layout.outer_noise_threshold",
                config.outerNoiseThreshold(), MIN_OUTER_THRESHOLD,
                MAX_OUTER_THRESHOLD, config);
        if (outerThreshold.error().isPresent()) {
            return outerThreshold;
        }
        DataResult<BiomeVariantBlendConfig> variantBlend =
                BiomeVariantBlendConfigValidator.validate(config.variantBlendConfig());
        if (variantBlend.error().isPresent()) {
            return DataResult.error(() -> "biome_layout.variant_blend: "
                    + variantBlend.error().orElseThrow().message());
        }
        return DataResult.success(config);
    }

    private static DataResult<BiomeLayoutConfig> validateRange(String name, float value,
                                                               float min, float max,
                                                               BiomeLayoutConfig config) {
        if (!Float.isFinite(value) || value < min || value > max) {
            return DataResult.error(() -> name + " must be in ["
                    + min + ", " + max + "], got " + value);
        }
        return DataResult.success(config);
    }

    private static DataResult<BiomeLayoutConfig> validateIntRange(String name, int value,
                                                                  int min, int max,
                                                                  BiomeLayoutConfig config) {
        if (value < min || value > max) {
            return DataResult.error(() -> name + " must be in ["
                    + min + ", " + max + "], got " + value);
        }
        return DataResult.success(config);
    }
}
