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

import endterraforged.world.filter.ErosionConfig;
import endterraforged.world.noise.DistanceFunction;

/**
 * Round-trip and field-default tests for {@link EndPreset}'s DFU codec.
 *
 * <p>These establish the {@code world/config} package's first test baseline
 * (the architecture audit flagged it as having none) and pin the serialisation
 * contract that the stage-5 GUI / data-pack preset files will rely on:
 * <ul>
 *   <li>{@link EndPreset#defaults()} round-trips through {@link JsonOps} losslessly;</li>
 *   <li>a fully-custom preset round-trips losslessly;</li>
 *   <li>the {@link DimensionProfile} accessors on the decoded instance match;</li>
 *   <li>an empty JSON object decodes to {@link EndPreset#defaults()} (every field
 *       has a default, so partial preset files are valid);</li>
 *   <li>omitting a single field falls back to that field's default;</li>
 *   <li>an unknown {@link SeaMode} name fails decode (no silent fallback to a
 *       wrong mode — a typo in a preset file must surface).</li>
 * </ul>
 *
 * <p>Tests run on the common module's classpath, so Mojang's DFU
 * ({@code com.mojang.serialization}) and Gson are available.</p>
 */
class EndPresetCodecTest {

    private static EndPreset decode(JsonElement json) {
        DataResult<EndPreset> result = EndPreset.CODEC.parse(JsonOps.INSTANCE, json);
        return result.result().orElseThrow(() ->
                new AssertionError("decode failed: " + result.error().map(e -> e.message()).orElse("?")));
    }

    private static JsonElement encode(EndPreset preset) {
        DataResult<JsonElement> result = EndPreset.CODEC.encodeStart(JsonOps.INSTANCE, preset);
        return result.result().orElseThrow(() ->
                new AssertionError("encode failed: " + result.error().map(e -> e.message()).orElse("?")));
    }

    // ----- round-trip -----------------------------------------------------

    @Test
    void defaultsRoundTripLosslessly() {
        EndPreset original = EndPreset.defaults();
        EndPreset roundTripped = decode(encode(original));
        assertEquals(original, roundTripped,
                "defaults() must survive a codec round-trip unchanged");
    }

    @Test
    void customPresetRoundTripsLosslessly() {
        // Every field differs from defaults() so a swapped-field codec bug
        // (e.g. seaLevelY<->islandBaselineY) would be caught.
        EndPreset custom = new EndPreset(384, -64, 63, 100,
                SeaMode.WITH_FLOOR, TopologyMode.CONTINENTAL_SHATTERED, true,
                ContinentConfig.defaults(),
                new TerrainConfig(0.75F, 2.5F),
                new ClimateConfig(6000.0F, 900, 1200, 1500, 0.4F),
                new ErosionConfig(200, 40, 1.5F, 1.2F, 0.7F, 0.3F));
        EndPreset roundTripped = decode(encode(custom));
        assertEquals(custom, roundTripped,
                "a fully-custom preset must survive a codec round-trip unchanged");
    }

    @Test
    void rtfMultiContinentConfigRoundTripsInsidePreset() {
        ContinentConfig rtfMulti = new ContinentConfigBuilder(ContinentConfig.defaults())
                .continentScale(3000)
                .continentJitter(0.7F)
                .continentAlgorithm(ContinentAlgorithm.RTF_MULTI)
                .build();
        EndPreset preset = new EndPresetBuilder(EndPreset.defaults())
                .continentConfig(rtfMulti)
                .build();

        assertEquals(preset, decode(encode(preset)));
    }

    @Test
    void formatTwoRtfPresetWithoutBandsKeepsLegacyPassthrough() {
        JsonObject continent = new JsonObject();
        continent.addProperty("algorithm", "RTF_MULTI");
        continent.addProperty("continent_scale", 3000);
        continent.addProperty("continent_jitter", 0.7F);
        JsonObject preset = new JsonObject();
        preset.addProperty("format_version", 2);
        preset.addProperty("topology_mode", "OUTER_CONTINENTS");
        preset.add("continent", continent);

        EndPreset decoded = decode(preset);
        assertEquals(2, decoded.formatVersion());
        assertEquals(ContinentBandsConfig.LEGACY_PASSTHROUGH,
                decoded.continentConfig().continentBands());
    }

