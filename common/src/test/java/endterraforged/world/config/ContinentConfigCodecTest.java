package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import endterraforged.world.noise.DistanceFunction;

class ContinentConfigCodecTest {

    @Test
    void defaultsRoundTripLosslessly() {
        ContinentConfig config = ContinentConfig.defaults();
        assertEquals(config, decode(encode(config)));
    }

    @Test
    void emptyObjectDecodesToLegacyDefaults() {
        assertEquals(ContinentConfig.legacyDefaults(), decode(new JsonObject()));
        assertEquals(ContinentCoastShape.RADIAL_LEGACY, decode(new JsonObject()).coastShape());
        assertEquals(ContinentAlgorithm.LEGACY_RADIAL, decode(new JsonObject()).continentAlgorithm());
    }

    @Test
    void customConfigRoundTripsLosslessly() {
        ContinentConfig config = new ContinentConfig(320, 960, DistanceFunction.MANHATTAN,
                0.8F, 0.2F, 0.45F, 6, 0.3F, 3.5F,
                0.9F, 0.7F, 0.35F, 0.55F, 0.75F, 420, 64.0F, 8192);
        assertEquals(config, decode(encode(config)));
    }

    @Test
    void organicCoastConfigRoundTripsLosslessly() {
        ContinentConfig config = new ContinentConfigBuilder(ContinentConfig.defaults())
                .coastShape(ContinentCoastShape.ORGANIC)
                .coastScale(2304)
                .coastStrength(0.35F)
                .coastCellBlend(0.85F)
                .build();

        assertEquals(config, decode(encode(config)));
    }

    @Test
    void rtfMultiAlgorithmRoundTripsLosslessly() {
        ContinentConfig config = new ContinentConfigBuilder(ContinentConfig.defaults())
                .continentScale(3000)
                .continentJitter(0.7F)
                .continentAlgorithm(ContinentAlgorithm.RTF_MULTI)
                .build();

        assertEquals(config, decode(encode(config)));
    }

    @Test
    void missingBandsDecodeToLegacyPassthrough() {
        JsonObject json = new JsonObject();
        json.addProperty("algorithm", "RTF_MULTI");

        ContinentConfig decoded = decode(json);
        assertEquals(ContinentBandsConfig.LEGACY_PASSTHROUGH, decoded.continentBands());
    }

    @Test
    void bandsRoundTripLosslessly() {
        ContinentBandsConfig bands = new ContinentBandsConfig(
                true, 0.08F, 0.22F, 0.34F, 0.46F, 0.58F);
        ContinentConfig config = new ContinentConfigBuilder(ContinentConfig.defaults())
                .continentBands(bands)
                .build();

        assertEquals(config, decode(encode(config)));
    }

    @Test
    void invalidScaleFailsDecode() {
        JsonObject json = new JsonObject();
        json.addProperty("continent_scale", 8);
        DataResult<ContinentConfig> result = ContinentConfig.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("continent_scale"));
    }

    @Test
    void invalidOuterContinentScaleFailsDecode() {
        JsonObject json = new JsonObject();
        json.addProperty("outer_continent_scale", 512);
        DataResult<ContinentConfig> result = ContinentConfig.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("outer_continent_scale"));
    }

    @Test
    void invalidUnitFieldFailsDecode() {
        JsonObject json = new JsonObject();
        json.addProperty("rift_strength", 1.5F);
        DataResult<ContinentConfig> result = ContinentConfig.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("rift_strength"));
    }

    @Test
    void invalidShelfEdgeThicknessFailsDecode() {
        JsonObject json = new JsonObject();
        json.addProperty("volume_mode", "FLOATING_SHELF");
        json.addProperty("shelf_thickness", 64);
        json.addProperty("shelf_edge_thickness", 96);
        DataResult<ContinentConfig> result = ContinentConfig.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("shelf_edge_thickness"));
    }

    @Test
    void invalidCoastScaleFailsDecode() {
        JsonObject json = new JsonObject();
        json.addProperty("coast_scale", 64);
        DataResult<ContinentConfig> result = ContinentConfig.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("coast_scale"));
    }

    @Test
    void invalidCoastStrengthFailsDecode() {
        JsonObject json = new JsonObject();
        json.addProperty("coast_strength", 0.9F);
        DataResult<ContinentConfig> result = ContinentConfig.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("coast_strength"));
    }

    @Test
    void invalidCoastCellBlendFailsDecode() {
        JsonObject json = new JsonObject();
        json.addProperty("coast_cell_blend", -0.1F);
        DataResult<ContinentConfig> result = ContinentConfig.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("coast_cell_blend"));
    }

    @Test
    void unavailableAlgorithmFailsDecodeWithVisibleError() {
        JsonObject json = new JsonObject();
        json.addProperty("algorithm", "RTF_ADVANCED");
        DataResult<ContinentConfig> result = ContinentConfig.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("algorithm"));
    }

    private static ContinentConfig decode(JsonElement json) {
        DataResult<ContinentConfig> result = ContinentConfig.CODEC.parse(JsonOps.INSTANCE, json);
        return result.result().orElseThrow(() ->
                new AssertionError("decode failed: " + result.error().map(e -> e.message()).orElse("?")));
    }

    private static JsonElement encode(ContinentConfig config) {
        DataResult<JsonElement> result = ContinentConfig.CODEC.encodeStart(JsonOps.INSTANCE, config);
        return result.result().orElseThrow(() ->
                new AssertionError("encode failed: " + result.error().map(e -> e.message()).orElse("?")));
    }
}
