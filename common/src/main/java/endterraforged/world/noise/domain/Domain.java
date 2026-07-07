/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Domain (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged. Pure-algorithm adaptation: Codec dropped.
 */
package endterraforged.world.noise.domain;

import endterraforged.world.noise.Noise;

/**
 * A coordinate-rewriting layer that offsets the {@code (x, z)} passed to a
 * {@link Noise} field, producing domain warping.
 *
 * <p>A {@code Domain} does not itself produce a scalar — it answers "given the
 * caller's {@code (x, z, seed)}, what coordinates should the wrapped noise
 * actually be sampled at?". {@link #getX}/{@link #getZ} return the warped
 * coordinate by adding the offset to the input; {@link #getOffsetX}/{@link
 * #getOffsetZ} return just the delta. Splitting the two lets a caller inspect
 * or compose the offset independently.</p>
 *
 * <p>{@link #mapAll(Noise.Visitor)} mirrors {@link Noise#mapAll} so a tree-wide
 * visitor can rewrite the noise leaves buried inside a {@code Domain} (e.g. the
 * perlin fields driving a {@link DomainWarp}) without the visitor needing to
 * know about the domain layer.</p>
 *
 * <p>Implementations should be immutable and thread-safe.</p>
 */
public interface Domain {

    /** Offset added to {@code x} at {@code (x, z)} under {@code seed}. */
    float getOffsetX(float x, float z, int seed);

    /** Offset added to {@code z} at {@code (x, z)} under {@code seed}. */
    float getOffsetZ(float x, float z, int seed);

    /**
     * Recursively rewrites every {@link Noise} leaf inside this domain by
     * applying {@code visitor}, returning a new domain.
     */
    Domain mapAll(Noise.Visitor visitor);

    /** Warped x coordinate: {@code x + getOffsetX(x, z, seed)}. */
    default float getX(float x, float z, int seed) {
        return x + this.getOffsetX(x, z, seed);
    }

    /** Warped z coordinate: {@code z + getOffsetZ(x, z, seed)}. */
    default float getZ(float x, float z, int seed) {
        return z + this.getOffsetZ(x, z, seed);
    }
}
