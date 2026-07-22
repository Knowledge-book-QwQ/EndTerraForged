/*
 * Copyright (c) 2023 ReTerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Per-world persistence
 * layer for {@link EndPreset}: reads / writes the user's edited preset from /
 * to {@code <worldDir>/endterraforged_preset.json} so the GUI's edits survive
 * a JVM restart and apply on subsequent world loads.
 *
 * <p>This is the pure-logic half of stage 6.1 — it has zero MC classpath
 * dependencies beyond DFU + Gson (both already on the common classpath via
 * {@code EndPreset.CODEC}) and is fully unit-testable with a JUnit
 * {@code @TempDir}. The MC integration half (a Mixin that wires
 * {@link #load} into the world-load path and {@link #save} into the
 * world-create / world-save path) follows as a separate commit so the
 * storage contract can be locked in by tests before any Mixin code calls
 * it.</p>
 */
package endterraforged.world.config;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

/**
 * Per-world on-disk persistence for the user-edited {@link EndPreset}.
 *
 * <p><b>The problem.</b> Stage 5.3 wired the GUI's Done button to publish
 * the edited preset via {@link EndPresetAccess#set} — a process-scoped
 * {@code volatile} holder. That holder lives only as long as the JVM:
 * on a dedicated-server restart or a single-player world re-open, the
 * holder is empty and {@code MixinRandomState.<init>} falls back to
 * {@link EndPreset#defaults()}, silently discarding the user's edits.
 * This class closes that gap by reading / writing the preset from / to
 * a JSON file inside the world save directory.</p>
 *
 * <p><b>File location.</b> {@code <worldDir>/endterraforged_preset.json}
 * — top-level, alongside {@code level.dat}. A single file at the top
 * level is the simplest layout that doesn't conflict with vanilla's
 * {@code data/} (DFU-registered resources) or {@code region/} (chunk
 * storage) folders, and is easy to find when browsing a world directory.
 * If the project later needs more per-world files, the layout can be
 * refactored to {@code <worldDir>/endterraforged/...} without breaking
 * existing worlds (a one-time migration on first load).</p>
 *
 * <p><b>Serialisation.</b> Reuses {@link EndPreset#CODEC} with
 * {@link JsonOps#INSTANCE} so the on-disk format is identical to the
 * data-pack preset format (and to what the GUI encodes). {@code optionalFieldOf}
 * means a preset file may omit any subset of fields and the missing
 * fields inherit their codec-declared defaults — the file only stores
 * the values the user actually overrode.</p>
 *
 * <p><b>Atomicity.</b> {@link #save} writes to a sibling
 * {@code endterraforged_preset.json.tmp} file first, then atomically renames
 * it over the real file ({@code Files.move} with {@link StandardCopyOption#ATOMIC_MOVE}
 * + {@link StandardCopyOption#REPLACE_EXISTING}). A crash during the write
 * therefore leaves the previously-saved preset intact rather than a
 * truncated / corrupt file. On filesystems that don't support atomic
 * move (rare: some network mounts), it falls back to a non-atomic
 * replace — still safer than writing in place.</p>
 *
 * <p><b>Defensive IO.</b> Every method swallows {@link IOException}
 * and {@link JsonParseException} and reports failure through its return
 * value rather than propagating — the world-load path must not crash
 * the server because a preset file is corrupt or unreadable. The
 * fail-fast contract is reserved for programmer error: a {@code null}
 * {@code worldDir} or {@code preset} argument throws {@link NullPointerException}
 * via {@link Objects#requireNonNull}, because passing {@code null} is a
 * caller bug (not a runtime IO condition).</p>
 *
 * <p><b>Thread-safety.</b> Stateless — all state lives in local variables
 * and the immutable {@link EndPreset} argument / return value. Safe to
 * call from any thread. The atomic-rename write makes concurrent
 * {@link #save} calls safe in practice (last writer wins, but neither
 * file is ever left half-written).</p>
 */
public final class EndPresetStorage {

    /**
     * File name (no directory prefix) of the preset file inside the
     * world save directory. Exposed as a constant so callers (e.g. a
     * Mixin that wants to log the resolved path) and tests can reference
     * the same string this class uses internally.
     */
    public static final String FILE_NAME = "endterraforged_preset.json";

    /**
     * Suffix used for the temp file written before the atomic rename in
     * {@link #save}. Exposed for tests that want to assert no temp file
     * is left behind after a successful save.
     */
    public static final String TEMP_SUFFIX = ".tmp";

