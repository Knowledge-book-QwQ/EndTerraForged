package endterraforged.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Defensive tests for the mod's datapack JSON resources — the files that
 * replace vanilla End worldgen data.
 *
 * <p><b>Why these tests exist.</b> v0.1.3-preview shipped with a critical
 * world-load crash ({@code IllegalStateException: Failed to load registries}
 * at {@code RegistryDataLoader.load}) caused by two JSON format bugs:</p>
 * <ol>
 *   <li><b>{@code monster_spawn_light_level} nested {@code value} wrapper.</b>
 *       The {@code dimension_type/the_end.json} used the pre-1.20.5 nested
 *       format {@code {"type":"minecraft:uniform","value":{"min_inclusive":0,
 *       "max_inclusive":7}}}. Vanilla 1.21.1's {@code IntProvider} codec
 *       emits (and the loader now expects) the flat format
 *       {@code {"type":"minecraft:uniform","max_inclusive":7,"min_inclusive":0}}.
 *       The nested form caused a DFU decode error when loading the
 *       {@code minecraft:the_end} dimension_type.</li>
 *   <li><b>{@code noise_settings} file at wrong path.</b> The mod placed
 *       its custom End noise settings at
 *       {@code data/minecraft/noise_settings/the_end.json} (missing the
 *       {@code worldgen/} path segment). Vanilla 1.21.1 expects noise
 *       settings at {@code data/<ns>/worldgen/noise_settings/<name>.json}.
 *       The misplaced file was silently ignored by the registry loader,
 *       so {@code minecraft:the_end} noise settings did not exist; then
 *       the mod's {@code dimension/the_end.json} reference
 *       {@code "settings": "minecraft:the_end"} failed to resolve, which
 *       propagated as a registry load failure.</li>
 * </ol>
 *
 * <p>These bugs slipped through because:</p>
 * <ul>
 *   <li>The vanilla DFU codec that parses {@code monster_spawn_light_level}
 *       is not exercised by the mod's pure-Java unit tests (DFU runs at
 *       game-load time, not at JUnit time).</li>
 *   <li>The registry-loader path conventions ({@code worldgen/} prefix for
 *       noise_settings, no prefix for dimension/dimension_type) are not
 *       documented in the mod's source and are easy to get wrong.</li>
 * </ul>
 *
 * <p><b>What these tests check.</b></p>
 * <ul>
 *   <li>{@code monster_spawn_light_level} is flat (no nested {@code value}).</li>
 *   <li>{@code noise_settings} is at the {@code worldgen/noise_settings/}
 *       path (not the legacy {@code noise_settings/} path).</li>
 *   <li>{@code noise_router} contains all 15 vanilla 1.21.1 fields.</li>
 *   <li>Every {@code endterraforged:*} reference in the JSON resolves to
 *       a name that the mod actually registers (catches typos and
 *       registration/reference drift).</li>
 * </ul>
 *
 * <p><b>Why these are structural tests, not DFU round-trips.</b> A full DFU
 * round-trip would require booting the vanilla codec registry, which the
 * mod's pure-Java unit tests do not do. The structural checks here catch
 * the classes of bug that actually shipped in v0.1.3-preview without
 * requiring the full vanilla runtime.</p>
 */
class DatapackResourceValidationTest {

    private static final Gson GSON = new Gson();

    /**
     * The End dimension_type's {@code monster_spawn_light_level} field
     * must use vanilla 1.21.1's flat IntProvider format — no nested
     * {@code value} object.
     *
     * <p>The pre-1.20.5 nested format
     * ({@code "value": {"min_inclusive": 0, "max_inclusive": 7}}) was
     * accepted by older versions but causes a DFU decode error in 1.21.1
     * because the {@code IntProvider} codec no longer wraps the
     * {@code min_inclusive}/{@code max_inclusive} pair in a {@code value}
     * sub-object.</p>
     */
    @Test
    void monsterSpawnLightLevelUsesFlatFormatNotNestedValue() throws Exception {
        JsonObject config = loadJsonResource("/data/minecraft/dimension_type/the_end.json");
        assertTrue(config.has("monster_spawn_light_level"),
                "dimension_type/the_end.json must declare monster_spawn_light_level");
        JsonObject msl = config.getAsJsonObject("monster_spawn_light_level");
        // Flat format: min_inclusive and max_inclusive sit directly under msl
        assertTrue(msl.has("min_inclusive"),
                "monster_spawn_light_level must have min_inclusive at the top "
                        + "level (flat 1.21.1 format). The nested 'value' wrapper "
                        + "is the pre-1.20.5 format and causes a DFU decode error.");
        assertTrue(msl.has("max_inclusive"),
                "monster_spawn_light_level must have max_inclusive at the top "
                        + "level (flat 1.21.1 format). The nested 'value' wrapper "
                        + "is the pre-1.20.5 format and causes a DFU decode error.");
        assertFalse(msl.has("value"),
                "monster_spawn_light_level must NOT have a 'value' wrapper. "
                        + "The nested 'value' form is the pre-1.20.5 format; "
                        + "vanilla 1.21.1 uses the flat form where min_inclusive "
                        + "and max_inclusive are siblings of 'type'.");
    }

