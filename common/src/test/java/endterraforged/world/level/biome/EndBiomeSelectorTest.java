package endterraforged.world.level.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.function.BiFunction;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;

import endterraforged.world.climate.EndClimate;
import endterraforged.world.noise.Noise;

/**
 * Contract tests for {@link EndBiomeSelector}: the two fast paths, the slow
 * four-corner bilinear aggregation, the reference-identity comparison (no
 * {@code equals()} collision), and the deterministic corner-order tie-break
 * {@code 00 > 10 > 01 > 11}.
 *
 * <p>The climate field is constructed from a test-only {@link PointNoise}
 * (a {@code BiFunction<Float, Float, Float>} wrapped as a {@link Noise}) so
 * each test can pin exact temperature/moisture values per corner. Biome
 * holders are {@code Holder.direct(null)} stubs; assertions use
 * {@code assertSame} (reference identity) because all such stubs collide
 * under {@code equals()} (spec design decision 5).</p>
 */
class EndBiomeSelectorTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Test-only {@link Noise} backed by a {@code BiFunction<Float, Float,
     * Float>}. Lets each test pin exact per-corner climate values without
     * depending on the real simplex-based {@link EndClimate} channels.
     */
    private record PointNoise(BiFunction<Float, Float, Float> fn) implements Noise {
        @Override
        public float compute(float x, float z, int seed) {
            return fn.apply(x, z);
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
            return visitor.apply(this);
        }

        static PointNoise constant(float v) {
            return new PointNoise((x, z) -> v);
        }
    }

    private static Holder<Biome> stubHolder() {
        return Holder.direct(null);
    }

    private static BiomeVariant variant(Holder<Biome> biome,
                                        float tMin, float tMax,
                                        float mMin, float mMax) {
        return new BiomeVariant(biome, tMin, tMax, mMin, mMax);
    }

    /** A variant covering the full climate range — matches any sample. */
    private static BiomeVariant fullRange(Holder<Biome> biome) {
        return variant(biome, 0.0F, 1.0F, 0.0F, 1.0F);
    }

    private static EndClimate climate(Noise temp, Noise moist) {
        // wind is unused by the selector; pass a constant zero channel.
        return new EndClimate(temp, moist, PointNoise.constant(0.0F), 1.0F, 0.0F);
    }

    /** A climate where temperature = (x + 2·z) / 3, moisture = 0.5 everywhere. */
    private static EndClimate spatialClimate() {
        Noise temp = new PointNoise((x, z) -> (x + 2.0F * z) / 3.0F);
        Noise moist = PointNoise.constant(0.5F);
        return climate(temp, moist);
    }

    // ----- fast-path 1 ----------------------------------------------------

    @Test
    void selectWithNullClimateReturnsBase() {
        Holder<Biome> base = stubHolder();
        BiomeSlot slot = new BiomeSlot(base, List.of(fullRange(stubHolder())));
        // climate == null → fast-path 1, no climate sampling, base returned.
        assertSame(base, EndBiomeSelector.select(0, 0, 0.5F, 0.5F, null, 0, slot),
                "null climate must short-circuit to base");
    }

    @Test
    void selectWithEmptyVariantsReturnsBase() {
        Holder<Biome> base = stubHolder();
        BiomeSlot slot = new BiomeSlot(base, List.of());
        EndClimate climate = climate(PointNoise.constant(0.5F), PointNoise.constant(0.5F));
        // no variants → fast-path 1, no climate sampling, base returned.
        assertSame(base, EndBiomeSelector.select(0, 0, 0.5F, 0.5F, climate, 0, slot),
                "slot with no variants must short-circuit to base");
    }

    // ----- fast-path 2 ----------------------------------------------------

    @Test
    void allCornersMatchingSameVariantReturnThatVariant() {
        // Constant climate (0.5, 0.5); one full-range variant matches at every
        // corner. All four candidates == the variant's biome → fast-path 2.
        Holder<Biome> base = stubHolder();
        Holder<Biome> variantBiome = stubHolder();
        BiomeVariant v = fullRange(variantBiome);
        BiomeSlot slot = new BiomeSlot(base, List.of(v));
        EndClimate climate = climate(PointNoise.constant(0.5F), PointNoise.constant(0.5F));

        assertSame(variantBiome, EndBiomeSelector.select(7, 9, 0.3F, 0.7F, climate, 0, slot),
                "all four corners matching the same variant must return that variant's biome");
    }

    @Test
    void noVariantMatchesReturnsBase() {
        // Climate sample (0.5, 0.5) does not match a narrow-range variant, so
        // every corner falls back to base. All four candidates == base →
        // fast-path 2 returns base.
        Holder<Biome> base = stubHolder();
        // Variant matches only temp in [0, 0.4] — climate temp is 0.5 everywhere.
        BiomeVariant narrowTemp = variant(stubHolder(), 0.0F, 0.4F, 0.0F, 1.0F);
        BiomeSlot slot = new BiomeSlot(base, List.of(narrowTemp));
        EndClimate climate = climate(PointNoise.constant(0.5F), PointNoise.constant(0.5F));

        assertSame(base, EndBiomeSelector.select(0, 0, 0.5F, 0.5F, climate, 0, slot),
                "when no variant matches at any corner, base must be returned");
    }

    // ----- slow path: single corner dominant -----------------------------

    @Test
    void singleCornerWithHighestWeightWins() {
        // spatialClimate: temp(0,0)=0, temp(1,0)=1/3, temp(0,1)=2/3, temp(1,1)=1.
        // Three variants carve the temperature axis into three bands.
        //   v_cold [0, 0.3]   → matches corner (0,0)
        //   v_warm [0.3, 0.7] → matches corners (1,0) and (0,1)
        //   v_hot  [0.7, 1.0] → matches corner (1,1)
        Holder<Biome> base = stubHolder();
        Holder<Biome> coldBiome = stubHolder();
        Holder<Biome> warmBiome = stubHolder();
        Holder<Biome> hotBiome = stubHolder();
        BiomeSlot slot = new BiomeSlot(base, List.of(
                variant(coldBiome, 0.0F, 0.3F, 0.0F, 1.0F),
                variant(warmBiome, 0.3F, 0.7F, 0.0F, 1.0F),
                variant(hotBiome, 0.7F, 1.0F, 0.0F, 1.0F)));
        EndClimate climate = spatialClimate();

        // fracX=0.9, fracZ=0.9 → w11 = 0.81 dominates all other corners.
        Holder<Biome> result = EndBiomeSelector.select(0, 0, 0.9F, 0.9F, climate, 0, slot);
        assertSame(hotBiome, result,
                "corner 11 (hot) has weight 0.81, exceeding the sum of all others (0.19)");
    }

    @Test
    void aggregationOfMultipleCornersBeatsSingleMaxCorner() {
        // Same climate/variants as above, but equal weights. The warm biome
        // appears at two corners (10 and 01), so its aggregated weight (0.5)
        // beats the single-corner hot (0.25) and cold (0.25).
        Holder<Biome> base = stubHolder();
        Holder<Biome> warmBiome = stubHolder();
        BiomeSlot slot = new BiomeSlot(base, List.of(
                variant(stubHolder(), 0.0F, 0.3F, 0.0F, 1.0F),    // cold
                variant(warmBiome, 0.3F, 0.7F, 0.0F, 1.0F),         // warm (corners 10, 01)
                variant(stubHolder(), 0.7F, 1.0F, 0.0F, 1.0F)));     // hot
        EndClimate climate = spatialClimate();

        // fracX=0.5, fracZ=0.5 → all weights 0.25. Warm total = 0.5, others 0.25.
        Holder<Biome> result = EndBiomeSelector.select(0, 0, 0.5F, 0.5F, climate, 0, slot);
        assertSame(warmBiome, result,
                "warm biome aggregated across corners 10+01 (total 0.5) must beat single corners");
    }

    // ----- slow path: tie-break by corner order ---------------------------

    @Test
    void tieBreakPrefersCorner00WhenAllFourCornerWeightsEqual() {
        // Four distinct biomes at the four corners, equal weights (0.25 each)
        // → four-way tie. Corner order 00 > 10 > 01 > 11 → c00 wins.
        Holder<Biome> base = stubHolder();
        Holder<Biome> aBiome = stubHolder();  // corner 00
        Holder<Biome> bBiome = stubHolder();  // corner 10
        Holder<Biome> cBiome = stubHolder();  // corner 01
        Holder<Biome> dBiome = stubHolder();  // corner 11
        BiomeSlot slot = new BiomeSlot(base, List.of(
                variant(aBiome, 0.0F, 0.2F, 0.0F, 1.0F),     // matches temp(0,0)=0
                variant(bBiome, 0.2F, 0.4F, 0.0F, 1.0F),      // matches temp(1,0)=1/3
                variant(cBiome, 0.4F, 0.7F, 0.0F, 1.0F),      // matches temp(0,1)=2/3
                variant(dBiome, 0.7F, 1.0F, 0.0F, 1.0F)));    // matches temp(1,1)=1
        EndClimate climate = spatialClimate();

        Holder<Biome> result = EndBiomeSelector.select(0, 0, 0.5F, 0.5F, climate, 0, slot);
        assertSame(aBiome, result,
                "four-way tie (0.25 each) must resolve to corner 00 (earliest in tie-break order)");
    }

    @Test
    void tieBreakPrefersCorner10Over11() {
        // Isolate corners 10 and 11 with fracX=1, fracZ=0.5: w10=0.5, w11=0.5,
        // w00=w01=0. Two distinct biomes B (corner 10) and C (corner 11)
        // each get 0.5 — a float-exact tie (0.5 and 0.5 are powers of 2).
        // Corner 10 < corner 11 → B wins.
        // Climate: temp=x, moist=z. Variants carve so:
        //   c00 (0,0): temp=0, moist=0 → no match → base
        //   c10 (1,0): temp=1, moist=0 → variant B (temp [0.6, 1.0], moist [0, 0.4])
        //   c01 (0,1): temp=0, moist=1 → no match → base
        //   c11 (1,1): temp=1, moist=1 → variant C (temp [0.6, 1.0], moist [0.6, 1.0])
        Noise temp = new PointNoise((x, z) -> x);
        Noise moist = new PointNoise((x, z) -> z);
        EndClimate climate = climate(temp, moist);

        Holder<Biome> base = stubHolder();
        Holder<Biome> bBiome = stubHolder();  // corner 10
        Holder<Biome> cBiome = stubHolder();  // corner 11
        BiomeSlot slot = new BiomeSlot(base, List.of(
                variant(bBiome, 0.6F, 1.0F, 0.0F, 0.4F),     // matches (1, 0)
                variant(cBiome, 0.6F, 1.0F, 0.6F, 1.0F)));    // matches (1, 1)

        Holder<Biome> result = EndBiomeSelector.select(0, 0, 1.0F, 0.5F, climate, 0, slot);
        assertSame(bBiome, result,
                "B (corner 10) and C (corner 11) tie at 0.5; B must win (10 < 11)");
    }

    @Test
    void tieBreakPrefersCorner01Over11() {
        // Climate: temperature = x, moisture = z (both already in [0, 1]).
        // Variants carve by (temp, moist) so that:
        //   c00 = base  (temp=0, moist=0 — no variant matches)
        //   c10 = base  (temp=1, moist=0 — no variant matches)
        //   c01 = C     (temp=0, moist=1 — matches variant C: temp [0, 0.4], moist [0.6, 1.0])
        //   c11 = D     (temp=1, moist=1 — matches variant D: temp [0.6, 1.0], moist [0.6, 1.0])
        // With fz=1, fx=0.5: w00=0, w10=0, w01=0.5, w11=0.5. Only corners 01
        // and 11 carry weight. C and D tie at 0.5; C's first corner is 01,
        // D's is 11 → C wins.
        Noise temp = new PointNoise((x, z) -> x);
        Noise moist = new PointNoise((x, z) -> z);
        EndClimate climate = climate(temp, moist);

        Holder<Biome> base = stubHolder();
        Holder<Biome> cBiome = stubHolder();  // corner 01 only
        Holder<Biome> dBiome = stubHolder();  // corner 11 only
        BiomeSlot slot = new BiomeSlot(base, List.of(
                variant(cBiome, 0.0F, 0.4F, 0.6F, 1.0F),     // matches (temp=0, moist=1)
                variant(dBiome, 0.6F, 1.0F, 0.6F, 1.0F)));    // matches (temp=1, moist=1)

        // fx=0.5, fz=1: w00=0, w10=0, w01=0.5, w11=0.5. C and D tie → C wins.
        Holder<Biome> result = EndBiomeSelector.select(0, 0, 0.5F, 1.0F, climate, 0, slot);
        assertSame(cBiome, result,
                "C and D tie at 0.5; C (corner 01) must win over D (corner 11)");
    }

    @Test
    void sharedBiomeAggregationPicksEarliestCorner() {
        // c00 == c10 (biome A), c01 == c11 (biome B). With fz=0.5 (so north
        // and south halves have equal total weight): s_A = 0.5, s_B = 0.5.
        // Tie between A (first at corner 00) and B (first at corner 01).
        // Expected: A returned as c00 (corner 00 < corner 01).
        // Climate: temp = z. temp(0,0)=0, temp(1,0)=0, temp(0,1)=1, temp(1,1)=1.
        Noise temp = new PointNoise((x, z) -> z);
        EndClimate climate = climate(temp, PointNoise.constant(0.5F));

        Holder<Biome> base = stubHolder();
        Holder<Biome> aBiome = stubHolder();  // corners 00, 10 (temp=0)
        Holder<Biome> bBiome = stubHolder();  // corners 01, 11 (temp=1)
        BiomeSlot slot = new BiomeSlot(base, List.of(
                variant(aBiome, 0.0F, 0.4F, 0.0F, 1.0F),
                variant(bBiome, 0.6F, 1.0F, 0.0F, 1.0F)));

        // fracX=0.5, fracZ=0.5 → all weights 0.25. s_A = w00 + w10 = 0.5,
        // s_B = w01 + w11 = 0.5. Tie. A's first corner is 00 → returns c00.
        Holder<Biome> result = EndBiomeSelector.select(0, 0, 0.5F, 0.5F, climate, 0, slot);
        assertSame(aBiome, result,
                "shared-biome tie must resolve to the earliest corner (00 for A)");
        // Sanity: the result is specifically c00, not c10 (both are aBiome, but
        // the contract returns the first corner of the winning biome).
        // We can't distinguish c00 from c10 by reference (both are aBiome), so
        // the assertion above is the strongest we can make.
    }

    // ----- variant precedence ---------------------------------------------

    @Test
    void firstMatchingVariantWins() {
        // Two variants both match the climate sample at every corner. The
        // first one in list order must be selected — the second is never
        // reached. This pins the precedence contract documented on
        // BiomeSlot: "variants is an ordered list; first match wins".
        Holder<Biome> base = stubHolder();
        Holder<Biome> firstBiome = stubHolder();
        Holder<Biome> secondBiome = stubHolder();
        // Both variants cover the full range — both match every sample.
        BiomeVariant first = fullRange(firstBiome);
        BiomeVariant second = fullRange(secondBiome);
        BiomeSlot slot = new BiomeSlot(base, List.of(first, second));
        EndClimate climate = climate(PointNoise.constant(0.5F), PointNoise.constant(0.5F));

        assertSame(firstBiome, EndBiomeSelector.select(0, 0, 0.5F, 0.5F, climate, 0, slot),
                "first matching variant in list order must win precedence");
        // Sanity: the second variant's biome is a distinct reference, so a
        // swapped-precedence bug would return secondBiome instead.
        assertNotSame(secondBiome, EndBiomeSelector.select(0, 0, 0.5F, 0.5F, climate, 0, slot),
                "second matching variant must never be reached when the first matches");
    }

    // ----- determinism ----------------------------------------------------

    @Test
    void selectIsDeterministicForSameInputs() {
        // Same inputs → same output reference. The selector is stateless, so
        // two calls must return holder-equal results (and for the fast paths,
        // the exact same reference). This guards against accidental
        // non-determinism from HashMap iteration or float reordering.
        Holder<Biome> base = stubHolder();
        Holder<Biome> warmBiome = stubHolder();
        BiomeSlot slot = new BiomeSlot(base, List.of(
                variant(stubHolder(), 0.0F, 0.3F, 0.0F, 1.0F),
                variant(warmBiome, 0.3F, 0.7F, 0.0F, 1.0F),
                variant(stubHolder(), 0.7F, 1.0F, 0.0F, 1.0F)));
        EndClimate climate = spatialClimate();

        Holder<Biome> first = EndBiomeSelector.select(0, 0, 0.5F, 0.5F, climate, 0, slot);
        Holder<Biome> second = EndBiomeSelector.select(0, 0, 0.5F, 0.5F, climate, 0, slot);
        assertSame(first, second,
                "selector must be deterministic: same inputs → same output reference");
    }

    @Test
    void selectCellOffsetDoesNotBreakDeterminism() {
        // The selector reads climate at (cellX, cellZ), (cellX+1, cellZ), etc.
        // A non-zero cell offset must not introduce stateful drift — the
        // result depends only on the climate samples at the four corners and
        // the fractional position, not on any accumulated cell counter.
        Holder<Biome> base = stubHolder();
        Holder<Biome> variantBiome = stubHolder();
        BiomeSlot slot = new BiomeSlot(base, List.of(fullRange(variantBiome)));
        // Constant climate: every corner matches the full-range variant, so
        // fast-path 2 returns variantBiome regardless of cell offset.
        EndClimate climate = climate(PointNoise.constant(0.5F), PointNoise.constant(0.5F));

        Holder<Biome> atOrigin = EndBiomeSelector.select(0, 0, 0.5F, 0.5F, climate, 0, slot);
        Holder<Biome> atOffset = EndBiomeSelector.select(123, -456, 0.5F, 0.5F, climate, 0, slot);
        assertEquals(atOrigin, atOffset,
                "constant climate + full-range variant must yield the same biome at any cell offset");
    }
}
