package endterraforged.world.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.heightmap.EndHeightmap;
import endterraforged.world.heightmap.EndLevels;

/**
 * Contract tests for {@link ClimateModulator}: climate-driven height
 * modulation that scales elevation-above-surface, no-op on void/surface,
 * bounded by [minScale, maxScale], deterministic, and chainable in
 * EndHeightmap via withClimate.
 */
class ClimateModulatorTest {

    private static final int SEED = 42;
    private static final int SAMPLES = 400;

    private static float x(int i) { return (i - SAMPLES / 2) * 13.7F; }
    private static float z(int i) { return (i - SAMPLES / 2) * 9.3F; }

    // ----- no-op cases ----------------------------------------------------

    @Test
    void noOpOnSurface() {
        // inputHeight == surface → no elevation to modulate → returns input.
        EndClimate climate = EndClimate.defaults(SEED);
        ClimateModulator mod = ClimateModulator.defaults(climate);
        EndLevels levels = new EndHeightmap(
                new endterraforged.world.config.TestProfile(4064, -2032, 0, 0,
                        endterraforged.world.config.SeaMode.NONE,
                        endterraforged.world.config.TopologyMode.ISLANDS, false), SEED).levels();
        for (int i = 0; i < SAMPLES; i++) {
            float result = mod.modulate(x(i), z(i), SEED, levels, levels.surface);
            assertEquals(levels.surface, result, 0.0F,
                    "surface input should be no-op");
        }
    }

    @Test
    void noOpBelowSurface() {
        // Void columns (input < surface) must be untouched — modulator must
        // not push surface below itself or carve into the void threshold.
        EndClimate climate = EndClimate.defaults(SEED);
        ClimateModulator mod = ClimateModulator.defaults(climate);
        EndLevels levels = new EndHeightmap(
                new endterraforged.world.config.TestProfile(4064, -2032, 0, 0,
                        endterraforged.world.config.SeaMode.NONE,
                        endterraforged.world.config.TopologyMode.ISLANDS, false), SEED).levels();
        for (int i = 0; i < SAMPLES; i++) {
            float below = levels.surface - 0.1F;
            float result = mod.modulate(x(i), z(i), SEED, levels, below);
            assertEquals(below, result, 0.0F,
                    "below-surface input should be no-op");
        }
    }

    // ----- modulation contract --------------------------------------------

    @Test
    void modulationStaysWithinClampBounds() {
        // The modulated elevation-above-surface must stay within
        // [minScale, maxScale] × original elevation.
        EndClimate climate = EndClimate.defaults(SEED);
        ClimateModulator mod = ClimateModulator.defaults(climate);
        EndHeightmap map = new EndHeightmap(
                new endterraforged.world.config.TestProfile(4064, -2032, 0, 0,
                        endterraforged.world.config.SeaMode.NONE,
                        endterraforged.world.config.TopologyMode.ISLANDS, false), SEED);
        for (int i = 0; i < SAMPLES; i++) {
            float raw = map.getTerrainHeight(x(i), z(i), SEED);
            float modulated = mod.modulate(x(i), z(i), SEED, map.levels(), raw);
            if (raw > map.levels().surface) {
                float elev = raw - map.levels().surface;
                float minResult = map.levels().surface + elev * mod.minScale();
                float maxResult = map.levels().surface + elev * mod.maxScale();
                assertTrue(modulated >= minResult - 1e-5F,
                        "modulated " + modulated + " below min " + minResult);
                assertTrue(modulated <= maxResult + 1e-5F,
                        "modulated " + modulated + " above max " + maxResult);
            }
        }
    }

    @Test
    void coldBoostRaisesTerrain() {
        // With only coldBoost (wetErosion=0), cold regions (temperature→0)
        // should raise terrain, hot regions (temperature→1) should leave it
        // unchanged (modulation=1).
        EndClimate climate = EndClimate.defaults(SEED);
        ClimateModulator mod = new ClimateModulator(climate, 0.20F, 0.0F, 0.5F, 1.5F);
        EndHeightmap map = new EndHeightmap(
                new endterraforged.world.config.TestProfile(4064, -2032, 0, 0,
                        endterraforged.world.config.SeaMode.NONE,
                        endterraforged.world.config.TopologyMode.ISLANDS, false), SEED);
        boolean anyRaised = false;
        for (int i = 0; i < SAMPLES; i++) {
            float raw = map.getTerrainHeight(x(i), z(i), SEED);
            if (raw <= map.levels().surface) continue;
            float modulated = mod.modulate(x(i), z(i), SEED, map.levels(), raw);
            float temp = climate.getTemperature(x(i), z(i), SEED);
            if (temp < 0.3F) {
                // Cold: modulation = 1 + 0.2*(1-temp) > 1 → raised.
                assertTrue(modulated > raw,
                        "cold region should raise terrain: " + modulated + " vs " + raw);
                anyRaised = true;
            }
        }
        assertTrue(anyRaised, "should have at least one cold-raised sample");
    }

