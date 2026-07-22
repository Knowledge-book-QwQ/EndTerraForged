/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged changes: extracted only the MIT pure noise mask semantics
 * from RTF ArchipelagoPopulator; Cell, water, biome, terrain and cache
 * responsibilities remain outside this class.
 */
package endterraforged.world.heightmap;

import java.util.Objects;

import endterraforged.world.continent.EndCentralRegionPolicy;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.Noises;

/** Immutable, allocation-free mask for attached End archipelago features. */
public final class EndArchipelagoMask {

    private static final int ISLAND_SIZE = 185;
    private static final float ISLAND_HORIZONTAL_SCALE = 0.25F;
    private static final float ISLAND_DENSITY = 0.655F;
    private static final int CHAIN_CELL_SIZE = 2048;

    public static final EndArchipelagoMask DISABLED = new EndArchipelagoMask();

    private final Noise sizeNoise;
    private final Noise densityNoise;
    private final boolean enabled;

    public EndArchipelagoMask(int seed) {
        int size = Math.max(ISLAND_SIZE, Math.round(ISLAND_SIZE / Math.max(0.1F,
                ISLAND_HORIZONTAL_SCALE)));
        Noise sizeField = Noises.simplex(seed + 1273,
                Math.max(512, Math.round(size * 3.5F)), 3);
        sizeField = Noises.warpPerlin(sizeField, seed + 1273,
                Math.max(512, Math.round(size * 2.0F)), 2, size * 0.5F);
        sizeField = Noises.warpPerlin(sizeField, seed + 4830,
                Math.max(256, Math.round(size * 0.5F)), 1, size * 0.3F);
        sizeField = Noises.warpPerlin(sizeField, seed + 8932,
                Math.max(128, Math.round(size * 0.08F)), 2, size * 0.15F);
        this.sizeNoise = Noises.clamp(sizeField, 0.0F, 1.0F);

        Noise densityField = Noises.simplex(seed + 9735, 4000, 3);
        densityField = Noises.warpPerlin(densityField, seed + 9735, 2000, 2, 1000.0F);
        this.densityNoise = Noises.clamp(densityField, 0.0F, 1.0F);
        this.enabled = true;
    }

    private EndArchipelagoMask() {
        this.sizeNoise = Noises.zero();
        this.densityNoise = Noises.zero();
        this.enabled = false;
    }

    /** Samples the feature without allocating a signal record. */
    public void sample(float x, float z, int seed, float edge, float mainlandLandness,
                       EndArchipelagoSignalBuffer output) {
        Objects.requireNonNull(output, "output");
        if (!this.enabled) {
            output.clear();
            return;
        }

        float activation = EndCentralRegionPolicy.outerActivation(x, z);
        if (activation <= 0.0F) {
            output.clear();
            return;
        }

        float sizeValue = this.sizeNoise.compute(x, z, 0);
        float densityValue = this.densityNoise.compute(x, z, 0);
        float densityThreshold = Math.clamp(1.0F - ISLAND_DENSITY * 0.8F, 0.05F, 0.98F);
        float densityFade = Math.clamp((1.0F - densityThreshold) * 0.5F, 0.04F, 0.12F);
        float shapeAlpha = smoothstep(sizeValue, 0.5F, 1.0F);
        float densityAlpha = smoothstep(densityValue, densityThreshold,
                densityThreshold + densityFade);

        float remoteVoid = 1.0F - smoothstep(edge, 0.0F, 0.12F);
        float coastVoid = smoothstep(edge, 0.0F, 0.06F)
                * (1.0F - smoothstep(edge, 0.12F, 0.30F));
        float offshoreEnvelope = Math.max(0.18F * remoteVoid, coastVoid);
        float mainlandExclusion = 1.0F - smoothstep(mainlandLandness, 0.18F, 0.60F);
        float mask = Math.clamp(shapeAlpha * densityAlpha * offshoreEnvelope
                * mainlandExclusion * activation * activation, 0.0F, 1.0F);
        if (mask <= 0.001F) {
            output.clear();
            return;
        }

        int chainX = Math.floorDiv((int) Math.floor(x), CHAIN_CELL_SIZE);
        int chainZ = Math.floorDiv((int) Math.floor(z), CHAIN_CELL_SIZE);
        output.set(mask, EndCoastBands.landness(mask), EndCoastBands.inlandness(mask),
                EndCoastBands.reliefWeight(mask), chainX, chainZ);
    }

    public boolean enabled() {
        return this.enabled;
    }

    private static float smoothstep(float value, float start, float end) {
        float alpha = Math.clamp((value - start) / (end - start), 0.0F, 1.0F);
        return alpha * alpha * (3.0F - 2.0F * alpha);
    }
}
