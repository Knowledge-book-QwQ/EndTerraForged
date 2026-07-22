package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

class TerrainConfigCodecTest {

    @Test
    void defaultsRoundTripLosslessly() {
        TerrainConfig decoded = decode(encode(TerrainConfig.DEFAULT));

        assertEquals(TerrainConfig.DEFAULT, decoded);
    }

    @Test
    void emptyObjectDecodesToDefaults() {
        TerrainConfig decoded = decode(new JsonObject());

        assertEquals(TerrainConfig.DEFAULT, decoded);
        assertEquals(0, decoded.terrainSeedOffset());
        assertEquals(1200, decoded.terrainRegionSize());
        assertEquals(0.0F, decoded.terrainBlendRange());
        assertEquals(TerrainLayoutMode.LEGACY_SELECTOR, decoded.terrainLayoutMode());
        assertEquals(TerrainShape.SHATTERED_RIDGES, decoded.terrainShape());
        assertEquals(TerrainLayerConfig.DISABLED, decoded.plains());
        assertEquals(TerrainLayerConfig.DISABLED, decoded.hills());
        assertEquals(TerrainLayerConfig.DISABLED, decoded.plateau());
        assertEquals(TerrainLayerConfig.DEFAULT, decoded.mountains());
        assertEquals(TerrainLayerConfig.DISABLED, decoded.volcano());
    }

    @Test
    void customConfigRoundTripsLosslessly() {
        TerrainConfig original = new TerrainConfig(42, 1600, 0.75F, 2.25F, 0.35F,
                TerrainLayoutMode.REGION_PLANNED, TerrainShape.ROLLING_RIDGES,
                new TerrainLayerConfig(0.5F, 1.0F, 2.0F, 3.0F),
                new TerrainLayerConfig(0.75F, 1.1F, 2.5F, 3.5F),
                new TerrainLayerConfig(1.25F, 1.2F, 3.0F, 4.0F),
                new TerrainLayerConfig(2.0F, 1.25F, 3.0F, 4.0F),
                TerrainLayerConfig.DISABLED);

        assertEquals(original, decode(encode(original)));
    }

    @Test
    void regionPlannedCompactVolcanoIsRejected() {
        TerrainConfig original = new TerrainConfig(17, 1800, 1.0F, 1.0F, 0.25F,
                TerrainLayoutMode.REGION_PLANNED, TerrainShape.SHATTERED_RIDGES,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED, TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DISABLED, new TerrainLayerConfig(0.8F, 1.25F, 1.5F, 0.75F));

        DataResult<JsonElement> result = TerrainConfig.CODEC.encodeStart(JsonOps.INSTANCE, original);

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("terrain.volcano"));
    }

    @Test
    void twoArgumentConstructorKeepsDefaultTerrainShape() {
        TerrainConfig config = new TerrainConfig(0.75F, 2.25F);

        assertEquals(TerrainShape.SHATTERED_RIDGES, config.terrainShape());
        assertEquals(TerrainConfig.DEFAULT.terrainSeedOffset(), config.terrainSeedOffset());
        assertEquals(TerrainConfig.DEFAULT.terrainRegionSize(), config.terrainRegionSize());
        assertEquals(TerrainConfig.DEFAULT.terrainBlendRange(), config.terrainBlendRange());
        assertEquals(TerrainLayerConfig.DISABLED, config.plains());
        assertEquals(TerrainLayerConfig.DISABLED, config.hills());
        assertEquals(TerrainLayerConfig.DISABLED, config.plateau());
        assertEquals(TerrainLayerConfig.DEFAULT, config.mountains());
        assertEquals(TerrainLayerConfig.DISABLED, config.volcano());
    }

    @Test
    void rejectsInvalidTerrainRegionSize() {
        DataResult<TerrainConfig> result = parseResult("{\"terrain_region_size\": 124}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("terrain_region_size"));
    }

    @Test
    void rejectsInvalidVerticalScale() {
        DataResult<TerrainConfig> result = parseResult("{\"global_vertical_scale\": 0.0}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("global_vertical_scale"));
    }

    @Test
    void rejectsInvalidHorizontalScale() {
        DataResult<TerrainConfig> result = parseResult("{\"global_horizontal_scale\": 6.0}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("global_horizontal_scale"));
    }

    @Test
    void rejectsInvalidTerrainBlendRange() {
        DataResult<TerrainConfig> result = parseResult("{\"terrain_blend_range\": 1.1}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("terrain_blend_range"));
    }

    @Test
    void rejectsUnknownTerrainShape() {
        DataResult<TerrainConfig> result = parseResult("{\"terrain_shape\": \"UNKNOWN\"}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("TerrainShape"));
    }

    @Test
    void rejectsUnknownTerrainLayoutMode() {
        DataResult<TerrainConfig> result = parseResult("{\"terrain_layout_mode\": \"UNKNOWN\"}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("TerrainLayoutMode"));
    }

    @Test
    void regionPlannedRequiresAnImplementedAreaLayer() {
        DataResult<TerrainConfig> result = parseResult(
                "{\"terrain_layout_mode\":\"REGION_PLANNED\"}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("AREA layer"));
    }

    @Test
    void rejectsInvalidMountainLayer() {
        DataResult<TerrainConfig> result = parseResult("{\"mountains\": {\"vertical_scale\": 10.1}}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("vertical_scale"));
    }

    private static JsonElement encode(TerrainConfig config) {
        return TerrainConfig.CODEC.encodeStart(JsonOps.INSTANCE, config)
                .resultOrPartial(msg -> { throw new AssertionError(msg); })
                .orElseThrow();
    }

    private static TerrainConfig decode(JsonElement json) {
        return TerrainConfig.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(msg -> { throw new AssertionError(msg); })
                .orElseThrow();
    }

    private static DataResult<TerrainConfig> parseResult(String json) {
        JsonElement parsed = com.google.gson.JsonParser.parseString(json);
        return TerrainConfig.CODEC.parse(JsonOps.INSTANCE, parsed);
    }
}
