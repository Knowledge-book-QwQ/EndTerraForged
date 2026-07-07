package endterraforged.world.floatingislands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Contract tests for {@link FloatingIslandsField}: the pure-logic 3D solidity
 * field that produces lens-shaped islands floating in the void.
 *
 * <p>Pins the shape contract: output is bounded {@code [0,1]}, the vertical
 * profile is a Gaussian peaked at {@code centerY}, the horizontal profile is a
 * hermite falloff (1 inside {@code coreRadius}, 0 beyond {@code shellRadius}),
 * degenerate configs are no-ops, layout is deterministic and seed-sensitive,
 * and the field is continuous (no single-step jumps bigger than the falloff
 * curvature allows).</p>
 */
class FloatingIslandsFieldTest {

    private static final int SEED = 42;
    private static final int SAMPLES = 800;

    private static float x(int i) { return i * 7.3F; }
    private static float z(int i) { return i * 11.1F; }

    // ----- output contract ------------------------------------------------

    @Test
    void solidityAlwaysInRangeZeroOne() {
        FloatingIslandsField field = FloatingIslandsField.defaults();
        for (int i = 0; i < SAMPLES; i++) {
            for (int y = 0; y <= 400; y += 40) {
                float s = field.solidity(x(i), y, z(i), SEED);
                assertTrue(s >= 0.0F && s <= 1.0F,
                        "solidity out of [0,1]: " + s + " at (" + x(i) + "," + y + "," + z(i) + ")");
            }
        }
    }

    // ----- no-op cases ----------------------------------------------------

    @Test
    void degenerateCellSizeIsNoOp() {
        // Regression guard mirroring EndLakeMap/EndRiverMap: cellSize<=0 must
        // not produce NaN via invCell=Inf; returns 0 cleanly.
        FloatingIslandsField field = new FloatingIslandsField(0.0F, 1.0F, 18.0F, 55.0F, 120.0F, 22.0F);
        for (int i = 0; i < SAMPLES; i++) {
            float s = field.solidity(x(i), 120, z(i), SEED);
            assertEquals(0.0F, s, 0.0F, "cellSize=0 must be no-op");
        }
    }

    @Test
    void islandChanceZeroProducesNoIslands() {
        FloatingIslandsField field = new FloatingIslandsField(500.0F, 0.0F, 18.0F, 55.0F, 120.0F, 22.0F);
        for (int i = 0; i < SAMPLES; i++) {
            float s = field.solidity(x(i), 120, z(i), SEED);
            assertEquals(0.0F, s, 0.0F, "islandChance=0 must yield no islands");
        }
    }

    @Test
    void islandChanceOneProducesIslands() {
        FloatingIslandsField field = new FloatingIslandsField(500.0F, 1.0F, 18.0F, 55.0F, 120.0F, 22.0F);
        boolean anySolid = false;
        for (int i = 0; i < SAMPLES; i++) {
            if (field.solidity(x(i), 120, z(i), SEED) > 0.0F) {
                anySolid = true;
                break;
            }
        }
        assertTrue(anySolid, "islandChance=1 should produce at least one island in the sample grid");
    }

    // ----- shape: vertical Gaussian --------------------------------------

    @Test
    void verticalProfilePeaksAtCenterY() {
        // Find an island centre (max-solidity sample), then assert solidity at
        // centerY >= solidity at centerY ± verticalScale (one σ off-peak).
        FloatingIslandsField field = FloatingIslandsField.defaults();
        float cy = field.centerY();
        float sigma = field.verticalScale();

        float bestS = -1.0F;
        float bestX = 0.0F;
        float bestZ = 0.0F;
        for (int i = 0; i < SAMPLES; i++) {
            float s = field.solidity(x(i), cy, z(i), SEED);
            if (s > bestS) {
                bestS = s;
                bestX = x(i);
                bestZ = z(i);
            }
        }
        assertTrue(bestS > 0.0F, "should find at least one solid sample at centerY");

        float atCentre = field.solidity(bestX, cy, bestZ, SEED);
        float above = field.solidity(bestX, cy + sigma, bestZ, SEED);
        float below = field.solidity(bestX, cy - sigma, bestZ, SEED);
        assertTrue(atCentre >= above,
                "solidity at centerY should dominate above: " + atCentre + " vs " + above);
        assertTrue(atCentre >= below,
                "solidity at centerY should dominate below: " + atCentre + " vs " + below);
        // One σ off-peak the Gaussian is exp(-0.5) ≈ 0.606 — solidly above 0.
        assertTrue(above > 0.0F, "one σ above centre should still be solid");
        assertTrue(below > 0.0F, "one σ below centre should still be solid");
    }

