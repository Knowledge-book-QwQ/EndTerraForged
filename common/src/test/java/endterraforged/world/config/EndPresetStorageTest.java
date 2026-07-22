package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import endterraforged.world.filter.ErosionConfig;

/**
 * Contract tests for {@link EndPresetStorage}: the per-world persistence
 * layer that reads / writes the user-edited {@link EndPreset} from / to
 * {@code <worldDir>/endterraforged_preset.json}.
 *
 * <p><b>Why these tests exist.</b> The storage layer is the foundation
 * of stage 6.1 (preset persistence to world save). Before any Mixin code
 * wires it into the world-load / world-save path, the IO contract must
 * be locked in by tests so that the future Mixin work can rely on it
 * without re-auditing every code path. The tests below cover the
 * documented surface:</p>
 *
 * <ul>
 *   <li><b>Load.</b> Missing file → empty; valid file → preset; corrupt
 *       JSON → empty; valid JSON but wrong shape → empty; partial JSON
 *       → preset with codec-defaults for missing fields; versionless empty
 *       JSON object → {@link EndPreset#legacyDefaults()}; IO error → empty.</li>
 *   <li><b>Save.</b> Writes file at the expected path; round-trips
 *       through {@link EndPresetStorage#load} losslessly; overwrites an
 *       existing file; produces pretty-printed JSON (git-friendly);
 *       leaves no temp file behind on success.</li>
 *   <li><b>Delete.</b> Returns {@code true} and removes the file when it
 *       exists; returns {@code false} when the file is missing.</li>
 *   <li><b>Null-args fail-fast.</b> {@code null} {@code worldDir} or
 *       {@code preset} throws {@link NullPointerException} (programmer
 *       error, not a runtime IO condition — see the class Javadoc).</li>
 *   <li><b>{@link EndPresetStorage#presetFile(Path)}.</b> Resolves to
 *       {@code <worldDir>/endterraforged_preset.json}.</li>
 * </ul>
 *
 * <p>Each test uses JUnit 5's {@link TempDir} parameter-injection so the
 * tests are isolated and order-independent — no shared state between
 * tests, no leak of a written file from one test into another.</p>
 */
class EndPresetStorageTest {

    /**
     * Per-test temporary directory that stands in for the world save
     * directory. JUnit injects a fresh one for each test method, so
     * the tests are isolated and order-independent.
     */
    @TempDir
    Path worldDir;

    /**
     * Sanity check before each test: the temp directory must start empty.
     * If a previous test leaked a file, this catches it before the test
     * runs and produces a much clearer failure than "load returned
     * unexpected preset".
     */
    @BeforeEach
    void worldDirStartsEmpty() throws IOException {
        try (var stream = Files.list(worldDir)) {
            assertEquals(0, stream.count(),
                    "@TempDir worldDir must start empty for test isolation");
        }
    }

    // ------------------------------------------------------------------
    //  load(): missing / unreadable file
    // ------------------------------------------------------------------

    @Test
    void loadReturnsEmptyWhenFileMissing() {
        // Fresh worldDir, no preset file written — load must report "no
        // preset on disk" so the caller falls back to defaults().
        Optional<EndPreset> loaded = EndPresetStorage.load(worldDir);
        assertTrue(loaded.isEmpty(),
                "load() on a world with no preset file must return empty");
    }

    @Test
    void loadReturnsEmptyWhenPathIsDirectoryNotFile() throws IOException {
        // If something accidentally created a directory at the preset
        // file's name, Files.isRegularFile returns false → load returns
        // empty rather than crashing with FileSystemException.
        Files.createDirectory(EndPresetStorage.presetFile(worldDir));
        Optional<EndPreset> loaded = EndPresetStorage.load(worldDir);
        assertTrue(loaded.isEmpty(),
                "load() when the preset path is a directory must return empty");
    }

    @Test
    void loadReturnsEmptyForCorruptJson() throws IOException {
        // Garbage that is not valid JSON must surface as Optional.empty()
        // — JsonParseException is caught and swallowed so world load
        // proceeds with defaults rather than crashing.
        writePresetFile("garbage{not json");
        Optional<EndPreset> loaded = EndPresetStorage.load(worldDir);
        assertTrue(loaded.isEmpty(),
                "load() on a corrupt-JSON preset file must return empty");
    }

