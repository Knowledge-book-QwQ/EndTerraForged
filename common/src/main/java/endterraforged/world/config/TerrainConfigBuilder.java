package endterraforged.world.config;

import java.util.Objects;

import com.mojang.serialization.DataResult;

/**
 * Mutable editing state for {@link TerrainConfig}.
 */
public final class TerrainConfigBuilder {

    private int terrainSeedOffset;
    private int terrainRegionSize;
    private float globalVerticalScale;
    private float globalHorizontalScale;
    private float terrainBlendRange;
    private TerrainLayoutMode terrainLayoutMode;
    private TerrainShape terrainShape;
    private TerrainLayerConfig plains;
    private TerrainLayerConfig hills;
    private TerrainLayerConfig plateau;
    private TerrainLayerConfig mountains;
    private TerrainLayerConfig volcano;

    public TerrainConfigBuilder() {
        this(TerrainConfig.DEFAULT);
    }

    public TerrainConfigBuilder(TerrainConfig source) {
        load(source);
    }

    public TerrainConfig build() {
        TerrainConfig config = new TerrainConfig(
                terrainSeedOffset, terrainRegionSize,
                globalVerticalScale, globalHorizontalScale, terrainBlendRange,
                terrainLayoutMode, terrainShape, plains, hills, plateau, mountains, volcano);
        DataResult<TerrainConfig> result = TerrainConfigValidator.validate(config);
        return result.result().orElseThrow(() -> invalidState(result));
    }

    public TerrainConfigBuilder reset() {
        load(TerrainConfig.DEFAULT);
        return this;
    }

    private void load(TerrainConfig source) {
        Objects.requireNonNull(source, "source");
        this.terrainSeedOffset = source.terrainSeedOffset();
        this.terrainRegionSize = source.terrainRegionSize();
        this.globalVerticalScale = source.globalVerticalScale();
        this.globalHorizontalScale = source.globalHorizontalScale();
        this.terrainBlendRange = source.terrainBlendRange();
        this.terrainLayoutMode = source.terrainLayoutMode();
        this.terrainShape = source.terrainShape();
        this.plains = source.plains();
        this.hills = source.hills();
        this.plateau = source.plateau();
        this.mountains = source.mountains();
        this.volcano = source.volcano();
    }

    private static IllegalStateException invalidState(DataResult<?> result) {
        String message = result.error().map(error -> error.message()).orElse("unknown validation error");
        return new IllegalStateException("invalid terrain config builder state: " + message);
    }

    public int terrainSeedOffset() {
        return terrainSeedOffset;
    }

    public int terrainRegionSize() {
        return terrainRegionSize;
    }

    public float globalVerticalScale() {
        return globalVerticalScale;
    }

    public float globalHorizontalScale() {
        return globalHorizontalScale;
    }

    public float terrainBlendRange() {
        return terrainBlendRange;
    }

    public TerrainLayoutMode terrainLayoutMode() {
        return terrainLayoutMode;
    }

    public TerrainShape terrainShape() {
        return terrainShape;
    }

    public TerrainLayerConfig plains() {
        return plains;
    }

    public TerrainLayerConfig hills() {
        return hills;
    }

    public TerrainLayerConfig plateau() {
        return plateau;
    }

    public TerrainLayerConfig mountains() {
        return mountains;
    }

    public TerrainLayerConfig volcano() {
        return volcano;
    }

    public TerrainConfigBuilder terrainSeedOffset(int terrainSeedOffset) {
        this.terrainSeedOffset = terrainSeedOffset;
        return this;
    }

    public TerrainConfigBuilder terrainRegionSize(int terrainRegionSize) {
        this.terrainRegionSize = terrainRegionSize;
        return this;
    }

    public TerrainConfigBuilder globalVerticalScale(float globalVerticalScale) {
        this.globalVerticalScale = globalVerticalScale;
        return this;
    }

    public TerrainConfigBuilder globalHorizontalScale(float globalHorizontalScale) {
        this.globalHorizontalScale = globalHorizontalScale;
        return this;
    }

    public TerrainConfigBuilder terrainBlendRange(float terrainBlendRange) {
        this.terrainBlendRange = terrainBlendRange;
        return this;
    }

    public TerrainConfigBuilder terrainLayoutMode(TerrainLayoutMode terrainLayoutMode) {
        this.terrainLayoutMode = Objects.requireNonNull(terrainLayoutMode, "terrainLayoutMode");
        return this;
    }

    public TerrainConfigBuilder terrainShape(TerrainShape terrainShape) {
        this.terrainShape = Objects.requireNonNull(terrainShape, "terrainShape");
        return this;
    }

    public TerrainConfigBuilder plains(TerrainLayerConfig plains) {
        this.plains = Objects.requireNonNull(plains, "plains");
        return this;
    }

    public TerrainConfigBuilder hills(TerrainLayerConfig hills) {
        this.hills = Objects.requireNonNull(hills, "hills");
        return this;
    }

    public TerrainConfigBuilder plateau(TerrainLayerConfig plateau) {
        this.plateau = Objects.requireNonNull(plateau, "plateau");
        return this;
    }

    public TerrainConfigBuilder mountains(TerrainLayerConfig mountains) {
        this.mountains = Objects.requireNonNull(mountains, "mountains");
        return this;
    }

    public TerrainConfigBuilder volcano(TerrainLayerConfig volcano) {
        this.volcano = Objects.requireNonNull(volcano, "volcano");
        return this;
    }
}
