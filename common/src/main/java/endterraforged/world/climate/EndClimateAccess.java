/*
 * Copyright (c) 2023 ReTerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Process-wide holder
 * for the active {@link EndClimate} so the climate field can flow from the
 * bootstrap thread (where {@link net.minecraft.world.level.levelgen.RandomState}
 * is constructed) to the worker threads that run
 * {@link endterraforged.world.level.biome.EndBiomeSource#getNoiseBiome}.
 */
package endterraforged.world.climate;

/**
 * Process-wide holder for the active {@link EndClimate}.
 *
 * <p><b>The problem.</b> {@link EndClimate} is constructed once per dimension
 * load, inside {@code MixinRandomState}'s capture of vanilla's
 * {@link net.minecraft.world.level.levelgen.RandomState} factory. That
 * construction runs on the bootstrap / server thread. The biome source's
 * {@code getNoiseBiome} — the consumer of the climate field — runs on
 * chunk-generation worker threads, which have no handle to the
 * {@code RandomState} that created them. A static holder is the simplest
 * bridge that does not require plumbing a new parameter through vanilla's
 * biome-source call chain.</p>
 *
 * <p><b>Thread-safety.</b> The {@code current} field is {@code volatile}.
 * {@link EndClimate} is an immutable {@code record} whose {@link endterraforged.world.noise.Noise}
 * components are themselves immutable and stateless across {@code compute}
 * calls, so publishing the reference through a {@code volatile} write
 * establishes a happens-before edge that makes the entire immutable graph
 * visible to the worker threads that read it. There is exactly one writer
 * (the bootstrap thread, on dimension load) and many readers (worker
 * threads), so the volatile publish pattern is correct without further
 * synchronisation.</p>
 *
 * <p><b>Lifecycle.</b> {@link #set} is called from {@code MixinRandomState}
 * when an End dimension is loaded; {@link #get} returns {@code null} before
 * the first End load and after {@link #clear}. {@link #clear} is exposed
 * for tests that need to assert the "no climate configured" fast path of
 * the biome selector, and production clears it from
 * {@code MixinMinecraftServer.endTerraForged$clearRuntimeHoldersOnServerHalt}
 * so integrated-server world switches cannot observe stale climate state.</p>
 *
 * <p><b>Why not inject the climate through EndRandomStateAccess.</b> The
 * interface-injected {@code EndRandomStateAccess} on {@code RandomState}
 * already exposes the seed, the End flag, and the {@code EndDensity}. But
 * the biome source is constructed by DFU codec decode and does not receive
 * the {@code RandomState} — vanilla's biome-source factory only hands it
 * the {@code BiomeResolver} context, which has no climate handle. A static
 * holder sidesteps the codec boundary without polluting the
 * {@code BiomeSource} record's serialisation surface.</p>
 */
public final class EndClimateAccess {

    /**
     * The currently active End climate, or {@code null} if no End dimension
     * has been loaded (or {@link #clear} has been called).
     *
     * <p>{@code volatile} so the bootstrap-thread write is visible to
     * chunk-gen worker threads without locking.</p>
     */
    private static volatile EndClimate current;

    private EndClimateAccess() {
        // Holder only — no instances.
    }

    /**
     * Publishes the active {@link EndClimate}.
     *
     * <p>Called from {@code MixinRandomState} on the bootstrap thread when an
     * End dimension is loaded. A {@code null} argument is permitted and
     * equivalent to {@link #clear} (e.g. for a non-End dimension, although
     * the call site already gates on {@code isEnd}).</p>
     *
     * @param climate the climate to publish, or {@code null} to clear
     */
    public static void set(EndClimate climate) {
        current = climate;
    }

    /**
     * Returns the active {@link EndClimate}, or {@code null} if no End
     * dimension is currently loaded.
     *
     * <p>Callers must tolerate {@code null}: the biome selector's fast-path
     * treats a null climate as "no climate signal" and falls back to the
     * ring's base biome, preserving vanilla behaviour when the holder is
     * unconfigured (e.g. in unit tests that exercise the selector directly).</p>
     *
     * @return the active climate, or {@code null} if none has been published
     */
    public static EndClimate get() {
        return current;
    }

    /**
     * Drops the published climate.
     *
     * <p>Used by unit tests that need to assert the "no climate configured"
     * fast path of the biome selector in isolation, and by the server halt
     * hook to prevent stale runtime state from leaking into the next world
     * loaded in the same JVM session.</p>
     */
    public static void clear() {
        current = null;
    }
}
