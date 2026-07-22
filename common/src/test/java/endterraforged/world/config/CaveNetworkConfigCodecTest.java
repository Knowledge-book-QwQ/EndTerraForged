package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

class CaveNetworkConfigCodecTest {

    @Test
    void emptyObjectDecodesToDefaults() {
        assertEquals(CaveNetworkConfig.DEFAULT, decode(new JsonObject()));
    }

    @Test
    void customConfigRoundTripsLosslessly() {
        CaveNetworkConfig config = customConfig();

        assertEquals(config, decode(encode(config)));
    }

    @Test
    void encodedConfigUsesSnakeCaseKeys() {
        JsonObject json = encode(customConfig()).getAsJsonObject();

        assertTrue(json.has("region_size"));
        assertTrue(json.has("network_density"));
        assertTrue(json.has("chamber_spacing"));
        assertTrue(json.has("branching_factor"));
        assertTrue(json.has("loop_chance"));
        assertTrue(json.has("max_slope"));
        assertTrue(json.has("min_landness"));
    }

    @Test
    void rejectsOutOfRangeRegionSize() {
        DataResult<CaveNetworkConfig> result = parseResult("{\"region_size\": 64}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("region_size"));
    }

    private static JsonElement encode(CaveNetworkConfig config) {
        return CaveNetworkConfig.CODEC.encodeStart(JsonOps.INSTANCE, config)
                .resultOrPartial(msg -> { throw new AssertionError(msg); })
                .orElseThrow();
    }

    private static CaveNetworkConfig decode(JsonElement json) {
        return CaveNetworkConfig.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(msg -> { throw new AssertionError(msg); })
                .orElseThrow();
    }

    private static DataResult<CaveNetworkConfig> parseResult(String json) {
        return CaveNetworkConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static CaveNetworkConfig customConfig() {
        return new CaveNetworkConfig(1536, 0.42F, 512, 2.5F, 0.22F, 0.6F, 0.3F);
    }
}
