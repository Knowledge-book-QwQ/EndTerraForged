package endterraforged.world.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;

import endterraforged.world.climate.ClimateModulator;
import endterraforged.world.climate.ClimatePredicate;
import endterraforged.world.climate.EndClimate;
import endterraforged.world.climate.EndClimateAccess;
import endterraforged.world.config.TestProfile;
import endterraforged.world.heightmap.EndHeightmap;
import endterraforged.world.lake.EndLakeMap;
import endterraforged.world.level.biome.BiomeSlot;
import endterraforged.world.level.biome.BiomeVariant;
import endterraforged.world.level.biome.EndBiomeSelector;
import endterraforged.world.river.EndRiverMap;

/**
 * Lightweight micro-benchmarks for the EndTerraForged worldgen hot paths.
 *
 * <p><b>Why not JMH.</b> JMH adds a heavy build dependency and a separate
 * runner for what is — at this stage — a smoke-test level assurance that the
 * hot paths are not catastrophically slow. These benchmarks use a hand-rolled
 * loop with {@link #WARMUP} iterations followed by {@link #MEASURE} measured
 * iterations, which is sufficient to spot order-of-magnitude regressions
 * (the threshold at which a perf issue becomes a real gameplay problem).</p>
 *
 * <p><b>Iteration counts.</b> {@code 5000} warmup + {@code 50000} measure is
 * a sweet spot: long enough for the JIT to settle and for the timer noise to
 * average out, short enough to finish inside the IDE test runner without
 * timing out (each benchmark takes ~50-150 ms wall-clock on a modern CPU).</p>
 *
 * <p><b>DCE guard.</b> Each benchmark accumulates a {@code long checksum}
 * from the operation's result and asserts it is non-zero at the end. Without
 * this guard the JIT could prove the loop body has no observable effect and
 * delete it entirely (dead-code elimination), reporting absurdly fast times.
 * The checksum is the cheapest possible observable: it forces the JIT to
 * compute the result and feed it into a side-effecting accumulator, but it
 * does not otherwise perturb the benchmark — no allocation, no boxing.</p>
 *
 * <p><b>No hardcoded thresholds.</b> Each benchmark prints its observed
 * nanoseconds-per-operation as a {@code System.out} line for human inspection
 * and future regression comparison, but the tests do not assert
 * {@code ns/op < X}. Hardcoded thresholds become flaky on different hardware,
 * CI runners, or GC pauses; instead the DCE-guard checksum asserts that the
 * code ran and produced a non-trivial result. A regression will be visible
 * in the printed numbers and in subsequent code review.</p>
 *
 * <p><b>Coverage.</b> Eight benchmarks cover the three hot paths of
 * {@link EndBiomeSelector} (fast-path 1 / 2 / slow), the raw and full-chain
 * height queries of {@link EndHeightmap}, and the published-climate vs
 * null-climate paths of {@link ClimatePredicate}. Together these are the
 * per-cell/per-column operations the chunk generator issues 65k times per
 * chunk — the make-or-break paths for stage 5.3 perf budgeting.</p>
 */
class PerformanceBenchmarkTest {

    /** Warmup iterations — lets the JIT compile the hot path before timing. */
    private static final int WARMUP = 5_000;

    /** Measured iterations — averaged for the reported ns/op. */
    private static final int MEASURE = 50_000;

    private static final int SEED = 42;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void clearClimate() {
        // Start every benchmark from a clean slate so the published-climate
        // benchmarks control their own setup. Tests that publish one call
        // EndClimateAccess.set themselves.
        EndClimateAccess.clear();
    }

    @AfterEach
    void tearDownClimate() {
        EndClimateAccess.clear();
    }

    // ----- EndBiomeSelector benchmarks -----------------------------------