    /**
     * Directory name used for user-managed preset library files. The single
     * {@link #FILE_NAME} file remains the active world preset; this directory
     * stores named copies for import/export style workflows in the editor.
     */
    public static final String LIBRARY_DIR_NAME = "endterraforged_presets";

    /**
     * Directory used to exchange preset JSON files with the player outside the
     * named per-world library.
     */
    public static final String EXCHANGE_DIR_NAME = "endterraforged_exports";

    /**
     * Extension used by named preset files. Names returned by
     * {@link #listNamed(Path)} and accepted by
     * {@link #saveNamed(Path, String, EndPreset)} are logical preset names
     * without this extension.
     */
    public static final String JSON_SUFFIX = ".json";

    private static final String FALLBACK_PRESET_NAME = "preset";

    /**
     * Pretty-printing Gson instance — preset files are written with
     * indentation so a human editing the JSON by hand (or diffing it
     * in git for a server world) can read it. The cost is negligible
     * (one save per world-create / world-save, not per chunk).
     */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private EndPresetStorage() {
        // Utility class — no instances.
    }

    /**
     * Loads the preset from {@code <worldDir>/endterraforged_preset.json}.
     *
     * <p>Returns {@link Optional#empty()} (never throws) when:</p>
     * <ul>
     *   <li>the file does not exist (a fresh world that has never had a
     *       preset saved — caller falls back to {@link EndPreset#defaults()});</li>
     *   <li>the path exists but is not a regular file (a directory was
     *       accidentally created at that name);</li>
     *   <li>the file cannot be read (IO error — permissions, disk error);</li>
     *   <li>the file content is not valid JSON ({@link JsonParseException});</li>
     *   <li>the JSON is valid but does not match {@link EndPreset#CODEC}'s
     *       expected shape (DFU decode error — e.g. {@code "sea_mode": 123}
     *       where a string was expected, or {@code "sea_mode": "TYPO"} where
     *       a known enum name was expected).</li>
     * </ul>
     *
     * <p>A failed load is non-fatal: {@code MixinRandomState} will fall
     * back to {@link EndPresetAccess#getOrDefault()} → {@link EndPreset#defaults()}
     * if this returns empty. The user's edits are lost for this load, but
     * the world still loads (with default terrain) instead of crashing the
     * server.</p>
     *
     * <p><b>Decode errors are silently swallowed</b> by this overload — use
     * {@link #load(Path, Consumer)} to receive the error message so a
     * corrupt preset file can be surfaced in the server log rather than
     * silently degrading to defaults.</p>
     *
     * @param worldDir the world save directory (the folder that contains
     *                 {@code level.dat}); must not be {@code null}
     * @return the loaded preset, or {@link Optional#empty()} if no valid
     *         preset file is present
     * @throws NullPointerException if {@code worldDir} is {@code null}
     */
    public static Optional<EndPreset> load(Path worldDir) {
        // No-op error handler — caller doesn't want the diagnostic message.
        return load(worldDir, msg -> {});
    }

