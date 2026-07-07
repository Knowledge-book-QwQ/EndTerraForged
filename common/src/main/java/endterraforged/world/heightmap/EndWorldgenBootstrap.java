/*
 * Copyright (c) 2023 ReTerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Stage 6.3 fallback
 * boundary: encapsulates the End worldgen bootstrap sequence (climate →
 * heightmap → density → floating islands) in a try/catch so a failure
 * anywhere in the chain degrades gracefully to "vanilla End generation"
 * rather than crashing world creation.
 */
package endterraforged.world.heightmap;

import java.util.function.BiFunction;
import java.util.function.Function;

import endterraforged.EndTerraForged;
import endterraforged.world.climate.ClimateModulator;
import endterraforged.world.climate.EndClimate;
import endterraforged.world.climate.EndClimateAccess;
import endterraforged.world.config.EndPreset;
import endterraforged.world.floatingislands.FloatingIslandsField;
import endterraforged.world.lake.EndLakeMap;
import endterraforged.world.river.EndRiverMap;

/**
 * Bootstraps the End worldgen field stack for one dimension load, with
 * end-to-end failure fallback.
 *
 * <p><b>The problem.</b> {@code MixinRandomState.<init>} previously ran the
 * construction sequence inline on the bootstrap thread:</p>
 * <pre>
 *   EndClimate climate = EndClimate.defaults(noiseSeed);
 *   EndClimateAccess.set(climate);
 *   EndHeightmap heightmap = new EndHeightmap(profile, noiseSeed)
 *           .withClimate(...).withRivers(...).withLakes(...);
 *   EndDensity endDensity = new EndDensity(heightmap);
 *   if (profile.floatingIslandsEnabled()) {
 *       this.endTerraForged$floatingIslandsField = FloatingIslandsField.defaults();
 *   }
 * </pre>
 * <p>Any exception in this sequence — a malformed {@link EndPreset} that
 * slipped past the codec validators, a noise-tree construction
 * {@link StackOverflowError} on a degenerate seed, an unexpected
 * {@link NullPointerException} from a future refactor — would propagate up
 * through {@code RandomState.create} and crash world creation. The user
 * would be unable to load their world at all.</p>
 *
 * <p><b>The fix.</b> Wrap the sequence in a single try/catch. On failure:</p>
 * <ol>
 *   <li>Log at WARN (the world still loads; only the End's custom terrain
 *       is lost for this load — the next save tick will write a fresh
 *       preset file, and the next load will retry).</li>
 *   <li>Roll back {@link EndClimateAccess} to {@code null} — otherwise a
 *       stale climate from a partially-constructed stack would leak into
 *       the next End load via the process-wide volatile holder.</li>
 *   <li>Return a {@link Result} with {@code degraded == true}, signalling
 *       the caller ({@code MixinRandomState}) to drop the End flag so
 *       {@code MixinNoiseChunk} skips the visitor pass — a
 *       {@code null} {@link EndDensity} would otherwise NPE inside
 *       {@link endterraforged.world.level.levelgen.EndDensityFunction.Bound#compute}
 *       on the first chunk and re-crash worldgen.</li>
 * </ol>
 *
 * <p><b>Why a pure-logic class (not inline in the Mixin).</b> Two reasons:</p>
 * <ol>
 *   <li><b>Testability.</b> {@code MixinRandomState} is a Mixin class — its
 *       bytecode is woven at runtime by the Mixin processor, so its
 *       {@code <init>} inject cannot be exercised by a plain JUnit test.
 *       Pulling the bootstrap into a plain class lets unit tests pin the
 *       contract: success path produces a non-degraded {@link Result},
 *       failure path (injected via the package-private factory overload)
 *       produces a degraded {@link Result} with no exception escaping.</li>
 *   <li><b>Single Responsibility.</b> The Mixin's job is to bridge vanilla's
 *       capture flow into our fields; the bootstrap's job is to assemble
 *       the field stack. Mixing them obscures both.</li>
 * </ol>
 *
 * <p><b>Why {@code catch (Exception)} not {@code catch (Throwable)}.</b>
 * {@link Error} subclasses like {@link OutOfMemoryError} and
 * {@link StackOverflowError} (the realistic ones — deep noise trees can
 * recurse) leave the JVM in an unstable state; catching them risks
 * corrupting further. The bootstrap lets {@link Error} propagate so the
 * JVM can fail fast. {@link Exception} covers all the realistic cases
 * ({@link NullPointerException}, {@link IllegalArgumentException},
 * {@link IllegalStateException}, {@link RuntimeException} from noise
 * nodes, etc.).</p>
 *
 * <p><b>Thread safety.</b> Stateless — the only mutable state touched is
 * {@link EndClimateAccess}, whose {@code volatile set} is safe under the
 * single-writer (bootstrap thread) / many-reader (worker thread)
 * contract documented on that class. The {@link Result} record is
 * immutable.</p>
 */
public final class EndWorldgenBootstrap {

    private EndWorldgenBootstrap() {
        // Static facade only — no instances.
    }

