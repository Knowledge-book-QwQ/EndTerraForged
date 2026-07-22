package endterraforged.client.gui.screen;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

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
import endterraforged.client.gui.widget.SliderScale;
import endterraforged.world.config.BiomeLayoutConfig;
import endterraforged.world.config.BiomeLayoutConfigBuilder;
import endterraforged.world.config.BiomeLayoutConfigValidator;
import endterraforged.world.config.BiomeVariantBlendConfigValidator;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetBuilder;
import endterraforged.world.preview.TerrainPreviewMode;
import endterraforged.world.preview.TerrainPreviewSettings;

/**
 * Sub-screen for scalar biome ring and edge controls with live preview.
 */
public final class BiomeLayoutConfigEditorScreen extends Screen {

    private static final int EDITOR_TOP = 44;
    private static final int EDITOR_BOTTOM_MARGIN = 34;
    private static final int WIDGET_HEIGHT = 18;
    private static final int ROW_HEIGHT = 23;
    private static final int SCROLL_STEP = 32;
    private static final int TRAILING_ROWS = 2;
    private static final TerrainPreviewMode[] PREVIEW_MODES = {
            TerrainPreviewMode.BIOMES,
            TerrainPreviewMode.BIOME_CLIMATE,
            TerrainPreviewMode.TEMPERATURE,
            TerrainPreviewMode.MOISTURE
    };

    private static final IntSliderScale MAIN_ISLAND_RADIUS_SCALE = new IntSliderScale(
            BiomeLayoutConfigValidator.MIN_MAIN_ISLAND_RADIUS,
            BiomeLayoutConfigValidator.MAX_MAIN_ISLAND_RADIUS, 1);
    private static final FloatSliderScale RADIAL_COEFFICIENT_SCALE = new FloatSliderScale(
            BiomeLayoutConfigValidator.MIN_RADIAL_COEFFICIENT,
            BiomeLayoutConfigValidator.MAX_RADIAL_COEFFICIENT, 0.25F, 2);
    private static final FloatSliderScale FALLOFF_THRESHOLD_SCALE = new FloatSliderScale(
            BiomeLayoutConfigValidator.MIN_FALLOFF_THRESHOLD,
            BiomeLayoutConfigValidator.MAX_FALLOFF_THRESHOLD, 1.0F, 0);
    private static final IntSliderScale EDGE_SCALE = new IntSliderScale(
            BiomeLayoutConfigValidator.MIN_NOISE_SCALE,
            BiomeLayoutConfigValidator.MAX_NOISE_SCALE, 1);
    private static final IntSliderScale EDGE_OCTAVES = new IntSliderScale(
            BiomeLayoutConfigValidator.MIN_NOISE_OCTAVES,
            BiomeLayoutConfigValidator.MAX_NOISE_OCTAVES, 1);
    private static final FloatSliderScale EDGE_LACUNARITY = new FloatSliderScale(
            BiomeLayoutConfigValidator.MIN_LACUNARITY,
            BiomeLayoutConfigValidator.MAX_LACUNARITY, 0.05F, 2);
    private static final FloatSliderScale EDGE_GAIN = new FloatSliderScale(
            BiomeLayoutConfigValidator.MIN_GAIN,
            BiomeLayoutConfigValidator.MAX_GAIN, 0.05F, 2);
    private static final FloatSliderScale EDGE_STRENGTH = new FloatSliderScale(
            BiomeLayoutConfigValidator.MIN_EDGE_STRENGTH,
            BiomeLayoutConfigValidator.MAX_EDGE_STRENGTH, 1.0F, 0);
    private static final FloatSliderScale WARP_STRENGTH = new FloatSliderScale(
            BiomeLayoutConfigValidator.MIN_WARP_STRENGTH,
            BiomeLayoutConfigValidator.MAX_WARP_STRENGTH, 1.0F, 0);
    private static final FloatSliderScale OUTER_THRESHOLD = new FloatSliderScale(
            BiomeLayoutConfigValidator.MIN_OUTER_THRESHOLD,
            BiomeLayoutConfigValidator.MAX_OUTER_THRESHOLD, 0.05F, 2);
    private static final IntSliderScale VARIANT_BLEND_SCALE = new IntSliderScale(
            BiomeVariantBlendConfigValidator.MIN_SCALE,
            BiomeVariantBlendConfigValidator.MAX_SCALE, 1);
    private static final IntSliderScale VARIANT_BLEND_OCTAVES = new IntSliderScale(
            BiomeVariantBlendConfigValidator.MIN_OCTAVES,
            BiomeVariantBlendConfigValidator.MAX_OCTAVES, 1);

