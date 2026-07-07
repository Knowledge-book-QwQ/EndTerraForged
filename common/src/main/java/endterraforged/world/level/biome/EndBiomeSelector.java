/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). No RTF equivalent —
 * RTF's biome system rides on vanilla MultiNoiseBiomeSource with TerraBlender;
 * the End uses a geometric EndBiomeSource and never consults climate. This
 * selector adds an optional climate-variant picker on top of the geometric
 * rings, fed by EndTerraForged's own EndClimate field.
 */
package endterraforged.world.level.biome;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import endterraforged.world.climate.EndClimate;

/**
 * Pure-logic four-corner bilinear selector that picks a biome for one
 * geometric ring cell, optionally overriding the ring's {@code base} with a
 * matching {@link BiomeVariant climate variant}.
 *
 * <p><b>Why four corners, not single-point.</b> The vanilla biome API gives
 * the source an integer cell coordinate (1 cell = 4 blocks), not a sub-cell
 * position. Sampling climate once per cell would produce 4-block stairsteps
 * at cell boundaries. Sampling at the cell's four corners and bilinearly
 * blending their candidate biomes yields smooth in-cell transitions — the
 * cell's biome is whichever candidate's reference-weight sum is greatest, so
 * a cell straddling a climate boundary picks the dominant side rather than
 * flipping discretely.</p>
 *
 * <p><b>Two fast paths.</b>
 * <ol>
 *   <li><b>No climate or no variants.</b> If {@code climate == null} (non-End
 *       dimension, or EndClimateAccess not yet published) or the ring slot
 *       has no variants, return {@code ringSlot.base()} immediately — no
 *       climate sampling. This is the default-config path and matches
 *       vanilla performance.</li>
 *   <li><b>Four corners identical.</b> If all four corners' candidate biomes
 *       are the same reference (common when the cell is wholly inside one
 *       climate region), return that reference — no weight math.</li>
 * </ol>
 * Only when the four corners disagree does the slow path run.</p>
 *
 * <p><b>Aggregation by reference identity.</b> Each corner's candidate is
 * summed with the weights of every corner sharing the same biome
 * <em>reference</em> ({@code ==}, not {@code equals()}). This matters for
 * {@code Holder.direct(...)} stubs whose {@code equals()} all collide (their
 * values are null); reference identity is the only sound comparison. The
 * biome with the highest total weight wins; ties break by corner order
 * {@code 00 > 10 > 01 > 11} (deterministic, no hidden priority rules).</p>
 *
 * <p><b>Hot-path allocation profile.</b> The slow path is hand-unrolled: four
 * candidate computations, four weight sums, four comparisons. No
 * {@code Map<Holder, Float>}, no boxed floats, no arrays. The only
 * allocations are the four {@code float} locals (stack slots).</p>
 *
 * <p><b>Thread safety.</b> Stateless — all inputs are immutable references.
 * Safe to call from parallel chunk-gen threads.</p>
 */
public final class EndBiomeSelector {

    private EndBiomeSelector() {
        // Utility class — static entry point only.
    }