    @Test
    void wetErosionLowersTerrain() {
        // With only wetErosion (coldBoost=0), wet regions (moisture→1) should
        // lower terrain, dry regions (moisture→0) should leave it unchanged.
        EndClimate climate = EndClimate.defaults(SEED);
        ClimateModulator mod = new ClimateModulator(climate, 0.0F, 0.15F, 0.5F, 1.5F);
        EndHeightmap map = new EndHeightmap(
                new endterraforged.world.config.TestProfile(4064, -2032, 0, 0,
                        endterraforged.world.config.SeaMode.NONE,
                        endterraforged.world.config.TopologyMode.ISLANDS, false), SEED);
        boolean anyLowered = false;
        for (int i = 0; i < SAMPLES; i++) {
            float raw = map.getTerrainHeight(x(i), z(i), SEED);
            if (raw <= map.levels().surface) continue;
            float modulated = mod.modulate(x(i), z(i), SEED, map.levels(), raw);
            float moist = climate.getMoisture(x(i), z(i), SEED);
            if (moist > 0.7F) {
                assertTrue(modulated < raw,
                        "wet region should lower terrain: " + modulated + " vs " + raw);
                anyLowered = true;
            }
        }
        assertTrue(anyLowered, "should have at least one wet-lowered sample");
    }

    @Test
    void zeroBoostAndErosionIsNoOp() {
        // coldBoost=0, wetErosion=0 → modulation=1 everywhere → no-op.
        EndClimate climate = EndClimate.defaults(SEED);
        ClimateModulator mod = new ClimateModulator(climate, 0.0F, 0.0F, 0.5F, 1.5F);
        EndHeightmap map = new EndHeightmap(
                new endterraforged.world.config.TestProfile(4064, -2032, 0, 0,
                        endterraforged.world.config.SeaMode.NONE,
                        endterraforged.world.config.TopologyMode.ISLANDS, false), SEED);
        for (int i = 0; i < SAMPLES; i++) {
            float raw = map.getTerrainHeight(x(i), z(i), SEED);
            float modulated = mod.modulate(x(i), z(i), SEED, map.levels(), raw);
            assertEquals(raw, modulated, 1e-6F,
                    "zero boost + zero erosion should be no-op");
        }
    }

    // ----- determinism ----------------------------------------------------

    @Test
    void modulateIsDeterministic() {
        EndClimate climate = EndClimate.defaults(SEED);
        ClimateModulator mod = ClimateModulator.defaults(climate);
        EndHeightmap map = new EndHeightmap(
                new endterraforged.world.config.TestProfile(4064, -2032, 0, 0,
                        endterraforged.world.config.SeaMode.NONE,
                        endterraforged.world.config.TopologyMode.ISLANDS, false), SEED);
        for (int i = 0; i < SAMPLES; i++) {
            float raw = map.getTerrainHeight(x(i), z(i), SEED);
            float a = mod.modulate(x(i), z(i), SEED, map.levels(), raw);
            float b = mod.modulate(x(i), z(i), SEED, map.levels(), raw);
            assertEquals(Float.floatToIntBits(a), Float.floatToIntBits(b),
                    "modulate must be deterministic");
        }
    }

    // ----- EndHeightmap integration --------------------------------------

    @Test
    void withClimateModulatesGetHeight() {
        // withClimate should make getHeight return modulated values that differ
        // from raw getTerrainHeight on at least some land samples.
        EndHeightmap base = new EndHeightmap(
                new endterraforged.world.config.TestProfile(4064, -2032, 0, 0,
                        endterraforged.world.config.SeaMode.NONE,
                        endterraforged.world.config.TopologyMode.ISLANDS, false), SEED);
        EndHeightmap withClimate = base.withClimate(
                ClimateModulator.defaults(EndClimate.defaults(SEED)));
        boolean anyDifferent = false;
        for (int i = 0; i < SAMPLES; i++) {
            float raw = base.getTerrainHeight(x(i), z(i), SEED);
            float modulated = withClimate.getHeight(x(i), z(i), SEED);
            if (Math.abs(raw - modulated) > 1e-5F) {
                anyDifferent = true;
                break;
            }
        }
        assertTrue(anyDifferent, "withClimate should modulate at least some heights");
    }

