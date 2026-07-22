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

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import endterraforged.client.gui.widget.ActionButtonLayout;
import endterraforged.client.gui.widget.EndSlider;
import endterraforged.client.gui.widget.EditorColumnLayout;
import endterraforged.client.gui.widget.EditorScrollLayout;
import endterraforged.client.gui.widget.FloatSliderScale;
import endterraforged.client.gui.widget.IntSliderScale;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetBuilder;
import endterraforged.world.filter.ErosionConfig;
import endterraforged.world.filter.ErosionConfigBuilder;
import endterraforged.world.preview.TerrainPreviewMode;
import endterraforged.world.preview.TerrainPreviewSettings;

/**
 * Sub-screen for editing the {@link ErosionConfig} embedded inside an
 * {@link EndPreset}. It mirrors the continent/climate editor pattern: mutable
 * config builder for sliders, preview preset builder for live snapshots, and a
 * last-valid preset fallback if validation fails while dragging.
 */
public class ErosionConfigEditorScreen extends Screen {

    private static final int EDITOR_TOP = 50;
    private static final int EDITOR_BOTTOM_MARGIN = 34;
    private static final int WIDGET_HEIGHT = 20;
    private static final int ROW_HEIGHT = 26;
    private static final int SCROLL_STEP = 32;
    private static final int SLIDER_ROWS = 6;
    private static final int TRAILING_ROWS = 2;

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

    private final Screen parent;
    private final EndPresetBuilder previewBuilder;
    private final ErosionConfigBuilder builder;
    private final Consumer<ErosionConfig> onDone;
    private EndPreset lastValidPreset;
    private TerrainPreviewSettings previewSettings =
            TerrainPreviewSettings.DEFAULT.withMode(TerrainPreviewMode.HEIGHT);
    private int scrollOffset;
    private int maxScroll;
    private Component statusMessage;

    /**
     * @param initial preset snapshot to load for editing
     * @param parent screen to return to on Done/Cancel
     * @param onDone callback invoked with the built config when the user
     *               presses Done — the parent screen embeds it into its
     *               EndPresetBuilder via {@code builder.erosionConfig(config)}
     */
    public ErosionConfigEditorScreen(EndPreset initial,
                                     Screen parent,
                                     Consumer<ErosionConfig> onDone) {
        super(Component.translatable("endterraforged.gui.erosion_editor.title"));
        this.previewBuilder = new EndPresetBuilder(initial);
        this.builder = new ErosionConfigBuilder(initial.erosionConfig());
        this.parent = parent;
        this.onDone = onDone;
        this.lastValidPreset = initial;
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(parent);
            return;
        }
        super.onClose();
    }

    @Override
    protected void init() {
        int widgetWidth = Math.min(210, Math.max(120, this.width - 40));
        int left = this.width >= 500
                ? Math.max(20, this.width / 2 - 230)
                : Math.max(20, (this.width - widgetWidth) / 2);
        this.maxScroll = EditorScrollLayout.maxScroll(
                EditorScrollLayout.contentBottom(EDITOR_TOP, ROW_HEIGHT, WIDGET_HEIGHT,
                        SLIDER_ROWS, TRAILING_ROWS),
                this.height, EDITOR_BOTTOM_MARGIN);
        this.scrollOffset = EditorScrollLayout.clampScroll(this.scrollOffset, this.maxScroll);
        int top = EDITOR_TOP - this.scrollOffset;
        EditorColumnLayout column =
                new EditorColumnLayout(left, top, widgetWidth, WIDGET_HEIGHT, ROW_HEIGHT);

        ActionButtonLayout.Bounds row = column.nextRow();
        addRenderableWidget(new EndSlider(
                row.x(), row.y(), row.width(), row.height(),
                Component.translatable("endterraforged.gui.erosion.droplets_per_chunk"),
                DROPLETS_PER_CHUNK_SCALE, builder.dropletsPerChunk(),
                v -> builder.dropletsPerChunk((int) v)));

        row = column.nextRow();
        addRenderableWidget(new EndSlider(
                row.x(), row.y(), row.width(), row.height(),
                Component.translatable("endterraforged.gui.erosion.droplet_lifetime"),
                DROPLET_LIFETIME_SCALE, builder.dropletLifetime(),
                v -> builder.dropletLifetime((int) v)));

        row = column.nextRow();
        addRenderableWidget(new EndSlider(
                row.x(), row.y(), row.width(), row.height(),
                Component.translatable("endterraforged.gui.erosion.droplet_volume"),
                DROPLET_VOLUME_SCALE, builder.dropletVolume(),
                v -> builder.dropletVolume((float) v)));

        row = column.nextRow();
        addRenderableWidget(new EndSlider(
                row.x(), row.y(), row.width(), row.height(),
                Component.translatable("endterraforged.gui.erosion.droplet_velocity"),
                DROPLET_VELOCITY_SCALE, builder.dropletVelocity(),
                v -> builder.dropletVelocity((float) v)));

        row = column.nextRow();
        addRenderableWidget(new EndSlider(
                row.x(), row.y(), row.width(), row.height(),
                Component.translatable("endterraforged.gui.erosion.erosion_rate"),
                EROSION_RATE_SCALE, builder.erosionRate(),
                v -> builder.erosionRate((float) v)));

        row = column.nextRow();
        addRenderableWidget(new EndSlider(
                row.x(), row.y(), row.width(), row.height(),
                Component.translatable("endterraforged.gui.erosion.deposit_rate"),
                DEPOSIT_RATE_SCALE, builder.depositRate(),
                v -> builder.depositRate((float) v)));
        column.space(4);

        row = column.nextRow();
        addRenderableWidget(Button.builder(
                        Component.translatable("controls.reset"),
                        btn -> resetToDefaults())
                .bounds(row.x(), row.y(), row.width(), row.height())
                .build());

        row = column.nextRow();
        EditorScreenWidgets.addActionBar(this::addRenderableWidget, ErosionConfigEditorScreen.class,
                row.x(), row.y(), row.width(), row.height(),
                Component.translatable("endterraforged.gui.done"),
                () -> {
                    try {
                        ErosionConfig built = builder.build();
                        if (onDone != null) onDone.accept(built);
                        onClose();
                    } catch (IllegalStateException e) {
                        statusMessage = Component.literal(e.getMessage());
                    }
                },
                Component.translatable("gui.cancel"),
                this::onClose);

        EditorScreenWidgets.addLivePreview(this::addRenderableWidget, left + widgetWidth + 28,
                this.width, previewSettings,
                mode -> previewSettings = previewSettings.withMode(mode),
                scale -> previewSettings = previewSettings.withScale(scale),
                this::buildPreviewPreset, () -> previewSettings);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        EditorScreenWidgets.renderStatus(graphics, this.font, statusMessage, this.width, this.height);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int next = EditorScrollLayout.scrollOffsetAfterWheel(scrollOffset, maxScroll,
                scrollY, SCROLL_STEP);
        if (next == scrollOffset) {
            return true;
        }
        scrollOffset = next;
        rebuildWidgets();
        return true;
    }

    private void resetToDefaults() {
        builder.reset();
        lastValidPreset = previewBuilder.erosionConfig(ErosionConfig.DEFAULT).build();
        statusMessage = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(this);
        }
    }

    private EndPreset buildPreviewPreset() {
        try {
            ErosionConfig built = builder.build();
            lastValidPreset = previewBuilder.erosionConfig(built).build();
            statusMessage = null;
        } catch (IllegalStateException e) {
            statusMessage = Component.literal(e.getMessage());
        }
        return lastValidPreset;
    }
}
