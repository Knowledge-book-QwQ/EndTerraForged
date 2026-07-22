/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Interface-injection
 * accessor applied to vanilla's RandomState via Mixin — exposes the world
 * seed and End-dimension flag that RandomState otherwise discards.
 */
package endterraforged.world.level.levelgen;

import endterraforged.world.floatingislands.FloatingIslandsField;
import endterraforged.world.heightmap.EndDensity;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Aquifer;

/**
 * Duck-typed accessor injected onto {@code net.minecraft.world.level.
 * levelgen.RandomState} by {@code MixinRandomState}. Vanilla's
 * {@code RandomState} consumes the world seed and worldgen settings during
 * creation and exposes neither — but the End's late-binding
 * {@link EndDensityVisitor} needs
 * both to build the per-dimension {@link EndDensity}.
 *
 * <p><b>How this gets populated.</b> {@code MixinRandomState} hooks the
 * {@code RandomState.create} overloads to stash the seed, provider and loaded
 * settings into thread-confined capture state, then hooks
 * {@code RandomState.<init>} immediately before its router {@code mapAll}
 * call to consume that state and (for End dimensions)
 * eagerly build the {@link EndDensity} via
 * {@link endterraforged.world.heightmap.EndWorldgenBootstrap}. The
 * profile comes from {@link endterraforged.world.config.EndPresetAccess#getOrDefault()}
 * — the user-edited preset published by the GUI's Done button, or
 * {@link endterraforged.world.config.EndPreset#defaults()} when no GUI
 * has run. Non-End dimensions leave the fields at their defaults
 * ({@code isEnd() == false}, {@code endDensity() == null}), so the
 * {@code NoiseChunk} Mixin's visitor injection is a no-op there.</p>
 *
 * <p><b>Stage 6.3 fallback.</b> If {@link EndWorldgenBootstrap} catches
 * an exception during construction, it returns a {@code degraded} result.
 * {@code MixinRandomState} then builds vanilla's End {@code finalDensity}
 * from the density-function registry. {@code MixinNoiseChunk} keeps the End
 * visitor enabled and replaces the EndTerraForged placeholder with that
 * vanilla fallback, so a bad preset or bootstrap failure does not turn the
 * dimension into empty chunks.</p>
 *
 * <p><b>Why interface injection and not a side-map.</b> A
 * {@code WeakHashMap<RandomState, ...>} would work but leaks if the
 * world is reloaded often; interface-injected {@code @Unique} fields GC
 * with the instance, and is the idiomatic Mixin pattern (RTF and most
 * worldgen mods use it).</p>
 *
 * <p><b>Thread safety.</b> Populated once at {@code RandomState.create}
 * (single-threaded bootstrap) and read from chunk-gen threads
 * thereafter; the {@link EndDensity} is immutable, so unsynchronized
 * reads are safe.</p>
 */
public interface EndRandomStateAccess {

    /** The world seed, captured at {@code RandomState.create}. */
    long endTerraForged$getSeed();

    /**
     * Whether this RandomState is for the End dimension.
     *
     * <p>The flag means this RandomState contains ETF's End density
     * placeholder. A degraded bootstrap keeps it true so
     * {@code MixinNoiseChunk} can replace that placeholder with vanilla End
     * final density. Callers that need a physical-dimension identity should
     * still use the {@code NoiseGeneratorSettings.END} ResourceKey at
     * {@code RandomState.create} capture time.</p>
     */
    boolean endTerraForged$isEnd();

    /**
     * The End's terrain density field, eagerly built from the seed +
     * the dimension profile (typically {@link endterraforged.world.config.EndPresetAccess#getOrDefault()}).
     * {@code null} for non-End dimensions, and also {@code null} for
     * End dimensions that degraded via stage 6.3 fallback.
     */
    EndDensity endTerraForged$getEndDensity();

    /** Dimension-bound exterior-ocean picker, or {@code null} outside ETF's End. */
    Aquifer.FluidPicker endTerraForged$getFluidPicker();

    /**
     * Vanilla End {@code finalDensity} retained for the central protected
     * region and for EndTerraForged bootstrap degradation. {@code null} for
     * non-End states only: ETF rejects an End construction that cannot build
     * this fallback instead of risking central-region corruption.
     */
    DensityFunction endTerraForged$getFallbackEndDensity();

    /**
     * The End's floating-island overlay field, built when the dimension
     * profile has {@code floatingIslandsEnabled == true}; {@code null}
     * otherwise (and always {@code null} for non-End dimensions and
     * for End dimensions that degraded via stage 6.3 fallback). When
     * {@code null}, {@link EndDensityVisitor} leaves
     * {@link endterraforged.world.level.levelgen.FloatingIslandsFunction}
     * placeholders as the stateless INSTANCE so they contribute {@code 0.0}
     * to the {@code max} composition in {@code noise_settings}.
     */
    FloatingIslandsField endTerraForged$getFloatingIslandsField();
}
