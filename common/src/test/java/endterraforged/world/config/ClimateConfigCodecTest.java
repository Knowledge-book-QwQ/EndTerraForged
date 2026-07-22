package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

class ClimateConfigCodecTest {

    private static ClimateConfig decode(JsonElement json) {
        DataResult<ClimateConfig> result = ClimateConfig.CODEC.parse(JsonOps.INSTANCE, json);
        return result.result().orElseThrow(() ->
                new AssertionError("decode failed: "
                        + result.error().map(e -> e.message()).orElse("?")));
    }

    private static JsonElement encode(ClimateConfig config) {
        DataResult<JsonElement> result = ClimateConfig.CODEC.encodeStart(JsonOps.INSTANCE, config);
        return result.result().orElseThrow(() ->
                new AssertionError("encode failed: "
                        + result.error().map(e -> e.message()).orElse("?")));
    }

    @Test
    void defaultsRoundTripLosslessly() {
        ClimateConfig roundTripped = decode(encode(ClimateConfig.DEFAULT));
        assertEquals(ClimateConfig.DEFAULT, roundTripped);
    }

    @Test
    void emptyObjectDecodesToDefaults() {
        assertEquals(ClimateConfig.DEFAULT, decode(new JsonObject()));
    }

    @Test
    void customConfigRoundTripsLosslessly() {
        ClimateConfig custom = new ClimateConfig(6000.0F, 900, 901,
                1200, 1201, 1500, 0.4F,
                2.5F, 0.15F, 0.85F, 0.2F,
                3.5F, 0.1F, 0.9F, -0.15F);
        assertEquals(custom, decode(encode(custom)));
    }

    @Test
    void encodedConfigUsesSnakeCaseKeys() {
        ClimateConfig custom = new ClimateConfig(6000.0F, 900, 901,
                1200, 1201, 1500, 0.4F,
                2.5F, 0.15F, 0.85F, 0.2F,
                3.5F, 0.1F, 0.9F, -0.15F);
        JsonObject json = encode(custom).getAsJsonObject();
        assertTrue(json.has("climate_radius"));
        assertTrue(json.has("temperature_scale"));
        assertTrue(json.has("temperature_seed_offset"));
        assertTrue(json.has("moisture_scale"));
        assertTrue(json.has("moisture_seed_offset"));
        assertTrue(json.has("wind_scale"));
        assertTrue(json.has("perturbation"));
        assertTrue(json.has("temperature_falloff"));
        assertTrue(json.has("temperature_min"));
        assertTrue(json.has("temperature_max"));
        assertTrue(json.has("temperature_bias"));
        assertTrue(json.has("moisture_falloff"));
        assertTrue(json.has("moisture_min"));
        assertTrue(json.has("moisture_max"));
        assertTrue(json.has("moisture_bias"));
    }

    @Test
    void invalidClimateRadiusFailsDecode() {
        JsonObject json = new JsonObject();
        json.addProperty("climate_radius", 0.0F);
        DataResult<ClimateConfig> result = ClimateConfig.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("climate_radius"));
    }

    @Test
    void invalidChannelRangeFailsDecode() {
        JsonObject json = new JsonObject();
        json.addProperty("temperature_min", 0.9F);
        json.addProperty("temperature_max", 0.1F);
        DataResult<ClimateConfig> result = ClimateConfig.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("temperature_min"));
    }

    @Test
    void invalidFalloffFailsDecode() {
        JsonObject json = new JsonObject();
        json.addProperty("moisture_falloff", 0.5F);
        DataResult<ClimateConfig> result = ClimateConfig.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("moisture_falloff"));
    }
}
