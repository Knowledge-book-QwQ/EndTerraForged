/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Informed by RTF's
 * late-binding visitor pattern (MIT) — lineage TerraForged (dags) ->
 * ReTerraForged (raccoonman) -> EndTerraForged.
 */
package endterraforged.world.level.levelgen;

import net.minecraft.world.level.levelgen.DensityFunction;

import endterraforged.world.floatingislands.FloatingIslandsField;
import endterraforged.world.heightmap.EndDensity;

/**
 * The {@link DensityFunction.Visitor} that swaps every
 * {@link EndDensityFunction} / {@link FloatingIslandsFunction} placeholder
 * it encounters for a {@link EndDensityFunction.Bound} /
 * {@link FloatingIslandsFunction.Bound} instance carrying the current
 * dimension's live field + world seed.
 *
 * <p><b>When this runs.</b> A stage-3.4 Mixin on {@code NoiseChunk}
 * construction (End dimension only) calls
 * {@code noiseRouter.mapAll(new EndDensityVisitor(...))}. Vanilla's
 * {@code mapAll} walks the entire density-function tree and calls
 * {@link #apply} on each node, so every placeholder — including those
 * nested inside {@code add}/{@code clamp}/{@code slide} wrappers if the
 * JSON composes them — gets rebound in one pass.</p>
 *
 * <p><b>Floating-island gating.</b> When {@code floatingIslandsField} is
 * {@code null} (the dimension profile has
 * {@code floatingIslandsEnabled == false}), {@link FloatingIslandsFunction}
 * placeholders are left as the stateless {@link FloatingIslandsFunction#INSTANCE}
 * — their {@code compute} returns {@code 0.0}, so an add+clamp composition
 * in the JSON passes the main terrain through unchanged. When non-null,
 * placeholders are rebound to {@link FloatingIslandsFunction.Bound}.</p>
 *
 * <p><b>Why a visitor and not direct injection.</b> The
 * {@code NoiseRouter} is a 15-channel tree of density functions loaded
 * from JSON. The only clean way to rewrite one leaf type wherever it
 * appears in that tree is the visitor — vanilla itself uses
 * {@code mapAll} for the same purpose. Mutating channels by hand would
 * miss nested occurrences and break the tree shape.</p>
 *
 * <p><b>Non-targets pass through.</b> Any density function that is not
 * one of our placeholders is returned unchanged, and
 * {@link #visitNoise} returns the noise holder unchanged.</p>
 *
 * <p><b>Thread safety.</b> The visitor is short-lived (one per
 * {@code mapAll} call) and carries only immutable references; safe to
 * use from the chunk-gen thread that owns the {@code NoiseChunk}.</p>
 */
public final class EndDensityVisitor implements DensityFunction.Visitor {

    private final EndDensity endDensity;
    private final FloatingIslandsField floatingIslandsField;
    private final int seed;

    public EndDensityVisitor(EndDensity endDensity,
                             FloatingIslandsField floatingIslandsField, int seed) {
        this.endDensity = endDensity;
        this.floatingIslandsField = floatingIslandsField;
        this.seed = seed;
    }

    @Override
    public DensityFunction apply(DensityFunction function) {
        // Main terrain placeholder — always rebound.
        if (function == EndDensityFunction.INSTANCE
                || function instanceof EndDensityFunction.Bound) {
            return new EndDensityFunction.Bound(this.endDensity, this.seed);
        }
        // Floating-island placeholder — rebound only when the layer is
        // enabled (field != null). Otherwise leave as INSTANCE (returns 0).
        if (this.floatingIslandsField != null) {
            if (function == FloatingIslandsFunction.INSTANCE
                    || function instanceof FloatingIslandsFunction.Bound) {
                return new FloatingIslandsFunction.Bound(this.floatingIslandsField, this.seed);
            }
        }
        return function;
    }

    @Override
    public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise) {
        return noise;
    }
}