    /**
     * Fast-path 1: slot has no variants → selector returns base immediately,
     * no climate sampling. This is the default-config path; matches vanilla
     * performance. The selector checks {@code !ringSlot.hasVariants()} first
     * and short-circuits before touching {@link EndClimateAccess}.
     */
    @Test
    void endBiomeSelectorFastPath1NoVariants() {
        Holder<Biome> base = Holder.direct(null);
        BiomeSlot slot = new BiomeSlot(base, List.of());
        // Publish a real climate anyway to prove fast-path 1 does not consult
        // it — the variant-list check fires first.
        EndClimate climate = EndClimate.defaults(SEED);
        EndClimateAccess.set(climate);

        long checksum = 0;
        for (int warm = 0; warm < WARMUP; warm++) {
            checksum += System.identityHashCode(
                    EndBiomeSelector.select(warm & 0xFF, (warm >> 8) & 0xFF,
                            0.5F, 0.5F, climate, 0, slot));
        }
        long start = System.nanoTime();
        for (int i = 0; i < MEASURE; i++) {
            Holder<Biome> r = EndBiomeSelector.select(i & 0xFF, (i >> 8) & 0xFF,
                    0.5F, 0.5F, climate, 0, slot);
            checksum += System.identityHashCode(r);
        }
        long elapsed = System.nanoTime() - start;
        assertTrue(checksum != 0, "DCE guard: checksum must be non-zero");
        System.out.printf("[perf] endBiomeSelectorFastPath1NoVariants: %.1f ns/op%n",
                elapsed / (double) MEASURE);
    }

    /**
     * Fast-path 2: slot has variants but all four corners' candidates resolve
     * to the same biome reference (constant climate + full-range variant).
     * The selector computes the four candidates, sees they are {@code ==}, and
     * returns c00 without running the bilinear weight math.
     */
    @Test
    void endBiomeSelectorFastPath2AllCornersIdentical() {
        Holder<Biome> base = Holder.direct(null);
        Holder<Biome> variantBiome = Holder.direct(null);
        // Full-range variant matches any climate sample → every corner returns
        // variantBiome → fast-path 2 fires.
        BiomeVariant fullRange = new BiomeVariant(variantBiome,
                0.0F, 1.0F, 0.0F, 1.0F);
        BiomeSlot slot = new BiomeSlot(base, List.of(fullRange));
        EndClimate climate = EndClimate.defaults(SEED);

        long checksum = 0;
        for (int warm = 0; warm < WARMUP; warm++) {
            checksum += System.identityHashCode(
                    EndBiomeSelector.select(warm & 0xFF, (warm >> 8) & 0xFF,
                            0.5F, 0.5F, climate, 0, slot));
        }
        long start = System.nanoTime();
        for (int i = 0; i < MEASURE; i++) {
            Holder<Biome> r = EndBiomeSelector.select(i & 0xFF, (i >> 8) & 0xFF,
                    0.5F, 0.5F, climate, 0, slot);
            checksum += System.identityHashCode(r);
        }
        long elapsed = System.nanoTime() - start;
        assertTrue(checksum != 0, "DCE guard: checksum must be non-zero");
        System.out.printf("[perf] endBiomeSelectorFastPath2AllCornersIdentical: %.1f ns/op%n",
                elapsed / (double) MEASURE);
    }

    /**
     * Slow path: slot has variants and the four corners' candidates disagree
     * (variant covers only a narrow climate band, so different corners map to
     * different biomes). The full bilinear weight aggregation runs.
     */
    @Test
    void endBiomeSelectorSlowPathBilinearBlend() {
        Holder<Biome> base = Holder.direct(null);
        Holder<Biome> coldBiome = Holder.direct(null);
        Holder<Biome> warmBiome = Holder.direct(null);
        Holder<Biome> hotBiome = Holder.direct(null);
        // Temperature bands carve the climate axis into three. The defaults()
        // climate's temperature varies spatially so different corners sample
        // different bands → four-corner disagreement → slow path.
        BiomeSlot slot = new BiomeSlot(base, List.of(
                new BiomeVariant(coldBiome, 0.0F, 0.3F, 0.0F, 1.0F),
                new BiomeVariant(warmBiome, 0.3F, 0.7F, 0.0F, 1.0F),
                new BiomeVariant(hotBiome, 0.7F, 1.0F, 0.0F, 1.0F)));
        EndClimate climate = EndClimate.defaults(SEED);

        long checksum = 0;
        for (int warm = 0; warm < WARMUP; warm++) {
            checksum += System.identityHashCode(
                    EndBiomeSelector.select(warm & 0xFF, (warm >> 8) & 0xFF,
                            0.5F, 0.5F, climate, 0, slot));
        }
        long start = System.nanoTime();
        for (int i = 0; i < MEASURE; i++) {
            Holder<Biome> r = EndBiomeSelector.select(i & 0xFF, (i >> 8) & 0xFF,
                    0.5F, 0.5F, climate, 0, slot);
            checksum += System.identityHashCode(r);
        }
        long elapsed = System.nanoTime() - start;
        assertTrue(checksum != 0, "DCE guard: checksum must be non-zero");
        System.out.printf("[perf] endBiomeSelectorSlowPathBilinearBlend: %.1f ns/op%n",
                elapsed / (double) MEASURE);
    }