    /**
     * Picks the biome for one ring cell.
     *
     * @param cellX    the cell's integer X coordinate (vanilla biome coord)
     * @param cellZ    the cell's integer Z coordinate (vanilla biome coord)
     * @param fracX    sub-cell fractional X in {@code [0, 1]} (0 = west edge,
     *                 1 = east edge); provided by the source's fracNoise
     * @param fracZ    sub-cell fractional Z in {@code [0, 1]} (0 = north edge,
     *                 1 = south edge)
     * @param climate  the End's climate field, or {@code null} if not
     *                 published (non-End dimension / pre-bootstrap)
     * @param seed     per-call seed; vestigial (world seed is baked into the
     *                 climate's noise trees), pass 0
     * @param ringSlot the geometric ring's biome slot (base + variants)
     * @return the selected biome holder (a variant if one matches and wins
     *         the bilinear aggregation, otherwise {@code ringSlot.base()})
     */
    public static Holder<Biome> select(int cellX, int cellZ, float fracX, float fracZ,
                                       EndClimate climate, int seed, BiomeSlot ringSlot) {
        // Fast-path 1: no climate signal or no variants to pick between.
        // This is the default-config path — matches vanilla performance.
        if (climate == null || !ringSlot.hasVariants()) {
            return ringSlot.base();
        }

        // Sample the four cell corners. Each corner's candidate is the first
        // variant whose [tempMin, tempMax] x [moistMin, moistMax] contains
        // the corner's climate sample, or base() if no variant matches.
        Holder<Biome> c00 = candidate(ringSlot, climate, cellX, cellZ, seed);
        Holder<Biome> c10 = candidate(ringSlot, climate, cellX + 1, cellZ, seed);
        Holder<Biome> c01 = candidate(ringSlot, climate, cellX, cellZ + 1, seed);
        Holder<Biome> c11 = candidate(ringSlot, climate, cellX + 1, cellZ + 1, seed);

        // Fast-path 2: all four corners agree on the same reference. Common
        // when the cell is wholly inside one climate region. Skip the weight
        // math — the answer is c00.
        if (c00 == c10 && c00 == c01 && c00 == c11) {
            return c00;
        }

        // Slow path: bilinear weights per corner.
        float w00 = (1.0F - fracX) * (1.0F - fracZ);
        float w10 = fracX * (1.0F - fracZ);
        float w01 = (1.0F - fracX) * fracZ;
        float w11 = fracX * fracZ;

        // For each corner, sum the weights of all corners that share its
        // biome reference. Hand-unrolled to avoid Map<Holder, Float> (which
        // would box floats and break under Holder.direct equals() collision).
        float s00 = sumWeight(c00, c00, c10, c01, c11, w00, w10, w01, w11);
        float s10 = sumWeight(c10, c00, c10, c01, c11, w00, w10, w01, w11);
        float s01 = sumWeight(c01, c00, c10, c01, c11, w00, w10, w01, w11);
        float s11 = sumWeight(c11, c00, c10, c01, c11, w00, w10, w01, w11);

        // Pick the highest total. Strict '>' preserves corner order
        // 00 > 10 > 01 > 11 on ties: a later corner only wins if it strictly
        // exceeds the current best, so equal-weight ties resolve to the
        // earliest corner.
        Holder<Biome> best = c00;
        float bestWeight = s00;
        if (s10 > bestWeight) {
            best = c10;
            bestWeight = s10;
        }
        if (s01 > bestWeight) {
            best = c01;
            bestWeight = s01;
        }
        if (s11 > bestWeight) {
            best = c11;
            // bestWeight = s11;  // last comparison — no need to update
        }
        return best;
    }

    /**
     * Returns the candidate biome at one corner: the first variant whose
     * closed {@code [tempMin, tempMax]} x {@code [moistMin, moistMax]} range
     * contains the corner's climate sample, or {@code slot.base()} if no
     * variant matches. Variant list order is the preset author's precedence.
     */
    private static Holder<Biome> candidate(BiomeSlot slot, EndClimate climate,
                                           int x, int z, int seed) {
        float temp = climate.getTemperature(x, z, seed);
        float moist = climate.getMoisture(x, z, seed);
        for (BiomeVariant variant : slot.variants()) {
            if (variant.matches(temp, moist)) {
                return variant.biome();
            }
        }
        return slot.base();
    }

    /**
     * Sums the weights of every corner whose biome reference is
     * {@code == target}. Reference identity (not {@code equals()}) is
     * required because {@code Holder.direct(null)} stubs all collide under
     * {@code equals()} — see class doc.
     */
    private static float sumWeight(Holder<Biome> target,
                                   Holder<Biome> c00, Holder<Biome> c10,
                                   Holder<Biome> c01, Holder<Biome> c11,
                                   float w00, float w10, float w01, float w11) {
        float sum = 0.0F;
        if (c00 == target) sum += w00;
        if (c10 == target) sum += w10;
        if (c01 == target) sum += w01;
        if (c11 == target) sum += w11;
        return sum;
    }
}
