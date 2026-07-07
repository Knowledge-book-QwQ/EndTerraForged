/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Informed by RTF's
 * UpliftRiverCarver placeRiverWater (MIT) — lineage TerraForged (dags) ->
 * ReTerraForged (raccoonman) -> EndTerraForged — but the placement is
 * decoupled from MC block access: this class only computes a normalised
 * water-band contract (WaterInfo) that a stage-5 surface system translates
 * into actual fluid blocks.
 */
package endterraforged.world.river;

import endterraforged.world.heightmap.EndHeightmap;

/**
 * The End's water placer: a pure-logic layer that decides where river water
 * sits and where waterfalls occur, on top of an {@link EndRiverMap}'s carved
 * valleys.
 *
 * <p><b>Decoupling.</b> This class produces a {@link WaterInfo} contract — a
 * normalised {@code [0,1]} water band ({@code waterTop}, {@code waterBottom})
 * plus a waterfall flag — and touches no MC types. The stage-5 surface system
 * translates the band into fluid blocks by filling every column Y between the
 * scaled {@code waterBottom} and {@code waterTop}. Keeping the decision logic
 * here (not in a Feature) lets unit tests pin the water contract without a
 * running chunk generator, mirroring the project's algorithm/MC split.</p>
 *
 * <p><b>Where water goes.</b> Water fills only the bed centre — columns whose
 * {@link EndRiverMap#rivernessAt} is {@code 1} (perpendicular distance within
 * {@code bedWidth}). The carved banks (riverness in {@code (0,1)}) stay dry:
 * this matches RTF's "bed hosts water, valley is dry carved banks" and avoids
 * the wide-lake effect that filling the whole valley would produce. The water
 * surface ({@code waterTop}) is the river's along-reach level from
 * {@link EndRiverMap#waterLevel}; the water floor ({@code waterBottom}) is the
 * post-river-carved terrain height — i.e. water sits in the channel the carver
 * dug.</p>
 *
 * <p><b>Fail-fast contract.</b> {@link #waterInfo} requires the heightmap to
 * have {@code riverMap} attached via {@link EndHeightmap#withRivers} — the
 * water floor is read from {@link EndHeightmap#getHeight}, which only returns
 * the carved bed when the carver is wired in. If {@code riverMap} is absent
 * the placer throws {@link IllegalStateException} rather than silently emitting
 * water that sits on raw terrain (which would float above where the bed should
 * be). Use {@link EndHeightmap#riverMap()} to check before calling.</p>
 *
 * <p><b>Waterfalls.</b> A column is flagged a waterfall when a river-hosting
 * neighbour's water surface sits above the current column's terrain by more
 * than {@link #waterfallDrop}: the neighbour's water would spill over the lip
 * and fall onto this column. Only river-hosting columns (riverness {@code > 0})
 * can be waterfalls — a dry column past the valley edge is just a cliff, not a
 * waterfall. The check samples the four cardinal neighbours at
 * {@link #waterfallStep} blocks so a single block-height step does not false-
 * fire; the step is tuned to span the bed-to-bank transition.</p>
 *
 * <p><b>Thread safety.</b> All fields are immutable primitives; the backing
 * {@link EndRiverMap} and {@link EndHeightmap} are immutable. Safe to query
 * from parallel chunk-gen threads.</p>
 *
 * @param riverMap      the river network providing water level + riverness
 * @param waterfallDrop minimum neighbour-water-above-terrain drop, in
 *                      normalised {@code [0,1]} height units, for a waterfall
 * @param waterfallStep horizontal offset in blocks for the neighbour samples
 *                      used by waterfall detection
 */
public record EndRiverWater(EndRiverMap riverMap, float waterfallDrop, float waterfallStep) {

    /** End-tuned defaults: a 0.5%-of-world-height drop, 16-block neighbour step. */
    public static EndRiverWater defaults(EndRiverMap riverMap) {
        return new EndRiverWater(riverMap, 0.005F, 16.0F);
    }

