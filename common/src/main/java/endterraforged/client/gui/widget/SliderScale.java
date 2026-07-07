/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Pure-logic mapping
 * between a slider's normalised position [0,1] and a typed value range.
 * Informed by RTF's Slider widget (MIT), but decoupled from MC's GUI
 * classes so the math can be unit-tested in the sandbox without a live
 * Minecraft instance — the EndSlider widget layer is a thin adapter over
 * this.
 */
package endterraforged.client.gui.widget;

/**
 * Maps slider position {@code [0,1]} to a typed value range and back.
 *
 * <p><b>Why this exists.</b> Vanilla MC 1.21.1 ships no slider widget, and
 * RTF's slider interleaves the math (position↔value) with MC's
 * {@code AbstractWidget} lifecycle — making the math untestable in
 * isolation. This sealed interface extracts the pure mapping logic so it
 * can be unit-tested directly, and so the {@link EndSlider} widget can
 * stay a thin render/input adapter over a fully-validated scale.</p>
 *
 * <p><b>Why {@code minValue()} / {@code maxValue()} and not {@code min()} /
 * {@code max()}.</b> The records {@link IntSliderScale} and
 * {@link FloatSliderScale} expose type-specific component accessors
 * ({@code int min()} / {@code float min()}); Java doesn't allow primitive
 * return-type widening as an override, so the interface's double-returning
 * bounds use distinct names to avoid clashing with those accessors.</p>
 *
 * <p><b>Contract.</b> Implementations must satisfy:</p>
 * <ul>
 *   <li>{@link #toValue(double)} returns a value in {@code [minValue, maxValue]}
 *       for any input (positions outside {@code [0,1]} are clamped)</li>
 *   <li>{@link #toPosition(double)} returns a position in {@code [0,1]} for
 *       any input (values outside {@code [minValue, maxValue]} are clamped)</li>
 *   <li>{@code toValue(toPosition(v)) == v} (within step discretisation
 *       tolerance) for any {@code v} in {@code [minValue, maxValue]}</li>
 *   <li>{@code toPosition(toValue(p)) ≈ p} (within step discretisation
 *       tolerance) for any {@code p} in {@code [0,1]}</li>
 *   <li>If {@code minValue == maxValue}, both methods degenerate:
 *       {@code toValue} returns {@code minValue}, {@code toPosition}
 *       returns {@code 0.0}</li>
 * </ul>
 *
 * <p><b>Thread safety.</b> Implementations are immutable records; safe to
 * share across threads. The slider widget itself is single-threaded (render
 * thread), but sharing the scale instance across screens is harmless.</p>
 *
 * @see IntSliderScale discrete integer ranges (e.g. {@code worldHeight})
 * @see FloatSliderScale continuous or stepped float ranges (e.g. erosion)
 */
public sealed interface SliderScale permits IntSliderScale, FloatSliderScale {

    /** Lower bound of the value range (inclusive), as a double for polymorphism. */
    double minValue();

    /** Upper bound of the value range (inclusive), as a double for polymorphism. */
    double maxValue();

    /**
     * Scale a position {@code [0,1]} to a value in {@code [minValue, maxValue]}.
     * Positions outside {@code [0,1]} are clamped to the endpoints.
     */
    double toValue(double position);

    /**
     * Convert a value to slider position {@code [0,1]}.
     * Values outside {@code [minValue, maxValue]} are clamped to the endpoints.
     */
    double toPosition(double value);

    /**
     * Format the value for the slider's text label — int scales render
     * without a decimal point, float scales respect {@link FloatSliderScale#decimals()}.
     */
    String format(double value);
}
