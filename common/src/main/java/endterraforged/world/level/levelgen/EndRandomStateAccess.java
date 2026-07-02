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
 * {@code @At("RETURN")}: if the {@code ResourceKey} equals
 * {@code NoiseGeneratorSettings.END}, it stores the seed and lazily
 * builds the {@link EndDensity} (from {@code EndHeightmap} with that
 * seed + the default {@code DimensionProfile}), then exposes them via
 * this interface. Non-End dimensions leave the fields at their defaults
 * ({@code isEnd() == false}, {@code endDensity() == null}), so the
 * {@code NoiseChunk} Mixin's visitor injection is a no-op there.</p>
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

    /** Whether this RandomState is for the End dimension. */
    boolean endTerraForged$isEnd();

    /**
     * The End's terrain density field, lazily built from the seed +
     * default {@link endterraforged.world.config.DimensionProfile}.
     * {@code null} for non-End dimensions.
     */
    EndDensity endTerraForged$getEndDensity();

    /**
     * The End's floating-island overlay field, built when the dimension
     * profile has {@code floatingIslandsEnabled == true}; {@code null}
     * otherwise (and always {@code null} for non-End dimensions). When
     * {@code null}, {@link EndDensityVisitor} leaves
     * {@link endterraforged.world.level.levelgen.FloatingIslandsFunction}
     * placeholders as the stateless INSTANCE so they contribute {@code 0.0}
     * to the add+clamp composition in {@code noise_settings}.
     */
    FloatingIslandsField endTerraForged$getFloatingIslandsField();
}