    @Test
    void withClimateNullDetaches() {
        EndHeightmap withClimate = new EndHeightmap(
                new endterraforged.world.config.TestProfile(4064, -2032, 0, 0,
                        endterraforged.world.config.SeaMode.NONE,
                        endterraforged.world.config.TopologyMode.ISLANDS, false), SEED)
                .withClimate(ClimateModulator.defaults(EndClimate.defaults(SEED)));
        EndHeightmap detached = withClimate.withClimate(null);
        for (int i = 0; i < SAMPLES; i++) {
            assertEquals(detached.getTerrainHeight(x(i), z(i), SEED),
                    detached.getHeight(x(i), z(i), SEED), 1e-6F,
                    "withClimate(null) should detach the modulator");
        }
    }

    @Test
    void withClimateDoesNotMutateBase() {
        EndHeightmap base = new EndHeightmap(
                new endterraforged.world.config.TestProfile(4064, -2032, 0, 0,
                        endterraforged.world.config.SeaMode.NONE,
                        endterraforged.world.config.TopologyMode.ISLANDS, false), SEED);
        base.withClimate(ClimateModulator.defaults(EndClimate.defaults(SEED)));
        for (int i = 0; i < SAMPLES; i++) {
            assertEquals(base.getTerrainHeight(x(i), z(i), SEED),
                    base.getHeight(x(i), z(i), SEED), 1e-6F,
                    "withClimate must not mutate the original heightmap");
        }
    }

    @Test
    void climateRiverLakeChainDoesNotOverflow() {
        // Full chain: raw → climate → river → lake. If any post-processor
        // recursed through getHeight instead of reading its own input, this
        // would stack-overflow.
        EndHeightmap chained = new EndHeightmap(
                new endterraforged.world.config.TestProfile(4064, -2032, 0, 0,
                        endterraforged.world.config.SeaMode.NONE,
                        endterraforged.world.config.TopologyMode.ISLANDS, false), SEED)
                .withClimate(ClimateModulator.defaults(EndClimate.defaults(SEED)))
                .withRivers(new endterraforged.world.river.EndRiverMap(
                        380, 1.0F, 12, 90, 0.04F, 1.0F, 0.45F, 35.0F, 0.6F))
                .withLakes(new endterraforged.world.lake.EndLakeMap(620, 1.0F, 28, 75, 0.06F));
        for (int i = 0; i < SAMPLES; i++) {
            float h = chained.getHeight(x(i), z(i), SEED);
            assertTrue(Float.isFinite(h), "full chain must be finite (no recursion)");
        }
    }

    // ----- bug regression: B5 modulator must not push height above 1.0 -----

    @Test
    void modulatedHeightNeverExceedsWorldCeiling() {
        // Regression for B5: coldBoost=1.15 on a near-peak sample (input≈1.0)
        // would push result > 1.0, creating a solid pillar in EndDensity.
        // The modulator must clamp to [surface, 1.0].
        EndClimate climate = EndClimate.defaults(SEED);
        // Aggressive modulator to force the upper bound.
        ClimateModulator mod = new ClimateModulator(climate, 0.50F, 0.0F, 0.5F, 2.0F);
        EndHeightmap map = new EndHeightmap(
                new endterraforged.world.config.TestProfile(4064, -2032, 0, 0,
                        endterraforged.world.config.SeaMode.NONE,
                        endterraforged.world.config.TopologyMode.ISLANDS, false), SEED);
        for (int i = 0; i < SAMPLES; i++) {
            float raw = map.getTerrainHeight(x(i), z(i), SEED);
            float modulated = mod.modulate(x(i), z(i), SEED, map.levels(), raw);
            assertTrue(modulated <= 1.0F + 1e-5F,
                    "modulated height must not exceed 1.0 (world ceiling): " + modulated);
            assertTrue(modulated >= map.levels().surface - 1e-5F,
                    "modulated height must not drop below surface: " + modulated);
        }
    }

    // ----- bug regression: B6 degenerate radius must not produce NaN -------

    @Test
    void degenerateClimateRadiusDoesNotProduceNaN() {
        // Regression for B6: radius=0 must return 0 (cold everywhere), not NaN.
        // EndClimate constructed with climateRadius=0 must still yield finite
        // temperature everywhere.
        EndClimate climate = new EndClimate(SEED, 0.0F, 600, 800, 1000, 0.25F);
        for (int i = 0; i < SAMPLES; i++) {
            float t = climate.getTemperature(x(i) * 10, z(i) * 10, SEED);
            assertTrue(Float.isFinite(t), "temperature must be finite even with radius=0: " + t);
        }
    }
}
