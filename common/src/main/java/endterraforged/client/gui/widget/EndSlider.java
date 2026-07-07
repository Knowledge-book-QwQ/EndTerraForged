/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Thin adapter over
 * vanilla's AbstractSliderButton, delegating the position↔value math to
 * a SliderScale. Informed by RTF's Slider widget (MIT), but the math is
 * extracted into the testable SliderScale layer rather than inlined here.
 */
package endterraforged.client.gui.widget;

import java.util.Objects;
import java.util.function.DoubleConsumer;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * A vanilla-{@link AbstractSliderButton}-backed slider that delegates its
 * position↔value math to a {@link SliderScale}.
 *
 * <p><b>Why this exists.</b> Vanilla MC 1.21.1 ships no slider widget —
 * RTF's {@code Slider} interleaves the math (lerp, clamp, format) with the
 * widget lifecycle, making the math untestable in isolation. This class is
 * a thin adapter: the {@link SliderScale} (pure-logic, unit-tested) handles
 * all the math; this class just wires the scale into vanilla's slider
 * rendering and input pipeline.</p>
 *
 * <p><b>Architecture.</b> Mirrors RTF's {@code Slider} structure but with
 * the math extracted:</p>
 * <ul>
 *   <li>{@link AbstractSliderButton#value} (in {@code [0,1]}) is the
 *       slider's normalised position — vanilla owns this field</li>
 *   <li>{@link #getValue()} returns the scaled value via
 *       {@link SliderScale#toValue(double)}</li>
 *   <li>{@link #setValue(double)} accepts a scaled value and updates
 *       vanilla's {@code value} via {@link SliderScale#toPosition(double)}</li>
 *   <li>{@link #applyValue()} fires the {@link #onChange} callback with
 *       the scaled value whenever the user drags</li>
 *   <li>{@link #updateMessage()} renders {@code "prefix: formatted_value"}
 *       using {@link SliderScale#format(double)}</li>
 * </ul>
 *
 * <p><b>Why the prefix is a {@link Component}.</b> Slider labels are
 * translation keys (e.g. {@code endterraforged.gui.world_height}), so they
 * must pass through MC's translation system. The value side is a literal
 * number — no translation needed.</p>
 *
 * <p><b>Thread safety.</b> Single-threaded (render thread only) — the
 * slider is a UI widget. The {@link SliderScale} is immutable so sharing
 * the same scale across multiple EndSlider instances is safe.</p>
 *
 * <p><b>Compile-only verification.</b> This widget cannot be unit-tested
 * in the sandbox (it requires a live {@code Minecraft} instance for
 * rendering). Its testable core lives in {@link SliderScale}; this class
 * is a thin adapter that only fails if the vanilla API changes (caught by
 * stage-5.3 integration testing on a real client).</p>
 */
public final class EndSlider extends AbstractSliderButton {

    private final SliderScale scale;
    private final Component prefix;
    private final DoubleConsumer onChange;

    /**
     * @param x            screen X of top-left corner
     * @param y            screen Y of top-left corner
     * @param width        widget width in pixels
     * @param height       widget height in pixels
     * @param prefix       translation-keyed label rendered before the value
     *                     (e.g. {@code Component.translatable("endterraforged.gui.world_height")})
     * @param scale        pure-logic position↔value mapping
     * @param initialValue the scaled value to start at (clamped to range)
     * @param onChange     callback fired with the scaled value whenever the
     *                     user drags the slider — typically
     *                     {@code v -> builder.worldHeight((int) v)} to live-
     *                     update the EndPresetBuilder
     */
    public EndSlider(int x, int y, int width, int height,
                     Component prefix, SliderScale scale,
                     double initialValue, DoubleConsumer onChange) {
        super(x, y, width, height, CommonComponents.EMPTY, 0.0);
        this.scale = Objects.requireNonNull(scale, "scale");
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.onChange = Objects.requireNonNull(onChange, "onChange");
        // Set the slider position from the initial scaled value. Done after
        // super() so vanilla's `value` field exists; super's 0.0 is
        // immediately overwritten here.
        this.value = scale.toPosition(initialValue);
        updateMessage();
    }

    /**
     * Returns the current scaled value. Use this rather than reading
     * {@link #value} directly — the latter is the raw {@code [0,1]}
     * position, not the meaningful value.
     */
    public double getValue() {
        return scale.toValue(this.value);
    }

    /**
     * Sets the slider to the position closest to the given scaled value.
     * Used when an external change (e.g. a preset reset) needs to update
     * the slider's visual position.
     */
    public void setValue(double value) {
        this.value = scale.toPosition(value);
        updateMessage();
    }

    /**
     * Called by vanilla when the slider is dragged. Fires the
     * {@link #onChange} callback with the new scaled value.
     *
     * <p>Vanilla calls {@code applyValue} before {@code updateMessage} in
     * its {@code setValue} path, so the builder sees the new value before
     * the message re-renders.</p>
     */
    @Override
    protected void applyValue() {
        onChange.accept(scale.toValue(this.value));
    }

    /**
     * Renders the slider's text label as {@code "prefix: value"}.
     *
     * <p>{@link CommonComponents#optionNameValue(Component, Component)}
     * is vanilla's standard formatter for option sliders — it produces
     * the {@code "Prefix: Value"} shape used by vanilla's option screens,
     * keeping our sliders visually consistent with vanilla ones.</p>
     */
    @Override
    protected void updateMessage() {
        setMessage(CommonComponents.optionNameValue(
                prefix,
                Component.literal(scale.format(getValue()))));
    }
}
