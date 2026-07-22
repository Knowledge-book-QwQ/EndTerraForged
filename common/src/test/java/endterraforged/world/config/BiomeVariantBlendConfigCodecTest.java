package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

class BiomeVariantBlendConfigCodecTest {

    @Test
    void defaultsRoundTripLosslessly() {
        assertEquals(BiomeVariantBlendConfig.DEFAULT, decode(encode(BiomeVariantBlendConfig.DEFAULT)));
    }

    @Test
    void emptyObjectDecodesToDefaults() {
        assertEquals(BiomeVariantBlendConfig.DEFAULT, decode(new JsonObject()));
    }

    @Test
    void customConfigRoundTripsLosslessly() {
        BiomeVariantBlendConfig custom = new BiomeVariantBlendConfig(120, 3);
        assertEquals(custom, decode(encode(custom)));
    }

    @Test
    void invalidScaleFailsDecode() {
        JsonObject json = new JsonObject();
        json.addProperty("scale", 0);
        DataResult<BiomeVariantBlendConfig> result =
                BiomeVariantBlendConfig.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("scale"));
    }

    @Test
    void invalidOctavesFailsDecode() {
        JsonObject json = new JsonObject();
        json.addProperty("octaves", 0);
        DataResult<BiomeVariantBlendConfig> result =
                BiomeVariantBlendConfig.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("octaves"));
    }

    private static BiomeVariantBlendConfig decode(JsonElement json) {
        DataResult<BiomeVariantBlendConfig> result =
                BiomeVariantBlendConfig.CODEC.parse(JsonOps.INSTANCE, json);
        return result.result().orElseThrow(() ->
                new AssertionError("decode failed: "
                        + result.error().map(e -> e.message()).orElse("?")));
    }

    private static JsonElement encode(BiomeVariantBlendConfig config) {
        DataResult<JsonElement> result = BiomeVariantBlendConfig.CODEC.encodeStart(JsonOps.INSTANCE, config);
        return result.result().orElseThrow(() ->
                new AssertionError("encode failed: "
                        + result.error().map(e -> e.message()).orElse("?")));
    }
}
