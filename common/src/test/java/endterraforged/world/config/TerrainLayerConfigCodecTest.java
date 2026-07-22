package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

class TerrainLayerConfigCodecTest {

    @Test
    void emptyObjectDecodesToDefaults() {
        assertEquals(TerrainLayerConfig.DEFAULT, decode(new JsonObject()));
    }

    @Test
    void disabledLayerKeepsZeroWeightWithNeutralScales() {
        TerrainLayerConfig disabled = TerrainLayerConfig.DISABLED;

        assertEquals(0.0F, disabled.weight());
        assertEquals(1.0F, disabled.baseScale());
        assertEquals(1.0F, disabled.verticalScale());
        assertEquals(1.0F, disabled.horizontalScale());
    }

    @Test
    void customLayerRoundTripsLosslessly() {
        TerrainLayerConfig config = new TerrainLayerConfig(2.5F, 1.25F, 3.0F, 4.0F);

        assertEquals(config, decode(encode(config)));
    }

    @Test
    void rejectsOutOfRangeWeight() {
        DataResult<TerrainLayerConfig> result = parseResult("{\"weight\": 10.1}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("weight"));
    }

    @Test
    void rejectsOutOfRangeBaseScale() {
        DataResult<TerrainLayerConfig> result = parseResult("{\"base_scale\": 2.1}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("base_scale"));
    }

    private static JsonElement encode(TerrainLayerConfig config) {
        return TerrainLayerConfig.CODEC.encodeStart(JsonOps.INSTANCE, config)
                .resultOrPartial(msg -> { throw new AssertionError(msg); })
                .orElseThrow();
    }

    private static TerrainLayerConfig decode(JsonElement json) {
        return TerrainLayerConfig.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(msg -> { throw new AssertionError(msg); })
                .orElseThrow();
    }

    private static DataResult<TerrainLayerConfig> parseResult(String json) {
        JsonElement parsed = com.google.gson.JsonParser.parseString(json);
        return TerrainLayerConfig.CODEC.parse(JsonOps.INSTANCE, parsed);
    }
}
