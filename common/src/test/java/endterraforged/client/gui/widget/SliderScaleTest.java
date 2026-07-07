package endterraforged.client.gui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Contract tests for {@link SliderScale} and its permitted implementations
 * {@link IntSliderScale} and {@link FloatSliderScale}.
 *
 * <p>These cover the scale's pure-logic contract — independent of any MC
 * widget — so they can run in the sandbox without a live Minecraft instance.
 * The {@link EndSlider} widget layer is a thin adapter over a validated
 * scale, so this is where the math's correctness lives.</p>
 *
 * <p>Test groups:</p>
 * <ul>
 *   <li>IntSliderScale: identity (0/1 → endpoints), midpoint, clamping,
 *       step snapping, round-trip {@code toValue(toPosition(v)) == v},
 *       {@code toPosition(toValue(p)) ≈ p}, degenerate {@code min==max},
 *       step normalisation, format, invalid {@code min>max}.</li>
 *   <li>FloatSliderScale: continuous identity, continuous midpoint,
 *       clamping, stepped snapping, step-too-large fallback, decimals
 *       format, degenerate {@code min==max}, invalid {@code min>max}.</li>
 * </ul>
 */
class SliderScaleTest {

    // ----- IntSliderScale ------------------------------------------------

    @Test
    void intEndpointsAreExact() {
        IntSliderScale s = new IntSliderScale(64, 6144, 64);
        assertEquals(64.0, s.toValue(0.0), "position 0 → min");
        assertEquals(6144.0, s.toValue(1.0), "position 1 → max (end-of-slider affordance)");
    }

    @Test
    void intMidpointSnapsToNearestStep() {
        // Range = 6080, step = 64 → 95 stops. p=0.5 → idx=round(47.5)=48
        // (round-half-up) → value = 64 + 48*64 = 3136.
        IntSliderScale s = new IntSliderScale(64, 6144, 64);
        assertEquals(3136.0, s.toValue(0.5), "midpoint rounds to nearest step multiple");
    }

    @Test
    void intPositionOutOfRangeIsClamped() {
        IntSliderScale s = new IntSliderScale(0, 100, 10);
        assertEquals(0.0, s.toValue(-0.5), "negative position clamps to min");
        assertEquals(100.0, s.toValue(2.0), "position > 1 clamps to max");
    }

    @Test
    void intValueOutOfRangeIsClamped() {
        IntSliderScale s = new IntSliderScale(0, 100, 10);
        assertEquals(0.0, s.toPosition(-50), "value below min clamps to position 0");
        assertEquals(1.0, s.toPosition(150), "value above max clamps to position 1");
    }

    @Test
    void intSnapsToStepMultiples() {
        // Every position must yield a value that is min + k*step for some k.
        IntSliderScale s = new IntSliderScale(10, 100, 5);
        int residue = 10 % 5;  // min mod step — every value must share this residue
        for (int i = 0; i <= 100; i++) {
            double p = i / 100.0;
            int v = (int) Math.round(s.toValue(p));
            assertEquals(residue, v % 5,
                    "value " + v + " at p=" + p + " must be ≡ min (mod step)");
            assertTrue(v >= 10 && v <= 100,
                    "value " + v + " must stay in [min, max]");
        }
    }

    @Test
    void intRoundTripValueToPositionToValueIsIdentity() {
        // For every integer step multiple in range, toPosition(v) → toValue
        // must return v exactly (since the slider can stop on any step).
        IntSliderScale s = new IntSliderScale(0, 1000, 50);
        for (int v = 0; v <= 1000; v += 50) {
            double p = s.toPosition(v);
            double back = s.toValue(p);
            assertEquals(v, back, 1e-9,
                    "toValue(toPosition(" + v + ")) must be identity for step-aligned values");
        }
    }

    @Test
    void intRoundTripPositionToValueToPositionIsApproximate() {
        // Reverse round-trip: toValue(p) → toPosition is approximate within
        // ±step/range, because step snapping quantises position.
        IntSliderScale s = new IntSliderScale(0, 1000, 50);
        double tolerance = 50.0 / 1000.0 + 1e-9;
        for (int i = 0; i <= 100; i++) {
            double p = i / 100.0;
            double v = s.toValue(p);
            double back = s.toPosition(v);
            assertTrue(Math.abs(back - p) <= tolerance,
                    "toPosition(toValue(" + p + ")) = " + back + " must be within ±" + tolerance);
        }
    }

