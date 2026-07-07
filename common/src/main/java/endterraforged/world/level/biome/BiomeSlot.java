/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). No RTF equivalent —
 * RTF's biome system rides on vanilla MultiNoiseBiomeSource with TerraBlender;
 * the End uses a geometric EndBiomeSource and never consults climate. This
 * type pairs a geometric-ring biome with its optional climate-gated variants.
 */
package endterraforged.world.level.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * One geometric ring's biome slot in the {@link EndBiomeSource}: a nullable
 * {@code base} (the ring's default biome, inherited from the geometric layout
 * when the preset omits it) plus an ordered list of {@link BiomeVariant
 * climate-gated variants}.
 *
 * <p><b>Null base contract.</b> {@code base} may be {@code null} in a decoded
 * preset — the author lets the ring inherit its biome from the geometric
 * source's five canonical holders. {@link BiomeClimateConfig#resolve} fills
 * nulls once at {@code EndBiomeSource} construction, so the hot path never
 * null-checks. Tests that construct slots directly can use {@link #EMPTY}.</p>
 *
 * <p><b>Variant precedence.</b> {@code variants} is an ordered list; the
 * selector picks the <em>first</em> variant whose {@link BiomeVariant#matches}
 * returns true. A preset author controls precedence by ordering — no hidden
 * priority rules. An empty list means the ring has no climate variants, in
 * which case the selector short-circuits to {@code base}.</p>
 *
 * <p><b>Defensive copy.</b> The compact constructor runs {@code variants}
 * through {@link List#copyOf}, so the slot is immune to post-construction
 * mutation of the source list and is null-hostile (no variant may be null).
 * The resulting list is unmodifiable.</p>
 *
 * <p><b>Thread safety.</b> Immutable record of immutable holders + an
 * unmodifiable list; safe to share across parallel chunk-gen threads.</p>
 *
 * @param base     the ring's default biome; {@code null} means inherit from
 *                 the geometric source (resolved once at construction)
 * @param variants ordered climate-gated variants; first match wins
 */
public record BiomeSlot(Holder<Biome> base, List<BiomeVariant> variants) {

    /**
     * Canonical empty slot: null base, no variants. Used as the codec default
     * for omitted {@code biome_climate} sub-fields so an absent config decodes
     * to a no-op (the source falls back to vanilla geometric ring biomes).
     */
    public static final BiomeSlot EMPTY = new BiomeSlot(null, List.of());

    /**
     * DFU codec. {@code base} is an optional nullable field (absent → null,
     * i.e. inherit from the geometric ring biome); {@code variants} defaults
     * to an empty list. The list-of-variants codec delegates per-element
     * validation to {@link BiomeVariant#CODEC} (range clamping + min>max
     * rejection), so a malformed variant fails decode rather than silently
     * being dropped.
     */
    public static final Codec<BiomeSlot> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Biome.CODEC.optionalFieldOf("base", null).forGetter(BiomeSlot::base),
                    BiomeVariant.CODEC.listOf().optionalFieldOf("variants", List.of())
                            .forGetter(BiomeSlot::variants)
            ).apply(instance, instance.stable(BiomeSlot::new))
    );

    /**
     * Compact constructor — defensive copy of {@code variants}.
     *
     * @throws NullPointerException if any element of {@code variants} is null
     *         ({@link List#copyOf} is null-hostile)
     */
    public BiomeSlot {
        variants = List.copyOf(variants);
    }

    /**
     * Whether this slot has any climate-gated variants. The selector uses this
     * as its first fast-path gate: a slot with no variants always returns
     * {@code base}, skipping climate sampling entirely.
     *
     * @return {@code true} iff {@code variants} is non-empty
     */
    public boolean hasVariants() {
        return !variants.isEmpty();
    }
}
