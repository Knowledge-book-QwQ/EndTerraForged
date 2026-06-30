/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged (MIT) into EndTerraForged (LGPL-3.0-or-later).
 * Lineage: TerraForged (dags) -> ReTerraForged (raccoonman) -> EndTerraForged.
 * Original MIT notice retained per license terms.
 */
package endterraforged.world.cell;

/**
 * Per-cell worldgen state consumed by the heightmap pipeline.
 *
 * <p>This is a deliberately minimal field set covering only what the hydraulic
 * droplet erosion filter (stage 1) reads and writes. Fields for continent,
 * terrain, climate, rivers, etc. will be added incrementally in later stages
 * as each subsystem is ported; keeping the carrier lean now avoids carrying
 * unused state and makes the erosion unit tests self-contained.</p>
 *
 * <p><b>Thread safety:</b> <i>not thread-safe</i>. A {@code Cell} is owned by
 * exactly one tile/chunk and mutated in place by the generator thread that
 * owns that tile. The parallel tile generator hands each thread disjoint
 * {@code Cell[]} backing arrays, so no cross-thread sharing occurs.</p>
 */
public class Cell {
    /** Raw terrain height in normalised {@code [0,1]} units (pre-erosion base). */
    public float height;

    /** Accumulated height change applied by the erosion filter (negative = eroded). */
    public float heightErosion;

    /** Sediment currently carried/dropped by droplets at this cell. */
    public float sediment;

    /**
     * When {@code true}, the erosion filter must not modify this cell. Used to
     * protect riverbeds and other features that should carve their own shape.
     */
    public boolean erosionMask;

    public Cell() {
        reset();
    }

    /** Resets all fields to defaults; returns {@code this} for fluent reuse. */
    public Cell reset() {
        this.height = 0.0F;
        this.heightErosion = 0.0F;
        this.sediment = 0.0F;
        this.erosionMask = false;
        return this;
    }

    /** Copies all fields from {@code other} into {@code this}. */
    public void copyFrom(Cell other) {
        this.height = other.height;
        this.heightErosion = other.heightErosion;
        this.sediment = other.sediment;
        this.erosionMask = other.erosionMask;
    }
}
