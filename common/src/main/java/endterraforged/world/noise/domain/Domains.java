/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Domains (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged.
 *
 * Only the {@code domain(x, z, distance)} factory is ported — it is the sole
 * constructor the makeMountains recipes use (via Noises.warpPerlin). Upstream's
 * direction/compound/add/direct warp variants are deferred until a consumer
 * needs them, to avoid porting unused surface area.
 */
package endterraforged.world.noise.domain;

import endterraforged.world.noise.Noise;

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
}