    // ----- EndHeightmap benchmarks --------------------------------------

    /**
     * Raw terrain height: {@link EndHeightmap#getTerrainHeight} is the
     * continent × mountains composition scaled to world height, with no
     * post-processors. This is the baseline cost the chunk generator pays
     * for every column even when no rivers/lakes/climate are attached.
     */
    @Test
    void endHeightmapGetTerrainHeightRaw() {
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);

        long checksum = 0;
        for (int warm = 0; warm < WARMUP; warm++) {
            checksum += Float.floatToIntBits(map.getTerrainHeight(warm * 7.3F, warm * 11.1F, SEED));
        }
        long start = System.nanoTime();
        for (int i = 0; i < MEASURE; i++) {
            float h = map.getTerrainHeight(i * 7.3F, i * 11.1F, SEED);
            checksum += Float.floatToIntBits(h);
        }
        long elapsed = System.nanoTime() - start;
        assertTrue(checksum != 0, "DCE guard: checksum must be non-zero");
        System.out.printf("[perf] endHeightmapGetTerrainHeightRaw: %.1f ns/op%n",
                elapsed / (double) MEASURE);
    }

    /**
     * Full-chain height: {@link EndHeightmap#getHeight} runs the raw terrain
     * through the climate modulator → river carver → lake carver. This is the
     * heaviest per-column query and the one the stage-3 Mixin will expose as
     * the dimension's {@code final_density}. The attached post-processors
     * make this the realistic worst-case per-column cost.
     */
    @Test
    void endHeightmapGetHeightFullChain() {
        EndHeightmap base = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndClimate climate = EndClimate.defaults(SEED);
        EndHeightmap full = base
                .withClimate(new ClimateModulator(climate, 0.1F, 0.05F, 0.85F, 1.15F))
                .withRivers(EndRiverMap.defaults())
                .withLakes(EndLakeMap.defaults());

        long checksum = 0;
        for (int warm = 0; warm < WARMUP; warm++) {
            checksum += Float.floatToIntBits(full.getHeight(warm * 7.3F, warm * 11.1F, SEED));
        }
        long start = System.nanoTime();
        for (int i = 0; i < MEASURE; i++) {
            float h = full.getHeight(i * 7.3F, i * 11.1F, SEED);
            checksum += Float.floatToIntBits(h);
        }
        long elapsed = System.nanoTime() - start;
        assertTrue(checksum != 0, "DCE guard: checksum must be non-zero");
        System.out.printf("[perf] endHeightmapGetHeightFullChain: %.1f ns/op%n",
                elapsed / (double) MEASURE);
    }

    // ----- ClimatePredicate benchmarks ----------------------------------

    /**
     * {@link ClimatePredicate#bothInRange} with a published climate: the
     * hot path that samples temperature + moisture and tests both ranges.
     * Used by {@code ClimatePlacementFilter} per feature placement attempt.
     */
    @Test
    void climatePredicateBothInRangePublishedClimate() {
        EndClimate climate = EndClimate.defaults(SEED);

        long checksum = 0;
        for (int warm = 0; warm < WARMUP; warm++) {
            checksum += ClimatePredicate.bothInRange(climate,
                    warm * 7.3F, warm * 11.1F, 0.2F, 0.6F, 0.3F, 0.7F) ? 1 : 0;
        }
        long start = System.nanoTime();
        for (int i = 0; i < MEASURE; i++) {
            boolean r = ClimatePredicate.bothInRange(climate,
                    i * 7.3F, i * 11.1F, 0.2F, 0.6F, 0.3F, 0.7F);
            checksum += r ? 1 : 0;
        }
        long elapsed = System.nanoTime() - start;
        assertTrue(checksum != 0, "DCE guard: checksum must be non-zero");
        System.out.printf("[perf] climatePredicateBothInRangePublishedClimate: %.1f ns/op%n",
                elapsed / (double) MEASURE);
    }

    /**
     * {@link ClimatePredicate#bothInRange} with a null climate: the fast-false
     * path that returns {@code false} immediately without sampling any noise.
     * This is the path a non-End dimension takes (where no EndClimate is
     * published), and the cost must be negligible.
     *
     * <p><b>DCE guard note.</b> The null-climate path always returns false, so
     * the obvious {@code r ? 1 : 0} checksum would be {@code 0} for every
     * iteration — a constant zero that the JIT can prove and elide. The guard
     * accumulates {@code (!r) ? 1 : 0} instead: this is also non-zero only
     * when {@code r} is false, but its value equals the iteration count
     * (proving the loop ran and the result was false every time).</p>
     */
    @Test
    void climatePredicateNullClimateFastFalse() {
        EndClimate climate = null;

        long warmChecksum = 0;
        for (int warm = 0; warm < WARMUP; warm++) {
            warmChecksum += !ClimatePredicate.bothInRange(climate,
                    warm * 7.3F, warm * 11.1F, 0.2F, 0.6F, 0.3F, 0.7F) ? 1 : 0;
        }
        // Reset checksum before the measured loop so the DCE assertion below
        // can pin an exact expected count (MEASURE) — the warmup loop's
        // contribution would otherwise make the assertion WARMUP+MEASURE.
        long checksum = 0;
        long start = System.nanoTime();
        for (int i = 0; i < MEASURE; i++) {
            boolean r = ClimatePredicate.bothInRange(climate,
                    i * 7.3F, i * 11.1F, 0.2F, 0.6F, 0.3F, 0.7F);
            checksum += !r ? 1 : 0;
        }
        long elapsed = System.nanoTime() - start;
        assertEquals(WARMUP, warmChecksum,
                "DCE guard (warmup): every warmup iteration must return false (climate is null)");
        assertEquals(MEASURE, checksum,
                "DCE guard: every measured iteration must have returned false (climate is null)");
        System.out.printf("[perf] climatePredicateNullClimateFastFalse: %.1f ns/op%n",
                elapsed / (double) MEASURE);
    }

    /**
     * {@link ClimatePredicate#temperatureInRange} with a published climate.
     * Single-axis gate (vs {@link #climatePredicateBothInRangePublishedClimate}
     * dual-axis). Used by {@code ClimateTemperatureCondition} surface rules.
     */
    @Test
    void climatePredicateTemperatureInRangePublishedClimate() {
        EndClimate climate = EndClimate.defaults(SEED);

        long checksum = 0;
        for (int warm = 0; warm < WARMUP; warm++) {
            checksum += ClimatePredicate.temperatureInRange(climate,
                    warm * 7.3F, warm * 11.1F, 0.2F, 0.6F) ? 1 : 0;
        }
        long start = System.nanoTime();
        for (int i = 0; i < MEASURE; i++) {
            boolean r = ClimatePredicate.temperatureInRange(climate,
                    i * 7.3F, i * 11.1F, 0.2F, 0.6F);
            checksum += r ? 1 : 0;
        }
        long elapsed = System.nanoTime() - start;
        assertTrue(checksum != 0, "DCE guard: checksum must be non-zero");
        System.out.printf("[perf] climatePredicateTemperatureInRangePublishedClimate: %.1f ns/op%n",
                elapsed / (double) MEASURE);
    }

    /**
     * {@link ClimatePredicate#moistureInRange} with a published climate.
     * Single-axis gate, used by {@code ClimateMoistureCondition} surface rules.
     */
    @Test
    void climatePredicateMoistureInRangePublishedClimate() {
        EndClimate climate = EndClimate.defaults(SEED);

        long checksum = 0;
        for (int warm = 0; warm < WARMUP; warm++) {
            checksum += ClimatePredicate.moistureInRange(climate,
                    warm * 7.3F, warm * 11.1F, 0.3F, 0.7F) ? 1 : 0;
        }
        long start = System.nanoTime();
        for (int i = 0; i < MEASURE; i++) {
            boolean r = ClimatePredicate.moistureInRange(climate,
                    i * 7.3F, i * 11.1F, 0.3F, 0.7F);
            checksum += r ? 1 : 0;
        }
        long elapsed = System.nanoTime() - start;
        assertTrue(checksum != 0, "DCE guard: checksum must be non-zero");
        System.out.printf("[perf] climatePredicateMoistureInRangePublishedClimate: %.1f ns/op%n",
                elapsed / (double) MEASURE);
    }
}