    @Test
    void formatThreePresetWithoutBandsFailsDecode() {
        JsonObject continent = new JsonObject();
        continent.addProperty("algorithm", "RTF_MULTI");
        JsonObject preset = new JsonObject();
        preset.addProperty("format_version", 3);
        preset.add("continent", continent);

        DataResult<EndPreset> result = EndPreset.CODEC.parse(JsonOps.INSTANCE, preset);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("continent.bands"));
    }

    @Test
    void decodedPresetImplementsDimensionProfile() {
        // The record's accessors must satisfy DimensionProfile after decode —
        // i.e. the decoded object is usable everywhere a DimensionProfile is.
        EndPreset decoded = decode(encode(EndPreset.defaults()));
        DimensionProfile profile = decoded;
        assertEquals(EndPreset.defaults().worldHeight(), profile.worldHeight());
        assertEquals(EndPreset.defaults().minY(), profile.minY());
        assertEquals(EndPreset.defaults().seaLevelY(), profile.seaLevelY());
        assertEquals(EndPreset.defaults().islandBaselineY(), profile.islandBaselineY());
        assertEquals(EndPreset.defaults().seaMode(), profile.seaMode());
        assertEquals(EndPreset.defaults().topologyMode(), profile.topologyMode());
        assertEquals(EndPreset.defaults().floatingIslandsEnabled(), profile.floatingIslandsEnabled());
        assertEquals(EndPreset.defaults().continentConfig(), profile.continentConfig());
        assertEquals(EndPreset.defaults().terrainConfig(), profile.terrainConfig());
        assertEquals(EndPreset.defaults().climateConfig(), decoded.climateConfig());
        assertEquals(EndPreset.defaults().biomeLayoutConfig(), decoded.biomeLayoutConfig());
        assertEquals(EndPreset.defaults().subsurfaceConfig(), profile.subsurfaceConfig());
        // surfaceY()/maxY() are DimensionProfile defaults derived from minY+worldHeight.
        assertEquals(EndPreset.defaults().surfaceY(), profile.surfaceY());
        assertEquals(EndPreset.defaults().maxY(), profile.maxY());
    }

    // ----- field defaults -------------------------------------------------

    @Test
    void emptyObjectDecodesToLegacyDefaults() {
        // Old compact preset files had no format_version. They retain their
        // historical topology rather than silently adopting new defaults.
        EndPreset decoded = decode(new JsonObject());
        assertEquals(EndPreset.legacyDefaults(), decoded,
                "an empty legacy preset object must retain the historical defaults");
    }

    @Test
    void omittingErosionFallsBackToDefaultErosion() {
        // A preset that sets shape switches but omits "erosion" must inherit
        // the default ErosionConfig — the GUI/data-pack must not be forced to
        // spell out every sub-field.
        JsonObject partial = new JsonObject();
        partial.addProperty("sea_mode", "WITH_FLOOR");
        EndPreset decoded = decode(partial);
        assertEquals(SeaMode.WITH_FLOOR, decoded.seaMode(),
                "the explicitly-set field must be honoured");
        assertEquals(TerrainConfig.DEFAULT, decoded.terrainConfig(),
                "the omitted terrain field must fall back to the default");
        assertEquals(ClimateConfig.DEFAULT, decoded.climateConfig(),
                "the omitted climate field must fall back to the default");
        assertEquals(BiomeLayoutConfig.DEFAULT, decoded.biomeLayoutConfig(),
                "the omitted biome_layout field must fall back to the default");
        assertEquals(SubsurfaceConfig.DEFAULT, decoded.subsurfaceConfig(),
                "the omitted subsurface field must fall back to the default");
        assertEquals(ErosionConfig.DEFAULT, decoded.erosionConfig(),
                "the omitted erosion field must fall back to the default");
    }

    @Test
    void omittingFloatingIslandsFallsBackToFalse() {
        JsonObject partial = new JsonObject();
        partial.addProperty("topology_mode", "CONTINENTAL_SHATTERED");
        EndPreset decoded = decode(partial);
        assertEquals(TopologyMode.CONTINENTAL_SHATTERED, decoded.topologyMode());
        assertFalse(decoded.floatingIslandsEnabled(),
                "omitting floating_islands must fall back to the default (false)");
    }

    // ----- error handling -------------------------------------------------

    @Test
    void unknownSeaModeNameFailsDecode() {
        // A typo'd enum name must NOT silently fall back to a default mode —
        // that would silently generate the wrong dimension shape. It must
        // surface as a failed DataResult.
        JsonObject bad = new JsonObject();
        bad.addProperty("sea_mode", "WIT_FLOOR");  // typo
        DataResult<EndPreset> result = EndPreset.CODEC.parse(JsonOps.INSTANCE, bad);
        assertTrue(result.error().isPresent(),
                "an unknown sea_mode name must fail decode, not silently default");
    }

    @Test
    void defaultsUseStandardPlayerWorldEnvelope() {
        // The default must stay in lockstep with the bundled End data pack.
        // Larger envelopes are deliberate creation-time choices, not the
        // default cost paid by every player and server.
        EndPreset d = EndPreset.defaults();
        assertEquals(512, d.worldHeight());
        assertEquals(-256, d.minY());
        assertEquals(0, d.seaLevelY());
        assertEquals(0, d.islandBaselineY());
        assertEquals(SeaMode.NONE, d.seaMode());
        assertEquals(TopologyMode.OUTER_CONTINENTS, d.topologyMode());
        assertEquals(EndPreset.CURRENT_FORMAT_VERSION, d.formatVersion());
        assertFalse(d.floatingIslandsEnabled(), "defaults must keep floating islands off");
        assertEquals(ContinentConfig.rtfMultiDefaults(), d.continentConfig());
        assertEquals(TerrainConfig.DEFAULT, d.terrainConfig());
        assertEquals(ClimateConfig.DEFAULT, d.climateConfig());
        assertEquals(BiomeLayoutConfig.DEFAULT, d.biomeLayoutConfig());
        assertEquals(SubsurfaceConfig.DEFAULT, d.subsurfaceConfig());
        assertEquals(ErosionConfig.DEFAULT, d.erosionConfig());
    }

    // ----- JSON shape sanity ---------------------------------------------

    @Test
    void encodedPresetUsesSnakeCaseKeys() {
        // Every field differs from defaults(), so none are elided by the
        // optionalFieldOf compact encoding — this verifies the documented
        // snake_case keys a data-pack / GUI will read. (Encoding defaults()
        // itself yields {} because every field equals its default — see
        // defaultsEncodeCompactly.)
        EndPreset custom = new EndPreset(384, -64, 63, 100,
                SeaMode.WITH_FLOOR, TopologyMode.CONTINENTAL_SHATTERED, true,
                customContinentConfig(),
                new TerrainConfig(0.75F, 2.5F),
                new ClimateConfig(6000.0F, 900, 1200, 1500, 0.4F),
                customBiomeLayoutConfig(),
                customSubsurfaceConfig(),
                new ErosionConfig(200, 40, 1.5F, 1.2F, 0.7F, 0.3F));
        JsonObject json = encode(custom).getAsJsonObject();
        assertTrue(json.has("world_height"), "must serialise world_height");
        assertTrue(json.has("min_y"), "must serialise min_y");
        assertTrue(json.has("sea_level_y"), "must serialise sea_level_y");
        assertTrue(json.has("island_baseline_y"), "must serialise island_baseline_y");
        assertTrue(json.has("sea_mode"), "must serialise sea_mode");
        assertTrue(json.has("topology_mode"), "must serialise topology_mode");
        assertTrue(json.has("floating_islands"), "must serialise floating_islands");
        assertTrue(json.has("continent"), "must serialise continent");
        assertTrue(json.has("terrain"), "must serialise terrain");
        assertTrue(json.has("climate"), "must serialise climate");
        assertTrue(json.has("biome_layout"), "must serialise biome_layout");
        assertTrue(json.has("subsurface"), "must serialise subsurface");
        assertTrue(json.has("erosion"), "must serialise erosion");
        assertTrue(json.has("format_version"), "must serialise format_version");
        // Enum values serialise by name.
        assertEquals("WITH_FLOOR", json.get("sea_mode").getAsString());
        assertEquals("CONTINENTAL_SHATTERED", json.get("topology_mode").getAsString());
    }

    @Test
    void currentDefaultsEncodeMigrationMetadata() {
        // topology_mode uses the historical ISLANDS codec default, so a new
        // default preset must explicitly write both migration fields.
        JsonObject json = encode(EndPreset.defaults()).getAsJsonObject();
        assertEquals(3, json.size(),
                "current defaults must write format, topology and continent migration fields: " + json);
        assertEquals(EndPreset.CURRENT_FORMAT_VERSION, json.get("format_version").getAsInt());
        assertEquals("OUTER_CONTINENTS", json.get("topology_mode").getAsString());
        assertEquals("FLOATING_SHELF", json.getAsJsonObject("continent")
                .get("volume_mode").getAsString());
        assertTrue(json.getAsJsonObject("continent").getAsJsonObject("bands")
                .get("enabled").getAsBoolean());
    }

    @Test
    void unsupportedFormatVersionFailsDecode() {
        JsonObject json = new JsonObject();
        json.addProperty("format_version", EndPreset.CURRENT_FORMAT_VERSION + 1);
        DataResult<EndPreset> result = EndPreset.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("format_version"));
    }

    @Test
    void presetFileRoundTripsThroughStringForm() {
        // Simulate a data-pack preset file: hand-written JSON string -> parse
        // -> decode -> re-encode -> compare. This exercises the full path a
        // real preset file takes and guards against Gson/DFU interop surprises.
        String presetJson = """
                {
                  "world_height": 384,
                  "min_y": -64,
                  "sea_level_y": 63,
                  "sea_mode": "WITH_FLOOR",
                  "topology_mode": "CONTINENTAL_SHATTERED",
                  "floating_islands": true,
                  "continent": {
                    "islands_scale": 320,
                    "continent_scale": 960,
                    "continent_shape": "NATURAL",
                    "continent_jitter": 0.8,
                    "continent_skipping": 0.2,
                    "continent_size_variance": 0.45,
                    "continent_noise_octaves": 6,
                    "continent_noise_gain": 0.3,
                    "continent_noise_lacunarity": 3.5,
                    "feature_spread": 0.9,
                    "island_radius": 0.7,
                    "island_scatter": 0.35,
                    "rift_threshold": 0.55,
                    "rift_strength": 0.75,
                    "warp_scale": 420,
                    "warp_strength": 64.0
                  },
                  "terrain": {
                    "terrain_seed_offset": 42,
                    "terrain_region_size": 1600,
                    "global_vertical_scale": 0.75,
                    "global_horizontal_scale": 2.5,
                    "terrain_blend_range": 0.25,
                    "terrain_shape": "ROLLING_RIDGES",
                    "plains": {
                      "weight": 0.5,
                      "base_scale": 1.0,
                      "vertical_scale": 2.0,
                      "horizontal_scale": 3.0
                    },
                    "mountains": {
                      "weight": 2.0,
                      "base_scale": 1.25,
                      "vertical_scale": 3.0,
                      "horizontal_scale": 4.0
                    }
                  },
                  "climate": {
                    "climate_radius": 6000.0,
                    "temperature_scale": 900,
                    "moisture_scale": 1200,
                    "wind_scale": 1500,
                    "perturbation": 0.4
                  },
                  "erosion": {
                    "droplets_per_chunk": 256,
                    "droplet_lifetime": 48,
                    "droplet_volume": 1.25,
                    "droplet_velocity": 1.1,
                    "erosion_rate": 0.6,
                    "deposit_rate": 0.4
                  }
                }
                """;
        JsonElement parsed = JsonParser.parseString(presetJson);
        EndPreset decoded = decode(parsed);
        assertEquals(384, decoded.worldHeight());
        assertEquals(SeaMode.WITH_FLOOR, decoded.seaMode());
        assertEquals(960, decoded.continentConfig().continentScale());
        assertEquals(DistanceFunction.NATURAL, decoded.continentConfig().continentShape());
        assertEquals(new TerrainConfig(42, 1600, 0.75F, 2.5F, 0.25F, TerrainShape.ROLLING_RIDGES,
                        new TerrainLayerConfig(0.5F, 1.0F, 2.0F, 3.0F),
                        TerrainLayerConfig.DISABLED,
                        TerrainLayerConfig.DISABLED,
                        new TerrainLayerConfig(2.0F, 1.25F, 3.0F, 4.0F),
                        TerrainLayerConfig.DISABLED),
                decoded.terrainConfig());
        assertEquals(new ClimateConfig(6000.0F, 900, 1200, 1500, 0.4F),
                decoded.climateConfig());
        assertEquals(256, decoded.erosionConfig().dropletsPerChunk);
        // And the decoded form re-encodes back to a parseable, equal preset.
        EndPreset reDecoded = decode(encode(decoded));
        assertEquals(decoded, reDecoded, "decoded preset must be stable under re-encode");
    }

    // ------------------------------------------------------------------
    //  Codec-level validation (EndPresetValidator via flatXmap)
    //
    //  These pin the contract that a JSON file structurally valid under
    //  the RecordCodecBuilder but violating a constraint enforced by
    //  EndPresetValidator surfaces as a DataResult.error at decode time,
    //  with a field-specific message — NOT a silent value that crashes
    //  worldgen later at DimensionType construction.
    // ------------------------------------------------------------------

    /**
     * Parses a JSON string through {@link EndPreset#CODEC} and returns
     * the {@link DataResult} (without unwrapping) so codec-level
     * validation tests can assert on the error message.
     */
    @Test
    void decodeRejectsRegionPlannedTerrainBeforeFormatVersionFour() {
        DataResult<EndPreset> result = parseResult(
                "{\"terrain\":{\"terrain_layout_mode\":\"REGION_PLANNED\","
                        + "\"plains\":{\"weight\":1.0,\"base_scale\":1.0,"
                        + "\"vertical_scale\":1.0,\"horizontal_scale\":1.0}}}");

        assertTrue(result.error().isPresent());
        String message = result.error().orElseThrow().message();
        assertTrue(message.contains("terrain.terrain_layout_mode"));
        assertTrue(message.contains("format_version 4"));
    }

    private static DataResult<EndPreset> parseResult(String json) {
        return EndPreset.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static ContinentConfig customContinentConfig() {
        return new ContinentConfig(320, 960, DistanceFunction.NATURAL,
                0.8F, 0.2F, 0.45F, 6, 0.3F, 3.5F,
                0.9F, 0.7F, 0.35F, 0.55F, 0.75F, 420, 64.0F);
    }

    private static BiomeLayoutConfig customBiomeLayoutConfig() {
        return new BiomeLayoutConfig(32, 6.0F, 35.0F, -5.0F,
                180, 3, 2.5F, 0.65F, 22.0F,
                360, 2, 0.35F);
    }

    private static SubsurfaceConfig customSubsurfaceConfig() {
        return new SubsurfaceConfig(new AbyssPitConfig(
                true, 1700, 640, 0.65F, 0.2F, 512, 0.1F));
    }

    @Test
    void decodeRejectsWorldHeightNotMultipleOf16() {
        // 100 is in [16, 4064] but not a multiple of 16 — vanilla
        // DimensionType would throw IllegalArgumentException at
        // construction time; the codec's flatXmap must surface it
        // as a DataResult.error with a field-specific message instead.
        DataResult<EndPreset> result = parseResult("{\"world_height\": 100}");
        assertTrue(result.error().isPresent(),
                "world_height not a multiple of 16 must fail decode via flatXmap");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("world_height"),
                "error message must name the offending field");
        assertTrue(msg.contains("multiple of 16"),
                "error message must state the alignment constraint");
        assertTrue(msg.contains("100"),
                "error message must include the offending value");
    }

    @Test
    void decodeRejectsWorldHeightBelowMinimum() {
        // 15 is below the vanilla minimum of 16.
        DataResult<EndPreset> result = parseResult("{\"world_height\": 15}");
        assertTrue(result.error().isPresent(),
                "world_height below 16 must fail decode via flatXmap");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("world_height"));
        assertTrue(msg.contains("[16, 4064]"),
                "error message must state the constraint range");
    }

    @Test
    void decodeRejectsMinYBelowMinimum() {
        // -2048 is below the vanilla -2032 lower bound.
        DataResult<EndPreset> result = parseResult("{\"min_y\": -2048}");
        assertTrue(result.error().isPresent(),
                "min_y below -2032 must fail decode via flatXmap");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("min_y"));
        assertTrue(msg.contains(">= -2032"));
    }

    @Test
    void decodeRejectsMinYNotMultipleOf16() {
        // -2031 is in range but not a multiple of 16.
        DataResult<EndPreset> result = parseResult("{\"min_y\": -2031}");
        assertTrue(result.error().isPresent(),
                "min_y not a multiple of 16 must fail decode via flatXmap");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("min_y"));
        assertTrue(msg.contains("multiple of 16"));
    }

    @Test
    void decodeRejectsWorldTopAboveMaximum() {
        // minY=0 + worldHeight=4064 = 4064 > 2032 — violates the
        // cross-field constraint that the world's top must not exceed
        // y=2032. Both minY=0 and worldHeight=4064 are individually
        // valid, so only the cross-field check catches this.
        DataResult<EndPreset> result = parseResult(
                "{\"min_y\": 0, \"world_height\": 4064}");
        assertTrue(result.error().isPresent(),
                "min_y + world_height > 2032 must fail decode via flatXmap");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("min_y + world_height"));
        assertTrue(msg.contains("<= 2032"));
        assertTrue(msg.contains("0 + 4064"),
                "error message must show the breakdown");
    }

    @Test
    void decodeRejectsSeaLevelYOutOfBounds() {
        // 99999 is well outside the world's vertical bounds — without
        // validation, this would decode cleanly and break worldgen later
        // when sea-level logic tries to read the bogus Y.
        DataResult<EndPreset> result = parseResult(
                "{\"sea_level_y\": 99999}");
        assertTrue(result.error().isPresent(),
                "sea_level_y outside world bounds must fail decode via flatXmap");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("sea_level_y"));
        assertTrue(msg.contains("99999"));
    }

    @Test
    void decodeRejectsIslandBaselineYOutOfBounds() {
        // -99999 is well outside the world's vertical bounds.
        DataResult<EndPreset> result = parseResult(
                "{\"island_baseline_y\": -99999}");
        assertTrue(result.error().isPresent(),
                "island_baseline_y outside world bounds must fail decode via flatXmap");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("island_baseline_y"));
    }

    @Test
    void decodeRejectsInvalidErosionRateViaDelegation() {
        // erosion_rate = 1.5 is outside [0, 1] — the embedded
        // ErosionConfig codec's flatXmap catches this and surfaces it
        // through EndPreset's codec as an error.
        DataResult<EndPreset> result = parseResult(
                "{\"erosion\": {\"erosion_rate\": 1.5}}");
        assertTrue(result.error().isPresent(),
                "erosion_rate outside [0, 1] must fail decode via flatXmap");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("erosion_rate"));
    }

    @Test
    void decodeRejectsInvalidContinentConfigViaDelegation() {
        DataResult<EndPreset> result = parseResult(
                "{\"continent\": {\"warp_strength\": -1}}");
        assertTrue(result.error().isPresent(),
                "invalid continent config must fail decode via flatXmap");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("continent"));
        assertTrue(msg.contains("warp_strength"));
    }

    @Test
    void decodeRejectsInvalidTerrainConfigViaDelegation() {
        DataResult<EndPreset> result = parseResult(
                "{\"terrain\": {\"global_horizontal_scale\": 6.0}}");
        assertTrue(result.error().isPresent(),
                "invalid terrain config must fail decode via flatXmap");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("terrain"));
        assertTrue(msg.contains("global_horizontal_scale"));
    }

    @Test
    void decodeRejectsInvalidSubsurfaceConfigViaDelegation() {
        DataResult<EndPreset> result = parseResult(
                "{\"subsurface\": {\"abyss\": {\"depth\": 0}}}");
        assertTrue(result.error().isPresent(),
                "invalid subsurface config must fail decode via flatXmap");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("subsurface"));
        assertTrue(msg.contains("depth"));
    }

    @Test
    void decodeRejectsInvalidDropletsPerChunkViaDelegation() {
        // droplets_per_chunk = -1 — ErosionConfigValidator catches this
        // and surfaces through the EndPreset codec.
        DataResult<EndPreset> result = parseResult(
                "{\"erosion\": {\"droplets_per_chunk\": -1}}");
        assertTrue(result.error().isPresent(),
                "droplets_per_chunk < 0 must fail decode via flatXmap");
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("droplets_per_chunk"));
    }

    @Test
    void decodeRejectsOnlyFirstViolationWhenMultiplePresent() {
        // Multiple violations: bad world_height (15) and bad min_y (-2048).
        // Fail-fast: only the first violation (world_height) must be
        // reported, not a list of all errors.
        DataResult<EndPreset> result = parseResult(
                "{\"world_height\": 15, \"min_y\": -2048}");
        assertTrue(result.error().isPresent());
        String msg = result.error().orElseThrow().message();
        assertTrue(msg.contains("world_height"),
                "the first violation (world_height) must be reported");
        assertFalse(msg.contains("min_y"),
                "later violations must not appear in the same error message");
    }
}
