/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged R9.3.6/R9.6 (MIT):
 * raccoonman.reterraforged.world.worldgen.noise.domain.CompoundWarp
 *
 * EndTerraForged changes:
 * - removed Codec and registry integration
 * - retains only the immutable coordinate-composition primitive
 */
package endterraforged.world.noise.domain;

import java.util.Objects;

import endterraforged.world.noise.Noise;

/**
 * Applies a second domain warp to coordinates already transformed by a first
 * domain warp.
 *
 * <p>This is not equivalent to summing two offsets at the original point:
 * the second warp samples its driver fields at the first warp's transformed
 * coordinates. The distinction is required for RTF's continent warp parity.</p>
 */
public record CompoundWarp(Domain input1, Domain input2) implements Domain {

    public CompoundWarp {
        Objects.requireNonNull(input1, "input1");
        Objects.requireNonNull(input2, "input2");
    }

    @Override
    public float getOffsetX(float x, float z, int seed) {
        float warpedX = this.input1.getX(x, z, seed);
        float warpedZ = this.input1.getZ(x, z, seed);
        return this.input2.getOffsetX(warpedX, warpedZ, seed);
    }

    @Override
    public float getOffsetZ(float x, float z, int seed) {
        float warpedX = this.input1.getX(x, z, seed);
        float warpedZ = this.input1.getZ(x, z, seed);
        return this.input2.getOffsetZ(warpedX, warpedZ, seed);
    }

    @Override
    public Domain mapAll(Noise.Visitor visitor) {
        return new CompoundWarp(this.input1.mapAll(visitor), this.input2.mapAll(visitor));
    }
}
