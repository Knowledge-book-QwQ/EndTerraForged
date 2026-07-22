/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Adapted from ReTerraForged's MIT-licensed uplift continent gradient. ETF
 * keeps only a pure scalar and removes Cell, water-table and river coupling.
 */
package endterraforged.world.heightmap;

import java.util.Objects;

import endterraforged.world.config.ContinentAlgorithm;
import endterraforged.world.config.ContinentConfig;
import endterraforged.world.continent.ContinentSignalBuffer;
import endterraforged.world.continent.ContinentSignals;

/**
 * Immutable macro-continent uplift signal derived from the existing continent
 * ownership sample.
 *
 * <p>The runtime follows the useful part of RTF's uplift model: a corrected
 * Voronoi-centre envelope is combined with a smooth edge gradient and an
 * explicit coastal fade. It deliberately does not resample ownership, query a
 * river cache, or introduce water-table semantics. The caller-owned continent
 * signal remains the single source of ownership and centre identity.</p>
 *
 * <p>Sampling is allocation-free and thread-safe after construction.</p>
 */
public final class EndTerrainUpliftRuntime {

    private static final float CELL_RADIUS_FACTOR = 2.5F;
    private static final float COAST_START = 0.10F;
    private static final float COAST_END = 0.65F;

    /** Disabled runtime used by legacy or non-RTF continent algorithms. */
    public static final EndTerrainUpliftRuntime DISABLED =
            new EndTerrainUpliftRuntime(false, 1.0F);

    private final boolean enabled;
    private final float cellRadius;
    private final float inverseCellRadius;

    /** Builds uplift for the RTF-compatible continent scale. */
    public EndTerrainUpliftRuntime(ContinentConfig config) {
        Objects.requireNonNull(config, "config");
        this.enabled = config.continentAlgorithm() == ContinentAlgorithm.RTF_MULTI
                || config.continentAlgorithm() == ContinentAlgorithm.RTF_ADVANCED;
        this.cellRadius = Math.max(256.0F, config.continentScale() * CELL_RADIUS_FACTOR);
        this.inverseCellRadius = 1.0F / this.cellRadius;
    }

    private EndTerrainUpliftRuntime(boolean enabled, float cellRadius) {
        this.enabled = enabled;
        this.cellRadius = cellRadius;
        this.inverseCellRadius = 1.0F / cellRadius;
    }

    /** Returns whether this runtime can produce a non-zero uplift signal. */
    public boolean enabled() {
        return this.enabled;
    }

    /** Returns the conservative world-space radius used around a continent centre. */
    public float cellRadius() {
        return this.cellRadius;
    }

    /** Samples without allocating a diagnostic record. */
    public float sample(float x, float z, ContinentSignalBuffer signals) {
        Objects.requireNonNull(signals, "signals");
        if (!this.enabled || !signals.identified()) {
            return 0.0F;
        }
        return sample(x, z, signals.edge(), signals.landness(), signals.inlandness(),
                signals.centerX(), signals.centerZ());
    }

    /** Samples an immutable diagnostic snapshot outside the density hot path. */
    public float sample(float x, float z, ContinentSignals signals) {
        Objects.requireNonNull(signals, "signals");
        if (!this.enabled || !signals.identified()) {
            return 0.0F;
        }
        return sample(x, z, signals.edge(), signals.landness(), signals.inlandness(),
                signals.centerX(), signals.centerZ());
    }

    private float sample(float x, float z, float edge, float landness, float inlandness,
                         int centerX, int centerZ) {
        if (edge <= 0.0F || landness <= 0.0F) {
            return 0.0F;
        }

        float dx = x - centerX;
        float dz = z - centerZ;
        float distance = (float) Math.sqrt(dx * dx + dz * dz) * this.inverseCellRadius;
        float centroidEnvelope = smoothstep(1.0F - distance);
        float coastEnvelope = smoothstep((inlandness - COAST_START) / (COAST_END - COAST_START));
        float voronoiGradient = smoothstep(edge);
        return Math.clamp(voronoiGradient * centroidEnvelope * coastEnvelope
                * Math.min(edge, landness), 0.0F, 1.0F);
    }

    private static float smoothstep(float value) {
        float alpha = Math.clamp(value, 0.0F, 1.0F);
        return alpha * alpha * (3.0F - 2.0F * alpha);
    }
}
