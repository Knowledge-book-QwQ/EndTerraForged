/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). The DensityFunction
 * wrapper for {@link FloatingIslandsField} — same two-state placeholder +
 * mapAll late-binding pattern as {@link endterraforged.world.level.levelgen.EndDensityFunction}.
 */
package endterraforged.world.level.levelgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

import endterraforged.world.continent.EndCentralRegionPolicy;
import endterraforged.world.floatingislands.FloatingIslandsField;

/**
 * The vanilla {@link DensityFunction} backing the End's floating-island
 * overlay layer — the bridge from the pure-logic
 * {@link FloatingIslandsField} into vanilla chunk generation.
 *
 * <p><b>Two-state design.</b> Identical to {@link EndDensityFunction}:
 * a stateless {@link #INSTANCE} placeholder produced by DFU (unit codec),
 * rebound at {@code NoiseChunk} construction time by
 * {@link EndDensityVisitor} into a {@link Bound} instance carrying the
 * live {@link FloatingIslandsField} + seed. See
 * {@link EndDensityFunction}'s class doc for the full rationale — only
 * the differences are documented here.</p>
 *
 * <p><b>Difference from EndDensityFunction: combination semantics.</b>
 * The main terrain {@link EndDensityFunction} <em>replaces</em> the
 * channel's value (it IS the {@code final_density}). The floating-island
 * layer must <em>combine</em> with the main terrain: solid where either
 * the main terrain OR an island is solid. In the noise_settings JSON this
 * is expressed by composing the two via a {@code max} node, so the
 * JSON's {@code final_density} looks roughly like:
 * <pre>{@code
 * { "type": "minecraft:max",
 *   "argument1": "endterraforged:end_density",
 *   "argument2": "endterraforged:floating_islands" }
 * }</pre>
 * Both ETF fields are in {@code [0,1]}, so {@code max} is their boolean
 * union without the temporary array allocated by vanilla's add node. In the
 * protected central region, the overlay instead returns vanilla density's
 * lower bound, making {@code max(vanilla, lowerBound)} an exact identity.</p>
 *
 * <p><b>Gating by {@code floatingIslandsEnabled}.</b> When the dimension
 * profile has {@code floatingIslandsEnabled == false}, the
 * {@link EndDensityVisitor} does NOT rebind this placeholder (it checks
 * the flag before binding), so the placeholder's {@code compute} returns
 * {@code 0.0} — the {@code max} node passes the main terrain through
 * unchanged. This keeps a single noise_settings JSON valid for both
 * enabled/disabled modes; the toggle is runtime, not JSON.</p>
 */
public final class FloatingIslandsFunction implements DensityFunction.SimpleFunction {

    public static final String NAME = "floating_islands";

    public static final FloatingIslandsFunction INSTANCE = new FloatingIslandsFunction();

    public static final MapCodec<FloatingIslandsFunction> CODEC = MapCodec.unit(INSTANCE);

    private FloatingIslandsFunction() {
    }

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
     * The late-bound form produced by {@link EndDensityVisitor#apply}.
     * Carries the live {@link FloatingIslandsField} + seed.
     */
    public static final class Bound implements DensityFunction.SimpleFunction {

        private final FloatingIslandsField field;
        private final int seed;
        private final double centralDensityFloor;

        public Bound(FloatingIslandsField field, int seed) {
            this(field, seed, Double.NaN);
        }

        /**
         * Creates an overlay bound to the minimum of the protected vanilla
         * density. This keeps the router's {@code max(main, floating)} node
         * mathematically neutral inside the protected central region.
         */
        public Bound(FloatingIslandsField field, int seed, double centralDensityFloor) {
            this.field = field;
            this.seed = seed;
            this.centralDensityFloor = centralDensityFloor;
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            if (EndCentralRegionPolicy.usesVanillaDensity(context.blockX(), context.blockZ())) {
                return Double.isNaN(this.centralDensityFloor) ? 0.0 : this.centralDensityFloor;
            }
            return this.field.solidity(
                    (float) context.blockX(),
                    context.blockY(),
                    (float) context.blockZ(),
                    this.seed
            );
        }

        @Override
        public double minValue() {
            return Double.isNaN(this.centralDensityFloor)
                    ? 0.0
                    : Math.min(0.0, this.centralDensityFloor);
        }

        @Override
        public double maxValue() {
            return 1.0;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return KeyDispatchDataCodec.of(FloatingIslandsFunction.CODEC);
        }
    }
}