    @Test
    void loadReturnsEmptyForNonObjectJson() throws IOException {
        // Valid JSON but the wrong shape (a string literal, not an
        // object) — DFU's RecordCodecBuilder requires a JsonObject and
        // must fail decode rather than silently coerce.
        writePresetFile("\"hello\"");
        Optional<EndPreset> loaded = EndPresetStorage.load(worldDir);
        assertTrue(loaded.isEmpty(),
                "load() on a non-object JSON value must return empty");
    }

    @Test
    void loadReturnsEmptyForArrayJson() throws IOException {
        // Same shape mismatch as the previous test, but for arrays —
        // guards against any codec that might happen to accept lists.
        writePresetFile("[]");
        Optional<EndPreset> loaded = EndPresetStorage.load(worldDir);
        assertTrue(loaded.isEmpty(),
                "load() on a JSON array must return empty");
    }

    @Test
    void loadReturnsEmptyForEmptyFile() throws IOException {
        // An empty (0-byte) file is a common corruption mode (crash
        // during a previous non-atomic write). Must not crash the load.
        writePresetFile("");
        Optional<EndPreset> loaded = EndPresetStorage.load(worldDir);
        assertTrue(loaded.isEmpty(),
                "load() on an empty file must return empty");
    }

    @Test
    void loadReturnsEmptyForWhitespaceOnlyFile() throws IOException {
        // Whitespace-only is technically valid JSON5 but not parseable
        // by Gson — must surface as empty rather than throwing.
        writePresetFile("   \n  \t ");
        Optional<EndPreset> loaded = EndPresetStorage.load(worldDir);
        assertTrue(loaded.isEmpty(),
                "load() on a whitespace-only file must return empty");
    }

    @Test
    void loadReturnsEmptyForTypeMismatch() throws IOException {
        // sea_mode expects a string enum name; an integer must fail
        // decode (the alternative — silently coercing 123 to a
        // SeaMode — would be a silent footgun).
        writePresetFile("{\"sea_mode\": 123}");
        Optional<EndPreset> loaded = EndPresetStorage.load(worldDir);
        assertTrue(loaded.isEmpty(),
                "load() on a JSON with a wrong-typed field must return empty");
    }

    @Test
    void loadReturnsEmptyForUnknownEnumName() throws IOException {
        // A typo'd SeaMode name (WIT_FLOOR instead of WITH_FLOOR) must
        // fail decode rather than silently defaulting — pinning the
        // EndPreset.CODEC's flatXmap error behaviour at the IO layer.
        writePresetFile("{\"sea_mode\": \"WIT_FLOOR\"}");
        Optional<EndPreset> loaded = EndPresetStorage.load(worldDir);
        assertTrue(loaded.isEmpty(),
                "load() on a JSON with an unknown enum name must return empty");
    }

    // ------------------------------------------------------------------
    //  load(): valid file
    // ------------------------------------------------------------------

    @Test
    void loadReturnsDefaultsForValidDefaultsPreset() throws IOException {
        // Save defaults() and load it back — must equal defaults().
        EndPresetStorage.save(worldDir, EndPreset.defaults());
        Optional<EndPreset> loaded = EndPresetStorage.load(worldDir);
        assertTrue(loaded.isPresent(),
                "load() on a freshly-saved defaults preset must succeed");
        assertEquals(EndPreset.defaults(), loaded.get(),
                "loaded defaults() must equal EndPreset.defaults()");
    }

    @Test
    void loadReturnsCustomPresetForValidCustomFile() throws IOException {
        // Every field differs from defaults() so a swapped-field codec
        // bug (e.g. seaLevelY <-> islandBaselineY) would be caught.
        EndPreset custom = fullyCustomPreset();
        EndPresetStorage.save(worldDir, custom);
        Optional<EndPreset> loaded = EndPresetStorage.load(worldDir);
        assertTrue(loaded.isPresent());
        assertEquals(custom, loaded.get(),
                "loaded custom preset must equal the saved custom preset");
    }

