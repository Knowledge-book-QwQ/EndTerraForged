package endterraforged.world.config;

import java.util.Objects;

import com.mojang.serialization.DataResult;

/**
 * Mutable editing state for {@link BiomeLayoutConfig}.
 */
public final class BiomeLayoutConfigBuilder {

    private int mainIslandRadius;
    private float radialCoefficient;
    private float highlandThreshold;
    private float midlandFloor;
    private int biomeEdgeScale;
    private int biomeEdgeOctaves;
    private float biomeEdgeLacunarity;
    private float biomeEdgeGain;
    private float biomeEdgeStrength;
    private int biomeWarpScale;
    private float biomeWarpStrength;
    private int outerNoiseScale;
    private int outerNoiseOctaves;
    private float outerNoiseThreshold;
    private int variantBlendScale;
    private int variantBlendOctaves;

    public BiomeLayoutConfigBuilder() {
        this(BiomeLayoutConfig.DEFAULT);
    }

    public BiomeLayoutConfigBuilder(BiomeLayoutConfig source) {
        load(source);
    }

    public BiomeLayoutConfig build() {
        BiomeLayoutConfig config = new BiomeLayoutConfig(
                mainIslandRadius, radialCoefficient, highlandThreshold,
                midlandFloor, biomeEdgeScale, biomeEdgeOctaves,
                biomeEdgeLacunarity, biomeEdgeGain, biomeEdgeStrength,
                biomeWarpScale, biomeWarpStrength,
                outerNoiseScale, outerNoiseOctaves, outerNoiseThreshold,
                new BiomeVariantBlendConfig(variantBlendScale, variantBlendOctaves));
        DataResult<BiomeLayoutConfig> result = BiomeLayoutConfigValidator.validate(config);
        return result.result().orElseThrow(() -> invalidState(result));
    }

    public BiomeLayoutConfigBuilder reset() {
        load(BiomeLayoutConfig.DEFAULT);
        return this;
    }

    private void load(BiomeLayoutConfig source) {
        Objects.requireNonNull(source, "source");
        this.mainIslandRadius = source.mainIslandRadius();
        this.radialCoefficient = source.radialCoefficient();
        this.highlandThreshold = source.highlandThreshold();
        this.midlandFloor = source.midlandFloor();
        this.biomeEdgeScale = source.biomeEdgeScale();
        this.biomeEdgeOctaves = source.biomeEdgeOctaves();
        this.biomeEdgeLacunarity = source.biomeEdgeLacunarity();
        this.biomeEdgeGain = source.biomeEdgeGain();
        this.biomeEdgeStrength = source.biomeEdgeStrength();
        this.biomeWarpScale = source.biomeWarpScale();
        this.biomeWarpStrength = source.biomeWarpStrength();
        this.outerNoiseScale = source.outerNoiseScale();
        this.outerNoiseOctaves = source.outerNoiseOctaves();
        this.outerNoiseThreshold = source.outerNoiseThreshold();
        this.variantBlendScale = source.variantBlendConfig().scale();
        this.variantBlendOctaves = source.variantBlendConfig().octaves();
    }

    private static IllegalStateException invalidState(DataResult<?> result) {
        String message = result.error().map(error -> error.message()).orElse("unknown validation error");
        return new IllegalStateException("invalid biome layout config builder state: " + message);
    }

    public int mainIslandRadius() { return mainIslandRadius; }
    public float radialCoefficient() { return radialCoefficient; }
    public float highlandThreshold() { return highlandThreshold; }
    public float midlandFloor() { return midlandFloor; }
    public int biomeEdgeScale() { return biomeEdgeScale; }
    public int biomeEdgeOctaves() { return biomeEdgeOctaves; }
    public float biomeEdgeLacunarity() { return biomeEdgeLacunarity; }
    public float biomeEdgeGain() { return biomeEdgeGain; }
    public float biomeEdgeStrength() { return biomeEdgeStrength; }
    public int biomeWarpScale() { return biomeWarpScale; }
    public float biomeWarpStrength() { return biomeWarpStrength; }
    public int outerNoiseScale() { return outerNoiseScale; }
    public int outerNoiseOctaves() { return outerNoiseOctaves; }
    public float outerNoiseThreshold() { return outerNoiseThreshold; }
    public int variantBlendScale() { return variantBlendScale; }
    public int variantBlendOctaves() { return variantBlendOctaves; }

    public BiomeLayoutConfigBuilder mainIslandRadius(int v) { this.mainIslandRadius = v; return this; }
    public BiomeLayoutConfigBuilder radialCoefficient(float v) { this.radialCoefficient = v; return this; }
    public BiomeLayoutConfigBuilder highlandThreshold(float v) { this.highlandThreshold = v; return this; }
    public BiomeLayoutConfigBuilder midlandFloor(float v) { this.midlandFloor = v; return this; }
    public BiomeLayoutConfigBuilder biomeEdgeScale(int v) { this.biomeEdgeScale = v; return this; }
    public BiomeLayoutConfigBuilder biomeEdgeOctaves(int v) { this.biomeEdgeOctaves = v; return this; }
    public BiomeLayoutConfigBuilder biomeEdgeLacunarity(float v) { this.biomeEdgeLacunarity = v; return this; }
    public BiomeLayoutConfigBuilder biomeEdgeGain(float v) { this.biomeEdgeGain = v; return this; }
    public BiomeLayoutConfigBuilder biomeEdgeStrength(float v) { this.biomeEdgeStrength = v; return this; }
    public BiomeLayoutConfigBuilder biomeWarpScale(int v) { this.biomeWarpScale = v; return this; }
    public BiomeLayoutConfigBuilder biomeWarpStrength(float v) { this.biomeWarpStrength = v; return this; }
    public BiomeLayoutConfigBuilder outerNoiseScale(int v) { this.outerNoiseScale = v; return this; }
    public BiomeLayoutConfigBuilder outerNoiseOctaves(int v) { this.outerNoiseOctaves = v; return this; }
    public BiomeLayoutConfigBuilder outerNoiseThreshold(float v) { this.outerNoiseThreshold = v; return this; }
    public BiomeLayoutConfigBuilder variantBlendScale(int v) { this.variantBlendScale = v; return this; }
    public BiomeLayoutConfigBuilder variantBlendOctaves(int v) { this.variantBlendOctaves = v; return this; }
}
