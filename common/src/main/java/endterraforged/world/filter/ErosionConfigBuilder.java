/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Mutable builder for
 * {@link ErosionConfig}, mirroring {@link EndPresetBuilder}'s pattern so the
 * GUI's erosion sub-editor can mutate state while the user drags sliders.
 */
package endterraforged.world.filter;

import java.util.Objects;

import com.mojang.serialization.DataResult;

/**
 * Mutable builder for {@link ErosionConfig}, mirroring {@code EndPresetBuilder}'s
 * pattern: the GUI's erosion sub-editor binds sliders to the setters, and
 * {@link #build()} snapshots and validates the current state into an immutable
 * {@link ErosionConfig} for the worldgen pipeline.
 *
 * <p><b>Why this exists.</b> {@link ErosionConfig} is an immutable POJO with
 * public final fields, so a GUI editor cannot mutate it in place while the
 * user drags sliders. This builder mirrors the six fields as mutable
 * private fields with fluent setters; the GUI binds sliders to the setters,
 * and {@link #build()} snapshots and validates state into an immutable
 * {@link ErosionConfig}.</p>
 *
 * <p><b>Pure logic.</b> This class has zero Minecraft / DFU dependencies —
 * it is the testable core behind the (sandbox-untestable) Screen layer.
 * Unit tests verify round-trip ({@code builder(config).build() == config}),
 * {@link #reset()} restores defaults, and each setter stores the value.</p>
 *
 * <p><b>Thread safety.</b> Not thread-safe — GUI editing happens on the
 * render thread only. The resulting {@link ErosionConfig} is then handed to
 * the worldgen pipeline, which is the thread-safety boundary.</p>
 */
public final class ErosionConfigBuilder {

    private int dropletsPerChunk;
    private int dropletLifetime;
    private float dropletVolume;
    private float dropletVelocity;
    private float erosionRate;
    private float depositRate;

    /** Creates a builder initialised to {@link ErosionConfig#DEFAULT}. */
    public ErosionConfigBuilder() {
        this(ErosionConfig.DEFAULT);
    }

    /**
     * Creates a builder initialised to the given config's values — the GUI
     * loads an existing config for editing via this constructor.
     */
    public ErosionConfigBuilder(ErosionConfig source) {
        Objects.requireNonNull(source, "source");
        this.dropletsPerChunk = source.dropletsPerChunk;
        this.dropletLifetime = source.dropletLifetime;
        this.dropletVolume = source.dropletVolume;
        this.dropletVelocity = source.dropletVelocity;
        this.erosionRate = source.erosionRate;
        this.depositRate = source.depositRate;
    }

    /** Snapshots and validates the current state into an immutable {@link ErosionConfig}. */
    public ErosionConfig build() {
        ErosionConfig config = new ErosionConfig(dropletsPerChunk, dropletLifetime, dropletVolume,
                dropletVelocity, erosionRate, depositRate);
        DataResult<ErosionConfig> result = ErosionConfigValidator.validate(config);
        return result.result().orElseThrow(() -> invalidState(result));
    }

    private static IllegalStateException invalidState(DataResult<?> result) {
        String message = result.error().map(error -> error.message()).orElse("unknown validation error");
        return new IllegalStateException("invalid erosion config builder state: " + message);
    }

    /** Resets every field to {@link ErosionConfig#DEFAULT}. */
    public ErosionConfigBuilder reset() {
        ErosionConfig d = ErosionConfig.DEFAULT;
        this.dropletsPerChunk = d.dropletsPerChunk;
        this.dropletLifetime = d.dropletLifetime;
        this.dropletVolume = d.dropletVolume;
        this.dropletVelocity = d.dropletVelocity;
        this.erosionRate = d.erosionRate;
        this.depositRate = d.depositRate;
        return this;
    }

    // ----- getters (for slider initial values) -----------------------------

    public int dropletsPerChunk() { return dropletsPerChunk; }
    public int dropletLifetime() { return dropletLifetime; }
    public float dropletVolume() { return dropletVolume; }
    public float dropletVelocity() { return dropletVelocity; }
    public float erosionRate() { return erosionRate; }
    public float depositRate() { return depositRate; }

    // ----- setters (bound to GUI sliders) ---------------------------------

    public ErosionConfigBuilder dropletsPerChunk(int v) {
        this.dropletsPerChunk = v;
        return this;
    }

    public ErosionConfigBuilder dropletLifetime(int v) {
        this.dropletLifetime = v;
        return this;
    }

    public ErosionConfigBuilder dropletVolume(float v) {
        this.dropletVolume = v;
        return this;
    }

    public ErosionConfigBuilder dropletVelocity(float v) {
        this.dropletVelocity = v;
        return this;
    }

    public ErosionConfigBuilder erosionRate(float v) {
        this.erosionRate = v;
        return this;
    }

    public ErosionConfigBuilder depositRate(float v) {
        this.depositRate = v;
        return this;
    }
}
