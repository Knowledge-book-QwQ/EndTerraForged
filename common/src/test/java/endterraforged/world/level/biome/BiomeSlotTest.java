package endterraforged.world.level.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;

/**
 * Contract tests for {@link BiomeSlot}: defensive-copy semantics, null base
 * contract, {@code hasVariants} predicate, and exact-reference preservation of
 * the base holder (the spec uses {@code Holder.direct(null)} stubs whose
 * {@code equals()} all collide, so reference identity is the only sound
 * comparison — see spec design decision 5).
 *
 * <p>Bootstrap is required because {@link BiomeSlot#CODEC} references
 * {@link Biome#CODEC} as a static initializer, which transitively loads
 * biome-registry machinery. {@code tryDetectVersion} must precede
 * {@code Bootstrap.bootStrap} or {@code DataFixers.<clinit>} fails with
 * "Game version not set" (spec decision 6).</p>
 */
class BiomeSlotTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** A fresh {@code Holder.direct(null)} stub — each call returns a new reference. */
    private static Holder<Biome> stubHolder() {
        return Holder.direct(null);
    }

    /** A variant covering the full climate range so any climate sample matches. */
    private static BiomeVariant fullRangeVariant(Holder<Biome> biome) {
        return new BiomeVariant(biome, 0.0F, 1.0F, 0.0F, 1.0F);
    }

    // ----- EMPTY constant -------------------------------------------------

    @Test
    void emptyHasNullBase() {
        assertNull(BiomeSlot.EMPTY.base(),
                "EMPTY slot's base must be null (inherit-from-geometry sentinel)");
    }

    @Test
    void emptyHasNoVariants() {
        assertFalse(BiomeSlot.EMPTY.hasVariants(),
                "EMPTY slot must have no variants");
    }

    // ----- hasVariants predicate ------------------------------------------

    @Test
    void slotWithNonEmptyVariantsReportsHasVariants() {
        BiomeSlot slot = new BiomeSlot(stubHolder(), List.of(fullRangeVariant(stubHolder())));
        assertTrue(slot.hasVariants(),
                "slot with a non-empty variants list must report hasVariants");
    }

    @Test
    void slotWithExplicitEmptyVariantsReportsNoVariants() {
        BiomeSlot slot = new BiomeSlot(stubHolder(), List.of());
        assertFalse(slot.hasVariants(),
                "slot with an explicit empty variants list must report no variants");
    }

    // ----- defensive copy -------------------------------------------------

    @Test
    void variantsListIsDefensivelyCopied() {
        // A mutable source list is passed in; the slot must not retain a
        // reference to it. Mutating the source after construction must not
        // affect the slot — otherwise a preset author could accidentally
        // mutate the slot's internals via a retained alias.
        List<BiomeVariant> source = new ArrayList<>();
        source.add(fullRangeVariant(stubHolder()));
        BiomeSlot slot = new BiomeSlot(stubHolder(), source);

        source.add(fullRangeVariant(stubHolder()));
        source.clear();

        assertEquals(1, slot.variants().size(),
                "slot.variants() must be a defensive copy, immune to source mutation");
    }

    @Test
    void variantsListIsUnmodifiable() {
        BiomeSlot slot = new BiomeSlot(stubHolder(), List.of(fullRangeVariant(stubHolder())));
        assertThrows(UnsupportedOperationException.class,
                () -> slot.variants().add(fullRangeVariant(stubHolder())),
                "slot.variants() must be unmodifiable (List.copyOf contract)");
    }

    // ----- reference identity ---------------------------------------------

    @Test
    void baseHolderIsPreservedByReferenceIdentity() {
        // Holder.direct(null) stubs all collide under equals() (their values
        // are null), so the only sound assertion is reference identity.
        Holder<Biome> base = stubHolder();
        BiomeSlot slot = new BiomeSlot(base, List.of());
        assertSame(base, slot.base(),
                "base holder must be the exact reference passed in (no wrapping, no copy)");
    }
}
