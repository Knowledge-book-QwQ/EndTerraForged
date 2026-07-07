/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's DomainWarp (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged. Pure-algorithm adaptation: Codec dropped.
 *
 * Architecture note: the upstream DomainWarp calls Noises.map(in, -0.5, 0.5)
 * to centre its driver fields on zero. We inline that as
 * `new Map(in, Constant(-0.5), Constant(0.5))` so this package depends only
 * on the noise leaf types (Noise, Map, Constant) and NOT on the Noises
 * factory — breaking the noise<->domain package cycle that upstream tolerates.
 * Behaviour is identical: the [-0.5,0.5] range check below short-circuits the
 * exact case Noises.map would also short-circuit.
 */
package endterraforged.world.noise.domain;

import endterraforged.world.noise.Constant;
import endterraforged.world.noise.Map;
import endterraforged.world.noise.Noise;

/**
 * Domain warp driven by two independent {@link Noise} fields ({@code x} and
 * {@code z}) scaled by a per-point {@code distance}.
 *
 * <p>This is the engine behind RTF's {@code warpPerlin} — the step that gives
 * the makeMountains ridges their organic, non-grid-aligned flow. Each driver
 * field is first remapped to {@code [-0.5, 0.5]} (so its output is a signed
 * offset centred on zero), then multiplied by {@code distance} to produce the
 * final coordinate delta:</p>
 * <pre>
 *   getOffsetX(x, z, seed) = mappedX.compute(x, z, seed) * distance.compute(x, z, seed)
 * </pre>
 *
 * <p>The remap is skipped when a driver already declares a {@code [-0.5, 0.5]}
 * range, avoiding a redundant {@link Map} node for fields constructed natively
 * in that range.</p>
 *
 * <p>Immutable record, thread-safe (assuming the driver noises are).</p>
 *
 * @param x        driver for the x-axis offset
 * @param z        driver for the z-axis offset
 * @param mappedX  {@code x} remapped to {@code [-0.5, 0.5]} (cached at construction)
 * @param mappedZ  {@code z} remapped to {@code [-0.5, 0.5]} (cached at construction)
 * @param distance per-point scale applied to both offsets
 */
public record DomainWarp(Noise x, Noise z, Noise mappedX, Noise mappedZ, Noise distance) implements Domain {

    /** Convenience constructor that derives {@code mappedX}/{@code mappedZ} from {@code x}/{@code z}. */
    public DomainWarp(Noise x, Noise z, Noise distance) {
        this(x, z, map(x), map(z), distance);
    }

    @Override
    public float getOffsetX(float x, float z, int seed) {
        return this.mappedX.compute(x, z, seed) * this.distance.compute(x, z, seed);
    }

    @Override
    public float getOffsetZ(float x, float z, int seed) {
        return this.mappedZ.compute(x, z, seed) * this.distance.compute(x, z, seed);
    }

    @Override
    public Domain mapAll(Noise.Visitor visitor) {
        return new DomainWarp(
                this.x.mapAll(visitor),
                this.z.mapAll(visitor),
                this.mappedX.mapAll(visitor),
                this.mappedZ.mapAll(visitor),
                this.distance.mapAll(visitor));
    }

    /**
     * Centres {@code in} on zero by remapping its declared range to
     * {@code [-0.5, 0.5]}. Short-circuits when {@code in} is already in that
     * range, returning it untouched.
     */
    private static Noise map(Noise in) {
        if (in.minValue() == -0.5F && in.maxValue() == 0.5F) {
            return in;
        }
        return new Map(in, new Constant(-0.5F), new Constant(0.5F));
    }
}
