/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Informed by vanilla's
 * TheEndBiomeSource (geometric ring segmentation) — but the biome set is
 * codec-driven (explicit Holder<Biome> fields) so the source is decoupled
 * from vanilla's bootstrap order and can be customised per-world.
 */
package endterraforged.world.level.biome;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.Set;
import java.util.stream.Stream;

import endterraforged.world.noise.Noise;
import endterraforged.world.noise.Noises;

/**
 * The End's biome source: geometric ring segmentation around the world
 * origin, producing the vanilla End biome layout (highlands → midlands →
 * barrens/islands) but driven by EndTerraForged's own simplex noise so the
 * ring edges can be tuned independently of vanilla's seed.
 *
 * <p><b>Layout algorithm.</b> A radial falloff {@code 100 - 8·sqrt(x²+z²)}
 * (in biome coordinates, where 1 unit = 4 blocks) defines concentric rings:
 * {@code > 40} → {@code highlands}, {@code [0, 40]} → {@code midlands},
 * {@code < 0} → outer void. The falloff is perturbed by two simplex noise
 * channels so the ring edges are ragged rather than perfectly circular.
 * Inside the {@code < 0} outer band, a second noise test splits the area
 * between {@code small_end_islands} (sparse far islets) and
 * {@code end_barrens} (empty void). Near the world origin (radius below
 * {@link #MAIN_ISLAND_RADIUS}) the source returns {@code the_end} — the
 * central main-island biome.</p>
 *
 * <p><b>Why geometric, not climate-driven.</b> Vanilla's End uses
 * {@code TheEndBiomeSource} (geometry) not {@code MultiNoiseBiomeSource}
 * (climate), because the End's {@code noise_settings} router leaves the
 * climate channels at zero — there is no climate signal to consume. This
 * class follows the same design: the {@link Climate.Sampler} parameter is
 * accepted (1.21.1 signature) but unused. Stage 2.5c may later add an
 * optional climate-variant selector that samples EndTerraForged's own
 * {@link endterraforged.world.climate.EndClimate} field, but that requires
 * seed injection (a Mixin on the chunk generator) and is deferred.</p>
 *
 * <p><b>Codec design.</b> The five biome holders are explicit
 * {@code Holder<Biome>} fields serialised by the codec — this lets the
 * {@code biome_source} JSON name each biome explicitly
 * ({@code "end": "minecraft:the_end", ...}) rather than relying on vanilla's
 * bootstrap-ordered {@code create(BootstrapContext)} factory. The trade-off
 * is a longer JSON, but the source is then independent of biome-registry
 * load order, which matters for Architectury cross-loader stability.</p>
 *
 * <p><b>Thread safety.</b> All fields are immutable after construction;
 * the two simplex noises are records and safe to query from parallel
 * chunk-gen threads. {@link #getNoiseBiome} is stateless beyond the
 * constant seed baked into the simplex at construction.</p>
 */
public class EndBiomeSource extends BiomeSource {

    public static final String NAME = "end_biome_source";

