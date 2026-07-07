/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Sub-screen for editing
 * the erosion config — 6 slider fields (2 int + 4 float). Opens from and
 * returns to EndPresetEditorScreen.
 */
package endterraforged.client.gui.screen;

import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import endterraforged.client.gui.widget.EndSlider;
import endterraforged.client.gui.widget.FloatSliderScale;
import endterraforged.client.gui.widget.IntSliderScale;
import endterraforged.world.filter.ErosionConfig;
import endterraforged.world.filter.ErosionConfigBuilder;

/**
 * Sub-screen for editing the {@link ErosionConfig} embedded inside an
 * {@link endterraforged.world.config.EndPreset}.
 *
 * <p><b>Layout.</b> A vertical stack of 6 {@link EndSlider}s — 2 int
 * sliders (dropletsPerChunk, dropletLifetime) + 4 float sliders
 * (dropletVolume, dropletVelocity, erosionRate, depositRate) — plus
 * Done / Cancel buttons at the bottom.</p>
 *
 * <p><b>Why a sub-screen rather than inline on EndPresetEditorScreen.</b>
 * The EndPresetEditorScreen already has 7 widgets; adding 6 more would
 * overflow a single screen on small displays. A sub-screen keeps each
 * editor focused on one concern and lets the user navigate with Done /
 * Cancel rather than scrolling.</p>
 *
 * <p><b>Flow.</b> Opens from EndPresetEditorScreen's "Erosion..." button.
 * Done button calls {@code onDone.accept(builder.build())} (passing the
 * edited config back to the parent for embedding into EndPresetBuilder),
 * then closes back to the parent. Cancel closes without invoking onDone,
 * discarding the user's edits.</p>
 *
 * <p><b>Architecture.</b> Same as EndPresetEditorScreen: holds an
 * {@link ErosionConfigBuilder} (mutable editing state), widgets mutate the
 * builder, Done builds the immutable {@link ErosionConfig} and hands it
 * back via the {@code onDone} callback.</p>
 *
 * <p><b>Compile-only verification.</b> Like EndPresetEditorScreen, this
 * screen cannot be unit-tested in the sandbox (requires a live Minecraft).
 * The testable core lives in {@link ErosionConfigBuilder}.</p>
 */
public class ErosionConfigEditorScreen extends Screen {

    /** Slider scales — exposed as constants for test reference if needed. */
    private static final IntSliderScale DROPLETS_PER_CHUNK_SCALE =
            new IntSliderScale(0, 1024, 16);
    private static final IntSliderScale DROPLET_LIFETIME_SCALE =
            new IntSliderScale(1, 256, 4);
    private static final FloatSliderScale DROPLET_VOLUME_SCALE =
            new FloatSliderScale(0.0f, 4.0f, 0.0f, 2);
    private static final FloatSliderScale DROPLET_VELOCITY_SCALE =
            new FloatSliderScale(0.0f, 4.0f, 0.0f, 2);
    private static final FloatSliderScale EROSION_RATE_SCALE =
            new FloatSliderScale(0.0f, 1.0f, 0.0f, 2);
    private static final FloatSliderScale DEPOSIT_RATE_SCALE =
            new FloatSliderScale(0.0f, 1.0f, 0.0f, 2);

    private final ErosionConfigBuilder builder;
    private final Consumer<ErosionConfig> onDone;

    /**
     * @param initial the erosion config to load for editing (or {@link ErosionConfig#DEFAULT})
     * @param onDone callback invoked with the built config when the user
     *               presses Done — the parent screen embeds it into its
     *               EndPresetBuilder via {@code builder.erosionConfig(config)}
     */
    public ErosionConfigEditorScreen(ErosionConfig initial,
                                     Consumer<ErosionConfig> onDone) {
        super(Component.translatable("endterraforged.gui.erosion_editor.title"));
        this.builder = new ErosionConfigBuilder(initial);
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 50;
        final int widgetWidth = 200;
        final int widgetHeight = 20;
        final int rowHeight = 26;

        // --- int sliders (dropletsPerChunk, dropletLifetime) ----------------

        addRenderableWidget(new EndSlider(
                cx - widgetWidth / 2, y, widgetWidth, widgetHeight,
                Component.translatable("endterraforged.gui.erosion.droplets_per_chunk"),
                DROPLETS_PER_CHUNK_SCALE, builder.dropletsPerChunk(),
                v -> builder.dropletsPerChunk((int) v)));
        y += rowHeight;

        addRenderableWidget(new EndSlider(
                cx - widgetWidth / 2, y, widgetWidth, widgetHeight,
                Component.translatable("endterraforged.gui.erosion.droplet_lifetime"),
                DROPLET_LIFETIME_SCALE, builder.dropletLifetime(),
                v -> builder.dropletLifetime((int) v)));
        y += rowHeight;

        // --- float sliders (volume / velocity / erosionRate / depositRate) --

        addRenderableWidget(new EndSlider(
                cx - widgetWidth / 2, y, widgetWidth, widgetHeight,
                Component.translatable("endterraforged.gui.erosion.droplet_volume"),
                DROPLET_VOLUME_SCALE, builder.dropletVolume(),
                v -> builder.dropletVolume((float) v)));
        y += rowHeight;

        addRenderableWidget(new EndSlider(
                cx - widgetWidth / 2, y, widgetWidth, widgetHeight,
                Component.translatable("endterraforged.gui.erosion.droplet_velocity"),
                DROPLET_VELOCITY_SCALE, builder.dropletVelocity(),
                v -> builder.dropletVelocity((float) v)));
        y += rowHeight;

        addRenderableWidget(new EndSlider(
                cx - widgetWidth / 2, y, widgetWidth, widgetHeight,
                Component.translatable("endterraforged.gui.erosion.erosion_rate"),
                EROSION_RATE_SCALE, builder.erosionRate(),
                v -> builder.erosionRate((float) v)));
        y += rowHeight;

        addRenderableWidget(new EndSlider(
                cx - widgetWidth / 2, y, widgetWidth, widgetHeight,
                Component.translatable("endterraforged.gui.erosion.deposit_rate"),
                DEPOSIT_RATE_SCALE, builder.depositRate(),
                v -> builder.depositRate((float) v)));
        y += rowHeight;

        // --- Done / Cancel buttons ------------------------------------------

        addRenderableWidget(Button.builder(
                        Component.translatable("endterraforged.gui.done"),
                        btn -> {
                            // Snapshot the builder state into an immutable
                            // ErosionConfig and hand it back to the parent.
                            ErosionConfig built = builder.build();
                            if (onDone != null) onDone.accept(built);
                            onClose();
                        })
                .bounds(cx - widgetWidth / 2, y, widgetWidth / 2 - 2, widgetHeight)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.cancel"),
                        btn -> onClose())
                .bounds(cx + 2, y, widgetWidth / 2 - 2, widgetHeight)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }
}
