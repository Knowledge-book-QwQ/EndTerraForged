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

class SubsurfaceConfigCodecTest {

    @Test
    void emptyObjectDecodesToDisabledDefaults() {
        assertEquals(SubsurfaceConfig.DISABLED, decode(new JsonObject()));
    }

    @Test
    void customConfigRoundTripsLosslessly() {
        SubsurfaceConfig config = customConfig();

        assertEquals(config, decode(encode(config)));
    }

    @Test
    void encodedConfigUsesSnakeCaseKeys() {
        JsonObject json = encode(customConfig()).getAsJsonObject();

        assertTrue(json.has("abyss"));
        JsonObject abyss = json.getAsJsonObject("abyss");
        assertTrue(abyss.has("enabled"));
        assertTrue(abyss.has("seed_offset"));
        assertTrue(abyss.has("pit_scale"));
        assertTrue(abyss.has("pit_octaves"));
        assertTrue(abyss.has("pit_lacunarity"));
        assertTrue(abyss.has("pit_gain"));
        assertTrue(abyss.has("edge_falloff"));
        assertTrue(abyss.has("depth_curve"));
        assertTrue(abyss.has("min_landness"));
        assertTrue(json.has("caves"));
        JsonObject caves = json.getAsJsonObject("caves");
        assertTrue(caves.has("enabled"));
        assertTrue(caves.has("entrance_probability"));
        assertTrue(caves.has("cheese_depth_offset"));
        assertTrue(caves.has("cheese_probability"));
        assertTrue(caves.has("spaghetti_probability"));
        assertTrue(caves.has("noodle_probability"));
        assertTrue(json.has("cave_system"));
        JsonObject caveSystem = json.getAsJsonObject("cave_system");
        assertTrue(caveSystem.has("enabled"));
        assertTrue(caveSystem.has("seed_offset"));
        assertTrue(caveSystem.has("depth_start"));
        assertTrue(caveSystem.has("depth_end"));
        assertTrue(caveSystem.has("spectacle_bias"));
        assertTrue(caveSystem.has("connectivity"));
        assertTrue(caveSystem.has("surface_opening_chance"));
        assertTrue(json.has("cave_network"));
        JsonObject caveNetwork = json.getAsJsonObject("cave_network");
        assertTrue(caveNetwork.has("region_size"));
        assertTrue(caveNetwork.has("network_density"));
        assertTrue(caveNetwork.has("chamber_spacing"));
        assertTrue(caveNetwork.has("branching_factor"));
        assertTrue(caveNetwork.has("loop_chance"));
        assertTrue(caveNetwork.has("max_slope"));
        assertTrue(caveNetwork.has("min_landness"));
        assertTrue(json.has("cave_chambers"));
        JsonObject caveChambers = json.getAsJsonObject("cave_chambers");
        assertTrue(caveChambers.has("chamber_probability"));
        assertTrue(caveChambers.has("min_radius"));
        assertTrue(caveChambers.has("max_radius"));
        assertTrue(caveChambers.has("vertical_stretch"));
        assertTrue(caveChambers.has("floor_bias"));
        assertTrue(caveChambers.has("roughness"));
    }

    @Test
    void encodedConfigDoesNotExposePreviewOnlyFields() {
        JsonObject json = encode(customConfig()).getAsJsonObject();

        assertFalse(json.has("cave_liquids"));
        assertFalse(json.has("cave_water"));
        assertFalse(json.has("cave_lava"));
        assertFalse(json.has("preview_mode"));
        assertFalse(json.has("slice_axis"));
        assertFalse(json.has("slice_offset"));
        assertFalse(json.has("blocks_per_pixel"));
    }

    @Test
    void previewOnlyFieldsFailDecode() {
        assertPreviewOnlyFieldFails("cave_liquids");
        assertPreviewOnlyFieldFails("cave_water");
        assertPreviewOnlyFieldFails("cave_lava");
        assertPreviewOnlyFieldFails("preview_mode");
        assertPreviewOnlyFieldFails("slice_axis");
        assertPreviewOnlyFieldFails("slice_offset");
        assertPreviewOnlyFieldFails("blocks_per_pixel");
    }

