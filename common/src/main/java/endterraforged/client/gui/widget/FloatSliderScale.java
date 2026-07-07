/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Continuous or
 * stepped float slider scale — RTF-inspired (MIT) but pure-logic and
 * testable.
 */
package endterraforged.client.gui.widget;

/**
 * A {@link SliderScale} over a float range, optionally snapped to a step.
 *
 * <p><b>Why float sliders.</b> The End's erosion / climate tuning fields
 * are continuous floats in {@code [0,1]} — a continuous slider is the
 * natural control. The {@code step} field exists for cases where the
 * consumer prefers discrete buckets (e.g. {@code 0.05} steps for
 * accessibility); {@code step <= 0} means continuous (no snapping).</p>
 *
 * <p><b>Step semantics.</b> When {@code step > 0}, the slider snaps to
 * the nearest {@code step} multiple of {@code (value - min)}: position
 * {@code p} maps to {@code min + round(p * range / step) * step}. As with
 * {@link IntSliderScale}, a position of exactly {@code 1.0} always
 * returns {@code max} regardless of step alignment.</p>
 *
 * <p><b>Validation.</b> The compact constructor normalises:</p>
 * <ul>
 *   <li>{@code step < 0} → {@code step = 0} (continuous)</li>
 *   <li>{@code step > (max - min)} → {@code step = 0} (degenerate to
 *       continuous — discrete snapping would collapse to a single value)</li>
 *   <li>{@code decimals < 0} → {@code decimals = 0}</li>
 *   <li>{@code min > max} → {@link IllegalArgumentException}</li>
 * </ul>
 *
 * @param min      lower bound of the value range (inclusive)
 * @param max      upper bound of the value range (inclusive)
 * @param step     discrete step size, or {@code 0} for continuous
 * @param decimals number of decimal places for {@link #format(double)}
 */
public record FloatSliderScale(float min, float max, float step, int decimals) implements SliderScale {

    /** Continuous float slider with 2 decimal places — common default for {@code [0,1]} ranges. */
    public static FloatSliderScale continuous(float min, float max) {
        return new FloatSliderScale(min, max, 0.0f, 2);
    }

    /**
     * Compact constructor — normalises step and decimals. {@code min > max}
     * is the only hard error; other inputs degrade gracefully so the slider
     * still renders a usable range.
     */
    public FloatSliderScale {
        if (min > max) {
            throw new IllegalArgumentException(
                    "FloatSliderScale: min (" + min + ") > max (" + max + ")");
        }
        float range = max - min;
        if (step < 0.0f) {
            step = 0.0f;
        } else if (range > 0.0f && step > range) {
            // Step larger than the whole range collapses to a single value
            // if we tried to snap; better to fall back to continuous.
            step = 0.0f;
        }
        if (decimals < 0) {
            decimals = 0;
        }
    }

    @Override
    public double minValue() {
        return min;
    }

    @Override
    public double maxValue() {
        return max;
    }

    @Override
    public double toValue(double position) {
        if (max == min) {
            return min;
        }
        double p = Math.clamp(position, 0.0, 1.0);
        if (step <= 0.0f) {
            // Continuous: linear interpolation.
            return min + p * (max - min);
        }
        // Stepped: snap to nearest step multiple of (max - min).
        int idx = (int) Math.round(p * (max - min) / step);
        float v = min + idx * step;
        // p == 1.0 must always return max (end-of-slider affordance).
        return Math.min(v, max);
    }

    @Override
    public double toPosition(double value) {
        if (max == min) {
            return 0.0;
        }
        double clamped = Math.clamp(value, (double) min, (double) max);
        return (clamped - min) / (max - min);
    }

    @Override
    public String format(double value) {
        if (decimals == 0) {
            return Integer.toString((int) Math.round(value));
        }
        return String.format("%." + decimals + "f", value);
    }
}
