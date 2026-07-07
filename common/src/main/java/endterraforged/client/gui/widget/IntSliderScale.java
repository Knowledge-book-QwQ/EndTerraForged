/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Discrete integer
 * slider scale — RTF-inspired (MIT) but pure-logic and testable.
 */
package endterraforged.client.gui.widget;

/**
 * A {@link SliderScale} over a discrete integer range, snapped to a step.
 *
 * <p><b>Why discrete steps.</b> The End's height fields ({@code worldHeight},
 * {@code minY}, …) are integers that the worldgen pipeline consumes verbatim,
 * so a slider that produced a continuous double and let the user stop at
 * {@code 4063.7} would silently round-trip to {@code 4064} in the codec,
 * hiding the rounding from the UI. Snapping the slider to a {@code step}
 * multiple keeps the displayed value and the persisted value identical.</p>
 *
 * <p><b>Step semantics.</b> The number of discrete stops is
 * {@code floor((max - min) / step) + 1}. The slider position {@code p} maps
 * to stop index {@code round(p * (max - min) / step)}, then to value
 * {@code min + idx * step}. If {@code step} does not evenly divide
 * {@code (max - min)}, the last stop lands short of {@code max}; the
 * implementation clamps the result so a position of exactly {@code 1.0}
 * always returns {@code max} (preserving the "drag to end = max" affordance).</p>
 *
 * <p><b>Validation.</b> The compact constructor normalises:</p>
 * <ul>
 *   <li>{@code step < 1} → {@code step = 1} (smallest meaningful int step)</li>
 *   <li>{@code step > (max - min)} → {@code step = max - min} (or {@code 1}
 *       if {@code min == max}, though in that case the scale degenerates)</li>
 *   <li>{@code min > max} → {@link IllegalArgumentException} (programmer error)</li>
 * </ul>
 *
 * @param min  lower bound of the value range (inclusive)
 * @param max  upper bound of the value range (inclusive)
 * @param step discrete step size (≥1, ≤ range)
 */
public record IntSliderScale(int min, int max, int step) implements SliderScale {

    /**
     * Compact constructor — normalises {@code step} to a sane value rather
     * than throwing on out-of-range input. {@code min > max} is the only
     * genuinely invalid input (programmer error); everything else degrades
     * gracefully so a misconfigured slider still renders a usable range.
     */
    public IntSliderScale {
        if (min > max) {
            throw new IllegalArgumentException(
                    "IntSliderScale: min (" + min + ") > max (" + max + ")");
        }
        int range = max - min;
        if (step < 1) {
            step = 1;
        } else if (range > 0 && step > range) {
            // Clamp step to range so there are always at least 2 stops
            // (min and max); otherwise the slider would be a single value.
            step = range;
        }
        // When min == max, range == 0, step stays as-is (degenerate scale).
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
        int idx = (int) Math.round(p * (max - min) / (double) step);
        int v = min + idx * step;
        // p == 1.0 → idx == round(range/step), which may round-trip short of
        // max when step doesn't divide range evenly; clamp to max so the
        // "drag to end" affordance holds.
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
        return Integer.toString((int) Math.round(value));
    }
}
