/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's EdgeFunction (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged.
 *
 * Pure-algorithm adaptation: drops the StringRepresentable/Codec serialisation
 * surface; the edge transforms and their min/max/range bounds are unchanged.
 */
package endterraforged.world.noise;

/**
 * Edge transform applied by {@link WorleyEdge}: combines the nearest and
 * second-nearest cell feature-point distances into a ridge/edge value.
 *
 * <p>Each constant reports the {@code [min, max]} and {@code range} of its
 * output so {@link WorleyEdge} can normalise the result into {@code [0,1]}.
 * The {@code -1.0F} offset common to every variant shifts the raw distance
 * combination so its midpoint sits at 0 — preserved verbatim from upstream.</p>
 */
public enum EdgeFunction {
    /** {@code d2 - 1}: peaks along cell boundaries, the staple for shattered ridges. */
    DISTANCE_2 {
        @Override
        public float apply(float distance, float distance2) {
            return distance2 - 1.0F;
        }

        @Override
        public float max() {
            return 1.0F;
        }

        @Override
        public float min() {
            return -1.0F;
        }

        @Override
        public float range() {
            return 2.0F;
        }
    },
    /** {@code d2 + d - 1}: wider boundary bands than {@link #DISTANCE_2}. */
    DISTANCE_2_ADD {
        @Override
        public float apply(float distance, float distance2) {
            return distance2 + distance - 1.0F;
        }

        @Override
        public float max() {
            return 1.6F;
        }

        @Override
        public float min() {
            return -1.0F;
        }

        @Override
        public float range() {
            return 2.6F;
        }
    },
    /** {@code d2 - d - 1}: the inter-cell gap, sharper than {@link #DISTANCE_2}. */
    DISTANCE_2_SUB {
        @Override
        public float apply(float distance, float distance2) {
            return distance2 - distance - 1.0F;
        }

        @Override
        public float max() {
            return 0.8F;
        }

        @Override
        public float min() {
            return -1.0F;
        }

        @Override
        public float range() {
            return 1.8F;
        }
    },
    /** {@code d2 * d - 1}: product of the two distances, emphasises mid-cell edges. */
    DISTANCE_2_MUL {
        @Override
        public float apply(float distance, float distance2) {
            return distance2 * distance - 1.0F;
        }

        @Override
        public float max() {
            return 0.7F;
        }

        @Override
        public float min() {
            return -1.0F;
        }

        @Override
        public float range() {
            return 1.7F;
        }
    },
    /** {@code d / d2 - 1}: ratio, collapses to 0 at cell centres — used for volcano cones. */
    DISTANCE_2_DIV {
        @Override
        public float apply(float distance, float distance2) {
            return distance / distance2 - 1.0F;
        }

        @Override
        public float max() {
            return 0.0F;
        }

        @Override
        public float min() {
            return -1.0F;
        }

        @Override
        public float range() {
            return 1.0F;
        }
    };

    /**
     * Combines the nearest ({@code distance}) and second-nearest
     * ({@code distance2}) feature-point distances into a raw edge value.
     */
    public abstract float apply(float distance, float distance2);

    /** Inclusive upper bound of {@link #apply}'s output, for normalisation. */
    public abstract float max();

    /** Inclusive lower bound of {@link #apply}'s output, for normalisation. */
    public abstract float min();

    /** {@code max() - min()}; the divisor used to normalise into {@code [0,1]}. */
    public abstract float range();
}
