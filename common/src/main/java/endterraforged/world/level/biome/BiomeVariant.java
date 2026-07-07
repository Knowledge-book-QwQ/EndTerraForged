/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). No RTF equivalent —
 * RTF's biome system rides on vanilla MultiNoiseBiomeSource with TerraBlender;
 * the End uses a geometric EndBiomeSource and never consults climate. This
 * layer adds an optional climate-variant selector on top of the geometric
 * rings, fed by EndTerraForged's own EndClimate field.
 */
package endterraforged.world.level.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * A climate-gated biome variant: a {@link Holder<Biome>} that applies when the
 * End's {@link endterraforged.world.climate.EndClimate} temperature and
 * moisture channels at a cell fall within this variant's closed ranges.
 *
 * <p><b>Matching semantics.</b> Both axes are <em>closed</em> intervals
 * {@code [min, max]}. {@link #matches(float, float)} returns {@code true} iff
 * the temperature sample is in {@code [tempMin, tempMax]} <em>and</em> the
 * moisture sample is in {@code [moistMin, moistMax]}. Overlapping variants in
 * a {@link BiomeSlot} are resolved by list order — the first match wins — so a
 * preset author controls precedence by ordering. This makes the contract
 * explicit and predictable (no hidden priority rules) and keeps the match
 * predicate branch-free for the hot path.</p>
 *
 * <p><b>Range validation.</b> {@code min <= max} is enforced at decode time
 * via {@link Codec#validate}: a variant with a reversed range fails decode
 * rather than silently matching nothing. All four bounds are clamped to
 * {@code [0, 1]} because EndClimate channels are normalised to that range.</p>
 *
 * <p><b>Thread safety.</b> A record of immutable primitives + an immutable
 * biome holder; safe to share across parallel chunk-gen threads.</p>
 *
 * @param biome    the biome this variant selects
 * @param tempMin  inclusive lower temperature bound, clamped to {@code [0,1]}
 * @param tempMax  inclusive upper temperature bound, clamped to {@code [0,1]}
 * @param moistMin inclusive lower moisture bound, clamped to {@code [0,1]}
 * @param moistMax inclusive upper moisture bound, clamped to {@code [0,1]}
 */
public record BiomeVariant(Holder<Biome> biome,
                           float tempMin, float tempMax,
                           float moistMin, float moistMax) {

    /**
     * DFU codec for {@link BiomeVariant}. Bounds are clamped to {@code [0,1]}
     * via {@link Codec#floatRange} (which also rejects NaN), and a
     * {@link Codec#validate} step rejects ranges where {@code min > max} so a
     * typo'd preset cannot silently produce an unsatisfiable variant.
     */
    public static final Codec<BiomeVariant> CODEC = RecordCodecBuilder.<BiomeVariant>create(
            instance -> instance.group(
                    Biome.CODEC.fieldOf("biome").forGetter(BiomeVariant::biome),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("temperature_min").forGetter(BiomeVariant::tempMin),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("temperature_max").forGetter(BiomeVariant::tempMax),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("moisture_min").forGetter(BiomeVariant::moistMin),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("moisture_max").forGetter(BiomeVariant::moistMax)
            ).apply(instance, instance.stable(BiomeVariant::new))
    ).validate(v -> {
        if (v.tempMin > v.tempMax) {
            return com.mojang.serialization.DataResult.error(() ->
                    "temperature_min > temperature_max: " + v.tempMin + " > " + v.tempMax);
        }
        if (v.moistMin > v.moistMax) {
            return com.mojang.serialization.DataResult.error(() ->
                    "moisture_min > moisture_max: " + v.moistMin + " > " + v.moistMax);
        }
        return com.mojang.serialization.DataResult.success(v);
    });

    /**
     * Whether a {@code (temperature, moisture)} climate sample falls in this
     * variant's closed ranges. Both axes must match. Used by
     * {@link EndBiomeSelector} on the hot path — kept branch-light.
     *
     * @param temperature EndClimate temperature sample in {@code [0,1]}
     * @param moisture    EndClimate moisture sample in {@code [0,1]}
     * @return {@code true} iff both samples are within this variant's ranges
     */
    public boolean matches(float temperature, float moisture) {
        return temperature >= tempMin && temperature <= tempMax
                && moisture >= moistMin && moisture <= moistMax;
    }
}