    /**
     * The End noise_settings must live at
     * {@code data/minecraft/worldgen/noise_settings/the_end.json} — with
     * the {@code worldgen/} path segment.
     *
     * <p>Vanilla 1.21.1's {@code RegistryDataLoader} resolves noise_settings
     * from {@code data/<ns>/worldgen/noise_settings/<name>.json}. The
     * {@code worldgen/} prefix is still required in 1.21.1 (it was not
     * removed until a later 1.21.x release). Placing the file at
     * {@code data/minecraft/noise_settings/} (without the prefix) causes
     * the loader to silently skip it, and any dimension JSON that
     * references the mod's custom noise settings will fail to resolve.</p>
     */
    @Test
    void noiseSettingsFileIsAtWorldgenPath() throws Exception {
        // The correct path: data/minecraft/worldgen/noise_settings/the_end.json
        try (InputStream in = DatapackResourceValidationTest.class
                .getResourceAsStream("/data/minecraft/worldgen/noise_settings/the_end.json")) {
            assertNotNull(in,
                    "noise_settings must be at "
                            + "data/minecraft/worldgen/noise_settings/the_end.json "
                            + "(with the 'worldgen/' path segment). The misplaced "
                            + "path data/minecraft/noise_settings/ was silently "
                            + "ignored by the 1.21.1 registry loader, which caused "
                            + "the dimension's settings reference to fail.");
        }
        // The legacy wrong path: data/minecraft/noise_settings/the_end.json
        try (InputStream in = DatapackResourceValidationTest.class
                .getResourceAsStream("/data/minecraft/noise_settings/the_end.json")) {
            assertFalse(in != null,
                    "Found a stale noise_settings file at the legacy path "
                            + "data/minecraft/noise_settings/the_end.json. This "
                            + "path is silently ignored by the 1.21.1 registry "
                            + "loader. The file should be moved to "
                            + "data/minecraft/worldgen/noise_settings/the_end.json.");
        }
    }

    /**
     * The noise_router in our custom End noise_settings must declare all 15
     * fields that vanilla 1.21.1's {@code NoiseRouter.CODEC} defines.
     *
     * <p>Vanilla's codec uses {@code optionalFieldOf} for the sub-routers,
     * so missing fields decode to the zero density function. The mod's
     * earlier 4-field version technically decoded — but matching vanilla's
     * full field set defensively avoids any future codec-strictness change
     * and makes the JSON self-documenting (a reader can see the router is
     * deliberately zeroed except for {@code final_density}).</p>
     */
    @Test
    void noiseRouterDeclaresAllVanilla21Fields() throws Exception {
        JsonObject settings = loadJsonResource(
                "/data/minecraft/worldgen/noise_settings/the_end.json");
        assertTrue(settings.has("noise_router"),
                "noise_settings/the_end.json must declare a noise_router");
        JsonObject router = settings.getAsJsonObject("noise_router");

        Set<String> vanillaFields = Set.of(
                "barrier", "continents", "depth", "erosion", "final_density",
                "fluid_level_floodedness", "fluid_level_spread",
                "initial_density_without_jaggedness", "lava", "ridges",
                "temperature", "vegetation", "vein_toggle", "vein_ridged",
                "vein_gap"
        );
        Set<String> missing = new HashSet<>(vanillaFields);
        missing.removeAll(router.keySet());
        assertTrue(missing.isEmpty(),
                "noise_router is missing vanilla 1.21.1 fields: " + missing
                        + ". Add them explicitly (use 0.0 for the unused "
                        + "channels — matches vanilla's End noise_settings).");
    }

