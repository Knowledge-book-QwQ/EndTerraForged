package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

class ContinentBandsConfigCodecTest {

    @Test
    void defaultsRoundTripLosslessly() {
        assertEquals(ContinentBandsConfig.DEFAULT, decode(encode(ContinentBandsConfig.DEFAULT)));
    }

    @Test
    void missingObjectDecodesToLegacyPassthrough() {
        assertEquals(ContinentBandsConfig.LEGACY_PASSTHROUGH, decode(new JsonObject()));
    }

    @Test
    void customBandsRoundTripLosslessly() {
        ContinentBandsConfig config = new ContinentBandsConfig(
                true, 0.08F, 0.22F, 0.34F, 0.46F, 0.58F);
        assertEquals(config, decode(encode(config)));
    }

    @Test
    void invalidThresholdOrderFailsDecode() {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", true);
        json.addProperty("void_outer", 0.30F);
        json.addProperty("shelf", 0.20F);
        json.addProperty("rim", 0.35F);
        json.addProperty("coast", 0.45F);
        json.addProperty("inland", 0.55F);

        DataResult<ContinentBandsConfig> result = ContinentBandsConfig.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("void_outer < shelf"));
    }

    private static ContinentBandsConfig decode(JsonElement json) {
        DataResult<ContinentBandsConfig> result = ContinentBandsConfig.CODEC.parse(JsonOps.INSTANCE, json);
        return result.result().orElseThrow(() ->
                new AssertionError(result.error().map(error -> error.message()).orElse("decode failed")));
    }

    private static JsonElement encode(ContinentBandsConfig config) {
        DataResult<JsonElement> result = ContinentBandsConfig.CODEC.encodeStart(JsonOps.INSTANCE, config);
        return result.result().orElseThrow(() ->
                new AssertionError(result.error().map(error -> error.message()).orElse("encode failed")));
    }
}
