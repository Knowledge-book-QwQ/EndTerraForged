/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Noise (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged.
 *
 * Pure-algorithm adaptation: drops the Codec/Holder/RegistryFileCodec
 * serialisation surface (a stage-3 DFU bridge concern); the compute/min/max
 * contract and the mapAll/Visitor tree-transform surface are preserved so
 * composed modules (Warp, Mul, Clamp, ...) can be added later without
 * touching the interface.
 */
package endterraforged.world.noise;

/**
 * A 2D scalar noise field sampled at world coordinates {@code (x, z)} under a
 * per-call {@code seed}.
 *
 * <p><b>Contract.</b> Every implementation must declare its output range via
 * {@link #minValue()} / {@link #maxValue()} so downstream composers (clamp,
 * map, blend) can normalise without introspecting the concrete type. Output of
 * {@link #compute(float, float, int)} must lie within {@code [minValue,
 * maxValue]} (a tiny epsilon for float drift is acceptable).</p>
 *
 * <p><b>Seed handling.</b> The {@code seed} parameter is part of the contract
 * so a single world seed can drive an entire noise tree. Some modules
 * (e.g. {@link Perlin}) store their own seed for octave offsetting and ignore
 * the passed value — that is faithful to upstream and intentional; callers
 * that need seed sensitivity should construct each module with a derived seed
 * (e.g. {@code seed + GOLDEN_OFFSET}).</p>
 *
 * <p><b>mapAll / Visitor.</b> Preserved from upstream so a composed noise graph
 * can be traversed and rewritten (e.g. insert a {@code Cache2d} at every
 * leaf). A leaf module returns {@code visitor.apply(this)}; a composed module
 * recurses into its children before applying the visitor.</p>
 *
 * <p><b>Thread safety.</b> Implementations should be immutable (records are
 * preferred) and stateless across {@code compute} calls, so the same instance
 * can be queried from multiple chunk-gen threads.</p>
 */
public interface Noise {

    /**
     * Samples the field at {@code (x, z)} under {@code seed}.
     *
     * @return a value in {@code [minValue(), maxValue()]}
     */
    float compute(float x, float z, int seed);

    /** Inclusive lower bound of {@link #compute}'s output. */
    float minValue();

    /** Inclusive upper bound of {@link #compute}'s output. */
    float maxValue();

    /**
     * Recursively rewrites this noise tree by applying {@code visitor} to every
     * node. Leaf modules return {@code visitor.apply(this)}; composed modules
     * recurse into their children first.
     */
    Noise mapAll(Visitor visitor);

    /** A tree-rewrite function applied to each node during {@link #mapAll}. */
    interface Visitor {
        Noise apply(Noise input);
    }
}
