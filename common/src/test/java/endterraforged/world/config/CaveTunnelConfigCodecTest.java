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

class CaveTunnelConfigCodecTest {

    @Test
    void emptyObjectDecodesToDisabledDefaults() {
        assertEquals(CaveTunnelConfig.DISABLED, decode(new JsonObject()));
        assertFalse(CaveTunnelConfig.DEFAULT.enabled());
    }

    @Test
    void customConfigRoundTripsLosslessly() {
        CaveTunnelConfig config = customConfig();

        assertEquals(config, decode(encode(config)));
    }

    @Test
    void encodedConfigUsesSnakeCaseKeys() {
        JsonObject json = encode(customConfig()).getAsJsonObject();

        assertTrue(json.has("enabled"));
        assertTrue(json.has("entrance_probability"));
        assertTrue(json.has("cheese_depth_offset"));
        assertTrue(json.has("cheese_probability"));
        assertTrue(json.has("spaghetti_probability"));
        assertTrue(json.has("noodle_probability"));
    }

    @Test
    void rejectsOutOfRangeProbability() {
        DataResult<CaveTunnelConfig> result = parseResult("{\"cheese_probability\": 1.1}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("cheese_probability"));
    }

    @Test
    void rejectsOutOfRangeCheeseDepthOffset() {
        DataResult<CaveTunnelConfig> result = parseResult("{\"cheese_depth_offset\": 8.1}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("cheese_depth_offset"));
    }

    private static JsonElement encode(CaveTunnelConfig config) {
        return CaveTunnelConfig.CODEC.encodeStart(JsonOps.INSTANCE, config)
                .resultOrPartial(msg -> { throw new AssertionError(msg); })
                .orElseThrow();
    }

    private static CaveTunnelConfig decode(JsonElement json) {
        return CaveTunnelConfig.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(msg -> { throw new AssertionError(msg); })
                .orElseThrow();
    }

    private static DataResult<CaveTunnelConfig> parseResult(String json) {
        return CaveTunnelConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static CaveTunnelConfig customConfig() {
        return new CaveTunnelConfig(true, 0.25F, 1.75F, 0.4F, 0.3F, 0.2F);
    }
}
