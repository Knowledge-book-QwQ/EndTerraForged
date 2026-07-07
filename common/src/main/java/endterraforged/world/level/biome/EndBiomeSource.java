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
 * class follows the same design for the <em>ring</em> layer: the
 * {@link Climate.Sampler} parameter is accepted (1.21.1 signature) but
 * unused for ring selection.</p>
 *
 * <p><b>Two-layer orthogonality: geometric ring × optional climate
 * variant.</b> The ring layer (radial falloff + simplex perturbation
 * below) selects which of the five geometric rings a cell belongs to.
 * An optional {@code biome_climate} codec field then selects a
 * climate-gated variant <em>within</em> that ring via
 * {@link EndBiomeSelector}, sampling {@link endterraforged.world.climate.EndClimate}
 * (published by {@link endterraforged.world.climate.EndClimateAccess}).
 * The two layers are independent: an omitted {@code biome_climate}
 * decodes to {@link BiomeClimateConfig#EMPTY}, {@code hasAnyVariants()}
 * is false, the hot path skips climate sampling entirely, and behaviour
 * — and performance — match vanilla. A ring with variants still falls
 * back to its {@code base} when no variant matches the local climate.</p>
 *
 * <p><b>Codec design.</b> The five biome holders are explicit
 * {@code Holder<Biome>} fields serialised by the codec — this lets the
 * {@code biome_source} JSON name each biome explicitly
 * ({@code "end": "minecraft:the_end", ...}) rather than relying on vanilla's
 * bootstrap-ordered {@code create(BootstrapContext)} factory. The trade-off
 * is a longer JSON, but the source is then independent of biome-registry
 * load order, which matters for Architectury cross-loader stability. The
 * sixth field, {@code biome_climate}, is an
 * {@code optionalFieldOf(..., BiomeClimateConfig.EMPTY)} so existing
 * 5-field JSON continues to decode unchanged.</p>
 *
 * <p><b>Thread safety.</b> All fields are immutable after construction;
 * the simplex noises and the resolved {@link BiomeClimateConfig} are
 * safe to query from parallel chunk-gen threads. {@link #getNoiseBiome}
 * is stateless beyond the constant seed baked into the simplex at
 * construction. The climate field is read from
 * {@link endterraforged.world.climate.EndClimateAccess} — a
 * {@code volatile}-published immutable record, so worker threads see a
 * consistent snapshot without locking.</p>
 */
public class EndBiomeSource extends BiomeSource {

    public static final String NAME = "end_biome_source";

    /**
     * Codec — five explicit biome holder fields plus an optional
     * {@code biome_climate} config. JSON example:
     * <pre>{@code
     * {
     *   "type": "endterraforged:end_biome_source",
     *   "end": "minecraft:the_end",
     *   "highlands": "minecraft:end_highlands",
     *   "midlands": "minecraft:end_midlands",
     *   "islands": "minecraft:small_end_islands",
     *   "barrens": "minecraft:end_barrens",
     *   "biome_climate": {
     *     "highlands": {
     *       "variants": [
     *         {"biome": "minecraft:modified_end_highlands", "temperature_min": 0.6, "temperature_max": 1.0}
     *       ]
     *     }
     *   }
     * }
     * }</pre>
     * The {@code biome_climate} field is an
     * {@code optionalFieldOf(..., BiomeClimateConfig.EMPTY)} so a
     * vanilla-style 5-field JSON continues to decode unchanged.
     */
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
     * Resolved climate-variant config: each slot's {@code base} is
     * guaranteed non-null (filled from the corresponding ring holder
     * above by {@link BiomeClimateConfig#resolve} at construction). The
     * hot path reads this without per-cell null checks.
     */
    private final BiomeClimateConfig biomeClimate;

    /**
     * Two simplex channels for ring-edge raggedness. Seed is fixed at
     * construction (not world-seed-driven) — biome layout is intentionally
     * seed-independent in this first version so it matches vanilla's
     * geometry regardless of world seed. A later Mixin-injected variant
     * can rebind these to the world seed.
     */
    private final Noise ringNoise;      // perturbs the falloff
    private final Noise outerNoise;     // splits islands vs barrens in the outer band

    /**
     * Two simplex channels producing a stable sub-cell fractional position
     * in {@code [0,1]} for the 4-corner bilinear blend in
     * {@link EndBiomeSelector}. Vanilla's biome API only gives integer
     * cell coordinates, so without these a single cell would sample its
     * top-left corner's climate only, producing a 4-block staircase at
     * climate boundaries. The fractional offset lets the bilinear blend
     * weight the four cell corners smoothly. Fixed seed (this stage does
     * not bind biome layout to the world seed — see {@link #ringNoise}).
     */
    private final Noise fracNoiseX;
    private final Noise fracNoiseZ;

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
        // Fill any null bases from the geometric-ring holders exactly once
        // at construction. The stored config is the one read by the hot
        // path — every slot base is non-null afterwards.
        this.biomeClimate = biomeClimate.resolve(end, highlands, midlands, islands, barrens);
        // Fixed seeds 1337..1340 — see class doc on the seed-independence decision.
        this.ringNoise = Noises.simplex(1337, 200, 4);
        this.outerNoise = Noises.simplex(1338, 400, 4);
        // Tighter scale (50) and fewer octaves (2) than the ring noises so
        // the fractional position varies smoothly within a cell rather than
        // aliasing to a near-constant.
        this.fracNoiseX = Noises.map(Noises.simplex(1339, 50, 2), 0.0F, 1.0F);
        this.fracNoiseZ = Noises.map(Noises.simplex(1340, 50, 2), 0.0F, 1.0F);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    /**
     * The full set of biomes this source can return — required by
     * {@link BiomeSource} so vanilla can pre-build biome-dependent
     * lookup tables (surface rules, feature placement) without sampling
     * every cell. Returns the five ring holders plus every variant biome
     * attached through {@code biome_climate}, so a preset that swaps in
     * climate-gated sub-types still gets its surface rules pre-built.
     */
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

    /**
     * Picks the End biome at the given biome coordinate (1 unit = 4 blocks).
     *
     * <p>The {@code sampler} parameter is accepted for the 1.21.1 signature
     * but unused for ring selection — the End's vanilla climate router is
     * zeroed and the ring layout is purely geometric. Climate-gated
     * variants within a ring are selected by sampling
     * {@link endterraforged.world.climate.EndClimate} (published by
     * {@link endterraforged.world.climate.EndClimateAccess}), not the
     * vanilla sampler. See class doc.</p>
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
        // The end ring is still routed through selectRing so a preset that
        // gates the central island on climate can do so — but the typical
        // preset leaves the end slot empty (vanilla behaviour).
        if (x * x + z * z < MAIN_ISLAND_RADIUS * MAIN_ISLAND_RADIUS) {
            return selectRing(x, z, this.biomeClimate.end());
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
            return selectRing(x, z, this.biomeClimate.highlands());
        }
        if (perturbed >= MIDLAND_FLOOR) {
            return selectRing(x, z, this.biomeClimate.midlands());
        }

        // Outer band (perturbed < 0): split between sparse far islets and
        // empty void. Use a coarser simplex so the islet patches are large
        // enough to read as biomes rather than speckle.
        float outer = this.outerNoise.compute(x, z, 0);
        if (outer > 0.2F) {
            return selectRing(x, z, this.biomeClimate.islands());
        }
        return selectRing(x, z, this.biomeClimate.barrens());
    }

    /**
     * Selects the biome for a single ring cell, applying the two-layer
     * fast-path contract:
     *
     * <ol>
     *   <li><b>Fast-path 1 — no variants.</b> If the ring's slot has no
     *       climate-gated variants, return its base immediately. This is
     *       the path taken by a vanilla-style config
     *       ({@link BiomeClimateConfig#EMPTY}) and is performance-equivalent
     *       to the pre-climate source: no
     *       {@link endterraforged.world.climate.EndClimateAccess} lookup,
     *       no climate sampling, no bilinear blend.</li>
     *   <li><b>Fast-path 2 — no climate published.</b> If
     *       {@link endterraforged.world.climate.EndClimateAccess#get}
     *       returns {@code null} (no End dimension loaded, or a unit test
     *       that has not published one), return the slot's base. This
     *       preserves vanilla behaviour when the climate layer is not
     *       wired.</li>
     *   <li><b>Slow path — delegate.</b> Sample the sub-cell fractional
     *       position from {@link #fracNoiseX}/{@link #fracNoiseZ} and
     *       delegate to {@link EndBiomeSelector#select}, which performs
     *       the 4-corner bilinear blend.</li>
     * </ol>
     *
     * <p>The per-call {@code seed} passed to {@link EndBiomeSelector#select}
     * is {@code 0}: the world seed is already baked into the
     * {@link endterraforged.world.climate.EndClimate} noise trees at
     * {@code MixinRandomState} construction time, so the per-call seed
     * is vestigial.</p>
     */
    private Holder<Biome> selectRing(int x, int z, BiomeSlot slot) {
        // Fast-path 1: no variants → no climate work at all. This is the
        // path a vanilla-style config takes, and matches pre-climate perf.
        if (!slot.hasVariants()) {
            return slot.base();
        }
        // Fast-path 2: variants exist but no climate has been published
        // (e.g. overworld-only server, or a unit test that didn't call
        // EndClimateAccess.set). Fall back to base — vanilla behaviour.
        endterraforged.world.climate.EndClimate climate =
                endterraforged.world.climate.EndClimateAccess.get();
        if (climate == null) {
            return slot.base();
        }
        // Slow path: 4-corner bilinear blend. The fractional position
        // turns the integer cell coordinate into a smooth sub-cell sample
        // so climate boundaries don't form 4-block staircases.
        float fracX = this.fracNoiseX.compute(x, z, 0);
        float fracZ = this.fracNoiseZ.compute(x, z, 0);
        return EndBiomeSelector.select(x, z, fracX, fracZ, climate, 0, slot);
    }
}