    @Test
    void loadReturnsLegacyDefaultsForEmptyJsonObject() throws IOException {
        // A pre-format-version compact file has no way to declare that it
        // wants current defaults. Preserve its historical topology instead.
        writePresetFile("{}");
        Optional<EndPreset> loaded = EndPresetStorage.load(worldDir);
        assertTrue(loaded.isPresent(),
                "an empty JSON object must decode (all fields fall back to defaults)");
        assertEquals(EndPreset.legacyDefaults(), loaded.get(),
                "{} must decode to EndPreset.legacyDefaults()");
    }

    @Test
    void loadReturnsPresetWithPartialFieldsUsingCodecDefaults() throws IOException {
        // A preset file with only one field set must load that field
        // and inherit defaults for the rest — this is the contract that
        // lets hand-edited preset files omit fields they don't care about.
        writePresetFile("{\"sea_mode\": \"WITH_FLOOR\"}");
        Optional<EndPreset> loaded = EndPresetStorage.load(worldDir);
        assertTrue(loaded.isPresent());
        EndPreset preset = loaded.get();
        assertEquals(SeaMode.WITH_FLOOR, preset.seaMode(),
                "the explicitly-set field must be honoured");
        assertEquals(EndPreset.defaults().worldHeight(), preset.worldHeight(),
                "omitted world_height must fall back to default");
        assertEquals(EndPreset.defaults().minY(), preset.minY(),
                "omitted min_y must fall back to default");
        assertEquals(EndPreset.defaults().erosionConfig(), preset.erosionConfig(),
                "omitted erosion must fall back to default ErosionConfig");
    }

    // ------------------------------------------------------------------
    //  save(): write, round-trip, overwrite, format
    // ------------------------------------------------------------------

    @Test
    void saveWritesFileAtExpectedPath() {
        // After save, the file must exist at worldDir/endterraforged_preset.json.
        EndPresetStorage.save(worldDir, EndPreset.defaults());
        Path expected = worldDir.resolve(EndPresetStorage.FILE_NAME);
        assertTrue(Files.isRegularFile(expected),
                "save() must create the preset file at the expected path");
        assertEquals(expected, EndPresetStorage.presetFile(worldDir),
                "presetFile() must resolve to the same path save() wrote to");
    }

    @Test
    void saveThenLoadRoundTripsLosslessly() {
        // The core round-trip contract: any preset saved and re-loaded
        // must equal the original. Uses the fully-custom preset so all
        // fields are exercised (default-valued fields would be elided
        // by optionalFieldOf's compact encoding and wouldn't pin the
        // round-trip for those fields).
        EndPreset custom = fullyCustomPreset();
        assertTrue(EndPresetStorage.save(worldDir, custom),
                "save() must report success on a valid round-trip");
        Optional<EndPreset> loaded = EndPresetStorage.load(worldDir);
        assertTrue(loaded.isPresent());
        assertEquals(custom, loaded.get(),
                "save() -> load() round-trip must preserve the preset");
    }

    @Test
    void saveRoundTripsDefaultsPresetLosslessly() {
        // Current defaults persist their format and topology explicitly, so
        // save(defaults()) must not collapse into a versionless legacy file.
        assertTrue(EndPresetStorage.save(worldDir, EndPreset.defaults()));
        Optional<EndPreset> loaded = EndPresetStorage.load(worldDir);
        assertTrue(loaded.isPresent());
        assertEquals(EndPreset.defaults(), loaded.get(),
                "save(defaults()) -> load() must yield defaults() back");
    }

    @Test
    void saveOverwritesExistingFile() {
        // A second save must replace the first file's contents — the
        // caller doesn't need to delete first.
        EndPreset first = fullyCustomPreset();
        EndPreset second = EndPreset.defaults();
        assertTrue(EndPresetStorage.save(worldDir, first));
        assertTrue(EndPresetStorage.save(worldDir, second));
        Optional<EndPreset> loaded = EndPresetStorage.load(worldDir);
        assertTrue(loaded.isPresent());
        assertEquals(second, loaded.get(),
                "the second save() must overwrite the first file");
        assertFalse(loaded.get().equals(first),
                "sanity: the loaded preset must be the second, not the first");
    }

