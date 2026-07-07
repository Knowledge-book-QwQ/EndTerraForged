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
        Objects.requireNonNull(worldDir, "worldDir");
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
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            // File exists but is unreadable (permissions, disk error) —
            // surface to the error handler so the server admin can see
            // why their preset isn't being applied.
            errorHandler.accept("Preset file could not be read: " + e.getMessage());
            return Optional.empty();
        }
        JsonElement json;
        try {
            json = JsonParser.parseString(content);
        } catch (JsonParseException e) {
            // Garbage that is not valid JSON — surface to the error
            // handler so the admin can find the syntax error.
            errorHandler.accept("Preset file is not valid JSON: " + e.getMessage());
            return Optional.empty();
        }
        DataResult<EndPreset> result = EndPreset.CODEC.parse(JsonOps.INSTANCE, json);
        Optional<EndPreset> decoded = result.result();
        if (decoded.isEmpty()) {
            // JSON parsed cleanly but failed DFU decode (wrong shape,
            // unknown enum name, or — post stage 6.3 — a validator
            // constraint violation). Surface the DataResult.error message
            // so the admin knows which field is wrong.
            String msg = result.error()
                    .map(e -> e.message())
                    .orElse("unknown decode error");
            errorHandler.accept("Preset file failed to decode: " + msg);
        }
        return decoded;
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
        Objects.requireNonNull(worldDir, "worldDir");
        Objects.requireNonNull(preset, "preset");
        Path file = presetFile(worldDir);
        Path temp = file.resolveSibling(FILE_NAME + TEMP_SUFFIX);

        // Encode first — if encoding fails we don't want to touch the disk.
        DataResult<JsonElement> encoded = EndPreset.CODEC.encodeStart(JsonOps.INSTANCE, preset);
        Optional<JsonElement> jsonOpt = encoded.result();
        if (jsonOpt.isEmpty()) {
            return false;
        }
        String content = GSON.toJson(jsonOpt.get());

        try {
            // Write to temp file first so a crash mid-write doesn't
            // corrupt the existing preset file.
            Files.writeString(temp, content);
            try {
                Files.move(temp, file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // Filesystem doesn't support atomic move (some network
                // mounts, some Windows configurations). Fall back to a
                // non-atomic replace — still safer than writing in place
                // because the temp file is fully written before the rename.
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            // Best-effort cleanup of the temp file before returning. If
            // cleanup itself fails there's nothing useful we can do —
            // leave the temp file for the next save (which will overwrite
            // it) or for an admin to find.
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // Cleanup failure is non-fatal; the actual save failure
                // is what we report.
            }
            return false;
        }
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
        Objects.requireNonNull(worldDir, "worldDir");
        Path file = presetFile(worldDir);
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            return false;
        }
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
}
