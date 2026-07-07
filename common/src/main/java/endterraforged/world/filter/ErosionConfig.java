/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged (MIT) into EndTerraForged (LGPL-3.0-or-later).
 * Lineage: TerraForged (dags) -> ReTerraForged (raccoonman) -> EndTerraForged.
 *
 * Pure-POJO adaptation of upstream FilterSettings.Erosion: the Codec binding
 * is deferred to stage 3 when the preset/serialisation layer is introduced,
 * so the erosion algorithm and its unit tests stay free of Minecraft/DFU
 * dependencies. The misspelled upstream field "depositeRate" is corrected to
 * "depositRate"; this project ships its own presets and shares no config
 * files with RTF, so the rename carries no compatibility cost.
 */
package endterraforged.world.filter;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Tunable parameters for the hydraulic droplet erosion filter.
 *
 * <p>These map one-to-one onto the fields exposed in the RTF preset editor;
 * the stage-5 GUI will bind sliders to them directly. Defaults are classical
 * droplet-erosion values and can be overridden per-dimension via the preset.</p>
 *
 * <p><b>Serialisation.</b> {@link #CODEC} is a {@link RecordCodecBuilder} over
 * the six primitive fields, so an {@code ErosionConfig} serialises as a flat
 * JSON object. It is embedded inside {@code EndPreset}'s codec (stage 5.1) so
 * erosion tuning rides along with the dimension profile. The class is a plain
 * immutable class (not a record) because the erosion hot path reads public
 * fields directly — the codec bridges that with field-accessor lambdas.</p>
 *
 * <p><b>Thread safety:</b> immutable once constructed (fields are primitive
 * and not mutated by the filter — it only reads them). Safe to share a single
 * instance across parallel tile generators.</p>
 */
public final class ErosionConfig {
    public static final ErosionConfig DEFAULT = new ErosionConfig(
            128,    // dropletsPerChunk
            32,     // dropletLifetime
            1.0F,   // dropletVolume
            1.0F,   // dropletVelocity
            0.5F,   // erosionRate
            0.5F    // depositRate
    );

    /**
     * DFU codec for {@link ErosionConfig}. Each field maps to a same-named JSON
     * key; getters read the public fields directly. {@code instance.stable}
     * wraps the canonical constructor so decode errors surface as failed
     * {@link com.mojang.serialization.DataResult}s rather than exceptions.
     *
     * <p>The builder is wrapped with {@link Codec#flatXmap} through
     * {@link ErosionConfigValidator#validate} on the decode side, so a
     * structurally-decoded {@link ErosionConfig} with out-of-range fields
     * (e.g. {@code "erosion_rate": 1.5}) surfaces as a {@link DataResult#error}
     * with a field-specific message, not a silent value that crashes the
     * hydrology simulation later. The encode side is identity
     * ({@link DataResult#success}) because a valid {@link ErosionConfig}
     * already satisfies the constraints by construction.</p>
     */
    private static final Codec<ErosionConfig> BASE_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.INT.optionalFieldOf("droplets_per_chunk", 128)
                            .forGetter(c -> c.dropletsPerChunk),
                    Codec.INT.optionalFieldOf("droplet_lifetime", 32)
                            .forGetter(c -> c.dropletLifetime),
                    Codec.FLOAT.optionalFieldOf("droplet_volume", 1.0F)
                            .forGetter(c -> c.dropletVolume),
                    Codec.FLOAT.optionalFieldOf("droplet_velocity", 1.0F)
                            .forGetter(c -> c.dropletVelocity),
                    Codec.FLOAT.optionalFieldOf("erosion_rate", 0.5F)
                            .forGetter(c -> c.erosionRate),
                    Codec.FLOAT.optionalFieldOf("deposit_rate", 0.5F)
                            .forGetter(c -> c.depositRate)
            ).apply(instance, instance.stable(ErosionConfig::new))
    );

    /**
     * Public {@link Codec} for {@link ErosionConfig}. Wraps {@link #BASE_CODEC}
     * with {@link ErosionConfigValidator#validate} via {@link Codec#flatXmap}
     * so decode-time constraint violations surface as {@link DataResult#error}
     * instead of crashing the hydrology simulation later.
     */
    public static final Codec<ErosionConfig> CODEC = BASE_CODEC.flatXmap(
            ErosionConfigValidator::validate,
            config -> DataResult.success(config));

    /** Number of droplets simulated per chunk per apply() call. */
    public final int dropletsPerChunk;

    /** Maximum steps a droplet travels before expiring. */
    public final int dropletLifetime;

    /** Initial water volume carried by each droplet. */
    public final float dropletVolume;

    /** Initial speed of each droplet. */
    public final float dropletVelocity;

    /** Fraction of excess capacity removed from the terrain per erosion step. */
    public final float erosionRate;

    /** Fraction of oversaturated sediment deposited per step. */
    public final float depositRate;

    public ErosionConfig(int dropletsPerChunk, int dropletLifetime, float dropletVolume,
                         float dropletVelocity, float erosionRate, float depositRate) {
        this.dropletsPerChunk = dropletsPerChunk;
        this.dropletLifetime = dropletLifetime;
        this.dropletVolume = dropletVolume;
        this.dropletVelocity = dropletVelocity;
        this.erosionRate = erosionRate;
        this.depositRate = depositRate;
    }

    public ErosionConfig copy() {
        return new ErosionConfig(dropletsPerChunk, dropletLifetime, dropletVolume,
                dropletVelocity, erosionRate, depositRate);
    }

    /**
     * Value equality over the six fields — needed so an {@link EndPreset}
     * round-tripped through its codec compares equal to the original. Without
     * this, record equality on {@code EndPreset} would fall back to reference
     * identity for the {@code erosionConfig} component and a decoded preset
     * would never equal its source.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ErosionConfig that)) {
            return false;
        }
        return this.dropletsPerChunk == that.dropletsPerChunk
                && this.dropletLifetime == that.dropletLifetime
                && this.dropletVolume == that.dropletVolume
                && this.dropletVelocity == that.dropletVelocity
                && this.erosionRate == that.erosionRate
                && this.depositRate == that.depositRate;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dropletsPerChunk, dropletLifetime, dropletVolume,
                dropletVelocity, erosionRate, depositRate);
    }
}