    @Test
    void saveProducesPrettyPrintedJson() throws IOException {
        // Pretty-printed JSON (with newlines and indentation) is
        // git-friendly — a server admin diffing two preset files can
        // read the changes, and a hand-editor doesn't have to parse a
        // single-line blob.
        EndPresetStorage.save(worldDir, fullyCustomPreset());
        String content = Files.readString(EndPresetStorage.presetFile(worldDir));
        assertTrue(content.contains("\n"),
                "saved JSON must be pretty-printed (contain newlines)");
        assertTrue(content.contains("  "),
                "saved JSON must be indented (contain leading spaces)");
    }

    @Test
    void saveLeavesNoTempFileBehindOnSuccess() throws IOException {
        // The atomic temp-file-then-rename strategy must clean up the
        // temp file on success — leaving a stale .tmp file would
        // confuse users browsing the world directory.
        EndPresetStorage.save(worldDir, EndPreset.defaults());
        Path temp = worldDir.resolve(EndPresetStorage.FILE_NAME + EndPresetStorage.TEMP_SUFFIX);
        assertFalse(Files.exists(temp),
                "save() must not leave a temp file behind on success");
    }

    @Test
    void saveReturnsTrueOnSuccess() {
        // Sanity-check the boolean return value — a future caller
        // (e.g. a Mixin) will branch on this.
        boolean result = EndPresetStorage.save(worldDir, EndPreset.defaults());
        assertTrue(result, "save() must report true on a successful write");
    }

    // ------------------------------------------------------------------
    //  delete()
    // ------------------------------------------------------------------

    @Test
    void deleteReturnsTrueWhenFileExists() {
        // After save(), delete() must succeed and report true.
        EndPresetStorage.save(worldDir, EndPreset.defaults());
        boolean deleted = EndPresetStorage.delete(worldDir);
        assertTrue(deleted, "delete() must report true when a file was removed");
        assertFalse(Files.exists(EndPresetStorage.presetFile(worldDir)),
                "after delete(), the preset file must not exist");
    }

    @Test
    void deleteReturnsFalseWhenFileMissing() {
        // Deleting a non-existent file must report false (not throw) so
        // the caller can treat it as a no-op.
        boolean deleted = EndPresetStorage.delete(worldDir);
        assertFalse(deleted, "delete() must report false when the file does not exist");
    }

    @Test
    void deleteAllowsSubsequentLoadToReturnEmpty() {
        // After delete, load must report empty — i.e. delete is the
        // inverse of save for the load() contract.
        EndPresetStorage.save(worldDir, EndPreset.defaults());
        assertTrue(EndPresetStorage.load(worldDir).isPresent(),
                "sanity: load returns the saved preset before delete");
        EndPresetStorage.delete(worldDir);
        assertTrue(EndPresetStorage.load(worldDir).isEmpty(),
                "after delete(), load() must return empty");
    }

    // ------------------------------------------------------------------
    //  import/export and named preset library
    // ------------------------------------------------------------------

    @Test
    void exportToCreatesParentDirectoryAndImportFromRoundTrips() {
        Path exported = worldDir.resolve("exports").resolve("shared.json");
        EndPreset custom = fullyCustomPreset();
        assertTrue(EndPresetStorage.exportTo(exported, custom),
                "exportTo() must create parent directories and write the preset");
        Optional<EndPreset> loaded = EndPresetStorage.importFrom(exported);
        assertTrue(loaded.isPresent(),
                "importFrom() must decode a file written by exportTo()");
        assertEquals(custom, loaded.get(),
                "exportTo() -> importFrom() must preserve the preset");
    }

    @Test
    void importFromReturnsEmptyForMissingFile() {
        Optional<EndPreset> loaded = EndPresetStorage.importFrom(worldDir.resolve("missing.json"));
        assertTrue(loaded.isEmpty(),
                "importFrom() must treat a missing file as no importable preset");
    }

