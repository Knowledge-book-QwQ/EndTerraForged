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
 * <p><b>When this runs.</b> A Mixin on {@code NoiseChunk} construction
 * (End dimension only) replaces the chunk visitor argument with an ETF-first
 * composite visitor. Vanilla or another mod then invokes {@code mapAll}; it
 * walks the entire density-function tree and calls
 * {@link #apply} on each node, so every placeholder — including those
 * nested inside {@code max}/{@code slide} and other wrappers if the
 * JSON composes them — gets rebound in one pass.</p>
 *
 * <p><b>Floating-island gating.</b> When {@code floatingIslandsField} is
 * {@code null} (the dimension profile has
 * {@code floatingIslandsEnabled == false}), {@link FloatingIslandsFunction}
 * placeholders are left as the stateless {@link FloatingIslandsFunction#INSTANCE}
 * — their {@code compute} returns {@code 0.0}, so the router's {@code max}
 * composition passes the main terrain through unchanged. When non-null,
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
    private final DensityFunction fallbackEndDensity;
    private final DensityFunction.Visitor downstream;
    private DensityFunction mappedFallbackEndDensity;

    public EndDensityVisitor(EndDensity endDensity,
                             FloatingIslandsField floatingIslandsField, int seed) {
        this(endDensity, floatingIslandsField, seed, null, null);
    }

    public EndDensityVisitor(EndDensity endDensity,
                             FloatingIslandsField floatingIslandsField, int seed,
                             DensityFunction fallbackEndDensity) {
        this(endDensity, floatingIslandsField, seed, fallbackEndDensity, null);
    }

    private EndDensityVisitor(EndDensity endDensity,
                              FloatingIslandsField floatingIslandsField, int seed,
                              DensityFunction fallbackEndDensity,
                              DensityFunction.Visitor downstream) {
        this.endDensity = endDensity;
        this.floatingIslandsField = floatingIslandsField;
        this.seed = seed;
        this.fallbackEndDensity = fallbackEndDensity;
        this.downstream = downstream;
    }

    /**
     * Creates a visitor that binds ETF placeholders before delegating to the owning
     * {@code NoiseChunk} visitor.
     *
     * <p>The downstream visitor supplies vanilla's per-chunk interpolation and cache wrappers. The
     * vanilla End fallback is mapped through it exactly once before it is stored in an ETF bound
     * leaf. This makes the binding safe to compose with mods which wrap the outer
     * {@code NoiseRouter.mapAll} invocation themselves.</p>
     */
    public static EndDensityVisitor withChunkVisitor(
            DensityFunction.Visitor downstream,
            EndDensity endDensity,
            FloatingIslandsField floatingIslandsField,
            int seed,
            DensityFunction fallbackEndDensity) {
        if (downstream == null) {
            throw new NullPointerException("downstream");
        }
        return new EndDensityVisitor(
                endDensity, floatingIslandsField, seed, fallbackEndDensity, downstream);
    }

    @Override
    public DensityFunction apply(DensityFunction function) {
        // Main terrain placeholder — rebound to EndTerraForged terrain on
        // success, or to vanilla End final density after a failed bootstrap.
        if (function == EndDensityFunction.INSTANCE
                || function instanceof EndDensityFunction.Bound) {
            DensityFunction fallback = this.mappedFallbackEndDensity();
            if (this.endDensity != null) {
                return this.applyDownstream(
                        new EndDensityFunction.Bound(this.endDensity, this.seed, fallback));
            }
            return fallback;
        }
        // Floating-island placeholder — rebound only when the layer is
        // enabled (field != null). Otherwise leave as INSTANCE (returns 0).
        if (this.floatingIslandsField != null) {
            if (function == FloatingIslandsFunction.INSTANCE
                    || function instanceof FloatingIslandsFunction.Bound) {
                double centralDensityFloor = this.mappedFallbackEndDensity().minValue();
                return this.applyDownstream(new FloatingIslandsFunction.Bound(
                        this.floatingIslandsField, this.seed, centralDensityFloor));
            }
        }
        return this.applyDownstream(function);
    }

    @Override
    public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise) {
        return this.downstream == null ? noise : this.downstream.visitNoise(noise);
    }

    private DensityFunction mappedFallbackEndDensity() {
        if (this.fallbackEndDensity == null) {
            throw new IllegalStateException(
                    "EndTerraForged cannot bind End density without a vanilla final-density fallback");
        }
        if (this.downstream == null) {
            return this.fallbackEndDensity;
        }
        if (this.mappedFallbackEndDensity == null) {
            this.mappedFallbackEndDensity = this.fallbackEndDensity.mapAll(this.downstream);
        }
        return this.mappedFallbackEndDensity;
    }

    private DensityFunction applyDownstream(DensityFunction function) {
        return this.downstream == null ? function : this.downstream.apply(function);
    }
}
