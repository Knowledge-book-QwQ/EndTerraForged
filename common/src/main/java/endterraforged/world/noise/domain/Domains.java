/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Domains (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged.
 *
 * The continent path additionally uses RTF's perlin/simplex factory helpers
 * and composite warp. Direction/add/direct variants remain deferred until a
 * consumer needs them.
 */
package endterraforged.world.noise.domain;

import endterraforged.world.noise.Noise;
import endterraforged.world.noise.Noises;

/**
 * Factory for {@link Domain} instances.
 *
 * <p>Static, stateless, thread-safe.</p>
 */
public final class Domains {

    private Domains() {
    }

    /**
     * Builds a {@link DomainWarp} from two driver noises and a per-point
     * distance. The drivers are remapped to {@code [-0.5, 0.5]} internally so
     * their output becomes a signed coordinate offset.
     *
     * @param x        driver for the x-axis offset
     * @param z        driver for the z-axis offset
     * @param distance per-point scale applied to both offsets
     */
    public static Domain domain(Noise x, Noise z, Noise distance) {
        return new DomainWarp(x, z, distance);
    }

    /**
     * Builds RTF's Perlin-driven warp using consecutive seed slots for its two
     * axes.
     */
    public static Domain domainPerlin(int seed, int scale, int octaves, float strength) {
        return domain(
                Noises.perlin(seed, scale, octaves),
                Noises.perlin(seed + 1, scale, octaves),
                Noises.constant(strength));
    }

    /**
     * Builds RTF's Simplex-driven warp using consecutive seed slots for its two
     * axes.
     */
    public static Domain domainSimplex(int seed, int scale, int octaves, float strength) {
        return domain(
                Noises.simplex(seed, scale, octaves),
                Noises.simplex(seed + 1, scale, octaves),
                Noises.constant(strength));
    }

    /** Composes two coordinate transforms using the first transform's output as the second input. */
    public static Domain compound(Domain input1, Domain input2) {
        return new CompoundWarp(input1, input2);
    }

    /**
     * Returns a domain that applies no offset — the identity coordinate
     * transform. Used by consumers (e.g. continent modules) that accept an
     * injected {@link Domain} but need a no-warp default without paying for
     * three {@code Constant(0)} samples per query.
     */
    public static Domain identity() {
        return Identity.INSTANCE;
    }

    /**
     * Singleton zero-offset domain. {@code mapAll} returns itself unchanged
     * since there are no child noises to recurse.
     */
    private enum Identity implements Domain {
        INSTANCE;

        @Override
        public float getOffsetX(float x, float z, int seed) {
            return 0.0F;
        }

        @Override
        public float getOffsetZ(float x, float z, int seed) {
            return 0.0F;
        }

        @Override
        public Domain mapAll(Noise.Visitor visitor) {
            return this;
        }
    }
}