    /**
     * Every {@code endterraforged:*} reference in the mod's datapack JSON
     * must resolve to a name that the mod actually registers. This catches
     * typos in the JSON and drift between the JSON references and the
     * Java-side {@code NAME} constants.
     *
     * <p>The mod registers six names across four vanilla registries:</p>
     * <ul>
     *   <li>{@code endterraforged:end_density} — DensityFunction type</li>
     *   <li>{@code endterraforged:floating_islands} — DensityFunction type</li>
     *   <li>{@code endterraforged:end_biome_source} — BiomeSource type</li>
     *   <li>{@code endterraforged:climate_temperature} — MaterialCondition type</li>
     *   <li>{@code endterraforged:climate_moisture} — MaterialCondition type</li>
     *   <li>{@code endterraforged:climate_filter} — PlacementModifierType</li>
     * </ul>
     */
    @Test
    void allEndTerraForgedReferencesResolveToRegisteredNames() throws Exception {
        Set<String> registered = Set.of(
                "endterraforged:end_density",
                "endterraforged:floating_islands",
                "endterraforged:end_biome_source",
                "endterraforged:climate_temperature",
                "endterraforged:climate_moisture",
                "endterraforged:climate_filter"
        );
        Set<String> referenced = new HashSet<>();
        // The three End datapack JSONs that reference mod-registered names.
        for (String path : new String[] {
                "/data/minecraft/dimension/the_end.json",
                "/data/minecraft/dimension_type/the_end.json",
                "/data/minecraft/worldgen/noise_settings/the_end.json"
        }) {
            JsonObject root = loadJsonResource(path);
            collectEndTerraForgedRefs(root, referenced);
        }
        // Every referenced name must be in the registered set.
        Set<String> unregistered = new HashSet<>(referenced);
        unregistered.removeAll(registered);
        assertTrue(unregistered.isEmpty(),
                "Datapack JSON references unregistered endterraforged:* "
                        + "names: " + unregistered + ". Either fix the JSON "
                        + "typo or register the missing name in Java.");
    }

    /**
     * The End dimension JSON must reference our custom noise_settings by
     * the name {@code minecraft:the_end} (the same name the
     * {@code worldgen/noise_settings/the_end.json} file registers). A
     * mismatch here causes {@code RegistryDataLoader} to fail with
     * "Failed to load registries due to above errors" because the
     * dimension's {@code generator.settings} cannot be resolved.
     */
    @Test
    void dimensionReferencesRegisteredNoiseSettings() throws Exception {
        JsonObject dim = loadJsonResource("/data/minecraft/dimension/the_end.json");
        assertTrue(dim.has("generator"),
                "dimension/the_end.json must have a 'generator' object");
        JsonObject gen = dim.getAsJsonObject("generator");
        assertTrue(gen.has("settings"),
                "dimension/the_end.json generator must declare 'settings'");
        String settings = gen.get("settings").getAsString();
        assertEquals("minecraft:the_end", settings,
                "dimension/the_end.json generator.settings must be "
                        + "'minecraft:the_end' to match the mod's custom "
                        + "noise_settings file at "
                        + "data/minecraft/worldgen/noise_settings/the_end.json. "
                        + "Any other value either fails to resolve or pulls "
                        + "in vanilla's minecraft:end settings (defeating the "
                        + "mod's noise_router override).");
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private static JsonObject loadJsonResource(String resourcePath) throws Exception {
        try (InputStream in = DatapackResourceValidationTest.class
                .getResourceAsStream(resourcePath)) {
            assertNotNull(in, "Resource not found on classpath: " + resourcePath
                    + " — the test module must include main resources on its "
                    + "classpath. This usually means the file is missing or "
                    + "has been moved to the wrong path.");
            return GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8),
                    JsonObject.class);
        }
    }

    /**
     * Recursively walks the JSON tree and collects every string value that
     * starts with {@code "endterraforged:"} into the {@code out} set. Used
     * to verify all mod-registry references resolve to registered names.
     */
    private static void collectEndTerraForgedRefs(JsonElement el, Set<String> out) {
        if (el == null || el.isJsonNull()) {
            return;
        }
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            String s = el.getAsString();
            if (s.startsWith("endterraforged:")) {
                out.add(s);
            }
        } else if (el.isJsonObject()) {
            for (JsonElement child : el.getAsJsonObject().asMap().values()) {
                collectEndTerraForgedRefs(child, out);
            }
        } else if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            for (JsonElement child : arr) {
                collectEndTerraForgedRefs(child, out);
            }
        }
    }
}