    /**
     * Loads the preset from {@code <worldDir>/endterraforged_preset.json},
     * invoking {@code errorHandler} with a diagnostic message when the
     * file exists but cannot be decoded.
     *
     * <p>This overload is the entry point for callers (e.g.
     * {@link endterraforged.mixin.MixinMinecraftServer}) that want to
     * distinguish "file missing" (a fresh world — silent fallback to
     * defaults is correct) from "file present but decode failed" (a
     * corrupt or hand-edited file that the user/server admin needs to
     * know about). Both still return {@link Optional#empty()} (never
     * throws); the difference is whether {@code errorHandler} is invoked.</p>
     *
     * <p><b>When the error handler is invoked:</b></p>
     * <ul>
     *   <li>the file exists but is not valid JSON — handler receives
     *       {@code "Preset file is not valid JSON: <reason>"};</li>
     *   <li>the JSON is valid but does not match {@link EndPreset#CODEC}'s
     *       expected shape — handler receives
     *       {@code "Preset file failed to decode: <DataResult.error message>"},
     *       which (post stage 6.3 validation layer) includes a
     *       field-specific pointer to the offending preset field (e.g.
     *       {@code "world_height must be a multiple of 16, got 100"});</li>
     *   <li>the file exists but cannot be read (IO error) — handler
     *       receives {@code "Preset file could not be read: <reason>"}.</li>
     * </ul>
     *
     * <p><b>When the error handler is NOT invoked:</b></p>
     * <ul>
     *   <li>the file does not exist (fresh world — silent fallback to
     *       defaults is the correct behaviour, no error to report);</li>
     *   <li>the path exists but is a directory (treated as "no preset
     *       file" — same as missing, no error message);</li>
     *   <li>the file decodes successfully (handler not called; the
     *       returned {@link Optional} carries the preset).</li>
     * </ul>
     *
     * <p>The error handler is invoked at most once per call, on the
     * calling thread, before the method returns. The handler must not
     * throw — if it does, the exception propagates (the world-load path
     * must not crash, so production callers should pass
     * {@code LOGGER::warn} or equivalent).</p>
     *
     * @param worldDir the world save directory (the folder that contains
     *                 {@code level.dat}); must not be {@code null}
     * @param errorHandler invoked with a diagnostic message when the file
     *                     exists but cannot be decoded; must not be
     *                     {@code null} (use {@link #load(Path)} for the
     *                     silent variant)
     * @return the loaded preset, or {@link Optional#empty()} if no valid
     *         preset file is present
     * @throws NullPointerException if {@code worldDir} or
     *         {@code errorHandler} is {@code null}
     */
    public static Optional<EndPreset> load(Path worldDir, Consumer<String> errorHandler) {
        Objects.requireNonNull(errorHandler, "errorHandler");
        Path file = presetFile(worldDir);
        if (!Files.isRegularFile(file)) {
            // Missing file (fresh world) or accidentally-created directory —
            // both are "no preset on disk" from the caller's perspective.
            // Not an error condition: a fresh world legitimately has no
            // preset file, and reporting it would spam the log on every
            // world load.
            return Optional.empty();
        }
        return readPresetFile(file, errorHandler);
    }

    /**
     * Saves the preset to {@code <worldDir>/endterraforged_preset.json},
     * atomically replacing any existing file.
     *
     * <p>Atomicity is achieved by writing to a sibling
     * {@code endterraforged_preset.json.tmp} file first, then
     * {@code Files.move}-ing it over the real file with
     * {@link StandardCopyOption#ATOMIC_MOVE} and
     * {@link StandardCopyOption#REPLACE_EXISTING}. On filesystems that
     * don't support atomic move, falls back to a non-atomic replace.</p>
     *
     * <p>Returns {@code false} (never throws) when:</p>
     * <ul>
     *   <li>the preset cannot be encoded by {@link EndPreset#CODEC}
     *       (encode error — defensive; should not happen for any
     *       well-formed {@link EndPreset});</li>
     *   <li>the temp file cannot be written (IO error — permissions,
     *       disk full);</li>
     *   <li>the atomic rename fails (IO error).</li>
     * </ul>
     *
     * <p>On any failure path, the previously-saved preset file (if any)
     * is left untouched — the temp file is best-effort deleted before
     * returning. A {@code false} return value should be surfaced to the
     * user (e.g. via a chat message on the save-thread) so they know their
     * edits won't persist.</p>
     *
     * @param worldDir the world save directory; must not be {@code null}
     * @param preset the preset to save; must not be {@code null}
     * @return {@code true} if the file was written successfully;
     *         {@code false} if encoding or IO failed
     * @throws NullPointerException if {@code worldDir} or {@code preset} is {@code null}
     */
    public static boolean save(Path worldDir, EndPreset preset) {
        Objects.requireNonNull(preset, "preset");
        return writePresetFile(presetFile(worldDir), preset);
    }

    /**
     * Removes the preset file from {@code <worldDir>/endterraforged_preset.json}
     * if it exists.
     *
     * <p>Useful when the user picks "reset to defaults" in a future GUI
     * iteration and wants the world to load with vanilla-equivalent
     * terrain on subsequent loads. Returns {@code false} (never throws)
     * if the file does not exist (already deleted) or if deletion fails
     * (IO error — e.g. permissions).</p>
     *
     * @param worldDir the world save directory; must not be {@code null}
     * @return {@code true} if the file existed and was deleted;
     *         {@code false} if it did not exist or deletion failed
     * @throws NullPointerException if {@code worldDir} is {@code null}
     */
    public static boolean delete(Path worldDir) {
        return deleteFile(presetFile(worldDir));
    }

    /**
     * Imports a preset from an arbitrary JSON file. This is the storage-level
     * hook future GUI file-pickers can use without duplicating DFU decode logic.
     */
    public static Optional<EndPreset> importFrom(Path file) {
        return importFrom(file, msg -> {});
    }

