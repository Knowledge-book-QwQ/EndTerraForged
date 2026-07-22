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

class AbyssPitConfigCodecTest {

    @Test
    void emptyObjectDecodesToDisabledDefaults() {
        assertEquals(AbyssPitConfig.DISABLED, decode(new JsonObject()));
        assertFalse(AbyssPitConfig.DEFAULT.enabled());
    }

    @Test
    void customConfigRoundTripsLosslessly() {
        AbyssPitConfig config = new AbyssPitConfig(
                true, 1700, 512, 5, 2.5F, 0.65F,
                0.7F, 0.2F, 256, 1.4F, 0.5F);

        assertEquals(config, decode(encode(config)));
    }

    @Test
    void rejectsOutOfRangeThreshold() {
        DataResult<AbyssPitConfig> result = parseResult("{\"threshold\": 1.1}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("threshold"));
    }

    @Test
    void rejectsOutOfRangePitScale() {
        DataResult<AbyssPitConfig> result = parseResult("{\"pit_scale\": 16}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("pit_scale"));
    }

    @Test
    void rejectsOutOfRangePitOctaves() {
        DataResult<AbyssPitConfig> result = parseResult("{\"pit_octaves\": 0}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("pit_octaves"));
    }

    @Test
    void rejectsOutOfRangeDepthCurve() {
        DataResult<AbyssPitConfig> result = parseResult("{\"depth_curve\": 0.0}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("depth_curve"));
    }

    private static JsonElement encode(AbyssPitConfig config) {
        return AbyssPitConfig.CODEC.encodeStart(JsonOps.INSTANCE, config)
                .resultOrPartial(msg -> { throw new AssertionError(msg); })
                .orElseThrow();
    }

    private static AbyssPitConfig decode(JsonElement json) {
        return AbyssPitConfig.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(msg -> { throw new AssertionError(msg); })
                .orElseThrow();
    }

    private static DataResult<AbyssPitConfig> parseResult(String json) {
        return AbyssPitConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }
}
