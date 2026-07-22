/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later).
 */
package endterraforged.world.level.biome;

import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Biome source for the End's geometric ring layout plus optional climate
 * variants inside each ring.
 */
public class EndBiomeSource extends BiomeSource {

    public static final String NAME = "end_biome_source";

    public static final MapCodec<EndBiomeSource> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Biome.CODEC.fieldOf("end").forGetter(s -> s.end),
                    Biome.CODEC.fieldOf("highlands").forGetter(s -> s.highlands),
                    Biome.CODEC.fieldOf("midlands").forGetter(s -> s.midlands),
                    Biome.CODEC.fieldOf("islands").forGetter(s -> s.islands),
                    Biome.CODEC.fieldOf("barrens").forGetter(s -> s.barrens),
                    BiomeClimateConfig.CODEC.optionalFieldOf("biome_climate", BiomeClimateConfig.EMPTY)
                            .forGetter(s -> s.biomeClimate)
            ).apply(instance, instance.stable(EndBiomeSource::new))
    );

    private final Holder<Biome> end;
    private final Holder<Biome> highlands;
    private final Holder<Biome> midlands;
    private final Holder<Biome> islands;
    private final Holder<Biome> barrens;
    private final BiomeClimateConfig biomeClimate;

    public EndBiomeSource(Holder<Biome> end, Holder<Biome> highlands,
                          Holder<Biome> midlands, Holder<Biome> islands,
                          Holder<Biome> barrens) {
        this(end, highlands, midlands, islands, barrens, BiomeClimateConfig.EMPTY);
    }

    public EndBiomeSource(Holder<Biome> end, Holder<Biome> highlands,
                          Holder<Biome> midlands, Holder<Biome> islands,
                          Holder<Biome> barrens, BiomeClimateConfig biomeClimate) {
        this.end = end;
        this.highlands = highlands;
        this.midlands = midlands;
        this.islands = islands;
        this.barrens = barrens;
        this.biomeClimate = biomeClimate.resolve(end, highlands, midlands, islands, barrens);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        Stream<Holder<Biome>> rings = Stream.of(this.end, this.highlands, this.midlands,
                this.islands, this.barrens);
        Stream<Holder<Biome>> variants = Stream.of(
                this.biomeClimate.end(), this.biomeClimate.highlands(),
                this.biomeClimate.midlands(), this.biomeClimate.islands(),
                this.biomeClimate.barrens()
        ).flatMap(slot -> slot.variants().stream().map(BiomeVariant::biome));
        return Stream.concat(rings, variants);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        EndBiomeLayout layout = EndBiomeLayoutAccess.getOrDefault();
        return switch (layout.ringAt(x, z)) {
            case END -> selectRing(x, z, layout, this.biomeClimate.end());
            case HIGHLANDS -> selectRing(x, z, layout, this.biomeClimate.highlands());
            case MIDLANDS -> selectRing(x, z, layout, this.biomeClimate.midlands());
            case ISLANDS -> selectRing(x, z, layout, this.biomeClimate.islands());
            case BARRENS -> selectRing(x, z, layout, this.biomeClimate.barrens());
        };
    }

    private Holder<Biome> selectRing(int x, int z, EndBiomeLayout layout, BiomeSlot slot) {
        if (!slot.hasVariants()) {
            return slot.base();
        }
        endterraforged.world.climate.EndClimate climate =
                endterraforged.world.climate.EndClimateAccess.get();
        if (climate == null) {
            return slot.base();
        }
        return EndBiomeSelector.select(x, z, layout.fracX(x, z), layout.fracZ(x, z),
                climate, 0, slot);
    }
}