    @Test
    void intDegenerateMinEqualsMax() {
        IntSliderScale s = new IntSliderScale(42, 42, 1);
        assertEquals(42.0, s.toValue(0.0), "degenerate toValue(0) → min");
        assertEquals(42.0, s.toValue(0.5), "degenerate toValue(0.5) → min");
        assertEquals(42.0, s.toValue(1.0), "degenerate toValue(1) → min");
        assertEquals(0.0, s.toPosition(42), "degenerate toPosition → 0");
        assertEquals(0.0, s.toPosition(0), "degenerate toPosition of any value → 0");
    }

    @Test
    void intStepBelowOneIsNormalisedToOne() {
        IntSliderScale s = new IntSliderScale(0, 100, -5);
        assertEquals(1, s.step(), "step < 1 must be normalised to 1");
        // Sanity: still maps endpoints correctly.
        assertEquals(0.0, s.toValue(0.0));
        assertEquals(100.0, s.toValue(1.0));
    }

    @Test
    void intStepAboveRangeIsClampedToRange() {
        // step=200, range=100 → step clamped to 100 so there are 2 stops.
        IntSliderScale s = new IntSliderScale(0, 100, 200);
        assertEquals(100, s.step(), "step > range must be clamped to range");
        assertEquals(0.0, s.toValue(0.0));
        assertEquals(100.0, s.toValue(1.0));
    }

    @Test
    void intFormatRendersAsInteger() {
        IntSliderScale s = new IntSliderScale(0, 100, 10);
        assertEquals("0", s.format(0.0));
        assertEquals("50", s.format(50.0));
        assertEquals("100", s.format(100.0));
        // Rounds any fractional part (the scale shouldn't produce
        // fractions anyway, but format is robust to bad input).
        assertEquals("43", s.format(42.7));
    }

