package endterraforged.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Defensive tests for {@code endterraforged-common.mixins.json} structure.
 *
 * <p><b>Why these tests exist.</b> v0.1.0-preview shipped with a critical
 * startup crash (game wouldn't launch at all) because the mixin config's
 * {@code mixins} array used full-qualified class names like
 * {@code "endterraforged.mixin.MixinRandomState"} while the {@code package}
 * field was also {@code "endterraforged.mixin"}. The Mixin processor
 * prepends {@code package} to each entry, producing
 * {@code "endterraforged.mixin.endterraforged.mixin.MixinRandomState"} —
 * a class that doesn't exist. The game crashed with
 * {@code InvalidMixinException} before any of our code ran.</p>
 *
 * <p><b>Why unit tests didn't catch this originally.</b> Unit tests run on
 * plain JUnit without the Mixin processor. The mixin config is only
 * validated at runtime by the Mixin subsystem. These tests validate the
 * config's <em>structure</em> directly so a similar regression is caught
 * at build time, not at game launch.</p>
 *
 * <p><b>What these tests check.</b></p>
 * <ul>
 *   <li>The {@code package} field exists and is non-empty.</li>
 *   <li>Every entry in {@code mixins} and {@code client} arrays is a
 *       simple class name (no dots) — the Mixin convention.</li>
 *   <li>Prepending {@code package + "."} to each entry produces a fully
 *       qualified class name that actually exists on the classpath.</li>
 * </ul>
 *
 * <p><b>Why only the common config is tested here.</b> The fabric and
 * neoforge loader-specific mixin configs ({@code endterraforged-fabric.
 * mixins.json}, {@code endterraforged-neoforge.mixins.json}) live in their
 * respective modules' {@code src/main/resources}, not the common module.
 * Their test sourcesets are not set up under Architectury Loom, and the
 * configs are currently empty arrays (no entries to validate). If entries
 * are added to those configs in the future, parallel tests should be added
 * to the respective module's test sourceset.</p>
 */
class MixinConfigValidationTest {

    private static final Gson GSON = new Gson();

    /**
     * The common mixin config — the one that broke in v0.1.0-preview.
     * Every entry in {@code mixins} and {@code client} arrays must be a
     * simple class name (no dots). If an entry already has the package
     * prefix, the Mixin processor will prepend {@code package} again,
     * producing a double-prefixed name that doesn't exist.
     */
    @Test
    void commonMixinConfigUsesSimpleClassNamesNotFullyQualified() throws Exception {
        JsonObject config = loadMixinConfig("/endterraforged-common.mixins.json");
        String pkg = assertPackageExists(config);

        for (String arrayName : List.of("mixins", "client")) {
            if (!config.has(arrayName)) {
                continue;
            }
            JsonArray array = config.getAsJsonArray(arrayName);
            for (JsonElement entry : array) {
                String className = entry.getAsString();
                assertFalse(className.contains("."),
                        "endterraforged-common.mixins.json [" + arrayName
                                + "] entry '" + className + "' contains a dot — "
                                + "the Mixin processor will prepend `package` ('"
                                + pkg + "'), producing a double-prefixed name. "
                                + "Use the simple class name instead.");
            }
        }
    }

    /**
     * Every mixin entry in the common config must resolve to a real class
     * on the classpath when {@code package + "." + entry} is formed.
     * This catches: (1) wrong package field, (2) typo in class name,
     * (3) class deleted but config entry not removed.
     */
    @Test
    void commonMixinConfigEntriesResolveToRealClasses() throws Exception {
        JsonObject config = loadMixinConfig("/endterraforged-common.mixins.json");
        String pkg = config.get("package").getAsString();

        for (String arrayName : List.of("mixins", "client")) {
            if (!config.has(arrayName)) {
                continue;
            }
            JsonArray array = config.getAsJsonArray(arrayName);
            for (JsonElement entry : array) {
                String simpleName = entry.getAsString();
                String fqn = pkg + "." + simpleName;
                assertClassExists(fqn,
                        "endterraforged-common.mixins.json [" + arrayName
                                + "] entry '" + simpleName + "' resolves to "
                                + fqn + " which is not on the classpath. "
                                + "Either the class was deleted, the package "
                                + "field is wrong, or the entry has a typo.");
            }
        }
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private static JsonObject loadMixinConfig(String resourcePath) throws Exception {
        try (InputStream in = MixinConfigValidationTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(in, "Mixin config not found on classpath: " + resourcePath
                    + " — the test module must include main resources on its classpath");
            return GSON.fromJson(new java.io.InputStreamReader(in), JsonObject.class);
        }
    }

    private static String assertPackageExists(JsonObject config) {
        assertTrue(config.has("package"),
                "endterraforged-common.mixins.json must have a 'package' field");
        String pkg = config.get("package").getAsString();
        assertFalse(pkg.isEmpty(),
                "endterraforged-common.mixins.json 'package' field must not be empty");
        return pkg;
    }

    private static void assertClassExists(String fqn, String message) {
        try {
            Class.forName(fqn, false, MixinConfigValidationTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(message, e);
        }
    }
}
