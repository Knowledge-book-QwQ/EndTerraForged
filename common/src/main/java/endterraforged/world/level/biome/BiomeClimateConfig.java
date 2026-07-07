/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). No RTF equivalent —
 * RTF's biome system rides on vanilla MultiNoiseBiomeSource with TerraBlender;
 * the End uses a geometric EndBiomeSource and never consults climate. This
 * type bundles one BiomeSlot per geometric ring into a single codec field.
 */
package endterraforged.world.level.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * The five-ring climate-variant configuration for {@link EndBiomeSource}: one
 * {@link BiomeSlot} per geometric ring (end / highlands / midlands / islands /
 * barrens). An optional {@code biome_climate} field on the source's codec
 * decodes to this type; absent → {@link #EMPTY}, and the source behaves as
 * vanilla's geometric ring source (no climate sampling).
 *
 * <p><b>Two-layer orthogonality.</b> The geometric ring layer (radial falloff
 * + simplex perturbation in {@code EndBiomeSource}) selects which ring a cell
 * belongs to. The climate layer (this config + {@link EndBiomeSelector}) then
 * optionally selects a climate-gated variant <em>within</em> that ring. The
 * two layers are independent: a ring with an empty slot is pure geometry, and
 * a ring with variants still falls back to {@code base} when no variant
 * matches the local climate sample.</p>
 *
 * <p><b>Resolve-once contract.</b> A decoded preset may leave a slot's
 * {@code base} null (inherit from the geometric source). {@link #resolve}
 * fills those nulls with the source's five canonical holders exactly once at
 * {@code EndBiomeSource} construction. The resolved config is what the hot
 * path reads — every slot is non-null-base, no per-cell null checks.</p>
 *
 * <p><b>Thread safety.</b> Immutable record of immutable {@link BiomeSlot}s;
 * safe to share across parallel chunk-gen threads after {@link #resolve}.</p>
 *
 * @param end       slot for the central main-island ring
 * @param highlands slot for the inner highlands ring
 * @param midlands  slot for the midlands ring
 * @param islands   slot for the small-end-islands outer band
 * @param barrens   slot for the end-barrens outer band
 */
public record BiomeClimateConfig(BiomeSlot end, BiomeSlot highlands, BiomeSlot midlands,
                                 BiomeSlot islands, BiomeSlot barrens) {

    /**
     * Canonical no-op config: five {@link BiomeSlot#EMPTY} slots. The codec
     * default for an omitted {@code biome_climate} field, so a vanilla-style
     * biome-source JSON decodes to this and the source runs in pure-geometry
     * mode (no climate sampling, performance identical to vanilla).
     */
    public static final BiomeClimateConfig EMPTY =
            new BiomeClimateConfig(BiomeSlot.EMPTY, BiomeSlot.EMPTY, BiomeSlot.EMPTY,
                    BiomeSlot.EMPTY, BiomeSlot.EMPTY);

    /**
     * DFU codec — five optional {@code BiomeSlot} fields, each defaulting to
     * {@link BiomeSlot#EMPTY}. A preset may override any subset of rings;
     * omitted rings inherit vanilla geometric behaviour. The per-slot codec
     * delegates to {@link BiomeSlot#CODEC} which in turn delegates variant
     * validation to {@link BiomeVariant#CODEC}.
     */
    public static final Codec<BiomeClimateConfig> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    BiomeSlot.CODEC.optionalFieldOf("end", BiomeSlot.EMPTY)
                            .forGetter(BiomeClimateConfig::end),
                    BiomeSlot.CODEC.optionalFieldOf("highlands", BiomeSlot.EMPTY)
                            .forGetter(BiomeClimateConfig::highlands),
                    BiomeSlot.CODEC.optionalFieldOf("midlands", BiomeSlot.EMPTY)
                            .forGetter(BiomeClimateConfig::midlands),
                    BiomeSlot.CODEC.optionalFieldOf("islands", BiomeSlot.EMPTY)
                            .forGetter(BiomeClimateConfig::islands),
                    BiomeSlot.CODEC.optionalFieldOf("barrens", BiomeSlot.EMPTY)
                            .forGetter(BiomeClimateConfig::barrens)
            ).apply(instance, instance.stable(BiomeClimateConfig::new))
    );

    /**
     * Whether any ring has climate-gated variants. {@code EndBiomeSource}'s
     * hot path uses this as its first fast-path gate: a config with no
     * variants anywhere skips {@code EndClimateAccess} lookup and climate
     * sampling entirely, matching vanilla performance.
     *
     * @return {@code true} iff at least one of the five slots has variants
     */
    public boolean hasAnyVariants() {
        return end.hasVariants() || highlands.hasVariants() || midlands.hasVariants()
                || islands.hasVariants() || barrens.hasVariants();
    }

    /**
     * Returns a config with each slot's null {@code base} filled from the
     * corresponding geometric-ring holder. Slots whose base is already
     * non-null are returned as-is (no allocation). Variants are untouched.
     *
     * <p>This is called once at {@code EndBiomeSource} construction. The
     * returned config is the one stored on the source and read by the hot
     * path — every slot's base is guaranteed non-null afterwards.</p>
     *
     * @param end       geometric-ring holder for the central main island
     * @param highlands geometric-ring holder for the highlands ring
     * @param midlands  geometric-ring holder for the midlands ring
     * @param islands   geometric-ring holder for the small-end-islands band
     * @param barrens   geometric-ring holder for the end-barrens band
     * @return a config with all null bases filled; non-null bases preserved
     */
    public BiomeClimateConfig resolve(Holder<Biome> end, Holder<Biome> highlands,
                                      Holder<Biome> midlands, Holder<Biome> islands,
                                      Holder<Biome> barrens) {
        return new BiomeClimateConfig(
                resolveSlot(this.end, end),
                resolveSlot(this.highlands, highlands),
                resolveSlot(this.midlands, midlands),
                resolveSlot(this.islands, islands),
                resolveSlot(this.barrens, barrens)
        );
    }

    /**
     * Fills a slot's null base with the given holder; returns the slot
     * unchanged if its base is already non-null. The variants list is passed
     * through to the new slot unchanged (the {@link BiomeSlot} compact
     * constructor's {@code List.copyOf} on an already-unmodifiable list is a
     * fast no-op in the JDK's immutable {@code ListN} implementation).
     */
    private static BiomeSlot resolveSlot(BiomeSlot slot, Holder<Biome> base) {
        return slot.base() == null ? new BiomeSlot(base, slot.variants()) : slot;
    }
}
