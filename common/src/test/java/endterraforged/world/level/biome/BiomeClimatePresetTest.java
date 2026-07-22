package endterraforged.world.level.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Structural validation tests for the stage 5.2.3 biome_climate preset pack:
 * {@code cold_end.json}, {@code temperate_end.json}, {@code hot_end.json}.
 *
 * <p>These tests verify the <em>data shape</em> of each preset file — that it
 * parses as valid JSON, declares the expected dimension/biome-source type,
 * lists all five ring biomes, and ships a {@code biome_climate} block whose
 * variants satisfy the {@code BiomeVariant} codec's invariants (closed-range
 * bounds, {@code min <= max}, values in {@code [0,1]}, biome IDs use the
 * {@code minecraft:} namespace).</p>
 *
 * <p><b>Why structural, not codec round-trip.</b> The {@code EndBiomeSource}
 * codec round-trips biome IDs through {@link net.minecraft.core.Holder<Biome>}
 * references, which requires a populated biome registry
 * ({@code HolderLookup.Provider}). Under the sandbox's bare
 * {@code Bootstrap.bootStrap()} the biome registry is not wired for direct
 * codec access (see stage 5.2.1 spec — {@code BuiltInRegistries.BIOME} is not
 * referenceable under current Loom mappings). Gson-level validation covers the
 * same invariants the codec would enforce, without that bootstrap dependency.
 * The full codec round-trip is deferred to the integration-test layer
 * (stage 5.3 multi-loader runClient verification).</p>
 *
 * <p><b>Per-preset variant counts (spec decision).</b> Each preset ships
 * multiple variants per ring to demonstrate the four-corner bilinear blend
 * in {@link EndBiomeSelector}: a single variant per ring could never exercise
 * the slow path. The minimum-three-variants-total invariant catches a
 * regression that strips variants down to one per ring.</p>
 *
 * <p><b>Overworld + nether biome mix (spec decision).</b> Per stage 5.2.3
 * user decision, each preset mixes overworld biomes (e.g. ice_spikes,
 * forest, desert) with nether biomes (e.g. soul_sand_valley, warped_forest,
 * basalt_deltas) so the End reads as a cross-dimensional collage rather
 * than a single-biome-set reskin.</p>
 */
class BiomeClimatePresetTest {

    private static final String PRESET_DIR = "/data/endterraforged/presets/dimension/";
    private static final String[] PRESET_FILES = {"cold_end.json", "temperate_end.json", "hot_end.json"};
    private static final String[] RING_NAMES = {"end", "highlands", "midlands", "islands", "barrens"};

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ----- helpers --------------------------------------------------------