    /**
     * Codec — five explicit biome holder fields. JSON example:
     * <pre>{@code
     * {
     *   "type": "endterraforged:end_biome_source",
     *   "end": "minecraft:the_end",
     *   "highlands": "minecraft:end_highlands",
     *   "midlands": "minecraft:end_midlands",
     *   "islands": "minecraft:small_end_islands",
     *   "barrens": "minecraft:end_barrens"
     * }
     * }</pre>
     */
    public static final MapCodec<EndBiomeSource> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Biome.CODEC.fieldOf("end").forGetter(s -> s.end),
                    Biome.CODEC.fieldOf("highlands").forGetter(s -> s.highlands),
                    Biome.CODEC.fieldOf("midlands").forGetter(s -> s.midlands),
                    Biome.CODEC.fieldOf("islands").forGetter(s -> s.islands),
                    Biome.CODEC.fieldOf("barrens").forGetter(s -> s.barrens)
            ).apply(instance, instance.stable(EndBiomeSource::new))
    );

    /** Biome-coordinate radius inside which the central main-island biome applies. */
    private static final int MAIN_ISLAND_RADIUS = 18;  // ~72 blocks; vanilla main island ≈ 64-block radius

    /** Radial falloff coefficient — matches vanilla TheEndBiomeSource. */
    private static final float RADIAL_COEFF = 8.0F;

    /** Ring thresholds for highlands / midlands split — matches vanilla. */
    private static final float HIGHLAND_THRESHOLD = 40.0F;
    private static final float MIDLAND_FLOOR = 0.0F;

    /** Radial falloff clamp range — matches vanilla. */
    private static final float FALLOFF_MIN = -100.0F;
    private static final float FALLOFF_MAX = 80.0F;

    private final Holder<Biome> end;
    private final Holder<Biome> highlands;
    private final Holder<Biome> midlands;
    private final Holder<Biome> islands;
    private final Holder<Biome> barrens;

    /**
     * Two simplex channels for ring-edge raggedness. Seed is fixed at
     * construction (not world-seed-driven) — biome layout is intentionally
     * seed-independent in this first version so it matches vanilla's
     * geometry regardless of world seed. A later Mixin-injected variant
     * can rebind these to the world seed.
     */
    private final Noise ringNoise;      // perturbs the falloff
    private final Noise outerNoise;     // splits islands vs barrens in the outer band

    public EndBiomeSource(Holder<Biome> end, Holder<Biome> highlands,
                          Holder<Biome> midlands, Holder<Biome> islands,
                          Holder<Biome> barrens) {
        this.end = end;
        this.highlands = highlands;
        this.midlands = midlands;
        this.islands = islands;
        this.barrens = barrens;
        // Fixed seed 1337 — see class doc on the seed-independence decision.
        this.ringNoise = Noises.simplex(1337, 200, 4);
        this.outerNoise = Noises.simplex(1338, 400, 4);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    /**
     * The full set of biomes this source can return — required by
     * {@link BiomeSource} so vanilla can pre-build biome-dependent
     * lookup tables (surface rules, feature placement) without sampling
     * every cell. Returns a stream of the five holders.
     */
    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(this.end, this.highlands, this.midlands,
                this.islands, this.barrens);
    }

    /**
     * Picks the End biome at the given biome coordinate (1 unit = 4 blocks).
     *
     * <p>The {@code sampler} parameter is accepted for the 1.21.1 signature
     * but unused — the End's climate router is zeroed and the layout is
     * purely geometric. See class doc.</p>
     *
     * @param x  biome X (block X >> 2)
     * @param y  biome Y (block Y >> 2; unused — End layout is 2D)
     * @param z  biome Z (block Z >> 2)
     * @param sampler  vanilla climate sampler (unused)
     * @return the biome holder for this biome cell
     */
    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        // Central main island: short-circuit before the ring math so the
        // dragon island is always the_end biome regardless of noise.
        if (x * x + z * z < MAIN_ISLAND_RADIUS * MAIN_ISLAND_RADIUS) {
            return this.end;
        }

        // Radial falloff: 100 - 8·sqrt(x²+z²), clamped to [-100, 80].
        float dist = (float) Math.sqrt(x * x + z * z);
        float falloff = 100.0F - dist * RADIAL_COEFF;
        falloff = Mth.clamp(falloff, FALLOFF_MIN, FALLOFF_MAX);

        // Perturb the falloff with simplex so ring edges are ragged.
        // The noise is in [-1, 1]; scale by 15 so perturbation reaches
        // roughly ±15 falloff units — enough to make the 0 and 40
        // thresholds wiggle noticeably without breaking the ring structure.
        float perturbed = falloff + this.ringNoise.compute(x, z, 0) * 15.0F;

        if (perturbed > HIGHLAND_THRESHOLD) {
            return this.highlands;
        }
        if (perturbed >= MIDLAND_FLOOR) {
            return this.midlands;
        }

        // Outer band (perturbed < 0): split between sparse far islets and
        // empty void. Use a coarser simplex so the islet patches are large
        // enough to read as biomes rather than speckle.
        float outer = this.outerNoise.compute(x, z, 0);
        if (outer > 0.2F) {
            return this.islands;
        }
        return this.barrens;
    }
}
