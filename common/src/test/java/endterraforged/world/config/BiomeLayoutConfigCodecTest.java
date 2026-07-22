package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

class BiomeLayoutConfigCodecTest {

    private static BiomeLayoutConfig decode(JsonElement json) {
        DataResult<BiomeLayoutConfig> result = BiomeLayoutConfig.CODEC.parse(JsonOps.INSTANCE, json);
        return result.result().orElseThrow(() ->
                new AssertionError("decode failed: "
                        + result.error().map(e -> e.message()).orElse("?")));
    }

    private static JsonElement encode(BiomeLayoutConfig config) {
        DataResult<JsonElement> result = BiomeLayoutConfig.CODEC.encodeStart(JsonOps.INSTANCE, config);
        return result.result().orElseThrow(() ->
                new AssertionError("encode failed: "
                        + result.error().map(e -> e.message()).orElse("?")));
    }

    @Test
    void defaultsRoundTripLosslessly() {
        assertEquals(BiomeLayoutConfig.DEFAULT, decode(encode(BiomeLayoutConfig.DEFAULT)));
    }

    @Test
    void emptyObjectDecodesToDefaults() {
        assertEquals(BiomeLayoutConfig.DEFAULT, decode(new JsonObject()));
    }

    @Test
    void customConfigRoundTripsLosslessly() {
        BiomeLayoutConfig custom = new BiomeLayoutConfig(32, 6.0F, 35.0F, -5.0F,
                180, 3, 2.5F, 0.65F, 22.0F,
                240, 36.0F,
                360, 2, 0.35F,
                new BiomeVariantBlendConfig(90, 3));
        assertEquals(custom, decode(encode(custom)));
    }

    @Test
    void encodedConfigUsesSnakeCaseKeys() {
        BiomeLayoutConfig custom = new BiomeLayoutConfig(32, 6.0F, 35.0F, -5.0F,
                180, 3, 2.5F, 0.65F, 22.0F,
                240, 36.0F,
                360, 2, 0.35F,
                new BiomeVariantBlendConfig(90, 3));
        JsonObject json = encode(custom).getAsJsonObject();
        assertTrue(json.has("main_island_radius"));
        assertTrue(json.has("radial_coefficient"));
        assertTrue(json.has("highland_threshold"));
        assertTrue(json.has("midland_floor"));
        assertTrue(json.has("biome_edge_scale"));
        assertTrue(json.has("biome_edge_octaves"));
        assertTrue(json.has("biome_edge_lacunarity"));
        assertTrue(json.has("biome_edge_gain"));
        assertTrue(json.has("biome_edge_strength"));
        assertTrue(json.has("biome_warp_scale"));
        assertTrue(json.has("biome_warp_strength"));
        assertTrue(json.has("outer_noise_scale"));
        assertTrue(json.has("outer_noise_octaves"));
        assertTrue(json.has("outer_noise_threshold"));
        assertTrue(json.has("variant_blend"));
    }

    @Test
    void invalidMidlandFloorFailsDecode() {
        JsonObject json = new JsonObject();
        json.addProperty("midland_floor", 60.0F);
        json.addProperty("highland_threshold", 40.0F);
        DataResult<BiomeLayoutConfig> result = BiomeLayoutConfig.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("midland_floor"));
    }

    @Test
    void invalidWarpStrengthFailsDecode() {
        JsonObject json = new JsonObject();
        json.addProperty("biome_warp_strength", -1.0F);
        DataResult<BiomeLayoutConfig> result = BiomeLayoutConfig.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("biome_warp_strength"));
    }

    @Test
    void invalidVariantBlendFailsDecode() {
        JsonObject variantBlend = new JsonObject();
        variantBlend.addProperty("scale", 0);
        JsonObject json = new JsonObject();
        json.add("variant_blend", variantBlend);
        DataResult<BiomeLayoutConfig> result = BiomeLayoutConfig.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("variant_blend"));
    }
}