    /**
     * Water placement at {@code (x, z)}.
     *
     * <p>Returns {@link WaterInfo#NONE} when there is no water here: the column
     * is outside the bed centre (riverness {@code < 1}), has no river in range,
     * is void, or the carved floor has risen to meet the water surface (the
     * reach outlet, where water spills into the void). Otherwise returns a
     * {@link WaterInfo} whose {@code waterTop} is the river's along-reach
     * surface level, {@code waterBottom} is the post-carve terrain height, and
     * {@code isWaterfall} flags whether a neighbour's water spills onto this
     * column.</p>
     *
     * @param x         world X
     * @param z         world Z
     * @param seed      the world seed (must match the heightmap's)
     * @param heightmap the terrain field; must have {@code riverMap} attached
     *                  via {@link EndHeightmap#withRivers} so {@link
     *                  EndHeightmap#getHeight} returns the carved bed floor
     * @return a water band, or {@link WaterInfo#NONE} if no water here
     * @throws IllegalStateException if {@code heightmap.riverMap()} is not this
     *         placer's {@code riverMap} (caller forgot to wire rivers in)
     */
    public WaterInfo waterInfo(float x, float z, int seed, EndHeightmap heightmap) {
        // Fail-fast: the water floor is read from getHeight, which only reflects
        // the carved bed when this riverMap is attached. A mismatched/absent
        // riverMap would silently place water on raw terrain — wrong shape.
        EndRiverMap attached = heightmap.riverMap();
        if (attached != this.riverMap) {
            throw new IllegalStateException(
                    "EndRiverWater.waterInfo requires the heightmap to carry this "
                            + "placer's riverMap via withRivers(); got " + attached
                            + ", expected " + this.riverMap);
        }
        float riverness = riverMap.rivernessAt(x, z, seed, heightmap);
        // Water fills only the bed centre; the carved banks stay dry.
        if (riverness < 1.0F) {
            return WaterInfo.NONE;
        }
        float waterTop = riverMap.waterLevel(x, z, seed, heightmap);
        if (Float.isNaN(waterTop)) {
            return WaterInfo.NONE;
        }
        // The water floor is the carved bed — getHeight runs the river carver
        // (riverMap must be attached to the heightmap). modifyHeight only
        // samples getTerrainHeight/getLandness, so this does not recurse.
        float waterBottom = heightmap.getHeight(x, z, seed);
        if (waterBottom >= waterTop) {
            // At the reach outlet the bed meets the surface (void spill point).
            return WaterInfo.NONE;
        }
        boolean waterfall = isWaterfall(x, z, seed, heightmap);
        return new WaterInfo(waterTop, waterBottom, waterfall);
    }

    /**
     * Whether a river-hosting neighbour's water surface spills onto this column.
     *
     * <p>Returns {@code false} for non-river columns (a dry cliff is not a
     * waterfall). Samples the four cardinal neighbours at {@link #waterfallStep};
     * if any river-hosting neighbour's water level exceeds this column's
     * terrain by more than {@link #waterfallDrop}, water would spill over.</p>
     */
    public boolean isWaterfall(float x, float z, int seed, EndHeightmap heightmap) {
        float riverness = riverMap.rivernessAt(x, z, seed, heightmap);
        if (riverness <= 0.0F) {
            return false;
        }
        float terrain = heightmap.getHeight(x, z, seed);
        float s = waterfallStep;
        // Four cardinal neighbours — diagonals would double-count and track the
        // bed's diagonal extent rather than its outward lip.
        float[][] neighbours = {
                {x + s, z}, {x - s, z}, {x, z + s}, {x, z - s}
        };
        for (float[] n : neighbours) {
            float nRiverness = riverMap.rivernessAt(n[0], n[1], seed, heightmap);
            if (nRiverness <= 0.0F) {
                continue;
            }
            float nWater = riverMap.waterLevel(n[0], n[1], seed, heightmap);
            if (Float.isNaN(nWater)) {
                continue;
            }
            if (nWater > terrain + waterfallDrop) {
                return true;
            }
        }
        return false;
    }

    /**
     * Normalised water band contract for a single column.
     *
     * <p>{@code waterTop} and {@code waterBottom} are in {@code [0,1]} world
     * height units; the stage-5 surface system fills fluid blocks between the
     * scaled Y values. {@link #NONE} (NaN {@code waterTop}) means "no water
     * here". {@code isWaterfall} is a hint that the column receives spilling
     * water from a neighbour — a surface system may extend the water column
     * downward or place a falling-water effect.</p>
     */
    public record WaterInfo(float waterTop, float waterBottom, boolean isWaterfall) {
        /** Sentinel for "no water at this column". */
        public static final WaterInfo NONE = new WaterInfo(Float.NaN, Float.NaN, false);

        /** Whether this info actually places water (non-NaN surface). */
        public boolean hasWater() {
            return !Float.isNaN(waterTop);
        }
    }
}