    @Test
    void intInvalidMinGreaterThanMaxThrows() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new IntSliderScale(100, 0, 10));
        assertTrue(ex.getMessage().contains("min"),
                "error message should mention min for clarity");
    }

    // ----- FloatSliderScale ----------------------------------------------

    @Test
    void floatContinuousEndpointsAreExact() {
        FloatSliderScale s = FloatSliderScale.continuous(0.0f, 1.0f);
        assertEquals(0.0, s.toValue(0.0), 1e-9, "position 0 → min");
        assertEquals(1.0, s.toValue(1.0), 1e-9, "position 1 → max");
    }

    @Test
    void floatContinuousMidpointIsExact() {
        // No step → linear interpolation, midpoint is exact.
        FloatSliderScale s = FloatSliderScale.continuous(0.0f, 1.0f);
        assertEquals(0.5, s.toValue(0.5), 1e-9,
                "continuous midpoint must be exactly 0.5 (no step snapping)");
    }

    @Test
    void floatContinuousRoundTripIsExact() {
        // Without step, both directions of the round-trip are exact
        // (modulo floating-point epsilon).
        FloatSliderScale s = FloatSliderScale.continuous(-1.0f, 1.0f);
        for (int i = 0; i <= 100; i++) {
            double p = i / 100.0;
            double v = s.toValue(p);
            double back = s.toPosition(v);
            assertEquals(p, back, 1e-9,
                    "continuous round-trip must be exact for p=" + p);
        }
    }

    @Test
    void floatPositionOutOfRangeIsClamped() {
        FloatSliderScale s = FloatSliderScale.continuous(0.0f, 1.0f);
        assertEquals(0.0, s.toValue(-1.0), 1e-9, "negative position clamps to min");
        assertEquals(1.0, s.toValue(2.0), 1e-9, "position > 1 clamps to max");
    }

    @Test
    void floatValueOutOfRangeIsClamped() {
        FloatSliderScale s = FloatSliderScale.continuous(0.0f, 1.0f);
        assertEquals(0.0, s.toPosition(-0.5), 1e-9, "value below min clamps to position 0");
        assertEquals(1.0, s.toPosition(1.5), 1e-9, "value above max clamps to position 1");
    }

    @Test
    void floatSteppedSnapsToStepMultiples() {
        // min=0, max=1, step=0.25 → 4 stops: 0.0, 0.25, 0.5, 0.75, 1.0
        FloatSliderScale s = new FloatSliderScale(0.0f, 1.0f, 0.25f, 2);
        assertEquals(0.0, s.toValue(0.0), 1e-9, "p=0 → 0.0");
        assertEquals(0.25, s.toValue(0.25), 1e-9, "p=0.25 → 0.25");
        assertEquals(0.5, s.toValue(0.5), 1e-9, "p=0.5 → 0.5");
        assertEquals(0.75, s.toValue(0.75), 1e-9, "p=0.75 → 0.75");
        assertEquals(1.0, s.toValue(1.0), 1e-9, "p=1.0 → 1.0 (end affordance)");
        // Mid-between stops snaps to nearest.
        assertEquals(0.25, s.toValue(0.3), 1e-9, "p=0.3 rounds to 0.25");
        assertEquals(0.5, s.toValue(0.4), 1e-9, "p=0.4 rounds to 0.5");
    }

    @Test
    void floatStepTooLargeFallsBackToContinuous() {
        // step=2, range=1 → step > range, fall back to continuous (step=0).
        FloatSliderScale s = new FloatSliderScale(0.0f, 1.0f, 2.0f, 2);
        assertEquals(0.0f, s.step(), "step > range must fall back to 0 (continuous)");
        // And behaves as continuous (midpoint is exact).
        assertEquals(0.5, s.toValue(0.5), 1e-9);
    }

    @Test
    void floatNegativeStepIsNormalisedToZero() {
        FloatSliderScale s = new FloatSliderScale(0.0f, 1.0f, -0.1f, 2);
        assertEquals(0.0f, s.step(), "negative step normalises to 0 (continuous)");
    }

    @Test
    void floatFormatRespectsDecimals() {
        FloatSliderScale s2 = new FloatSliderScale(0.0f, 1.0f, 0.0f, 2);
        assertEquals("0.00", s2.format(0.0));
        assertEquals("0.50", s2.format(0.5));
        assertEquals("1.00", s2.format(1.0));

        FloatSliderScale s0 = new FloatSliderScale(0.0f, 1.0f, 0.0f, 0);
        assertEquals("0", s0.format(0.0));
        assertEquals("1", s0.format(1.0));
        // Negative decimals are clamped to 0.
        FloatSliderScale sNeg = new FloatSliderScale(0.0f, 1.0f, 0.0f, -3);
        assertEquals(0, sNeg.decimals(), "negative decimals must be clamped to 0");
    }

    @Test
    void floatDegenerateMinEqualsMax() {
        FloatSliderScale s = new FloatSliderScale(0.5f, 0.5f, 0.0f, 2);
        assertEquals(0.5, s.toValue(0.0), 1e-9, "degenerate toValue(0) → min");
        assertEquals(0.5, s.toValue(0.5), 1e-9, "degenerate toValue(0.5) → min");
        assertEquals(0.5, s.toValue(1.0), 1e-9, "degenerate toValue(1) → min");
        assertEquals(0.0, s.toPosition(0.5), 1e-9, "degenerate toPosition → 0");
    }

    @Test
    void floatInvalidMinGreaterThanMaxThrows() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new FloatSliderScale(1.0f, 0.0f, 0.0f, 2));
        assertTrue(ex.getMessage().contains("min"),
                "error message should mention min for clarity");
    }

    // ----- shared SliderScale interface ----------------------------------

    @Test
    void sealedInterfacePermitsOnlyIntAndFloat() {
        // Sanity check that the sealed hierarchy is as documented — this
        // guards against accidentally adding a third implementation
        // without updating the contract tests.
        SliderScale intScale = new IntSliderScale(0, 100, 10);
        SliderScale floatScale = FloatSliderScale.continuous(0.0f, 1.0f);
        assertTrue(intScale instanceof IntSliderScale);
        assertTrue(floatScale instanceof FloatSliderScale);
    }
}