    private final Screen parent;
    private final EndPresetBuilder previewBuilder;
    private final BiomeLayoutConfigBuilder builder;
    private final Consumer<BiomeLayoutConfig> onDone;
    private EndPreset lastValidPreset;
    private TerrainPreviewSettings previewSettings =
            TerrainPreviewSettings.DEFAULT.withMode(TerrainPreviewMode.BIOMES);
    private int scrollOffset;
    private int maxScroll;
    private Component statusMessage;

    public BiomeLayoutConfigEditorScreen(EndPreset initial, Screen parent,
                                         Consumer<BiomeLayoutConfig> onDone) {
        super(Component.translatable("endterraforged.gui.biome_layout_editor.title"));
        this.parent = parent;
        this.previewBuilder = new EndPresetBuilder(initial);
        this.builder = new BiomeLayoutConfigBuilder(initial.biomeLayoutConfig());
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
        boolean twoColumns = this.width >= 620;
        int widgetWidth = twoColumns
                ? Math.min(190, Math.max(130, (this.width - 96) / 3))
                : Math.min(210, Math.max(120, this.width - 40));
        int controlGap = twoColumns ? 18 : 0;
        int controlWidth = twoColumns ? widgetWidth * 2 + controlGap : widgetWidth;
        int left = twoColumns
                ? Math.max(20, this.width / 2 - 310)
                : Math.max(20, (this.width - widgetWidth) / 2);
        SliderSpec[] shapeSliderSpecs = shapeSpecs();
        SliderSpec[] edgeSliderSpecs = edgeSpecs();
        int displayedRows =
                EditorScrollLayout.displayedRows(twoColumns, shapeSliderSpecs.length, edgeSliderSpecs.length);
        this.maxScroll = EditorScrollLayout.maxScroll(
                EditorScrollLayout.contentBottom(EDITOR_TOP, ROW_HEIGHT, WIDGET_HEIGHT,
                        displayedRows, TRAILING_ROWS),
                this.height, EDITOR_BOTTOM_MARGIN);
        this.scrollOffset = EditorScrollLayout.clampScroll(this.scrollOffset, this.maxScroll);
        int top = EDITOR_TOP - this.scrollOffset;
        EditorColumnLayout shapeColumn =
                new EditorColumnLayout(left, top, widgetWidth, WIDGET_HEIGHT, ROW_HEIGHT);
        EditorColumnLayout edgeColumn = twoColumns
                ? new EditorColumnLayout(left + widgetWidth + controlGap, top, widgetWidth,
                        WIDGET_HEIGHT, ROW_HEIGHT)
                : shapeColumn;

        addSliders(shapeColumn, shapeSliderSpecs);
        addSliders(edgeColumn, edgeSliderSpecs);

        int actionY = Math.max(shapeColumn.nextY(), edgeColumn.nextY()) + 4;
        ActionButtonLayout.Bounds row = new ActionButtonLayout.Bounds(left, actionY, controlWidth, WIDGET_HEIGHT);
        addRenderableWidget(Button.builder(
                        Component.translatable("controls.reset"),
                        btn -> resetToDefaults())
                .bounds(row.x(), row.y(), row.width(), row.height())
                .build());

        row = new ActionButtonLayout.Bounds(left, actionY + ROW_HEIGHT, controlWidth, WIDGET_HEIGHT);
        EditorScreenWidgets.addActionBar(this::addRenderableWidget, BiomeLayoutConfigEditorScreen.class,
                row.x(), row.y(), row.width(), row.height(),
                Component.translatable("endterraforged.gui.done"),
                () -> {
                    try {
                        BiomeLayoutConfig built = builder.build();
                        if (onDone != null) onDone.accept(built);
                        onClose();
                    } catch (IllegalStateException e) {
                        statusMessage = Component.literal(e.getMessage());
                    }
                },
                Component.translatable("gui.cancel"),
                this::onClose);

        EditorScreenWidgets.addLivePreview(this::addRenderableWidget, left + controlWidth + 28,
                this.width, previewSettings,
                mode -> previewSettings = previewSettings.withMode(mode),
                scale -> previewSettings = previewSettings.withScale(scale),
                this::buildPreviewPreset, () -> previewSettings, PREVIEW_MODES);
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

    private SliderSpec[] shapeSpecs() {
        return new SliderSpec[] {
                sliderSpec("endterraforged.gui.biome_layout.main_island_radius",
                        MAIN_ISLAND_RADIUS_SCALE, builder.mainIslandRadius(),
                        v -> builder.mainIslandRadius((int) v)),
                sliderSpec("endterraforged.gui.biome_layout.radial_coefficient",
                        RADIAL_COEFFICIENT_SCALE, builder.radialCoefficient(),
                        v -> builder.radialCoefficient((float) v)),
                sliderSpec("endterraforged.gui.biome_layout.highland_threshold",
                        FALLOFF_THRESHOLD_SCALE, builder.highlandThreshold(),
                        v -> builder.highlandThreshold((float) v)),
                sliderSpec("endterraforged.gui.biome_layout.midland_floor",
                        FALLOFF_THRESHOLD_SCALE, builder.midlandFloor(),
                        v -> builder.midlandFloor((float) v))
        };
    }

    private SliderSpec[] edgeSpecs() {
        return new SliderSpec[] {
                sliderSpec("endterraforged.gui.biome_layout.biome_edge_scale",
                        EDGE_SCALE, builder.biomeEdgeScale(), v -> builder.biomeEdgeScale((int) v)),
                sliderSpec("endterraforged.gui.biome_layout.biome_edge_octaves",
                        EDGE_OCTAVES, builder.biomeEdgeOctaves(), v -> builder.biomeEdgeOctaves((int) v)),
                sliderSpec("endterraforged.gui.biome_layout.biome_edge_lacunarity",
                        EDGE_LACUNARITY, builder.biomeEdgeLacunarity(),
                        v -> builder.biomeEdgeLacunarity((float) v)),
                sliderSpec("endterraforged.gui.biome_layout.biome_edge_gain",
                        EDGE_GAIN, builder.biomeEdgeGain(), v -> builder.biomeEdgeGain((float) v)),
                sliderSpec("endterraforged.gui.biome_layout.biome_edge_strength",
                        EDGE_STRENGTH, builder.biomeEdgeStrength(),
                        v -> builder.biomeEdgeStrength((float) v)),
                sliderSpec("endterraforged.gui.biome_layout.biome_warp_scale",
                        EDGE_SCALE, builder.biomeWarpScale(), v -> builder.biomeWarpScale((int) v)),
                sliderSpec("endterraforged.gui.biome_layout.biome_warp_strength",
                        WARP_STRENGTH, builder.biomeWarpStrength(),
                        v -> builder.biomeWarpStrength((float) v)),
                sliderSpec("endterraforged.gui.biome_layout.outer_noise_scale",
                        EDGE_SCALE, builder.outerNoiseScale(), v -> builder.outerNoiseScale((int) v)),
                sliderSpec("endterraforged.gui.biome_layout.outer_noise_octaves",
                        EDGE_OCTAVES, builder.outerNoiseOctaves(), v -> builder.outerNoiseOctaves((int) v)),
                sliderSpec("endterraforged.gui.biome_layout.outer_noise_threshold",
                        OUTER_THRESHOLD, builder.outerNoiseThreshold(),
                        v -> builder.outerNoiseThreshold((float) v)),
                sliderSpec("endterraforged.gui.biome_layout.variant_blend_scale",
                        VARIANT_BLEND_SCALE, builder.variantBlendScale(),
                        v -> builder.variantBlendScale((int) v)),
                sliderSpec("endterraforged.gui.biome_layout.variant_blend_octaves",
                        VARIANT_BLEND_OCTAVES, builder.variantBlendOctaves(),
                        v -> builder.variantBlendOctaves((int) v))
        };
    }

    private static SliderSpec sliderSpec(String labelKey, SliderScale scale,
                                         double initial, DoubleConsumer onChange) {
        return new SliderSpec(Component.translatable(labelKey), scale, initial, onChange);
    }

    private void addSliders(EditorColumnLayout column, SliderSpec[] specs) {
        for (SliderSpec spec : specs) {
            ActionButtonLayout.Bounds row = column.nextRow();
            addRenderableWidget(new EndSlider(row.x(), row.y(), row.width(), row.height(),
                    spec.label(), spec.scale(), spec.initial(), spec.onChange()));
        }
    }

    private void resetToDefaults() {
        builder.reset();
        lastValidPreset = previewBuilder.biomeLayoutConfig(BiomeLayoutConfig.DEFAULT).build();
        statusMessage = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(this);
        }
    }

    private EndPreset buildPreviewPreset() {
        try {
            BiomeLayoutConfig built = builder.build();
            lastValidPreset = previewBuilder.biomeLayoutConfig(built).build();
            statusMessage = null;
        } catch (IllegalStateException e) {
            statusMessage = Component.literal(e.getMessage());
        }
        return lastValidPreset;
    }

    private record SliderSpec(Component label, SliderScale scale, double initial, DoubleConsumer onChange) {
    }
}
