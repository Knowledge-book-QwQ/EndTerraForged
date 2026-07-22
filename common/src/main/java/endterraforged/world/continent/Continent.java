/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). RTF's Continent (MIT)
 * is a CellPopulator coupled to the rivermap/biome layer; EndTerraForged
 * instead models the macro landmass as a Noise so it composes directly into
 * the heightmap via the standard composers (Noises.mul, clamp, ...) and stays
 * decoupled from the Cell carrier until a later stage actually needs per-cell
 * fields.
 */
package endterraforged.world.continent;

import endterraforged.world.noise.Noise;

/**
 * Macro landmass shape: a {@code [0,1]} "landness" field sampled at world
 * {@code (x, z)}.
 *
 * <p>{@code 1} = solid landmass, {@code 0} = void / open space. The EndHeightmap
 * multiplies the mountain height layer by this landness so terrain vanishes
 * where the continent says there is no land — giving floating-island rims and
 * shattered-continent rifts without the heightmap itself branching on topology.</p>
 *
 * <p>Extending {@link Noise} (rather than RTF's {@code CellPopulator}) is the
 * seam that keeps the continent decoupled from {@link endterraforged.world.cell.Cell}
 * and from the rivermap (stage 4). A continent module is just another noise
 * tree node, so it composes with {@code Noises.mul} / {@code clamp} / warp and
 * participates in {@link Noise#mapAll} tree rewrites like every other node.</p>
 *
 * <p><b>Thread safety:</b> implementations are immutable records and safe to
 * query from multiple chunk-gen threads.</p>
 */
public interface Continent extends Noise {

    /**
     * Samples macro-continent diagnostics without allocating a value object.
     *
     * <p>Legacy continent implementations have no separate inland signal, so
     * they preserve their historical terrain strength by reporting full
     * inlandness wherever the caller chooses to consume it.</p>
     */
    default void sampleSignals(float x, float z, int seed, ContinentSignalBuffer output) {
        float landness = compute(x, z, seed);
        output.set(landness, landness, 1.0F);
    }

    /** Materialises an immutable signal snapshot for diagnostics and preview code. */
    default ContinentSignals signalsAt(float x, float z, int seed) {
        ContinentSignalBuffer output = new ContinentSignalBuffer();
        sampleSignals(x, z, seed, output);
        return output.snapshot();
    }

    /**
     * Packs signed cell coordinates into a non-negative, collision-free long
     * id via zigzag encoding.
     *
     * <p>Used to derive a deterministic per-cell identity (e.g. "which island
     * am I on") without the collisions a naive {@code (cx, cz)} int hash would
     * suffer at large coordinates. Mirrors RTF's
     * {@code Continent#getNearestCenter} long identity, but as a pure helper
     * so any continent implementation can use it.</p>
     */
    static long packId(int cx, int cz) {
        return (((long) zigzag(cx)) << 32) | (zigzag(cz) & 0xFFFFFFFFL);
    }

    /** Zigzag-encodes a signed int into a non-negative int (protobuf-style). */
    static int zigzag(int n) {
        return (n << 1) ^ (n >> 31);
    }
}
