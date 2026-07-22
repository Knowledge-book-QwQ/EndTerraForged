package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

class CaveSystemConfigCodecTest {

    @Test
    void emptyObjectDecodesToDisabledDefaults() {
        assertEquals(CaveSystemConfig.DISABLED, decode(new JsonObject()));
        assertFalse(CaveSystemConfig.DEFAULT.enabled());
    }

    @Test
    void customConfigRoundTripsLosslessly() {
        CaveSystemConfig config = customConfig();

        assertEquals(config, decode(encode(config)));
    }

    @Test
    void encodedConfigUsesSnakeCaseKeys() {
        JsonObject json = encode(customConfig()).getAsJsonObject();

        assertTrue(json.has("enabled"));
        assertTrue(json.has("seed_offset"));
        assertTrue(json.has("depth_start"));
        assertTrue(json.has("depth_end"));
        assertTrue(json.has("spectacle_bias"));
        assertTrue(json.has("connectivity"));
        assertTrue(json.has("surface_opening_chance"));
    }

    @Test
    void rejectsInvalidDepthOrder() {
        DataResult<CaveSystemConfig> result =
                parseResult("{\"depth_start\": 1600, \"depth_end\": 1200}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("depth_start"));
        assertTrue(result.error().orElseThrow().message().contains("depth_end"));
    }

    private static JsonElement encode(CaveSystemConfig config) {
        return CaveSystemConfig.CODEC.encodeStart(JsonOps.INSTANCE, config)
                .resultOrPartial(msg -> { throw new AssertionError(msg); })
                .orElseThrow();
    }

    private static CaveSystemConfig decode(JsonElement json) {
        return CaveSystemConfig.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(msg -> { throw new AssertionError(msg); })
                .orElseThrow();
    }

    private static DataResult<CaveSystemConfig> parseResult(String json) {
        return CaveSystemConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static CaveSystemConfig customConfig() {
        return new CaveSystemConfig(true, 2600, 96, 1800, 0.9F, 0.72F, 0.05F);
    }
}
