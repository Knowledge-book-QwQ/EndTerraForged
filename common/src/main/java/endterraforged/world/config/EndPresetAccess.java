/*
 * Copyright (c) 2023 ReTerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Process-wide holder
 * for the user-edited {@link EndPreset}, so the GUI's edits can flow from
 * the client thread (where CreateWorldScreen runs) to the bootstrap thread
 * (where {@link net.minecraft.world.level.levelgen.RandomState} is
 * constructed).
 */
package endterraforged.world.config;

/**
 * Process-wide holder for the user-edited {@link EndPreset}.
 *
 * <p><b>The problem.</b> The GUI's {@code EndPresetEditorScreen} builds an
 * immutable {@link EndPreset} when the user clicks Done — but that preset
 * needs to reach {@code MixinRandomState.<init>}, which runs later on the
 * same thread (world creation is synchronous from the create-world screen
 * through to {@code RandomState} construction). Vanilla's
 * {@code CreateWorldScreen.onCreate} → world-data build → chunk-gen setup
 * pipeline has no parameter we can hook to pass the preset, so a static
 * holder is the simplest bridge.</p>
 *
 * <p><b>Thread-safety.</b> The {@code current} field is {@code volatile}.
 * There is exactly one writer (the client thread, on GUI Done) and one
 * reader (the bootstrap thread, in {@code MixinRandomState.<init>}) — and
 * in practice these are the same thread because world creation is
 * synchronous. {@code volatile} is still correct: it establishes a
 * happens-before edge that makes the immutable {@link EndPreset}'s entire
 * graph visible to the reader, and future-proofs against any future
 * async-decoupling of the GUI from the create flow.</p>
 *
 * <p><b>Lifecycle.</b></p>
 * <ul>
 *   <li>Before any GUI Done: {@link #get()} returns {@code null} —
 *       {@code MixinRandomState} falls back to {@link EndPreset#defaults()},
 *       preserving the existing (pre-GUI) behaviour for dedicated servers
 *       and direct world loads</li>
 *   <li>After GUI Done (single-player create-world): the holder carries the
 *       user's edited preset for the duration of world creation</li>
 *   <li>On next GUI Done: the holder is overwritten with the new preset —
 *       no manual reset needed (each create-world flow sets before
 *       creating)</li>
 *   <li>On server {@code halt(boolean)} RETURN: the holder is cleared by
 *       {@code MixinMinecraftServer.endTerraForged$clearPresetHolderOnServerHalt}
 *       so a stale preset from a previous world load does not leak into
 *       the next world loaded in the same JVM session (single-player
 *       "save & quit to title" then load another world). Without this
 *       clear, the holder would carry the previous world's preset across
 *       the world boundary and {@code MixinRandomState.<init>} would
 *       read the wrong preset for the new world.</li>
 * </ul>
 *
 * <p><b>Why not inject through {@code EndRandomStateAccess}.</b> The
 * interface-injected {@code EndRandomStateAccess} on {@code RandomState}
 * already exposes the seed + End flag + {@code EndDensity}. But the
 * {@code EndPreset} is needed <em>before</em> {@code EndDensity} is built
 * — it's the input to {@code EndDensity}'s construction. A static holder
 * set on the GUI thread is the cleanest way to bridge the
 * create-world-screen → MixinRandomState boundary without plumbing a new
 * parameter through vanilla's world-data factory chain.</p>
 *
 * <p><b>Why not world-data attachment.</b> A world-data attachment (via
 * {@code AttachmentType}s on Fabric / {@code Capability}s on NeoForge)
 * would be the cleanest long-term solution — the preset would persist
 * with the world save and load on world re-open. But that requires
 * loader-specific registration and a serialisation step, and the
 * immediate goal is just "the user's edits reach worldgen". A future
 * iteration can replace this static holder with a world-data attachment
 * without breaking the GUI's {@code set} call site.</p>
 */
public final class EndPresetAccess {

    /**
     * The currently active user-edited End preset, or {@code null} if no
     * GUI Done has run yet (or {@link #clear} has been called).
     *
     * <p>{@code volatile} so the GUI-thread write is visible to the
     * bootstrap-thread read without locking.</p>
     */
    private static volatile EndPreset current;

    private EndPresetAccess() {
        // Holder only — no instances.
    }

    /**
     * Publishes the user-edited {@link EndPreset}.
     *
     * <p>Called from {@code EndPresetEditorScreen}'s Done button on the
     * client thread. The preset is then read by {@code MixinRandomState}
     * when the End dimension's {@code RandomState} is constructed. A
     * {@code null} argument is permitted and equivalent to {@link #clear}
     * (defensive — the call site never passes null).</p>
     *
     * @param preset the preset to publish, or {@code null} to clear
     */
    public static void set(EndPreset preset) {
        current = preset;
    }

    /**
     * Returns the user-edited preset, or {@code null} if no GUI Done has
     * run yet.
     *
     * <p>Callers must tolerate {@code null}: {@code MixinRandomState}
     * uses {@link #getOrDefault()} for the production path, which falls
     * back to {@link EndPreset#defaults()} when the holder is unconfigured
     * (dedicated server, direct world load, unit test). This raw
     * {@code get()} is exposed for tests that need to distinguish
     * "GUI never ran" from "GUI ran and produced defaults".</p>
     *
     * @return the user-edited preset, or {@code null} if none has been
     *         published
     */
    public static EndPreset get() {
        return current;
    }

    /**
     * Returns the user-edited preset, or {@link EndPreset#defaults()} if
     * no GUI Done has run yet.
     *
     * <p>This is the production-safe accessor: it never returns
     * {@code null}, so {@code MixinRandomState} can use it directly
     * without a null check. The default-value fallback preserves the
     * pre-GUI behaviour for dedicated servers and direct world loads.</p>
     *
     * @return the user-edited preset, or {@link EndPreset#defaults()} if
     *         the holder is unconfigured
     */
    public static EndPreset getOrDefault() {
        EndPreset preset = current;
        return (preset != null) ? preset : EndPreset.defaults();
    }

    /**
     * Drops the published preset.
     *
     * <p>Production code clears the holder from
     * {@code MixinMinecraftServer.endTerraForged$clearPresetHolderOnServerHalt}
     * (inject at RETURN of vanilla's {@code halt(boolean)}) when the
     * server shuts down — this prevents a stale preset from leaking into
     * the next world loaded in the same JVM session. Tests use this
     * method directly to assert the "no GUI ran" / "holder cleared"
     * fallback paths of {@code MixinRandomState} in isolation.</p>
     */
    public static void clear() {
        current = null;
    }
}
