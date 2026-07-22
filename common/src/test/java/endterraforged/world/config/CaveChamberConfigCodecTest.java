package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

class CaveChamberConfigCodecTest {

    @Test
    void emptyObjectDecodesToDefaults() {
        assertEquals(CaveChamberConfig.DEFAULT, decode(new JsonObject()));
    }

    @Test
    void customConfigRoundTripsLosslessly() {
        CaveChamberConfig config = customConfig();

        assertEquals(config, decode(encode(config)));
    }

    @Test
    void encodedConfigUsesSnakeCaseKeys() {
        JsonObject json = encode(customConfig()).getAsJsonObject();

        assertTrue(json.has("chamber_probability"));
        assertTrue(json.has("min_radius"));
        assertTrue(json.has("max_radius"));
        assertTrue(json.has("vertical_stretch"));
        assertTrue(json.has("floor_bias"));
        assertTrue(json.has("roughness"));
    }

    @Test
    void rejectsInvalidRadiusOrder() {
        DataResult<CaveChamberConfig> result =
                parseResult("{\"min_radius\": 240, \"max_radius\": 120}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("min_radius"));
        assertTrue(result.error().orElseThrow().message().contains("max_radius"));
    }

    private static JsonElement encode(CaveChamberConfig config) {
        return CaveChamberConfig.CODEC.encodeStart(JsonOps.INSTANCE, config)
                .resultOrPartial(msg -> { throw new AssertionError(msg); })
                .orElseThrow();
    }

    private static CaveChamberConfig decode(JsonElement json) {
        return CaveChamberConfig.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(msg -> { throw new AssertionError(msg); })
                .orElseThrow();
    }

    private static DataResult<CaveChamberConfig> parseResult(String json) {
        return CaveChamberConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static CaveChamberConfig customConfig() {
        return new CaveChamberConfig(0.62F, 72, 320, 2.2F, 0.42F, 0.7F);
    }
}
