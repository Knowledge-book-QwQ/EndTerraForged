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

/**
 * Tunable parameters for the hydraulic droplet erosion filter.
 *
 * <p>These map one-to-one onto the fields exposed in the RTF preset editor;
 * the stage-5 GUI will bind sliders to them directly. Defaults are classical
 * droplet-erosion values and can be overridden per-dimension via the preset.</p>
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
}