    @Test
    void loadNamedUsesErrorHandlerForCorruptLibraryFile() throws IOException {
        Path file = EndPresetStorage.namedPresetFile(worldDir, "broken");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"world_height\": 100}");
        ErrorSink sink = new ErrorSink();
        Optional<EndPreset> loaded = EndPresetStorage.loadNamed(worldDir, "broken", sink::accept);
        assertTrue(loaded.isEmpty(),
                "loadNamed() must return empty for an invalid named preset");
        assertEquals(1, sink.count(),
                "loadNamed() must surface decode errors through the handler");
        assertTrue(sink.first().contains("world_height"),
                "named preset decode error must preserve field-specific diagnostics");
    }

    @Test
    void saveNamedWritesSanitizedLibraryFileAndRoundTrips() {
        EndPreset custom = fullyCustomPreset();
        assertTrue(EndPresetStorage.saveNamed(worldDir, " My Preset.json ", custom));
        Path expected = EndPresetStorage.presetLibraryDir(worldDir).resolve("my_preset.json");
        assertTrue(Files.isRegularFile(expected),
                "saveNamed() must write the sanitized JSON file in the library dir");
        Optional<EndPreset> loaded = EndPresetStorage.loadNamed(worldDir, "my_preset");
        assertTrue(loaded.isPresent());
        assertEquals(custom, loaded.get(),
                "saveNamed() -> loadNamed() must preserve the preset");
        assertFalse(Files.exists(EndPresetStorage.presetFile(worldDir)),
                "saving a named library preset must not replace the active world preset");
    }

    @Test
    void deleteNamedRemovesOnlyTheNamedLibraryFile() {
        assertTrue(EndPresetStorage.saveNamed(worldDir, "first", EndPreset.defaults()));
        assertTrue(EndPresetStorage.saveNamed(worldDir, "second", fullyCustomPreset()));
        assertTrue(EndPresetStorage.deleteNamed(worldDir, "first"),
                "deleteNamed() must report true when it removed a library file");
        assertTrue(EndPresetStorage.loadNamed(worldDir, "first").isEmpty(),
                "deleted named preset must no longer load");
        assertTrue(EndPresetStorage.loadNamed(worldDir, "second").isPresent(),
                "deleteNamed() must not remove other named presets");
    }

    @Test
    void listNamedReturnsSortedJsonStemsOnly() throws IOException {
        Path directory = EndPresetStorage.presetLibraryDir(worldDir);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("zeta.json"), "{}");
        Files.writeString(directory.resolve("alpha.json"), "{}");
        Files.writeString(directory.resolve("notes.txt"), "{}");
        Files.createDirectory(directory.resolve("folder.json"));
        assertEquals(List.of("alpha", "zeta"), EndPresetStorage.listNamed(worldDir),
                "listNamed() must return sorted JSON file stems and ignore non-files");
    }

    @Test
    void listNamedReturnsEmptyWhenLibraryDirectoryIsMissing() {
        assertEquals(List.of(), EndPresetStorage.listNamed(worldDir),
                "listNamed() must be empty for worlds with no preset library yet");
    }

    @Test
    void sanitizePresetNameStripsExtensionAndUnsafeCharacters() {
        assertEquals("my_preset", EndPresetStorage.sanitizePresetName(" My:Preset.json "));
        assertEquals("a_b", EndPresetStorage.sanitizePresetName("a/../b"));
        assertEquals("preset", EndPresetStorage.sanitizePresetName("///"));
        assertEquals("con_preset", EndPresetStorage.sanitizePresetName("CON"));
        assertEquals("lpt1_preset", EndPresetStorage.sanitizePresetName("lpt1.json"));
    }

    @Test
    void normalizePresetNameRejectsNamesWithoutSafeCharacters() {
        assertEquals(Optional.of("my_preset"),
                EndPresetStorage.normalizePresetName(" My:Preset.json "));
        assertEquals(Optional.of("con_preset"),
                EndPresetStorage.normalizePresetName("CON"));
        assertEquals(Optional.empty(),
                EndPresetStorage.normalizePresetName("///"));
        assertEquals(Optional.empty(),
                EndPresetStorage.normalizePresetName("   "));
        assertEquals(Optional.empty(),
                EndPresetStorage.normalizePresetName("__..json"));
    }

    @Test
    void namedPresetFileResolvesInsidePresetLibrary() {
        Path resolved = EndPresetStorage.namedPresetFile(worldDir, "../Unsafe Name.json");
        assertEquals(EndPresetStorage.presetLibraryDir(worldDir).resolve("unsafe_name.json"), resolved,
                "namedPresetFile() must sanitize names before resolving paths");
        assertTrue(resolved.startsWith(EndPresetStorage.presetLibraryDir(worldDir)),
                "named preset files must stay inside the preset library directory");
    }

    @Test
    void exchangePresetFileResolvesInsideExchangeDirectory() {
        Path resolved = EndPresetStorage.exchangePresetFile(worldDir, "../Unsafe Name.json");
        assertEquals(EndPresetStorage.presetExchangeDir(worldDir).resolve("unsafe_name.json"), resolved,
                "exchangePresetFile() must sanitize names before resolving paths");
        assertTrue(resolved.startsWith(EndPresetStorage.presetExchangeDir(worldDir)),
                "exchange preset files must stay inside the exchange directory");
    }

    // ------------------------------------------------------------------
    //  Null-args fail-fast (NPE, not silent return)
    // ------------------------------------------------------------------

    @Test
    void loadWithNullPathThrowsNpe() {
        // null worldDir is a programmer error, not a runtime IO condition.
        // NPE via Objects.requireNonNull surfaces the bug at the call
        // site rather than returning a misleading "no preset on disk".
        assertThrows(NullPointerException.class,
                () -> EndPresetStorage.load(null));
    }

    @Test
    void saveWithNullPathThrowsNpe() {
        assertThrows(NullPointerException.class,
                () -> EndPresetStorage.save(null, EndPreset.defaults()));
    }

    @Test
    void saveWithNullPresetThrowsNpe() {
        assertThrows(NullPointerException.class,
                () -> EndPresetStorage.save(worldDir, null));
    }

    @Test
    void deleteWithNullPathThrowsNpe() {
        assertThrows(NullPointerException.class,
                () -> EndPresetStorage.delete(null));
    }

    @Test
    void presetFileWithNullPathThrowsNpe() {
        assertThrows(NullPointerException.class,
                () -> EndPresetStorage.presetFile(null));
    }

    @Test
    void importFromWithNullPathThrowsNpe() {
        assertThrows(NullPointerException.class,
                () -> EndPresetStorage.importFrom(null));
    }

    @Test
    void importFromWithNullHandlerThrowsNpe() {
        assertThrows(NullPointerException.class,
                () -> EndPresetStorage.importFrom(worldDir.resolve("preset.json"), null));
    }

    @Test
    void exportToWithNullPathThrowsNpe() {
        assertThrows(NullPointerException.class,
                () -> EndPresetStorage.exportTo(null, EndPreset.defaults()));
    }

    @Test
    void exportToWithNullPresetThrowsNpe() {
        assertThrows(NullPointerException.class,
                () -> EndPresetStorage.exportTo(worldDir.resolve("preset.json"), null));
    }

    @Test
    void saveNamedWithNullNameThrowsNpe() {
        assertThrows(NullPointerException.class,
                () -> EndPresetStorage.saveNamed(worldDir, null, EndPreset.defaults()));
    }

    @Test
    void loadNamedWithNullNameThrowsNpe() {
        assertThrows(NullPointerException.class,
                () -> EndPresetStorage.loadNamed(worldDir, null));
    }

    @Test
    void listNamedWithNullPathThrowsNpe() {
        assertThrows(NullPointerException.class,
                () -> EndPresetStorage.listNamed(null));
    }

    @Test
    void sanitizePresetNameWithNullNameThrowsNpe() {
        assertThrows(NullPointerException.class,
                () -> EndPresetStorage.sanitizePresetName(null));
    }

    @Test
    void normalizePresetNameWithNullNameThrowsNpe() {
        assertThrows(NullPointerException.class,
                () -> EndPresetStorage.normalizePresetName(null));
    }

    // ------------------------------------------------------------------
    //  load(Path, Consumer<String>) — error-handler overload
    //
    //  The no-arg load(Path) silently swallows decode errors; the
    //  Consumer overload surfaces them so a corrupt preset file can be
    //  logged instead of silently degrading to defaults. These tests pin
    //  the contract: missing file → no callback; corrupt/undecodable
    //  file → callback with field-specific message; valid file → no
    //  callback + preset returned.
    // ------------------------------------------------------------------

    /**
     * Captures error-handler invocations for inspection. Each call
     * appends the message to {@code messages}; the test asserts on
     * size + contents.
     */
    private static final class ErrorSink {
        final java.util.List<String> messages = new java.util.ArrayList<>();

        void accept(String msg) {
            messages.add(msg);
        }

        int count() {
            return messages.size();
        }

        String first() {
            return messages.get(0);
        }
    }

    @Test
    void loadWithHandlerDoesNotInvokeHandlerWhenFileMissing() {
        // Fresh worldDir, no preset file — the handler must NOT be called
        // (a fresh world legitimately has no preset; reporting it would
        // spam the log on every world load).
        ErrorSink sink = new ErrorSink();
        Optional<EndPreset> loaded = EndPresetStorage.load(worldDir, sink::accept);
        assertTrue(loaded.isEmpty(),
                "missing file must still return empty");
        assertEquals(0, sink.count(),
                "error handler must not be invoked for a missing file");
    }

    @Test
    void loadWithHandlerDoesNotInvokeHandlerWhenPathIsDirectory() throws IOException {
        // A directory at the preset file path is "no preset on disk",
        // not a decode error — handler must not be called.
        Files.createDirectory(EndPresetStorage.presetFile(worldDir));
        ErrorSink sink = new ErrorSink();
        Optional<EndPreset> loaded = EndPresetStorage.load(worldDir, sink::accept);
        assertTrue(loaded.isEmpty());
        assertEquals(0, sink.count(),
                "error handler must not be invoked for a directory path");
    }

    @Test
    void loadWithHandlerDoesNotInvokeHandlerForValidFile() {
        // A successfully-decoded preset must not invoke the error handler.
        EndPresetStorage.save(worldDir, EndPreset.defaults());
        ErrorSink sink = new ErrorSink();
        Optional<EndPreset> loaded = EndPresetStorage.load(worldDir, sink::accept);
        assertTrue(loaded.isPresent());
        assertEquals(0, sink.count(),
                "error handler must not be invoked on a successful load");
    }

    @Test
    void loadWithHandlerInvokesHandlerForCorruptJson() throws IOException {
        // Garbage that is not valid JSON — handler must be invoked with
        // a message indicating the JSON parse failure.
        writePresetFile("garbage{not json");
        ErrorSink sink = new ErrorSink();
        Optional<EndPreset> loaded = EndPresetStorage.load(worldDir, sink::accept);
        assertTrue(loaded.isEmpty(),
                "corrupt JSON must still return empty (non-fatal)");
        assertEquals(1, sink.count(),
                "error handler must be invoked exactly once for corrupt JSON");
        assertTrue(sink.first().contains("not valid JSON"),
                "error message must indicate JSON parse failure: " + sink.first());
    }

    @Test
    void loadWithHandlerInvokesHandlerForNonObjectJson() throws IOException {
        // Valid JSON but wrong shape — handler must be invoked.
        writePresetFile("\"hello\"");
        ErrorSink sink = new ErrorSink();
        EndPresetStorage.load(worldDir, sink::accept);
        assertEquals(1, sink.count());
        assertTrue(sink.first().contains("failed to decode"),
                "error message must indicate decode failure");
    }

    @Test
    void loadWithHandlerInvokesHandlerForUnknownEnumName() throws IOException {
        // A typo'd SeaMode name — handler must be invoked, with the
        // message from the SEA_MODE_CODEC flatXmap.
        writePresetFile("{\"sea_mode\": \"WIT_FLOOR\"}");
        ErrorSink sink = new ErrorSink();
        EndPresetStorage.load(worldDir, sink::accept);
        assertEquals(1, sink.count());
        assertTrue(sink.first().contains("SeaMode"),
                "error message must reference the unknown enum");
    }

    @Test
    void loadWithHandlerInvokesHandlerForConstraintViolation() throws IOException {
        // Post stage 6.3, a constraint-violating field (world_height
        // not a multiple of 16) must surface the validator's field-specific
        // message through the error handler — this is the user-facing
        // payoff of the validation layer.
        writePresetFile("{\"world_height\": 100}");
        ErrorSink sink = new ErrorSink();
        EndPresetStorage.load(worldDir, sink::accept);
        assertEquals(1, sink.count(),
                "constraint violation must invoke the error handler");
        assertTrue(sink.first().contains("world_height"),
                "error message must name the offending field");
        assertTrue(sink.first().contains("multiple of 16"),
                "error message must state the violated constraint");
        assertTrue(sink.first().contains("100"),
                "error message must include the offending value");
    }

    @Test
    void loadWithHandlerInvokesHandlerForErosionConstraintViolation() throws IOException {
        // A constraint violation in the embedded ErosionConfig must
        // surface through the error handler, prefixed so the user
        // knows it's the sub-config.
        writePresetFile("{\"erosion\": {\"erosion_rate\": 1.5}}");
        ErrorSink sink = new ErrorSink();
        EndPresetStorage.load(worldDir, sink::accept);
        assertEquals(1, sink.count());
        assertTrue(sink.first().contains("erosion_rate"),
                "error message must reference the offending erosion field");
    }

    @Test
    void loadWithHandlerReturnsEmptyEvenWhenHandlerInvoked() throws IOException {
        // The error handler is informational only — the return value
        // must still be Optional.empty() (not throwing) so the world-load
        // path proceeds with defaults rather than crashing the server.
        writePresetFile("{\"world_height\": 100}");
        ErrorSink sink = new ErrorSink();
        Optional<EndPreset> loaded = EndPresetStorage.load(worldDir, sink::accept);
        assertTrue(loaded.isEmpty(),
                "load must return empty on decode failure, not throw");
        assertEquals(1, sink.count());
    }

    @Test
    void loadWithHandlerNullPathThrowsNpe() {
        assertThrows(NullPointerException.class,
                () -> EndPresetStorage.load(null, msg -> {}));
    }

    @Test
    void loadWithHandlerNullHandlerThrowsNpe() {
        // null handler is a programmer error — the caller should use
        // the no-arg load(Path) overload instead.
        assertThrows(NullPointerException.class,
                () -> EndPresetStorage.load(worldDir, null));
    }

    // ------------------------------------------------------------------
    //  presetFile()
    // ------------------------------------------------------------------

    @Test
    void presetFileResolvesToExpectedPath() {
        // The path is worldDir/endterraforged_preset.json — i.e. the
        // FILE_NAME constant resolved against the world directory.
        Path resolved = EndPresetStorage.presetFile(worldDir);
        assertEquals(worldDir.resolve(EndPresetStorage.FILE_NAME), resolved,
                "presetFile() must resolve FILE_NAME against the world directory");
        assertTrue(resolved.endsWith(EndPresetStorage.FILE_NAME),
                "resolved path must end with the preset file name");
        assertTrue(resolved.startsWith(worldDir),
                "resolved path must be inside the world directory");
    }

    // ------------------------------------------------------------------
    //  helpers
    // ------------------------------------------------------------------

    /**
     * Writes the given content to the preset file path inside the test's
     * temp world directory. Used by the "load() must reject bad input"
     * tests to set up malformed / wrong-shape inputs that the public
     * {@link EndPresetStorage#save} API would never produce.
     */
    private void writePresetFile(String content) throws IOException {
        Files.writeString(EndPresetStorage.presetFile(worldDir), content);
    }

    /**
     * Returns a preset where every field differs from
     * {@link EndPreset#defaults()} — used by round-trip / overwrite tests
     * so a swapped-field codec bug (e.g. {@code seaLevelY} reading the
     * {@code islandBaselineY} JSON key) would produce a non-equal loaded
     * preset and fail the assertion.
     */
    private static EndPreset fullyCustomPreset() {
        return new EndPreset(384, -64, 63, 100,
                SeaMode.WITH_FLOOR, TopologyMode.CONTINENTAL_SHATTERED, true,
                ContinentConfig.defaults(),
                new TerrainConfig(0.75F, 2.5F),
                new ClimateConfig(6000.0F, 900, 1200, 1500, 0.4F),
                new ErosionConfig(200, 40, 1.5F, 1.2F, 0.7F, 0.3F));
    }
}
