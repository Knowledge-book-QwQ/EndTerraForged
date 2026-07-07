/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Informed by RTF's
 * HeightmapFunction (MIT) — lineage TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged — but the binding model is adapted to
 * vanilla 1.21.1's DensityFunction API (no contextual()/mapPartial()).
 */
package endterraforged.world.level.levelgen;

import java.util.concurrent.atomic.AtomicBoolean;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

import endterraforged.EndTerraForged;
import endterraforged.world.heightmap.EndDensity;

/**
 * The vanilla {@link DensityFunction} that backs the End's
 * {@code final_density} channel — the bridge from the pure-logic
 * {@link EndDensity} terrain field into vanilla chunk generation.
 *
 * <p><b>Two-state design (RTF pattern, adapted to 1.21.1).</b> Vanilla
 * worldgen loads {@code noise_settings} JSON through DFU, which serialises
 * density functions by codec. Our codec is a {@link MapCodec#unit unit}
 * codec — the JSON only says {@code {"type":"endterraforged:end_density"}}
 * with no parameters, and DFU produces the stateless {@link #INSTANCE}
 * placeholder. The placeholder's {@link #compute} returns {@code 0.0}
 * (all void) — it is <em>never queried as-is</em> during real chunk gen.</p>
 *
 * <p>Later, when vanilla builds a {@code NoiseChunk} for the End dimension,
 * a stage-3.4 Mixin calls {@code noiseRouter.mapAll(visitor)} with an
 * {@link endterraforged.world.level.levelgen.EndDensityVisitor} that holds
 * the dimension's {@link EndDensity} (built from seed + DimensionProfile).
 * The visitor's {@code apply} swaps every placeholder it encounters for a
 * {@link Bound} instance that carries the live {@link EndDensity} + seed.
 * From then on vanilla queries the {@link Bound#compute}, which delegates
 * to {@link EndDensity#density(float, int, float, int)}.</p>
 *
 * <p><b>Why not inject the heightmap via the codec?</b> The heightmap
 * depends on the world seed and the dimension profile, neither of which is
 * available at DFU deserialisation time — only at {@code NoiseChunk}
 * construction. RTF solves this the same way: stateless codec-bound
 * placeholder + late binding via {@code mapAll} at chunk-build time.</p>
 *
 * <p><b>Thread safety.</b> {@link #INSTANCE} is stateless. {@link Bound}
 * holds an immutable {@link EndDensity} (backed by an immutable
 * {@link endterraforged.world.heightmap.EndHeightmap}); safe to query from
 * parallel chunk-gen threads. Each chunk gets its own {@code mapAll}-bound
 * router, so there is no shared mutable state.</p>
 *
 * <p><b>Range.</b> {@link EndDensity#density} returns {@code 0.0} (void) or
 * {@code 1.0} (solid); {@link #minValue}/{@link #maxValue} mirror that.
 * Surface smoothing is left to vanilla's NoiseRouter interpolation, as in
 * the overworld — this function is the discrete solid/void decision only.</p>
 *
 * <p><b>Stage 6.3 sampling-time fallback.</b> {@link Bound#compute} wraps
 * the {@link EndDensity#density} call in {@code try (Exception) catch}. Under
 * normal operation (EndDensity constructed successfully via
 * {@link endterraforged.world.heightmap.EndWorldgenBootstrap}, noise trees
 * validated), {@code density()} does not throw — the catch is defensive.
 * The realistic throw sources are: (1) a noise node's bounds bug that
 * only manifests on specific coords (AIOOBE, not caught during
 * construction); (2) a future refactor of {@link EndDensity} /
 * {@link endterraforged.world.heightmap.EndHeightmap} that introduces an
 * NPE on edge-case coords. Without the catch, such a failure would
 * propagate up through vanilla's column generation and crash the
 * chunk-gen task for the failing chunk (and potentially cascade to
 * neighbours). On catch: log WARN with coords + exception (gated by
 * {@link #FIRST_SAMPLING_FAILURE_LOGGED} so only the first failure per
 * JVM session is logged — {@code compute} is a hot path, millions of
 * calls per chunk, and persistent failures would flood the log), then
 * return {@code 0.0} (void). The block becomes air; the rest of the
 * chunk generates normally. Consistent with the stage 6.3 layered
 * fallback philosophy: construction failure degrades the whole End
 * dimension to vanilla; binding failure (MixinNoiseChunk redirect)
 * degrades one chunk to placeholder; sampling failure degrades one
 * block to void.</p>
 */
public final class EndDensityFunction implements DensityFunction.SimpleFunction {

    /** Registry id under which the codec is published ({@code endterraforged:end_density}). */
    public static final String NAME = "end_density";

