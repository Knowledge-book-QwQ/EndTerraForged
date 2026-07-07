/*
 * Copyright (c) 2023 ReTerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Mixin on vanilla's
 * MinecraftServer to wire the per-world EndPreset persistence layer
 * (EndPresetStorage) into the server lifecycle: load the preset before
 * RandomState.create runs, save it after saveEverything completes.
 *
 * <p>This is the MC-integration half of stage 6.1 — the pure-logic half
 * ({@link endterraforged.world.config.EndPresetStorage}) was committed
 * first and unit-tested in isolation. This commit wires that tested
 * storage layer into MC's server lifecycle so that user-edited presets
 * actually survive a JVM restart.</p>
 */
package endterraforged.mixin;

import java.nio.file.Path;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;

import endterraforged.EndTerraForged;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetAccess;
import endterraforged.world.config.EndPresetStorage;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin on vanilla's {@link MinecraftServer} to wire
 * {@link EndPresetStorage} into the server lifecycle.
 *
 * <p><b>The problem.</b> Stage 5.3 wired the GUI's Done button to publish
 * the edited preset via {@link EndPresetAccess#set} — a process-scoped
 * {@code volatile} holder. That holder lives only as long as the JVM:
 * on a dedicated-server restart or a single-player world re-open, the
 * holder is empty and {@code MixinRandomState.<init>} falls back to
 * {@link EndPreset#defaults()}, silently discarding the user's edits.
 * The pure-logic {@link EndPresetStorage} class (stage 6.1 part 1) reads
 * / writes the preset from / to {@code <worldDir>/endterraforged_preset.json};
 * this Mixin is the MC-integration half that calls those methods at the
 * right points in the server lifecycle.</p>
 *
 * <p><b>Load injection — {@code createLevels(ChunkProgressListener)} at HEAD.</b>
 * Vanilla's {@code createLevels} is the method that constructs every
 * {@code ServerLevel} (and therefore triggers {@code ChunkGenerator.createState}
 * → {@code RandomState.create} for each dimension). Injecting at HEAD
 * guarantees our preset load runs <em>before</em> any dimension's
 * {@code RandomState} is built, so {@code MixinRandomState.<init>}'s
 * read of {@link EndPresetAccess#getOrDefault()} picks up the file-loaded
 * preset rather than falling back to {@link EndPreset#defaults()}.</p>
 *
 * <p><b>Why not {@code loadLevel()} (the wrapper) or {@code <init>}.</b>
 * {@code loadLevel} is also a valid injection point but is less specific
 * (it's a thin wrapper, easier to refactor in a future MC version). The
 * constructor is risky — field assignment order in {@code <init>} is
 * brittle, and the constructor runs once per server construction (broader
 * than "world load" — a server could be constructed without immediately
 * loading a world, e.g. in tests). {@code createLevels} is the most
 * specific point that runs after {@code storageSource} is set and before
 * {@code RandomState.create}.</p>
 *
 * <p><b>Save injection — {@code saveEverything(boolean, boolean, boolean)}
 * at RETURN.</b> {@code saveEverything} is the server-wide save entry
 * point: it's called periodically from {@code run()} (the auto-save tick,
 * default every 6000 ticks = 5 minutes) and on shutdown from
 * {@code halt(boolean)}. Injecting at RETURN (rather than HEAD) ensures
 * our save runs <em>after</em> vanilla's chunk-save IO completes, so we
 * don't compete with chunk writes for disk bandwidth. The preset file
 * is small (~500 bytes) so the extra IO is negligible.</p>
 *
 * <p><b>Skipping the save when {@link EndPresetAccess#get()} is null.</b>
 * A fresh world that the user never configured (no GUI ran, no preset
 * file existed at load time) would have {@code EndPresetAccess.get() == null}
 * at the first save tick. Writing {@link EndPreset#defaults()} to such a
 * world would pollute the world directory with a defaults file the user
 * didn't ask for — and would make every fresh EndTerraForged world look
 * "configured" when it isn't. The null check skips the save in that case:
 * the file is only written when the user has actively configured the End
 * (via GUI or by hand-editing the JSON before server start).</p>
 *
 * <p><b>Why a single Mixin (not separate load + save Mixins).</b> Both
 * injections target {@link MinecraftServer} and both access the same
 * {@code storageSource} field via {@code @Shadow @Final}. Combining them
 * in one class keeps the related load / save logic together (per the
 * project's "相关代码放在一起" principle) and avoids duplicating the
 * {@code @Shadow} declaration.</p>
 *
 * <p><b>Field access via {@code @Shadow @Final}.</b> {@code storageSource}
 * is a {@code protected final} field on {@link MinecraftServer}. Mixin
 * classes can't access protected fields outside the package via a cast
 * (the compiler refuses), so {@code @Shadow @Final} is the standard
 * pattern: the Mixin processor weaves the field-access bytecode at
 * runtime, where the access is legal because the Mixin code is
 * effectively part of {@link MinecraftServer}. The {@code @Final}
 * annotation tells the Mixin processor the field is {@code final} on
 * the target (so it doesn't warn about missing initialisation).</p>
 *
 * <p><b>Thread-safety.</b> Both {@code createLevels} and
 * {@code saveEverything} run on the server thread (the same thread
 * running {@code MinecraftServer.run()}). {@link EndPresetStorage} is
 * stateless and atomic-rename-based, so concurrent access is safe.
 * {@link EndPresetAccess#get()} is a {@code volatile} read, so the
 * happens-before edge from the load Mixin's {@code set()} call is
 * visible to the {@code MixinRandomState.<init>} read on the same
 * thread (and to any future cross-thread reader).</p>
 *
 * <p><b>Why no ThreadLocal capture (unlike {@link MixinRandomState}).</b>
 * {@link MixinRandomState} uses ThreadLocal to bridge data from a
 * <em>static factory</em> ({@code RandomState.create}) into an
 * <em>instance constructor</em> ({@code RandomState.<init>}) — there's
 * no vanilla parameter to plumb. This Mixin has no such constraint:
 * it injects into ordinary instance methods and reads the field directly.
 * No capture needed.</p>
 *
 * <p><b>Compile-only verification.</b> Like the other Mixins in this
 * package, the load / save behaviour can't be unit-tested in the
 * sandbox — it requires a live {@link MinecraftServer} (runClient or
 * a real server). The testable core lives in
 * {@link endterraforged.world.config.EndPresetStorageTest}, which
 * exercises the {@link EndPresetStorage} API that this Mixin calls.</p>
 */
@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {

    /**
     * Shadow of {@link MinecraftServer#storageSource} — the
     * {@link LevelStorageSource.LevelStorageAccess} that owns the world
     * save directory. {@code @Final} because the target field is
     * {@code final} (assigned in the constructor).
     *
     * <p>Used to resolve the world save {@link Path} via
     * {@code storageSource.getLevelDirectory().path()} — the top-level
     * world directory (the folder that contains {@code level.dat}),
     * which is where {@link EndPresetStorage} writes
     * {@code endterraforged_preset.json}.</p>
     */
    @Shadow
    @Final
    private LevelStorageSource.LevelStorageAccess storageSource;

    /**
     * Loads the user-edited preset from
     * {@code <worldDir>/endterraforged_preset.json} before any
     * dimension's {@code RandomState} is constructed.
     *
     * <p>Runs at HEAD of {@link MinecraftServer#createLevels}, which
     * is the method that constructs every {@code ServerLevel} (and
     * therefore triggers {@code ChunkGenerator.createState} →
     * {@code RandomState.create}). Injecting at HEAD guarantees the
     * file-loaded preset is in {@link EndPresetAccess} before
     * {@code MixinRandomState.<init>} reads
     * {@link EndPresetAccess#getOrDefault()}.</p>
     *
     * <p><b>No-op on a fresh world.</b> If the file doesn't exist
     * (single-player create flow, dedicated server with a fresh world
     * directory), {@link EndPresetStorage#load} returns
     * {@link java.util.Optional#empty()} and the {@code ifPresent} is
     * a no-op. The previously-published preset (from the GUI's
     * {@link EndPresetAccess#set} call on Done) flows through
     * unchanged — this is exactly the single-player create flow,
     * where the GUI sets the preset and the file doesn't yet exist.</p>
     *
     * <p><b>Loads on world re-open.</b> If the file exists (world was
     * previously saved with a preset), {@link EndPresetStorage#load}
     * decodes it and {@code ifPresent(EndPresetAccess::set)} publishes
     * it. Subsequent {@code MixinRandomState.<init>} reads the
     * file-loaded preset instead of falling back to defaults.</p>
     *
     * <p><b>Corrupt file is non-fatal but logged.</b> If the file is
     * present but cannot be decoded (corrupt JSON, wrong shape, or —
     * post stage 6.3 — a validator constraint violation like
     * {@code "world_height": 100}), {@link EndPresetStorage#load}
     * returns empty (never throws) and the world loads with
     * {@link EndPreset#defaults()} instead of crashing the server.
     * Unlike a fresh world (which is silent), a decode failure is
     * surfaced to the server log via {@link EndTerraForged#LOGGER}
     * so the user / server admin can see which preset field was
     * wrong — without this, a hand-edited preset file would silently
     * degrade to defaults with no indication of what went wrong.</p>
     */
    @Inject(method = "createLevels", at = @At("HEAD"))
    private void endTerraForged$loadPresetBeforeRandomState(CallbackInfo ci) {
        // storageSource is non-null by the time createLevels runs (it's
        // assigned in the MinecraftServer constructor, which completes
        // before run() invokes createLevels). getLevelDirectory().path()
        // is the top-level world directory Path.
        Path worldDir = storageSource.getLevelDirectory().path();
        // load(Path, Consumer) never throws — see EndPresetStorage.load
        // Javadoc. The error handler is invoked only when the file exists
        // but cannot be decoded (corrupt JSON, constraint violation, etc.)
        // — a missing file is silent (fresh world, no error to report).
        // We log at WARN (not ERROR) because the world still loads with
        // defaults; the user's edits are lost for this load but the
        // server is not in a broken state.
        EndPresetStorage.load(worldDir,
                msg -> EndTerraForged.LOGGER.warn(
                        "EndTerraForged preset file at {} could not be loaded; "
                                + "falling back to defaults for this world load. {}",
                        worldDir, msg))
                .ifPresent(EndPresetAccess::set);
    }

    /**
     * Persists the user-edited preset to
     * {@code <worldDir>/endterraforged_preset.json} after vanilla's
     * save completes.
     *
     * <p>Runs at RETURN of {@link MinecraftServer#saveEverything}, which
     * is called periodically from {@code run()} (auto-save tick, every
     * ~5 minutes) and on shutdown from {@code halt(boolean)}. Injecting
     * at RETURN (not HEAD) ensures our save runs after vanilla's
     * chunk-save IO completes, avoiding disk-bandwidth contention.</p>
     *
     * <p><b>Skip when {@link EndPresetAccess#get()} is null.</b> A null
     * holder means no GUI ran (single-player create) and no preset file
     * was loaded (world re-open of a never-configured world). Writing
     * {@link EndPreset#defaults()} to such a world would pollute the
     * world directory with a defaults file the user didn't ask for.
     * Skip the save — the world stays "vanilla-equivalent" until the
     * user explicitly configures it.</p>
     *
     * <p><b>Atomic write.</b> {@link EndPresetStorage#save} writes to a
     * sibling {@code .tmp} file first, then {@code Files.move}-s it over
     * the real file with {@code ATOMIC_MOVE + REPLACE_EXISTING}. A
     * crash during the write leaves the previously-saved preset intact
     * rather than a truncated / corrupt file.</p>
     *
     * <p><b>Never throws.</b> {@link EndPresetStorage#save} swallows
     * {@link java.io.IOException} and {@link com.google.gson.JsonParseException}
     * and returns {@code false} on failure. A failed save is non-fatal —
     * the world still saves its chunks; only the preset file isn't
     * updated. The next save tick will retry.</p>
     *
     * <p><b>Why {@link CallbackInfoReturnable} not {@link CallbackInfo}.</b>
     * Vanilla's {@code saveEverything} returns {@code boolean} (true if
     * save succeeded). The Mixin processor requires {@code @Inject} at
     * {@code @At("RETURN")} on a non-void method to use
     * {@link CallbackInfoReturnable}; using {@link CallbackInfo} triggers
     * {@code InvalidInjectionException: CallbackInfoReturnable is required!}
     * at apply time, crashing the game during early startup. This was the
     * v0.1.0-preview hotfix bug — the original code used {@code CallbackInfo}
     * and the game crashed before reaching the main menu.</p>
     */
    @Inject(method = "saveEverything", at = @At("RETURN"))
    private void endTerraForged$savePresetAfterVanilla(CallbackInfoReturnable<Boolean> cir) {
        EndPreset preset = EndPresetAccess.get();
        if (preset == null) {
            // No GUI ran and no preset file was loaded at world start —
            // don't pollute a never-configured world with a defaults()
            // file. The user's first interaction with the GUI will
            // populate EndPresetAccess via set(); subsequent save ticks
            // will then write the file.
            return;
        }
        Path worldDir = storageSource.getLevelDirectory().path();
        // save() never throws — see EndPresetStorage.save Javadoc. A
        // false return is non-fatal: the world's chunk save completed
        // successfully (vanilla's saveEverything handled that); only the
        // preset file wasn't updated. The next save tick will retry.
        EndPresetStorage.save(worldDir, preset);
    }

    /**
     * Clears the {@link EndPresetAccess} holder when the server shuts down,
     * preventing a stale preset from leaking into the next world loaded
     * in the same JVM session.
     *
     * <p><b>The problem.</b> {@link EndPresetAccess} is a process-wide
     * {@code volatile} holder. Without this clear, the following
     * single-player flow leaks a stale preset:</p>
     * <ol>
     *   <li>User creates world A in the GUI, edits preset to presetA,
     *       clicks Done — {@code EndPresetAccess.set(presetA)}</li>
     *   <li>{@code createLevels} runs, {@code MixinRandomState} reads
     *       presetA, world A generates correctly</li>
     *   <li>User saves & quits to title — server halt, but holder
     *       <em>stays</em> {@code presetA}</li>
     *   <li>User loads existing world C (never configured, no preset file).
     *       The create-world screen did NOT run for C, so the GUI's
     *       {@code set()} was not called this session.</li>
     *   <li>{@code createLevels} runs for world C — {@code load(worldDirC)}
     *       returns {@code Optional.empty()} (no preset file), the
     *       {@code ifPresent} is a no-op, holder is still {@code presetA}</li>
     *   <li>{@code MixinRandomState} reads {@code EndPresetAccess.getOrDefault()}
     *       → returns {@code presetA} — world C generates with world A's
     *       preset (wrong terrain)</li>
     * </ol>
     *
     * <p><b>The fix.</b> Inject at RETURN of vanilla's {@code halt(boolean)},
     * the single entry point for server shutdown (called from the
     * {@code /stop} command, dedicated server exit, and single-player
     * "Save and Quit to Title"). After halt returns, the server thread
     * is finished (or about to finish, depending on {@code waitForServer}),
     * so clearing the holder here is safe — no in-flight worldgen can
     * still be reading it.</p>
     *
     * <p><b>Why RETURN and not HEAD.</b> At HEAD, an in-flight worldgen
     * thread could still be reading {@link EndPresetAccess#getOrDefault()}.
     * RETURN guarantees the server thread has fully stopped (or joined),
     * so the holder is no longer being read from this server's lifecycle.</p>
     *
     * <p><b>Dedicated server.</b> On a dedicated server, {@code halt} is
     * followed by JVM exit, so the clear is unnecessary but harmless.
     * On an integrated (single-player) server, the JVM continues and the
     * user may load another world — the clear is essential there.</p>
     *
     * <p><b>Side effect on the GUI's set call.</b> The single-player
     * create-world flow runs the GUI on the client thread, which calls
     * {@code EndPresetAccess.set(presetB)} <em>before</em> the new server's
     * {@code createLevels} runs. The previous server's {@code halt} ran
     * earlier (when the user quit the previous world), so by the time
     * the GUI sets presetB the holder is already null and the GUI's
     * {@code set} repopulates it cleanly — no race.</p>
     */
    @Inject(method = "halt", at = @At("RETURN"))
    private void endTerraForged$clearPresetHolderOnServerHalt(CallbackInfo ci) {
        // Drop the published preset so the next world load in the same JVM
        // session starts from a clean slate — either the GUI sets a new
        // one (single-player create flow) or the load-path Mixin reads
        // the world's preset file (world re-open), or MixinRandomState
        // falls back to EndPreset.defaults() (never-configured fresh load).
        EndPresetAccess.set(null);
    }
}