    @Test
    void invalidAbyssConfigFailsDecode() {
        DataResult<SubsurfaceConfig> result =
                parseResult("{\"abyss\":{\"depth\":0}}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("abyss"));
        assertTrue(result.error().orElseThrow().message().contains("depth"));
    }

    @Test
    void invalidCaveConfigFailsDecode() {
        DataResult<SubsurfaceConfig> result =
                parseResult("{\"caves\":{\"cheese_probability\":1.1}}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("caves"));
        assertTrue(result.error().orElseThrow().message().contains("cheese_probability"));
    }

    @Test
    void invalidCaveSystemConfigFailsDecode() {
        DataResult<SubsurfaceConfig> result =
                parseResult("{\"cave_system\":{\"depth_start\":1600,\"depth_end\":1200}}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("cave_system"));
        assertTrue(result.error().orElseThrow().message().contains("depth_start"));
    }

    @Test
    void invalidCaveNetworkConfigFailsDecode() {
        DataResult<SubsurfaceConfig> result =
                parseResult("{\"cave_network\":{\"region_size\":64}}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("cave_network"));
        assertTrue(result.error().orElseThrow().message().contains("region_size"));
    }

    @Test
    void invalidCaveChamberConfigFailsDecode() {
        DataResult<SubsurfaceConfig> result =
                parseResult("{\"cave_chambers\":{\"min_radius\":240,\"max_radius\":120}}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("cave_chambers"));
        assertTrue(result.error().orElseThrow().message().contains("min_radius"));
    }

    @Test
    void missingCavesFallsBackToDisabledDefaults() {
        SubsurfaceConfig decoded = decode(JsonParser.parseString("""
                {
                  "abyss": {
                    "enabled": true,
                    "pit_scale": 512
                  }
                }
                """));

        assertEquals(new AbyssPitConfig(true,
                        AbyssPitConfig.DEFAULT.seedOffset(), 512,
                        AbyssPitConfig.DEFAULT.pitOctaves(),
                        AbyssPitConfig.DEFAULT.pitLacunarity(),
                        AbyssPitConfig.DEFAULT.pitGain(),
                        AbyssPitConfig.DEFAULT.threshold(),
                        AbyssPitConfig.DEFAULT.edgeFalloff(),
                        AbyssPitConfig.DEFAULT.depth(),
                        AbyssPitConfig.DEFAULT.depthCurve(),
                        AbyssPitConfig.DEFAULT.minLandness()),
                decoded.abyssPitConfig());
        assertEquals(CaveTunnelConfig.DEFAULT, decoded.caveTunnelConfig());
        assertEquals(CaveSystemConfig.DEFAULT, decoded.caveSystemConfig());
        assertEquals(CaveNetworkConfig.DEFAULT, decoded.caveNetworkConfig());
        assertEquals(CaveChamberConfig.DEFAULT, decoded.caveChamberConfig());
    }

    private static SubsurfaceConfig customConfig() {
        return new SubsurfaceConfig(
                new AbyssPitConfig(true, 1700, 512, 5, 2.5F, 0.65F,
                        0.7F, 0.2F, 256, 1.4F, 0.5F),
                new CaveTunnelConfig(true, 0.25F, 1.75F, 0.4F, 0.3F, 0.2F),
                new CaveSystemConfig(true, 2600, 96, 1800, 0.9F, 0.72F, 0.05F),
                new CaveNetworkConfig(1536, 0.42F, 512, 2.5F, 0.22F, 0.6F, 0.3F),
                new CaveChamberConfig(0.62F, 72, 320, 2.2F, 0.42F, 0.7F));
    }

    private static JsonElement encode(SubsurfaceConfig config) {
        return SubsurfaceConfig.CODEC.encodeStart(JsonOps.INSTANCE, config)
                .resultOrPartial(msg -> { throw new AssertionError(msg); })
                .orElseThrow();
    }

    private static SubsurfaceConfig decode(JsonElement json) {
        return SubsurfaceConfig.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(msg -> { throw new AssertionError(msg); })
                .orElseThrow();
    }

    private static DataResult<SubsurfaceConfig> parseResult(String json) {
        return SubsurfaceConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static void assertPreviewOnlyFieldFails(String field) {
        DataResult<SubsurfaceConfig> result = parseResult("{\"" + field + "\":{}}");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("preview-only"));
        assertTrue(result.error().orElseThrow().message().contains(field));
    }
}