    @Test
    void verticalProfileDecaysFarFromCenterY() {
        // At ±3σ the Gaussian is exp(-4.5) ≈ 0.011 — effectively void.
        FloatingIslandsField field = FloatingIslandsField.defaults();
        float cy = field.centerY();
        float sigma = field.verticalScale();
        // Find any solid sample at centerY first.
        float solidX = 0.0F, solidZ = 0.0F;
        boolean found = false;
        for (int i = 0; i < SAMPLES; i++) {
            if (field.solidity(x(i), cy, z(i), SEED) > 0.0F) {
                solidX = x(i);
                solidZ = z(i);
                found = true;
                break;
            }
        }
        assertTrue(found, "need at least one solid sample to test vertical decay");
        float farAbove = field.solidity(solidX, cy + 3.0F * sigma, solidZ, SEED);
        float farBelow = field.solidity(solidX, cy - 3.0F * sigma, solidZ, SEED);
        assertTrue(farAbove < 0.05F, "3σ above centre should be ~void: " + farAbove);
        assertTrue(farBelow < 0.05F, "3σ below centre should be ~void: " + farBelow);
    }

    // ----- shape: horizontal radial falloff ------------------------------

    @Test
    void horizontalFalloffVanishesBeyondShellRadius() {
        // Place a synthetic island field with a known cell grid, then sample
        // far enough from any island centre that solidity must be 0. With
        // islandChance=1 every cell hosts an island, so the furthest a sample
        // can be from a centre is ~half a cell diagonal; we instead use a tiny
        // shellRadius so the falloff cuts off sharply inside the cell.
        float cellSize = 200.0F;
        float shellRadius = 20.0F;
        FloatingIslandsField field = new FloatingIslandsField(
                cellSize, 1.0F, 5.0F, shellRadius, 120.0F, 22.0F);
        // Sample at a cell corner region — between four island centres, each
        // ~cellSize/2 ≈ 100 away, well beyond shellRadius=20.
        float cornerX = cellSize;     // cell boundary
        float cornerZ = cellSize;
        float s = field.solidity(cornerX, 120.0F, cornerZ, SEED);
        assertEquals(0.0F, s, 1e-5F,
                "sample at cell corner (far from any centre) should be void");
    }

    // ----- determinism + seed sensitivity --------------------------------

    @Test
    void solidityIsDeterministic() {
        FloatingIslandsField field = FloatingIslandsField.defaults();
        for (int i = 0; i < SAMPLES; i++) {
            float a = field.solidity(x(i), 120, z(i), SEED);
            float b = field.solidity(x(i), 120, z(i), SEED);
            assertEquals(a, b, 0.0F, "same args must yield same solidity");
        }
    }

    @Test
    void solidityIsSeedSensitive() {
        FloatingIslandsField field = FloatingIslandsField.defaults();
        boolean anyDifference = false;
        for (int i = 0; i < SAMPLES; i++) {
            float a = field.solidity(x(i), 120, z(i), SEED);
            float b = field.solidity(x(i), 120, z(i), SEED + 1);
            if (Float.floatToIntBits(a) != Float.floatToIntBits(b)) {
                anyDifference = true;
                break;
            }
        }
        assertTrue(anyDifference, "different seeds should produce different island layouts");
    }

    // ----- continuity -----------------------------------------------------

    @Test
    void solidityIsLocallyContinuous() {
        // The lens model is a product of smooth (hermite × Gaussian) fields,
        // so adjacent samples (1 block apart) must not jump by more than a
        // small epsilon. A big jump would indicate a discontinuity bug.
        FloatingIslandsField field = FloatingIslandsField.defaults();
        float step = 1.0F;
        for (int i = 0; i < 200; i++) {
            float x0 = x(i);
            float z0 = z(i);
            for (int y = 80; y <= 200; y += 30) {
                float s0 = field.solidity(x0, y, z0, SEED);
                float s1 = field.solidity(x0 + step, y, z0, SEED);
                float s2 = field.solidity(x0, y, z0 + step, SEED);
                float s3 = field.solidity(x0, y + step, z0, SEED);
                // The worst-case single-block slope of the lens is governed by
                // the steepest Gaussian/hermite derivative; allow a generous
                // 0.5/block ceiling (the real max is far lower).
                assertTrue(Math.abs(s1 - s0) <= 0.5F,
                        "X-discontinuity at (" + x0 + "," + y + "," + z0 + "): " + s0 + " → " + s1);
                assertTrue(Math.abs(s2 - s0) <= 0.5F,
                        "Z-discontinuity at (" + x0 + "," + y + "," + z0 + "): " + s0 + " → " + s2);
                assertTrue(Math.abs(s3 - s0) <= 0.5F,
                        "Y-discontinuity at (" + x0 + "," + y + "," + z0 + "): " + s0 + " → " + s3);
            }
        }
    }
}