    /**
     * Immutable outcome of a bootstrap attempt.
     *
     * <p>On success: {@link #endDensity} and {@link #floatingIslandsField}
     * carry the constructed fields (the latter may be {@code null} when the
     * profile disabled floating islands — that is a valid success, not a
     * degradation). {@link #degraded} is {@code false}.</p>
     *
     * <p>On failure: both fields are {@code null} and {@link #degraded} is
     * {@code true}. The caller ({@code MixinRandomState}) must drop the
     * End flag in this case so {@code MixinNoiseChunk} skips the visitor
     * pass (a {@code null} {@link EndDensity} would otherwise NPE inside
     * {@link endterraforged.world.level.levelgen.EndDensityFunction.Bound#compute}).</p>
     *
     * @param endDensity            the constructed End density field, or
     *                              {@code null} on degradation
     * @param floatingIslandsField  the constructed floating-island overlay
     *                              field, or {@code null} when the profile
     *                              disabled the layer or on degradation
     * @param degraded              {@code true} iff the bootstrap caught an
     *                              exception and degraded to a null field
     *                              stack; {@code false} on success
     */
    public record Result(EndDensity endDensity,
                         FloatingIslandsField floatingIslandsField,
                         boolean degraded) {

        /**
         * Convenience accessor matching the "did we succeed?" framing.
         * Equivalent to {@code !degraded()}.
         */
        public boolean succeeded() {
            return !this.degraded;
        }
    }

    /**
     * Bootstraps the End worldgen field stack for the given seed + profile.
     *
     * <p>This is the production entry point: it uses the real
     * {@link EndHeightmap} constructor and {@link FloatingIslandsField#defaults()}
     * factory. The package-private
     * {@link #bootstrap(int, EndPreset, BiFunction, Function)} overload
     * exists for tests that need to inject a failing factory.</p>
     *
     * @param seed    the world seed, cast to int to match the noise system's
     *                int-seed convention (high bits discarded, applied
     *                consistently everywhere the seed is used)
     * @param profile the dimension profile (typically from
     *                {@link endterraforged.world.config.EndPresetAccess#getOrDefault()})
     * @return a {@link Result}; never throws
     */
    public static Result bootstrap(int seed, EndPreset profile) {
        return bootstrap(seed, profile,
                (p, s) -> new EndHeightmap(p, s),
                p -> p.floatingIslandsEnabled() ? FloatingIslandsField.defaults() : null);
    }

    /**
     * Package-private overload that accepts factory lambdas, so tests can
     * inject a factory that throws to exercise the failure-fallback path
     * without depending on the (unlikely) event of a real factory failing.
     *
     * <p>The two factories are:</p>
     * <ul>
     *   <li>{@code heightmapFactory} — receives (profile, seed), returns an
     *       {@link EndHeightmap}. The default impl is
     *       {@code (p, s) -> new EndHeightmap(p, s)}.</li>
     *   <li>{@code floatingIslandsFactory} — receives the profile, returns
     *       a {@link FloatingIslandsField} or {@code null} (null = layer
     *       disabled). The default impl queries
     *       {@link EndPreset#floatingIslandsEnabled()} and returns
     *       {@link FloatingIslandsField#defaults()} when enabled.</li>
     * </ul>
     *
     * <p>The factories are called inside the try block — any {@link Exception}
     * they throw is caught and produces a degraded {@link Result}.</p>
     */
    static Result bootstrap(int seed, EndPreset profile,
                            BiFunction<EndPreset, Integer, EndHeightmap> heightmapFactory,
                            Function<EndPreset, FloatingIslandsField> floatingIslandsFactory) {
        try {
            EndClimate climate = EndClimate.defaults(seed);
            // Publish the climate so worker-thread biome lookups
            // (EndBiomeSource#getNoiseBiome) can sample it. If the
            // subsequent heightmap construction fails, the catch block
            // rolls this back to null — otherwise a stale climate from a
            // partially-constructed stack would leak into the next End
            // load via the process-wide volatile holder.
            EndClimateAccess.set(climate);
            EndHeightmap heightmap = heightmapFactory.apply(profile, seed)
                    .withClimate(ClimateModulator.defaults(climate))
                    .withRivers(EndRiverMap.defaults())
                    .withLakes(EndLakeMap.defaults());
            EndDensity endDensity = new EndDensity(heightmap);
            FloatingIslandsField floatingIslandsField = floatingIslandsFactory.apply(profile);
            return new Result(endDensity, floatingIslandsField, false);
        } catch (Exception e) {
            // WARN (not ERROR): the world still loads — the End dimension
            // will be generated by vanilla's pipeline (the placeholder
            // EndDensityFunction.INSTANCE returns 0, so chunks come out as
            // air; biome_source returns vanilla End biomes via fast-path 1
            // because EndClimateAccess is null after the rollback below).
            // The user's edits are lost for this load but the server is
            // not in a broken state; the next save tick writes a fresh
            // preset file, and the next load retries.
            EndTerraForged.LOGGER.warn(
                    "EndTerraForged worldgen init failed; End dimension will fall back to "
                            + "vanilla generation for this load. seed={}, profile={}",
                    seed, profile, e);
            // Roll back any partially-published climate so a stale
            // reference doesn't leak into the next End load (or into
            // EndBiomeSource#getNoiseBiome on this load — climate != null
            // but endDensity == null would be semantically inconsistent).
            EndClimateAccess.set(null);
            return new Result(null, null, true);
        }
    }
}
