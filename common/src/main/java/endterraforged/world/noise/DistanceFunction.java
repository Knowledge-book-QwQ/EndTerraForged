/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's DistanceFunction (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged.
 *
 * Pure-algorithm adaptation: drops the StringRepresentable/Codec serialisation
 * surface (a stage-3 bridge concern); the distance metrics are unchanged.
 */
package endterraforged.world.noise;

/**
 * Distance metric used by cell-noise ({@link Worley}, {@link WorleyEdge}) to
 * rank candidate feature points.
 *
 * <p>Each constant is a pure function of the axis-aligned delta from the query
 * point to a cell's feature point. {@link #EUCLIDEAN} returns the squared
 * distance (no {@code sqrt}) — sufficient for ranking and matches upstream
 * byte-for-byte; consumers that need true distance take the root themselves.</p>
 */
public enum DistanceFunction {
    EUCLIDEAN {
        @Override
        public float apply(float x, float y) {
            return x * x + y * y;
        }
    },
    MANHATTAN {
        @Override
        public float apply(float x, float y) {
            return Math.abs(x) + Math.abs(y);
        }
    },
    NATURAL {
        @Override
        public float apply(float x, float y) {
            return Math.abs(x) + Math.abs(y) + (x * x + y * y);
        }
    };

    /** Distance (or squared distance for {@link #EUCLIDEAN}) from {@code (0,0)} to {@code (x,y)}. */
    public abstract float apply(float x, float y);
}