    private static JsonObject loadPreset(String fileName) {
        String path = PRESET_DIR + fileName;
        InputStream stream = BiomeClimatePresetTest.class.getResourceAsStream(path);
        assertNotNull(stream, "preset file missing on classpath: " + path);
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            assertTrue(parsed.isJsonObject(),
                    "preset " + fileName + " must be a JSON object, got " + parsed.getClass());
            return parsed.getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("failed reading preset " + fileName, e);
        }
    }

    /** Returns the biome_source object from a dimension preset JSON. */
    private static JsonObject biomeSourceOf(JsonObject preset) {
        assertTrue(preset.has("generator"), "preset missing 'generator' field");
        JsonObject generator = preset.getAsJsonObject("generator");
        assertTrue(generator.has("biome_source"), "generator missing 'biome_source' field");
        return generator.getAsJsonObject("biome_source");
    }

    /** Returns the biome_climate object, asserting it exists. */
    private static JsonObject biomeClimateOf(JsonObject biomeSource) {
        assertTrue(biomeSource.has("biome_climate"),
                "biome_source missing 'biome_climate' field — preset must ship variants");
        return biomeSource.getAsJsonObject("biome_climate");
    }

    /** Flattens every variant across every ring of one preset. */
    private static List<JsonObject> allVariants(JsonObject biomeClimate) {
        List<JsonObject> out = new ArrayList<>();
        for (String ring : RING_NAMES) {
            if (!biomeClimate.has(ring)) {
                continue;
            }
            JsonObject slot = biomeClimate.getAsJsonObject(ring);
            if (!slot.has("variants")) {
                continue;
            }
            JsonArray variants = slot.getAsJsonArray("variants");
            for (JsonElement e : variants) {
                assertTrue(e.isJsonObject(), "variant entry must be an object, got " + e.getClass());
                out.add(e.getAsJsonObject());
            }
        }
        return out;
    }

    // ----- 1. file presence ----------------------------------------------

    @Test
    void coldEndPresetFileExists() {
        assertNotNull(BiomeClimatePresetTest.class.getResourceAsStream(PRESET_DIR + "cold_end.json"),
                "cold_end.json must ship on the classpath");
    }

    @Test
    void temperateEndPresetFileExists() {
        assertNotNull(BiomeClimatePresetTest.class.getResourceAsStream(PRESET_DIR + "temperate_end.json"),
                "temperate_end.json must ship on the classpath");
    }

    @Test
    void hotEndPresetFileExists() {
        assertNotNull(BiomeClimatePresetTest.class.getResourceAsStream(PRESET_DIR + "hot_end.json"),
                "hot_end.json must ship on the classpath");
    }

    // ----- 2. top-level shape --------------------------------------------

    @Test
    void allPresetsDeclareTheEndDimensionType() {
        for (String file : PRESET_FILES) {
            JsonObject preset = loadPreset(file);
            assertTrue(preset.has("type"), file + " missing 'type' field");
            assertEquals("minecraft:the_end", preset.get("type").getAsString(),
                    file + " must declare type=minecraft:the_end");
        }
    }

    @Test
    void allPresetsDeclareEndBiomeSourceType() {
        for (String file : PRESET_FILES) {
            JsonObject biomeSource = biomeSourceOf(loadPreset(file));
            assertTrue(biomeSource.has("type"), file + " biome_source missing 'type' field");
            assertEquals("endterraforged:end_biome_source",
                    biomeSource.get("type").getAsString(),
                    file + " must declare biome_source.type=endterraforged:end_biome_source");
        }
    }

    @Test
    void allPresetsDeclareFiveRingBiomes() {
        for (String file : PRESET_FILES) {
            JsonObject biomeSource = biomeSourceOf(loadPreset(file));
            for (String ring : RING_NAMES) {
                assertTrue(biomeSource.has(ring),
                        file + " biome_source missing ring field '" + ring + "'");
                String value = biomeSource.get(ring).getAsString();
                assertTrue(value.startsWith("minecraft:"),
                        file + " ring '" + ring + "' biome id must use minecraft: namespace, got " + value);
            }
        }
    }

    @Test
    void allPresetsShipBiomeClimateBlock() {
        for (String file : PRESET_FILES) {
            JsonObject biomeClimate = biomeClimateOf(biomeSourceOf(loadPreset(file)));
            assertFalse(biomeClimate.isEmpty(),
                    file + " biome_climate block is empty — must ship at least one ring variant");
        }
    }

    // ----- 3. per-preset variant counts ----------------------------------

    @Test
    void coldEndHasVariantsAcrossMultipleRings() {
        JsonObject climate = biomeClimateOf(biomeSourceOf(loadPreset("cold_end.json")));
        // Spec design: cold_end ships variants on highlands, midlands, barrens
        // (at least 3 rings) to exercise the slow path in those rings.
        int ringsWithVariants = 0;
        for (String ring : RING_NAMES) {
            if (climate.has(ring)
                    && climate.getAsJsonObject(ring).has("variants")
                    && !climate.getAsJsonObject(ring).getAsJsonArray("variants").isEmpty()) {
                ringsWithVariants++;
            }
        }
        assertTrue(ringsWithVariants >= 3,
                "cold_end.json must have variants on at least 3 rings, got " + ringsWithVariants);
    }

    @Test
    void temperateEndHasVariantsAcrossMultipleRings() {
        JsonObject climate = biomeClimateOf(biomeSourceOf(loadPreset("temperate_end.json")));
        int ringsWithVariants = 0;
        for (String ring : RING_NAMES) {
            if (climate.has(ring)
                    && climate.getAsJsonObject(ring).has("variants")
                    && !climate.getAsJsonObject(ring).getAsJsonArray("variants").isEmpty()) {
                ringsWithVariants++;
            }
        }
        assertTrue(ringsWithVariants >= 3,
                "temperate_end.json must have variants on at least 3 rings, got " + ringsWithVariants);
    }

    @Test
    void hotEndHasVariantsAcrossAllFourOuterRings() {
        JsonObject climate = biomeClimateOf(biomeSourceOf(loadPreset("hot_end.json")));
        // hot_end ships variants on all four outer rings (highlands/midlands/islands/barrens).
        String[] outerRings = {"highlands", "midlands", "islands", "barrens"};
        for (String ring : outerRings) {
            assertTrue(climate.has(ring), "hot_end.json missing ring '" + ring + "'");
            JsonObject slot = climate.getAsJsonObject(ring);
            assertTrue(slot.has("variants"),
                    "hot_end.json ring '" + ring + "' missing 'variants' array");
            JsonArray variants = slot.getAsJsonArray("variants");
            assertFalse(variants.isEmpty(),
                    "hot_end.json ring '" + ring + "' has empty variants array");
        }
    }

    // ----- 4. variant field completeness --------------------------------

    @Test
    void allVariantsHaveBiomeField() {
        for (String file : PRESET_FILES) {
            JsonObject climate = biomeClimateOf(biomeSourceOf(loadPreset(file)));
            for (JsonObject variant : allVariants(climate)) {
                assertTrue(variant.has("biome"),
                        file + " variant missing required 'biome' field: " + variant);
                String biome = variant.get("biome").getAsString();
                assertTrue(biome.startsWith("minecraft:"),
                        file + " variant biome id must use minecraft: namespace, got " + biome);
            }
        }
    }

    @Test
    void allVariantsHaveFourClimateBounds() {
        String[] requiredBounds = {"temperature_min", "temperature_max", "moisture_min", "moisture_max"};
        for (String file : PRESET_FILES) {
            JsonObject climate = biomeClimateOf(biomeSourceOf(loadPreset(file)));
            for (JsonObject variant : allVariants(climate)) {
                for (String bound : requiredBounds) {
                    assertTrue(variant.has(bound),
                            file + " variant missing required bound '" + bound + "': " + variant);
                    // Bounds must be parseable as floats (JSON numbers).
                    assertTrue(variant.get(bound).isJsonPrimitive(),
                            file + " variant bound '" + bound + "' must be a number, got " + variant.get(bound));
                }
            }
        }
    }

    // ----- 5. range invariants -------------------------------------------

    @Test
    void allVariantTemperatureRangesAreValid() {
        for (String file : PRESET_FILES) {
            JsonObject climate = biomeClimateOf(biomeSourceOf(loadPreset(file)));
            for (JsonObject variant : allVariants(climate)) {
                float min = variant.get("temperature_min").getAsFloat();
                float max = variant.get("temperature_max").getAsFloat();
                assertTrue(min <= max,
                        file + " variant has temperature_min > temperature_max: " + variant);
            }
        }
    }

    @Test
    void allVariantMoistureRangesAreValid() {
        for (String file : PRESET_FILES) {
            JsonObject climate = biomeClimateOf(biomeSourceOf(loadPreset(file)));
            for (JsonObject variant : allVariants(climate)) {
                float min = variant.get("moisture_min").getAsFloat();
                float max = variant.get("moisture_max").getAsFloat();
                assertTrue(min <= max,
                        file + " variant has moisture_min > moisture_max: " + variant);
            }
        }
    }

    @Test
    void allVariantBoundsAreInUnitRange() {
        for (String file : PRESET_FILES) {
            JsonObject climate = biomeClimateOf(biomeSourceOf(loadPreset(file)));
            for (JsonObject variant : allVariants(climate)) {
                float tMin = variant.get("temperature_min").getAsFloat();
                float tMax = variant.get("temperature_max").getAsFloat();
                float mMin = variant.get("moisture_min").getAsFloat();
                float mMax = variant.get("moisture_max").getAsFloat();
                assertTrue(tMin >= 0.0F && tMin <= 1.0F,
                        file + " temperature_min out of [0,1]: " + tMin);
                assertTrue(tMax >= 0.0F && tMax <= 1.0F,
                        file + " temperature_max out of [0,1]: " + tMax);
                assertTrue(mMin >= 0.0F && mMin <= 1.0F,
                        file + " moisture_min out of [0,1]: " + mMin);
                assertTrue(mMax >= 0.0F && mMax <= 1.0F,
                        file + " moisture_max out of [0,1]: " + mMax);
            }
        }
    }

    @Test
    void allVariantRangesOverlapNamedClimateBands() {
        for (String file : PRESET_FILES) {
            JsonObject climate = biomeClimateOf(biomeSourceOf(loadPreset(file)));
            for (JsonObject variant : allVariants(climate)) {
                float tMin = variant.get("temperature_min").getAsFloat();
                float tMax = variant.get("temperature_max").getAsFloat();
                float mMin = variant.get("moisture_min").getAsFloat();
                float mMax = variant.get("moisture_max").getAsFloat();
                assertTrue(overlapsTemperatureBand(tMin, tMax),
                        file + " variant temperature range does not overlap a named band: " + variant);
                assertTrue(overlapsMoistureBand(mMin, mMax),
                        file + " variant moisture range does not overlap a named band: " + variant);
            }
        }
    }

    @Test
    void coldEndVariantsStayInColdTemperatureBands() {
        JsonObject climate = biomeClimateOf(biomeSourceOf(loadPreset("cold_end.json")));
        for (JsonObject variant : allVariants(climate)) {
            float max = variant.get("temperature_max").getAsFloat();
            assertTrue(max <= BiomeClimateBands.Temperature.COLD.max(),
                    "cold_end.json variant exceeds cold temperature bands: " + variant);
        }
    }

    @Test
    void temperateEndVariantsOverlapTemperateBand() {
        JsonObject climate = biomeClimateOf(biomeSourceOf(loadPreset("temperate_end.json")));
        for (JsonObject variant : allVariants(climate)) {
            float min = variant.get("temperature_min").getAsFloat();
            float max = variant.get("temperature_max").getAsFloat();
            assertTrue(overlaps(min, max,
                            BiomeClimateBands.Temperature.TEMPERATE.min(),
                            BiomeClimateBands.Temperature.TEMPERATE.max()),
                    "temperate_end.json variant does not overlap temperate band: " + variant);
        }
    }

    @Test
    void hotEndVariantsOverlapWarmOrHotBands() {
        JsonObject climate = biomeClimateOf(biomeSourceOf(loadPreset("hot_end.json")));
        for (JsonObject variant : allVariants(climate)) {
            float min = variant.get("temperature_min").getAsFloat();
            float max = variant.get("temperature_max").getAsFloat();
            boolean warm = overlaps(min, max,
                    BiomeClimateBands.Temperature.WARM.min(),
                    BiomeClimateBands.Temperature.WARM.max());
            boolean hot = overlaps(min, max,
                    BiomeClimateBands.Temperature.HOT.min(),
                    BiomeClimateBands.Temperature.HOT.max());
            assertTrue(warm || hot,
                    "hot_end.json variant does not overlap warm or hot bands: " + variant);
        }
    }

    // ----- 6. overworld + nether biome mix invariant ---------------------

    @Test
    void eachPresetMixesOverworldAndNetherBiomes() {
        // Spec decision: each preset mixes overworld biomes (e.g. ice_spikes,
        // forest, desert) with nether biomes (soul_sand_valley, warped_forest,
        // basalt_deltas, nether_wastes, crimson_forest) so the End reads as a
        // cross-dimensional collage rather than a single-biome-set reskin.
        // The set of nether biomes in 1.21.1: basalt_deltas, crimson_forest,
        // nether_wastes, soul_sand_valley, warped_forest.
        java.util.Set<String> netherBiomes = java.util.Set.of(
                "minecraft:basalt_deltas", "minecraft:crimson_forest",
                "minecraft:nether_wastes", "minecraft:soul_sand_valley",
                "minecraft:warped_forest");
        for (String file : PRESET_FILES) {
            JsonObject climate = biomeClimateOf(biomeSourceOf(loadPreset(file)));
            List<JsonObject> variants = allVariants(climate);
            assertFalse(variants.isEmpty(), file + " has no variants to test");
            boolean hasOverworld = false;
            boolean hasNether = false;
            for (JsonObject variant : variants) {
                String biome = variant.get("biome").getAsString();
                if (netherBiomes.contains(biome)) {
                    hasNether = true;
                } else {
                    hasOverworld = true;
                }
            }
            assertTrue(hasOverworld,
                    file + " must contain at least one overworld biome variant");
            assertTrue(hasNether,
                    file + " must contain at least one nether biome variant");
        }
    }

    private static boolean overlapsTemperatureBand(float min, float max) {
        for (BiomeClimateBands.Temperature band : BiomeClimateBands.Temperature.values()) {
            if (overlaps(min, max, band.min(), band.max())) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlapsMoistureBand(float min, float max) {
        for (BiomeClimateBands.Moisture band : BiomeClimateBands.Moisture.values()) {
            if (overlaps(min, max, band.min(), band.max())) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlaps(float minA, float maxA, float minB, float maxB) {
        return minA <= maxB && maxA >= minB;
    }
}
