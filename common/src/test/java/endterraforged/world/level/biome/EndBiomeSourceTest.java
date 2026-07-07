package endterraforged.world.level.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

import endterraforged.world.climate.EndClimate;
import endterraforged.world.climate.EndClimateAccess;
import endterraforged.world.noise.Noise;

/**
 * Contract tests for {@link EndBiomeSource}'s climate-variant wiring: the
 * two-layer fast-path contract in {@code selectRing}, the
 * {@code biome_climate} codec field's resolve-once semantics, and the
 * {@code collectPossibleBiomes} surface that vanilla uses to pre-build
 * surface / feature lookup tables.
 *
 * <p>The central-island short-circuit (any cell within
 * {@code MAIN_ISLAND_RADIUS} of the origin) is used as the test vector
 * because it deterministically routes to the {@code end} ring regardless
 * of the simplex-perturbed falloff — so the tests can pin exact expected
 * biomes without depending on noise output.
 *
 * <p>Biome holders are {@code Holder.direct(null)} stubs. All such stubs
 * collide under {@code equals()} (their value is null), so
 * {@code collectPossibleBiomes} assertions use {@code List} +
 * {@code anyMatch(h -> h == expected)} (reference identity) per spec
 * design decision 5.
 *
 * <p>Each test clears {@link EndClimateAccess} in {@link BeforeEach} /
 * {@link AfterEach} so the published climate never leaks between tests
 * (the holder is process-wide static state).
 */
class EndBiomeSourceTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    @AfterEach
    void isolateClimateHolder() {
        // The holder is process-wide; clear before and after each test so
        // a published climate never leaks into a test that asserts the
        // "no climate configured" fast path.
        EndClimateAccess.clear();
    }

    /**
     * Test-only {@link Noise} backed by a {@code BiFunction<Float, Float,
     * Float>}. Lets each test pin exact climate values without depending
     * on the real simplex-based {@link EndClimate} channels.
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

    /**
     * A five-holder source with no climate variants — the vanilla-style
     * configuration that an omitted {@code biome_climate} field decodes to.
     */
    private static EndBiomeSource vanillaSource() {
        return new EndBiomeSource(stubHolder(), stubHolder(), stubHolder(),
                stubHolder(), stubHolder());
    }

    /** A point well inside the central main island (radius < MAIN_ISLAND_RADIUS=18). */
    private static final int CENTRAL_X = 0;
    private static final int CENTRAL_Z = 10;

    // ----- fast-path 1: EMPTY config, no climate published -----------------

    @Test
    void emptyConfigReturnsRingBase() {
        // Vanilla-style config: BiomeClimateConfig.EMPTY (the codec default
        // for an omitted biome_climate field). resolve() fills every slot's
        // null base from the corresponding ring holder, so the end slot's
        // base == the end holder we passed in. No climate is published, but
        // fast-path 1 (!slot.hasVariants()) short-circuits before the
        // climate lookup, so performance matches vanilla.
        Holder<Biome> endHolder = stubHolder();
        EndBiomeSource source = new EndBiomeSource(
                endHolder, stubHolder(), stubHolder(), stubHolder(), stubHolder());
        Holder<Biome> result = source.getNoiseBiome(CENTRAL_X, 0, CENTRAL_Z,
                (Climate.Sampler) null);
        assertSame(endHolder, result,
                "EMPTY config must fast-path to the end ring's base holder");
    }

    // ----- fast-path 2: variants present, no climate published -------------

    @Test
    void variantsButNoClimateReturnsBase() {
        // Variants exist on the end slot, but EndClimateAccess has no
        // published climate (cleared in @BeforeEach). Fast-path 2 returns
        // the slot's base — vanilla behaviour when the climate layer is
        // not wired (e.g. overworld-only server, or pre-Mixin wiring).
        Holder<Biome> endBase = stubHolder();
        Holder<Biome> variantBiome = stubHolder();
        BiomeSlot endSlot = new BiomeSlot(endBase, List.of(fullRange(variantBiome)));
        BiomeClimateConfig config = new BiomeClimateConfig(
                endSlot, BiomeSlot.EMPTY, BiomeSlot.EMPTY,
                BiomeSlot.EMPTY, BiomeSlot.EMPTY);
        EndBiomeSource source = new EndBiomeSource(
                endBase, stubHolder(), stubHolder(), stubHolder(), stubHolder(), config);

        Holder<Biome> result = source.getNoiseBiome(CENTRAL_X, 0, CENTRAL_Z,
                (Climate.Sampler) null);
        assertSame(endBase, result,
                "variants + no published climate must fast-path to base");
    }

    // ----- slow path: full-range variant + published climate ---------------

    @Test
    void fullRangeVariantWithClimateReturnsVariant() {
        // A full-range variant matches any climate sample, so all four
        // cell corners resolve to the variant biome. The selector's
        // fast-path 2 (all four corners ==) returns the variant.
        Holder<Biome> endBase = stubHolder();
        Holder<Biome> variantBiome = stubHolder();
        BiomeSlot endSlot = new BiomeSlot(endBase, List.of(fullRange(variantBiome)));
        BiomeClimateConfig config = new BiomeClimateConfig(
                endSlot, BiomeSlot.EMPTY, BiomeSlot.EMPTY,
                BiomeSlot.EMPTY, BiomeSlot.EMPTY);
        EndBiomeSource source = new EndBiomeSource(
                endBase, stubHolder(), stubHolder(), stubHolder(), stubHolder(), config);

        // Publish a climate that the variant will match. Constant 0.5/0.5
        // is in the full-range [0,1]×[0,1], so every corner hits the variant.
        EndClimateAccess.set(climate(PointNoise.constant(0.5F), PointNoise.constant(0.5F)));

        Holder<Biome> result = source.getNoiseBiome(CENTRAL_X, 0, CENTRAL_Z,
                (Climate.Sampler) null);
        assertSame(variantBiome, result,
                "full-range variant + published climate must select the variant");
    }

    // ----- slow path: narrow variant, no match → base ---------------------

    @Test
    void narrowRangeVariantWithNoMatchReturnsBase() {
        // A variant that only matches temperature >= 0.9, but the published
        // climate reports temperature 0.0 everywhere. No corner matches,
        // so every corner's candidate is the slot base. The selector's
        // fast-path 2 (all four corners == base) returns the base.
        Holder<Biome> endBase = stubHolder();
        Holder<Biome> variantBiome = stubHolder();
        BiomeSlot endSlot = new BiomeSlot(endBase, List.of(
                variant(variantBiome, 0.9F, 1.0F, 0.0F, 1.0F)));
        BiomeClimateConfig config = new BiomeClimateConfig(
                endSlot, BiomeSlot.EMPTY, BiomeSlot.EMPTY,
                BiomeSlot.EMPTY, BiomeSlot.EMPTY);
        EndBiomeSource source = new EndBiomeSource(
                endBase, stubHolder(), stubHolder(), stubHolder(), stubHolder(), config);

        EndClimateAccess.set(climate(PointNoise.constant(0.0F), PointNoise.constant(0.0F)));

        Holder<Biome> result = source.getNoiseBiome(CENTRAL_X, 0, CENTRAL_Z,
                (Climate.Sampler) null);
        assertSame(endBase, result,
                "narrow variant that does not match the climate must fall back to base");
    }

    // ----- determinism -----------------------------------------------------

    @Test
    void getNoiseBiomeIsDeterministic() {
        // Two calls with identical inputs must return the same holder
        // reference. The source is immutable and getNoiseBiome is
        // stateless beyond the constant seeds baked into the noises.
        Holder<Biome> endBase = stubHolder();
        Holder<Biome> variantBiome = stubHolder();
        BiomeSlot endSlot = new BiomeSlot(endBase, List.of(fullRange(variantBiome)));
        BiomeClimateConfig config = new BiomeClimateConfig(
                endSlot, BiomeSlot.EMPTY, BiomeSlot.EMPTY,
                BiomeSlot.EMPTY, BiomeSlot.EMPTY);
        EndBiomeSource source = new EndBiomeSource(
                endBase, stubHolder(), stubHolder(), stubHolder(), stubHolder(), config);
        EndClimateAccess.set(climate(PointNoise.constant(0.5F), PointNoise.constant(0.5F)));

        Holder<Biome> first = source.getNoiseBiome(CENTRAL_X, 0, CENTRAL_Z,
                (Climate.Sampler) null);
        Holder<Biome> second = source.getNoiseBiome(CENTRAL_X, 0, CENTRAL_Z,
                (Climate.Sampler) null);
        assertSame(first, second,
                "getNoiseBiome must be deterministic for identical inputs");
    }

    // ----- collectPossibleBiomes: EMPTY config ----------------------------

    @Test
    void collectPossibleBiomesEmptyReturnsFiveRings() {
        // An EMPTY config has no variants, so collectPossibleBiomes returns
        // exactly the five ring holders. Use reference-identity matching
        // (anyMatch ==) because Holder.direct(null) stubs all collide under
        // equals() — a Set-based assertion would collapse them to one entry.
        Holder<Biome> end = stubHolder();
        Holder<Biome> highlands = stubHolder();
        Holder<Biome> midlands = stubHolder();
        Holder<Biome> islands = stubHolder();
        Holder<Biome> barrens = stubHolder();
        EndBiomeSource source = new EndBiomeSource(end, highlands, midlands, islands, barrens);

        List<Holder<Biome>> list = source.collectPossibleBiomes()
                .collect(Collectors.toList());
        assertEquals(5, list.size(),
                "EMPTY config must yield exactly the five ring holders");

        // Each ring holder must be present (by reference identity, not equals).
        assertTrue(list.stream().anyMatch(h -> h == end),
                "end holder must be in collectPossibleBiomes");
        assertTrue(list.stream().anyMatch(h -> h == highlands),
                "highlands holder must be in collectPossibleBiomes");
        assertTrue(list.stream().anyMatch(h -> h == midlands),
                "midlands holder must be in collectPossibleBiomes");
        assertTrue(list.stream().anyMatch(h -> h == islands),
                "islands holder must be in collectPossibleBiomes");
        assertTrue(list.stream().anyMatch(h -> h == barrens),
                "barrens holder must be in collectPossibleBiomes");
    }

    // ----- collectPossibleBiomes: variants included -----------------------

    @Test
    void collectPossibleBiomesWithVariantsIncludesVariants() {
        // A config with two variant biomes (one on the end slot, one on
        // the highlands slot) must yield 5 ring holders + 2 variants = 7.
        // Variant holders must be distinct references from the ring holders
        // so the reference-identity assertion can distinguish them.
        Holder<Biome> end = stubHolder();
        Holder<Biome> highlands = stubHolder();
        Holder<Biome> midlands = stubHolder();
        Holder<Biome> islands = stubHolder();
        Holder<Biome> barrens = stubHolder();
        Holder<Biome> endVariant = stubHolder();
        Holder<Biome> highlandsVariant = stubHolder();

        BiomeSlot endSlot = new BiomeSlot(end, List.of(fullRange(endVariant)));
        BiomeSlot highlandsSlot = new BiomeSlot(highlands, List.of(fullRange(highlandsVariant)));
        BiomeClimateConfig config = new BiomeClimateConfig(
                endSlot, highlandsSlot, BiomeSlot.EMPTY,
                BiomeSlot.EMPTY, BiomeSlot.EMPTY);
        EndBiomeSource source = new EndBiomeSource(
                end, highlands, midlands, islands, barrens, config);

        List<Holder<Biome>> list = source.collectPossibleBiomes()
                .collect(Collectors.toList());
        assertEquals(7, list.size(),
                "config with 2 variants must yield 5 rings + 2 variants = 7");

        // All five ring holders + both variant holders must be present.
        assertTrue(list.stream().anyMatch(h -> h == end), "end ring must be present");
        assertTrue(list.stream().anyMatch(h -> h == highlands), "highlands ring must be present");
        assertTrue(list.stream().anyMatch(h -> h == midlands), "midlands ring must be present");
        assertTrue(list.stream().anyMatch(h -> h == islands), "islands ring must be present");
        assertTrue(list.stream().anyMatch(h -> h == barrens), "barrens ring must be present");
        assertTrue(list.stream().anyMatch(h -> h == endVariant),
                "end variant biome must be present");
        assertTrue(list.stream().anyMatch(h -> h == highlandsVariant),
                "highlands variant biome must be present");
    }
}
