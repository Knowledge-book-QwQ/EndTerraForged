/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). RTF (MIT) models only a
 * continuous continent+ocean; the "shattered supercontinent cut by void rifts"
 * is an End-specific topology, so the shape algorithm is original even though
 * it reuses the ported worley-edge kernel.
 */
package endterraforged.world.continent;

import endterraforged.world.noise.DistanceFunction;
import endterraforged.world.noise.EdgeFunction;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.NoiseMath;
import endterraforged.world.noise.WorleyEdge;
import endterraforged.world.noise.domain.Domain;

/**
 * {@link endterraforged.world.config.TopologyMode#CONTINENTAL_SHATTERED}
 * continent: a continuous supercontinent torn into pieces by a network of void
 * rifts.
 *
 * <p>Landness is {@code 1} everywhere except along warped voronoi cell
 * boundaries, where it drops toward {@code 0} to carve rift channels. Only the
 * strongest boundary-ness (above {@link #riftThreshold}) becomes void, so the
 * rift network is a set of torn segments rather than a closed lattice — that
 * is what reads as "shattered" rather than "checkerboard". The EndHeightmap
 * multiplies the mountain layer by this landness, so mountains vanish in the
 * rifts and the void shows through.</p>
 *
 * <p><b>Faithfulness vs RTF.</b> The dual-nearest cell scan delegates to
 * {@link WorleyEdge#sample}, so feature-point placement and the
 * {@link EdgeFunction#DISTANCE_2} combination are byte-identical to upstream
 * worley-edge. The rift thresholding, the warp-driven organic branching and
 * the landness composition are EndTerraForged originals.</p>
 *
 * @param frequency     rift-cell frequency ({@code 1/scale}); lower = wider, more dramatic rifts
 * @param distance      in-cell feature-point spread in {@code (0,1]}; {@code 1.0} roams the whole cell
 * @param riftThreshold boundary-ness above which a cell becomes void, in {@code [0,1]};
 *                      higher = sparser, narrower rifts. {@code 1} = no rifts (solid continent)
 * @param riftStrength  how far landness drops inside a rift, in {@code [0,1]};
 *                      {@code 1} = full void, {@code 0} = no carving
 * @param warp          coordinate warp applied before the scan, to make rifts branch organically;
 *                      pass {@link endterraforged.world.noise.domain.Domains#identity()} for no warp
 */
public record ContinentalShatteredContinent(float frequency, float distance,
                                            float riftThreshold, float riftStrength,
                                            Domain warp) implements Continent {

    @Override
    public float compute(float x, float z, int seed) {
        // No carving when the threshold clamps out the whole field.
        if (this.riftStrength <= 0.0F || this.riftThreshold >= 1.0F) {
            return 1.0F;
        }

        float wx = this.warp.getX(x, z, seed) * this.frequency;
        float wz = this.warp.getZ(x, z, seed) * this.frequency;

        // DISTANCE_2: nearest2 - 1 (squared), range [-1,1]. Normalised to
        // [0,1]: ~1 at cell centres, ~0 at boundaries.
        float raw = WorleyEdge.sample(wx, wz, seed, this.distance,
                EdgeFunction.DISTANCE_2, DistanceFunction.EUCLIDEAN);
        float centerNess = NoiseMath.map(raw, EdgeFunction.DISTANCE_2.min(),
                EdgeFunction.DISTANCE_2.max(), EdgeFunction.DISTANCE_2.range());

        // Boundary-ness: 0 at centres, ~1 at boundaries — the rift candidate field.
        float boundary = 1.0F - centerNess;

        // Keep only the strongest boundary-ness so the rifts are torn segments,
        // not a closed lattice. Renormalise the surviving band to [0,1].
        float span = 1.0F - this.riftThreshold;
        float rift = span > 0.0F
                ? NoiseMath.clamp((boundary - this.riftThreshold) / span, 0.0F, 1.0F)
                : 0.0F;

        return 1.0F - this.riftStrength * rift;
    }

    @Override
    public float minValue() {
        return 0.0F;
    }

    @Override
    public float maxValue() {
        return 1.0F;
    }

    @Override
    public Noise mapAll(Visitor visitor) {
        return visitor.apply(new ContinentalShatteredContinent(this.frequency, this.distance,
                this.riftThreshold, this.riftStrength, this.warp.mapAll(visitor)));
    }
}