    /** Stateless placeholder produced by DFU deserialisation. */
    public static final EndDensityFunction INSTANCE = new EndDensityFunction();

    /** Unit codec — JSON {@code {"type":"endterraforged:end_density"}} yields {@link #INSTANCE}. */
    public static final MapCodec<EndDensityFunction> CODEC = MapCodec.unit(INSTANCE);

    /**
     * Gate for {@link Bound#compute}'s first-failure log. {@code compute}
     * is a hot path (millions of calls per chunk); a persistent sampling
     * failure would flood the log if every failure logged. This gate
     * ensures only the first failure per JVM session is logged — enough
     * for a dev to diagnose, without spamming the user. Atomic because
     * {@code compute} is called from parallel chunk-gen threads.
     */
    private static final AtomicBoolean FIRST_SAMPLING_FAILURE_LOGGED = new AtomicBoolean(false);

    private EndDensityFunction() {
    }

    /**
     * Placeholder compute: returns {@code 0.0} (all void). Real chunk gen
     * never reaches here — the stage-3.4 Mixin's {@code mapAll} replaces
     * this placeholder with a {@link Bound} instance before any sampling.
     * Returning 0 (rather than throwing) keeps the placeholder safe to
     * sample in isolation (e.g. in pre-worldgen codec round-trips).
     */
    @Override
    public double compute(DensityFunction.FunctionContext context) {
        return 0.0;
    }

    @Override
    public double minValue() {
        return 0.0;
    }

    @Override
    public double maxValue() {
        return 1.0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return KeyDispatchDataCodec.of(CODEC);
    }

    /**
     * The late-bound form produced by {@link EndDensityVisitor#apply} during
     * {@code NoiseChunk} construction. Carries the live {@link EndDensity}
     * and world seed; {@link #compute} samples the terrain solidity field
     * at the context's block coordinates.
     *
     * <p>This class has no codec — it is constructed in code by the visitor
     * only, never serialised. Vanilla's {@link DensityFunctions} uses the
     * same pattern for runtime-bound functions (e.g. {@code EndIslandDensityFunction}
     * is built from a seed and registered as a constant, not via DFU).</p>
     */
    public static final class Bound implements DensityFunction.SimpleFunction {

        private final EndDensity endDensity;
        private final int seed;

        public Bound(EndDensity endDensity, int seed) {
            this.endDensity = endDensity;
            this.seed = seed;
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            // FunctionContext exposes block-aligned world coords. EndDensity
            // works in normalised space internally; the (x, worldY, z, seed)
            // signature is exactly what it expects.
            //
            // Stage 6.3 sampling-time fallback: under normal operation
            // EndDensity.density() does not throw (noise trees were built
            // and validated by EndWorldgenBootstrap at construction). The
            // try/catch is defensive — see the class-level "Stage 6.3
            // sampling-time fallback" Javadoc for the full rationale.
            try {
                return this.endDensity.density(
                        (float) context.blockX(),
                        context.blockY(),
                        (float) context.blockZ(),
                        this.seed
                );
            } catch (Exception e) {
                // First-failure log only — compute() is a hot path
                // (millions of calls per chunk). The AtomicBoolean gate
                // ensures only the first sampling failure per JVM
                // session is logged. Persistent failures would flood
                // the log otherwise. The dev can clear the flag
                // (reflectively) for re-diagnosis if needed.
                if (FIRST_SAMPLING_FAILURE_LOGGED.compareAndSet(false, true)) {
                    EndTerraForged.LOGGER.warn(
                            "EndTerraForged EndDensity sampling threw at "
                                    + "block ({}, {}, {}); returning void (0.0) for "
                                    + "this block. Further sampling failures will "
                                    + "be suppressed for this JVM session. seed={}",
                            context.blockX(), context.blockY(), context.blockZ(),
                            this.seed, e);
                }
                // Return void — the block becomes air. The rest of the
                // chunk generates normally; neighbour chunks are
                // independent. This matches the placeholder INSTANCE's
                // compute() return (0.0) so the behaviour is consistent
                // with "EndDensity not bound for this chunk".
                return 0.0;
            }
        }

        @Override
        public double minValue() {
            return 0.0;
        }

        @Override
        public double maxValue() {
            return 1.0;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            // Bound instances are never serialised — they exist only in the
            // mapAll'd router used for the current chunk. Returning the
            // placeholder's codec keeps the KeyDispatchDataCodec contract
            // satisfied if vanilla ever asks (it shouldn't post-mapAll).
            // NB: qualify as EndDensityFunction.CODEC — the bare CODEC would
            // resolve to DensityFunction.CODEC (inherited via SimpleFunction)
            // and shadow this placeholder's MapCodec.
            return KeyDispatchDataCodec.of(EndDensityFunction.CODEC);
        }
    }
}
