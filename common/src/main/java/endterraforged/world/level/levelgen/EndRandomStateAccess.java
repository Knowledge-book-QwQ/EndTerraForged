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

/**
 * Duck-typed accessor injected onto {@code net.minecraft.world.level.
 * levelgen.RandomState} by {@code MixinRandomState}. Vanilla's
 * {@code RandomState} consumes the world seed and the
 * {@code NoiseGeneratorSettings} key in its constructor and exposes
 * neither — but the End's late-binding {@link EndDensityVisitor} needs
 * both to build the per-dimension {@link EndDensity}.
 *
 * <p><b>How this gets populated.</b> {@code MixinRandomState} hooks
 * {@code RandomState.create(Provider, ResourceKey, long)} at
 * {@code @At("HEAD")} to stash the seed + isEnd flag into a
 * {@code ThreadLocal}, then hooks {@code RandomState.<init>} at
 * {@code @At("HEAD")} to consume that stash and (for End dimensions)
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
 * an exception during construction, it returns a {@code degraded} result
 * and {@code MixinRandomState} drops the End flag — so {@code isEnd()}
 * returns {@code false} even for the End dimension, causing
 * {@code MixinNoiseChunk} to skip the {@link EndDensityVisitor} pass.
 * The End dimension then loads with vanilla generation (placeholder
 * {@link EndDensityFunction#INSTANCE} returns {@code 0.0}, so chunks
 * come out as air) rather than crashing world creation.</p>
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
     * <p>Note: stage 6.3 fallback may set this to {@code false} even
     * for an End dimension — when {@link EndWorldgenBootstrap} catches
     * a construction failure, {@code MixinRandomState} drops the End
     * flag so {@code MixinNoiseChunk} skips the visitor pass.
     * Callers that need "this is physically the End dimension" should
     * not rely on this flag — they should check the
     * {@code NoiseGeneratorSettings.END} ResourceKey at
     * {@code RandomState.create} capture time instead.</p>
     */
    boolean endTerraForged$isEnd();

    /**
     * The End's terrain density field, eagerly built from the seed +
     * the dimension profile (typically {@link endterraforged.world.config.EndPresetAccess#getOrDefault()}).
     * {@code null} for non-End dimensions, and also {@code null} for
     * End dimensions that degraded via stage 6.3 fallback.
     */
    EndDensity endTerraForged$getEndDensity();

    /**
     * The End's floating-island overlay field, built when the dimension
     * profile has {@code floatingIslandsEnabled == true}; {@code null}
     * otherwise (and always {@code null} for non-End dimensions and
     * for End dimensions that degraded via stage 6.3 fallback). When
     * {@code null}, {@link EndDensityVisitor} leaves
     * {@link endterraforged.world.level.levelgen.FloatingIslandsFunction}
     * placeholders as the stateless INSTANCE so they contribute {@code 0.0}
     * to the add+clamp composition in {@code noise_settings}.
     */
    FloatingIslandsField endTerraForged$getFloatingIslandsField();
}