    /**
     * Imports a preset from an arbitrary JSON file, reporting decode/read
     * errors through the supplied handler.
     */
    public static Optional<EndPreset> importFrom(Path file, Consumer<String> errorHandler) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(errorHandler, "errorHandler");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return readPresetFile(file, errorHandler);
    }

    /**
     * Exports a preset to an arbitrary JSON file using the same pretty-printed,
     * temp-file-then-rename write path as the active world preset.
     */
    public static boolean exportTo(Path file, EndPreset preset) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(preset, "preset");
        return writePresetFile(file, preset);
    }

    /**
     * Saves a named preset copy into {@code <worldDir>/endterraforged_presets}.
     * The active world preset is unchanged until the caller explicitly saves it
     * through {@link #save(Path, EndPreset)}.
     */
    public static boolean saveNamed(Path worldDir, String name, EndPreset preset) {
        Objects.requireNonNull(preset, "preset");
        return exportTo(namedPresetFile(worldDir, name), preset);
    }

    /** Loads a named preset copy from the per-world preset library. */
    public static Optional<EndPreset> loadNamed(Path worldDir, String name) {
        return loadNamed(worldDir, name, msg -> {});
    }

    /** Loads a named preset copy and reports corrupt-library-file errors. */
    public static Optional<EndPreset> loadNamed(
            Path worldDir, String name, Consumer<String> errorHandler) {
        Objects.requireNonNull(errorHandler, "errorHandler");
        return importFrom(namedPresetFile(worldDir, name), errorHandler);
    }

    /** Deletes a named preset copy from the per-world preset library. */
    public static boolean deleteNamed(Path worldDir, String name) {
        return deleteFile(namedPresetFile(worldDir, name));
    }

    /**
     * Lists logical preset names in the per-world preset library. Corrupt JSON
     * files are still listed so the UI can show them and surface a decode error
     * when selected.
     */
    public static List<String> listNamed(Path worldDir) {
        Path directory = presetLibraryDir(worldDir);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(EndPresetStorage::isPresetJsonFile)
                    .map(EndPresetStorage::stripJsonSuffix)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Converts user-facing names into safe logical file stems. The returned
     * value never contains a path separator, never includes {@link #JSON_SUFFIX},
     * and never resolves to a Windows device name.
     */
    public static String sanitizePresetName(String name) {
        return normalizePresetName(name).orElse(FALLBACK_PRESET_NAME);
    }

    /**
     * Normalizes a user-facing library name without falling back to a generic
     * file stem. GUI workflows should use this before saving named presets so
     * blank or separator-only names can be reported to the user instead of
     * silently overwriting {@code preset.json}.
     */
    public static Optional<String> normalizePresetName(String name) {
        Objects.requireNonNull(name, "name");
        String sanitized = sanitizePresetNameStem(stripJsonSuffix(name.trim()));
        if (sanitized.isBlank()) {
            return Optional.empty();
        }
        if (isReservedWindowsName(sanitized)) {
            sanitized = sanitized + "_preset";
        }
        return Optional.of(sanitized);
    }

    private static String sanitizePresetNameStem(String source) {
        StringBuilder builder = new StringBuilder(source.length());
        boolean pendingSeparator = false;
        for (int i = 0; i < source.length();) {
            int codePoint = source.codePointAt(i);
            i += Character.charCount(codePoint);
            if (isAllowedPresetNameCodePoint(codePoint)) {
                if (pendingSeparator && builder.length() > 0
                        && builder.charAt(builder.length() - 1) != '_') {
                    builder.append('_');
                }
                builder.appendCodePoint(Character.toLowerCase(codePoint));
                pendingSeparator = false;
            } else {
                pendingSeparator = true;
            }
        }
        return trimTrailingDotsAndUnderscores(builder.toString());
    }

    /**
     * Returns the directory used by named preset library files. Does not create
     * the directory; save/export methods create parent directories when needed.
     */
    public static Path presetLibraryDir(Path worldDir) {
        Objects.requireNonNull(worldDir, "worldDir");
        return worldDir.resolve(LIBRARY_DIR_NAME);
    }

    /** Returns the JSON path for a named preset library entry. */
    public static Path namedPresetFile(Path worldDir, String name) {
        return presetLibraryDir(worldDir).resolve(sanitizePresetName(name) + JSON_SUFFIX);
    }

    /**
     * Returns the directory used for manual preset import and export. Files in
     * this directory are never treated as active or named-library presets.
     */
    public static Path presetExchangeDir(Path worldDir) {
        Objects.requireNonNull(worldDir, "worldDir");
        return worldDir.resolve(EXCHANGE_DIR_NAME);
    }

    /** Returns the JSON path for a user-managed import or export file. */
    public static Path exchangePresetFile(Path worldDir, String name) {
        return presetExchangeDir(worldDir).resolve(sanitizePresetName(name) + JSON_SUFFIX);
    }

    /**
     * Returns the absolute path of the preset file inside the given
     * world directory: {@code <worldDir>/endterraforged_preset.json}.
     *
     * <p>Exposed so callers (e.g. a Mixin that logs the resolved path on
     * world load / save) and tests can reference the same path this
     * class uses internally, without re-deriving it from {@link #FILE_NAME}.
     * Does <em>not</em> check whether the file exists.</p>
     *
     * @param worldDir the world save directory; must not be {@code null}
     * @return the path {@code <worldDir>/endterraforged_preset.json}
     *         (resolves the file name against the world directory)
     * @throws NullPointerException if {@code worldDir} is {@code null}
     */
    public static Path presetFile(Path worldDir) {
        Objects.requireNonNull(worldDir, "worldDir");
        return worldDir.resolve(FILE_NAME);
    }

    private static Optional<EndPreset> readPresetFile(
            Path file, Consumer<String> errorHandler) {
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            errorHandler.accept("Preset file could not be read: " + e.getMessage());
            return Optional.empty();
        }
        JsonElement json;
        try {
            json = JsonParser.parseString(content);
        } catch (JsonParseException e) {
            errorHandler.accept("Preset file is not valid JSON: " + e.getMessage());
            return Optional.empty();
        }
        DataResult<EndPreset> result = EndPreset.CODEC.parse(JsonOps.INSTANCE, json);
        Optional<EndPreset> decoded = result.result();
        if (decoded.isEmpty()) {
            String msg = result.error()
                    .map(e -> e.message())
                    .orElse("unknown decode error");
            errorHandler.accept("Preset file failed to decode: " + msg);
        }
        return decoded;
    }

    private static boolean writePresetFile(Path file, EndPreset preset) {
        Path fileName = file.getFileName();
        if (fileName == null) {
            return false;
        }
        DataResult<JsonElement> encoded = EndPreset.CODEC.encodeStart(JsonOps.INSTANCE, preset);
        Optional<JsonElement> jsonOpt = encoded.result();
        if (jsonOpt.isEmpty()) {
            return false;
        }
        String content = GSON.toJson(jsonOpt.get());
        Path temp = file.resolveSibling(fileName + TEMP_SUFFIX);
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(temp, content);
            try {
                Files.move(temp, file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // Cleanup is best effort; the false return reports the save failure.
            }
            return false;
        }
    }

    private static boolean deleteFile(Path file) {
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isPresetJsonFile(String fileName) {
        return fileName.length() > JSON_SUFFIX.length()
                && fileName.toLowerCase(Locale.ROOT).endsWith(JSON_SUFFIX);
    }

    private static String stripJsonSuffix(String name) {
        if (name.toLowerCase(Locale.ROOT).endsWith(JSON_SUFFIX)) {
            return name.substring(0, name.length() - JSON_SUFFIX.length());
        }
        return name;
    }

    private static boolean isAllowedPresetNameCodePoint(int codePoint) {
        return Character.isLetterOrDigit(codePoint)
                || codePoint == '-'
                || codePoint == '_';
    }

    private static String trimTrailingDotsAndUnderscores(String value) {
        int end = value.length();
        while (end > 0) {
            char c = value.charAt(end - 1);
            if (c != '.' && c != '_') {
                break;
            }
            end--;
        }
        return value.substring(0, end);
    }

    private static boolean isReservedWindowsName(String name) {
        int dot = name.indexOf('.');
        String stem = dot >= 0 ? name.substring(0, dot) : name;
        String upper = stem.toUpperCase(Locale.ROOT);
        if (upper.equals("CON") || upper.equals("PRN") || upper.equals("AUX")
                || upper.equals("NUL")) {
            return true;
        }
        if (upper.length() != 4) {
            return false;
        }
        char index = upper.charAt(3);
        return index >= '1' && index <= '9'
                && (upper.startsWith("COM") || upper.startsWith("LPT"));
    }
}
