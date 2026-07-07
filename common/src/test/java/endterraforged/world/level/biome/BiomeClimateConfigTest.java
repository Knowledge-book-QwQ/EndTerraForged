package endterraforged.world.level.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;

/**
 * Contract tests for {@link BiomeClimateConfig}: the {@code EMPTY} no-op
 * config, the {@code hasAnyVariants} OR-of-five-slots predicate, and the
 * {@code resolve} fill-null-bases / preserve-non-null-bases contract.
 *
 * <p>Holder stubs are {@code Holder.direct(null)}; assertions on stored
 * holders use {@code assertSame} (reference identity) because all such stubs
 * collide under {@code equals()} (spec design decision 5).</p>
 */
class BiomeClimateConfigTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Holder<Biome> stubHolder() {
        return Holder.direct(null);
    }

    private static BiomeVariant fullRangeVariant(Holder<Biome> biome) {
        return new BiomeVariant(biome, 0.0F, 1.0F, 0.0F, 1.0F);
    }

    /** A slot whose base is non-null (so resolve must preserve it). */
    private static BiomeSlot slotWithBase(Holder<Biome> base) {
        return new BiomeSlot(base, List.of());
    }

    /** A slot whose base is null (so resolve must fill it). */
    private static BiomeSlot slotWithNullBaseAndVariants(List<BiomeVariant> variants) {
        return new BiomeSlot(null, variants);
    }

    // ----- EMPTY constant -------------------------------------------------

    @Test
    void emptyHasNoVariants() {
        assertFalse(BiomeClimateConfig.EMPTY.hasAnyVariants(),
                "EMPTY config must report no variants (pure-geometry mode)");
    }

    @Test
    void emptySlotsAllHaveNullBase() {
        BiomeClimateConfig empty = BiomeClimateConfig.EMPTY;
        assertFalse(empty.end().base() != null
                        || empty.highlands().base() != null
                        || empty.midlands().base() != null
                        || empty.islands().base() != null
                        || empty.barrens().base() != null,
                "every EMPTY slot's base must be null (inherit-from-geometry sentinel)");
    }

    // ----- hasAnyVariants -------------------------------------------------

    @Test
    void hasAnyVariantsTrueWhenOneSlotHasVariants() {
        // Only the highlands slot has a variant; the others are EMPTY.
        BiomeClimateConfig config = new BiomeClimateConfig(
                BiomeSlot.EMPTY,
                new BiomeSlot(stubHolder(), List.of(fullRangeVariant(stubHolder()))),
                BiomeSlot.EMPTY,
                BiomeSlot.EMPTY,
                BiomeSlot.EMPTY);
        assertTrue(config.hasAnyVariants(),
                "config with one variant-bearing slot must report hasAnyVariants");
    }

    @Test
    void hasAnyVariantsFalseWhenAllSlotsEmpty() {
        // Construct a config where each slot has a base but no variants.
        BiomeClimateConfig config = new BiomeClimateConfig(
                slotWithBase(stubHolder()), slotWithBase(stubHolder()),
                slotWithBase(stubHolder()), slotWithBase(stubHolder()),
                slotWithBase(stubHolder()));
        assertFalse(config.hasAnyVariants(),
                "config with bases but no variants must report no variants");
    }

    // ----- resolve --------------------------------------------------------

    @Test
    void resolveFillsNullBasesWithCorrespondingHolders() {
        // All five slots have null base (EMPTY); resolve must fill each with
        // the corresponding geometric-ring holder passed in.
        Holder<Biome> end = stubHolder();
        Holder<Biome> highlands = stubHolder();
        Holder<Biome> midlands = stubHolder();
        Holder<Biome> islands = stubHolder();
        Holder<Biome> barrens = stubHolder();

        BiomeClimateConfig resolved = BiomeClimateConfig.EMPTY.resolve(
                end, highlands, midlands, islands, barrens);

        assertSame(end, resolved.end().base(), "end base must be filled");
        assertSame(highlands, resolved.highlands().base(), "highlands base must be filled");
        assertSame(midlands, resolved.midlands().base(), "midlands base must be filled");
        assertSame(islands, resolved.islands().base(), "islands base must be filled");
        assertSame(barrens, resolved.barrens().base(), "barrens base must be filled");
    }

    @Test
    void resolvePreservesNonNullBases() {
        // Each slot already has a non-null base; resolve must return those
        // exact references, not overwrite with the passed-in holders.
        Holder<Biome> presetEnd = stubHolder();
        Holder<Biome> presetHighlands = stubHolder();
        Holder<Biome> presetMidlands = stubHolder();
        Holder<Biome> presetIslands = stubHolder();
        Holder<Biome> presetBarrens = stubHolder();

        BiomeClimateConfig preset = new BiomeClimateConfig(
                slotWithBase(presetEnd), slotWithBase(presetHighlands),
                slotWithBase(presetMidlands), slotWithBase(presetIslands),
                slotWithBase(presetBarrens));

        // Pass different holders to resolve — they must be ignored for slots
        // whose base is already non-null.
        BiomeClimateConfig resolved = preset.resolve(
                stubHolder(), stubHolder(), stubHolder(), stubHolder(), stubHolder());

        assertSame(presetEnd, resolved.end().base(), "non-null end base must be preserved");
        assertSame(presetHighlands, resolved.highlands().base(),
                "non-null highlands base must be preserved");
        assertSame(presetMidlands, resolved.midlands().base(),
                "non-null midlands base must be preserved");
        assertSame(presetIslands, resolved.islands().base(),
                "non-null islands base must be preserved");
        assertSame(presetBarrens, resolved.barrens().base(),
                "non-null barrens base must be preserved");
    }

    @Test
    void resolvePreservesVariantsUnchanged() {
        // A slot with null base AND variants: resolve must fill the base but
        // keep the variants list (same elements, same order).
        BiomeVariant v1 = fullRangeVariant(stubHolder());
        BiomeVariant v2 = fullRangeVariant(stubHolder());
        BiomeClimateConfig config = new BiomeClimateConfig(
                slotWithNullBaseAndVariants(List.of(v1, v2)),
                BiomeSlot.EMPTY, BiomeSlot.EMPTY, BiomeSlot.EMPTY, BiomeSlot.EMPTY);

        BiomeClimateConfig resolved = config.resolve(
                stubHolder(), stubHolder(), stubHolder(), stubHolder(), stubHolder());

        assertEquals(2, resolved.end().variants().size(),
                "resolve must not change the variants list size");
        assertSame(v1, resolved.end().variants().get(0),
                "resolve must preserve variant reference order");
        assertSame(v2, resolved.end().variants().get(1),
                "resolve must preserve variant reference order");
        assertTrue(resolved.end().hasVariants(),
                "slot with variants before resolve must still have variants after");
    }
}
