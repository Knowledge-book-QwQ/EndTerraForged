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

import endterraforged.world.heightmap.EndDensity;

/**
 * The {@link DensityFunction.Visitor} that swaps every
 * {@link EndDensityFunction} placeholder it encounters for a
 * {@link EndDensityFunction.Bound} instance carrying the current
 * dimension's {@link EndDensity} + world seed.
 *
 * <p><b>When this runs.</b> A stage-3.4 Mixin on {@code NoiseChunk}
 * construction (End dimension only) calls
 * {@code noiseRouter.mapAll(new EndDensityVisitor(endDensity, seed))}.
 * Vanilla's {@code mapAll} walks the entire density-function tree and
 * calls {@link #apply} on each node, so every placeholder — including
 * those nested inside {@code y_clamped_gradient}/{@code slide} wrappers
 * if the JSON composes them — gets rebound in one pass.</p>
 *
 * <p><b>Why a visitor and not direct injection.</b> The
 * {@code NoiseRouter} is a 15-channel tree of density functions loaded
 * from JSON. The only clean way to rewrite one leaf type wherever it
 * appears in that tree is the visitor — vanilla itself uses
 * {@code mapAll} for the same purpose (e.g. wrapping functions with
 * {@code shift} / caching). Mutating channels by hand would miss nested
 * occurrences and break the tree shape.</p>
 *
 * <p><b>Non-targets pass through.</b> Any density function that is not
 * our placeholder is returned unchanged, and {@link #visitNoise} returns
 * the noise holder unchanged — we only rebind our own type, we do not
 * touch vanilla noise nodes.</p>
 *
 * <p><b>Thread safety.</b> The visitor is short-lived (one per
 * {@code mapAll} call) and carries only immutable references; safe to
 * use from the chunk-gen thread that owns the {@code NoiseChunk}.</p>
 */
public final class EndDensityVisitor implements DensityFunction.Visitor {

    private final EndDensity endDensity;
    private final int seed;

    public EndDensityVisitor(EndDensity endDensity, int seed) {
        this.endDensity = endDensity;
        this.seed = seed;
    }

    @Override
    public DensityFunction apply(DensityFunction function) {
        if (function == EndDensityFunction.INSTANCE) {
            return new EndDensityFunction.Bound(this.endDensity, this.seed);
        }
        // A Bound instance may appear if the router was already mapAll'd
        // (e.g. re-binding during a re-route). Rebind to the new
        // endDensity/seed so the visitor is idempotent under repeated
        // application — important because vanilla may wrap a router with
        // additional visitors (e.g. for caching) after ours runs.
        if (function instanceof EndDensityFunction.Bound) {
            return new EndDensityFunction.Bound(this.endDensity, this.seed);
        }
        return function;
    }

    @Override
    public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise) {
        return noise;
    }
}
