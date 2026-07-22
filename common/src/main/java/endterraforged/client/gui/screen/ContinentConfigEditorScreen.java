package endterraforged.client.gui.screen;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import endterraforged.client.gui.widget.ActionButtonLayout;
import endterraforged.client.gui.widget.EndSlider;
import endterraforged.client.gui.widget.EditorColumnLayout;
import endterraforged.client.gui.widget.EditorScrollLayout;
import endterraforged.client.gui.widget.FloatSliderScale;
import endterraforged.client.gui.widget.IntSliderScale;
import endterraforged.client.gui.widget.LandmassSlicePreviewLayout;
import endterraforged.client.gui.widget.LandmassSlicePreviewWidget;
import endterraforged.client.gui.widget.SliderScale;
import endterraforged.world.config.ContinentAlgorithm;
import endterraforged.world.config.ContinentBandsConfigBuilder;
import endterraforged.world.config.ContinentConfig;
import endterraforged.world.config.ContinentConfigBuilder;
import endterraforged.world.config.ContinentConfigValidator;
import endterraforged.world.config.ContinentCoastShape;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetBuilder;
import endterraforged.world.config.LandmassVolumeMode;
import endterraforged.world.noise.DistanceFunction;
import endterraforged.world.preview.TerrainPreviewMode;
import endterraforged.world.preview.TerrainPreviewSettings;
import endterraforged.world.preview.LandmassSlicePreviewSettings;

/**
 * Sub-screen for detailed macro-landmass tuning with live preview.
 */
public final class ContinentConfigEditorScreen extends Screen {

    private static final int EDITOR_TOP = 42;
    private static final int EDITOR_BOTTOM_MARGIN = 34;
    private static final int WIDGET_HEIGHT = 20;
    private static final int ROW_HEIGHT = 24;
    private static final int SCROLL_STEP = 32;
    private static final int LEADING_LEFT_ROWS = 5;
    private static final int TRAILING_RIGHT_ROWS = 2;

    private static final IntSliderScale ISLANDS_SCALE = new IntSliderScale(128, 2048, 64);
    private static final IntSliderScale CONTINENT_SCALE = new IntSliderScale(256, 4096, 64);
    private static final IntSliderScale OUTER_CONTINENT_SCALE = new IntSliderScale(
        ContinentConfigValidator.MIN_OUTER_CONTINENT_SCALE,
        ContinentConfigValidator.MAX_OUTER_CONTINENT_SCALE, 256);
    private static final IntSliderScale SHELF_THICKNESS = new IntSliderScale(
            ContinentConfigValidator.MIN_SHELF_THICKNESS,
            ContinentConfigValidator.MAX_SHELF_THICKNESS, 16);
    private static final IntSliderScale COAST_SCALE = new IntSliderScale(
            ContinentConfigValidator.MIN_COAST_SCALE,
            ContinentConfigValidator.MAX_COAST_SCALE, 64);
    private static final IntSliderScale LANDMASS_SLICE_OFFSET = new IntSliderScale(-2048, 2048, 64);
    private static final FloatSliderScale FEATURE_SPREAD = new FloatSliderScale(0.1F, 1.0F, 0.05F, 2);
    private static final FloatSliderScale UNIT = new FloatSliderScale(0.0F, 1.0F, 0.05F, 2);
    private static final FloatSliderScale BAND_THRESHOLD = new FloatSliderScale(0.0F, 1.0F, 0.001F, 3);
    private static final FloatSliderScale RADIUS = new FloatSliderScale(0.1F, 1.0F, 0.05F, 2);
    private static final IntSliderScale NOISE_OCTAVES = new IntSliderScale(1, 12, 1);
    private static final FloatSliderScale NOISE_LACUNARITY = new FloatSliderScale(0.5F, 10.5F, 0.25F, 2);
    private static final IntSliderScale WARP_SCALE = new IntSliderScale(64, 1024, 32);
    private static final FloatSliderScale WARP_STRENGTH = new FloatSliderScale(0.0F, 256.0F, 4.0F, 0);
    private static final FloatSliderScale COAST_STRENGTH =
            new FloatSliderScale(0.0F, ContinentConfigValidator.MAX_COAST_STRENGTH, 0.01F, 2);

    private final Screen parent;
    private final EndPresetBuilder previewBuilder;
    private final ContinentConfigBuilder builder;
    private final ContinentBandsConfigBuilder bandsBuilder;
    private final Consumer<ContinentConfig> onDone;
    private EndPreset lastValidPreset;
    private TerrainPreviewSettings previewSettings =
            TerrainPreviewSettings.DEFAULT.withMode(TerrainPreviewMode.VOLUME);
    private LandmassSlicePreviewSettings landmassSliceSettings = LandmassSlicePreviewSettings.DEFAULT;
    private int scrollOffset;
    private int maxScroll;
    private Component statusMessage;

    public ContinentConfigEditorScreen(EndPreset initial, Screen parent,
                                       Consumer<ContinentConfig> onDone) {
        super(Component.translatable("endterraforged.gui.continent_editor.title"));
        this.parent = parent;
        this.previewBuilder = new EndPresetBuilder(initial);
        this.builder = new ContinentConfigBuilder(initial.continentConfig());
        this.bandsBuilder = new ContinentBandsConfigBuilder(initial.continentConfig().continentBands());
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
        boolean twoColumns = this.width >= 500;
        int widgetWidth = twoColumns ? 210 : Math.min(210, this.width - 40);
        int left = twoColumns
                ? Math.max(20, this.width / 2 - 230)
                : Math.max(20, (this.width - widgetWidth) / 2);
        int right = twoColumns ? left + 220 : left;
        SliderSpec[] leftSpecs = leftSliderSpecs();
        SliderSpec[] rightSpecs = rightSliderSpecs();
        int displayedRows = EditorScrollLayout.displayedRows(
                twoColumns, LEADING_LEFT_ROWS + leftSpecs.length, rightSpecs.length);
        this.maxScroll = EditorScrollLayout.maxScroll(
                EditorScrollLayout.contentBottom(EDITOR_TOP, ROW_HEIGHT, WIDGET_HEIGHT,
                        displayedRows, TRAILING_RIGHT_ROWS),
                this.height, EDITOR_BOTTOM_MARGIN);
        this.scrollOffset = EditorScrollLayout.clampScroll(this.scrollOffset, this.maxScroll);
        int top = EDITOR_TOP - this.scrollOffset;
        EditorColumnLayout leftColumn =
                new EditorColumnLayout(left, top, widgetWidth, WIDGET_HEIGHT, ROW_HEIGHT);

        ActionButtonLayout.Bounds row = leftColumn.nextRow();
        addRenderableWidget(CycleButton.<DistanceFunction>builder(shape -> Component.literal(shape.name()))
                .withValues(DistanceFunction.values())
                .withInitialValue(builder.continentShape())
                .create(row.x(), row.y(), row.width(), row.height(),
                        Component.translatable("endterraforged.gui.continent.continent_shape"),
                        (btn, shape) -> builder.continentShape(shape)));

        row = leftColumn.nextRow();
        addRenderableWidget(CycleButton.<ContinentAlgorithm>builder(
                        ContinentConfigEditorScreen::continentAlgorithmName)
                .withValues(ContinentAlgorithm.LEGACY_RADIAL, ContinentAlgorithm.RTF_MULTI)
                .withInitialValue(builder.continentAlgorithm())
                .create(row.x(), row.y(), row.width(), row.height(),
                        Component.translatable("endterraforged.gui.continent.algorithm"),
                        (btn, algorithm) -> applyAlgorithmProfile(algorithm)));

        row = leftColumn.nextRow();
        addRenderableWidget(CycleButton.<LandmassVolumeMode>builder(
                        ContinentConfigEditorScreen::volumeModeName)
                .withValues(LandmassVolumeMode.values())
                .withInitialValue(builder.landmassVolumeMode())
                .create(row.x(), row.y(), row.width(), row.height(),
                        Component.translatable("endterraforged.gui.continent.volume_mode"),
                        (btn, mode) -> builder.landmassVolumeMode(mode)));

        row = leftColumn.nextRow();
        addRenderableWidget(CycleButton.<ContinentCoastShape>builder(
                        ContinentConfigEditorScreen::coastShapeName)
                .withValues(ContinentCoastShape.values())
                .withInitialValue(builder.coastShape())
                .create(row.x(), row.y(), row.width(), row.height(),
                        Component.translatable("endterraforged.gui.continent.coast_shape"),
                        (btn, shape) -> builder.coastShape(shape)));

        row = leftColumn.nextRow();
        addRenderableWidget(CycleButton.<Boolean>builder(
                        enabled -> Component.translatable(enabled ? "options.on" : "options.off"))
                .withValues(Boolean.TRUE, Boolean.FALSE)
                .withInitialValue(bandsBuilder.enabled())
                .create(row.x(), row.y(), row.width(), row.height(),
                        Component.translatable("endterraforged.gui.continent.bands_enabled"),
                        (btn, enabled) -> setBandsEnabled(enabled)));

        addSliders(leftColumn, leftSpecs);

        int rightY = twoColumns ? top : leftColumn.nextY();
        EditorColumnLayout rightColumn =
                new EditorColumnLayout(right, rightY, widgetWidth, WIDGET_HEIGHT, ROW_HEIGHT);
        addSliders(rightColumn, rightSpecs);
        rightColumn.space(4);

        row = rightColumn.nextRow();
        addRenderableWidget(Button.builder(
                        Component.translatable("controls.reset"),
                        btn -> resetToDefaults())
                .bounds(row.x(), row.y(), row.width(), row.height())
                .build());

        row = rightColumn.nextRow();
        EditorScreenWidgets.addActionBar(this::addRenderableWidget, ContinentConfigEditorScreen.class,
                row.x(), row.y(), row.width(), row.height(),
                Component.translatable("endterraforged.gui.done"),
                () -> {
                    try {
                        ContinentConfig built = buildContinentConfig();
                        if (onDone != null) onDone.accept(built);
                        onClose();
                    } catch (IllegalStateException e) {
                        statusMessage = Component.literal(e.getMessage());
                    }
                },
                Component.translatable("gui.cancel"),
                this::onClose);

        EditorScreenWidgets.addLivePreview(this::addRenderableWidget, right + widgetWidth + 28,
                this.width, 30, previewSettings,
                mode -> previewSettings = previewSettings.withMode(mode),
                scale -> previewSettings = previewSettings.withScale(scale),
                this::buildPreviewPreset, () -> previewSettings);
        addLandmassSlicePreview(right + widgetWidth + 28);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 18, 0xFFFFFF);
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

    private SliderSpec[] leftSliderSpecs() {
        return new SliderSpec[] {
                sliderSpec("endterraforged.gui.continent.islands_scale",
                        ISLANDS_SCALE, builder.islandsScale(), v -> builder.islandsScale((int) v)),
                sliderSpec("endterraforged.gui.continent.continent_scale",
                        CONTINENT_SCALE, builder.continentScale(), v -> builder.continentScale((int) v)),
                sliderSpec("endterraforged.gui.continent.outer_continent_scale",
                        OUTER_CONTINENT_SCALE, builder.outerContinentScale(),
                        v -> builder.outerContinentScale((int) v)),
                sliderSpec("endterraforged.gui.continent.continent_jitter",
                        UNIT, builder.continentJitter(), v -> builder.continentJitter((float) v)),
                sliderSpec("endterraforged.gui.continent.continent_skipping",
                        UNIT, builder.continentSkipping(), v -> builder.continentSkipping((float) v)),
                sliderSpec("endterraforged.gui.continent.continent_size_variance",
                        UNIT, builder.continentSizeVariance(), v -> builder.continentSizeVariance((float) v)),
                sliderSpec("endterraforged.gui.continent.feature_spread",
                        FEATURE_SPREAD, builder.featureSpread(), v -> builder.featureSpread((float) v)),
                sliderSpec("endterraforged.gui.continent.island_radius",
                        RADIUS, builder.islandRadius(), v -> builder.islandRadius((float) v)),
                sliderSpec("endterraforged.gui.continent.island_scatter",
                        UNIT, builder.islandScatter(), v -> builder.islandScatter((float) v))
        };
    }

    private SliderSpec[] rightSliderSpecs() {
        return new SliderSpec[] {
                sliderSpec("endterraforged.gui.continent.shelf_thickness",
                        SHELF_THICKNESS, builder.shelfThickness(),
                        v -> builder.shelfThickness((int) v)),
                sliderSpec("endterraforged.gui.continent.shelf_edge_thickness",
                        SHELF_THICKNESS, builder.shelfEdgeThickness(),
                        v -> builder.shelfEdgeThickness((int) v)),
                sliderSpec("endterraforged.gui.continent.coast_scale",
                        COAST_SCALE, builder.coastScale(), v -> builder.coastScale((int) v)),
                sliderSpec("endterraforged.gui.continent.coast_strength",
                        COAST_STRENGTH, builder.coastStrength(),
                        v -> builder.coastStrength((float) v)),
                sliderSpec("endterraforged.gui.continent.coast_cell_blend",
                        UNIT, builder.coastCellBlend(), v -> builder.coastCellBlend((float) v)),
                sliderSpec("endterraforged.gui.continent.band_void_outer",
                        BAND_THRESHOLD, bandsBuilder.voidOuterThreshold(),
                        v -> bandsBuilder.voidOuterThreshold((float) v)),
                sliderSpec("endterraforged.gui.continent.band_shelf",
                        BAND_THRESHOLD, bandsBuilder.shelfThreshold(),
                        v -> bandsBuilder.shelfThreshold((float) v)),
                sliderSpec("endterraforged.gui.continent.band_rim",
                        BAND_THRESHOLD, bandsBuilder.rimThreshold(),
                        v -> bandsBuilder.rimThreshold((float) v)),
                sliderSpec("endterraforged.gui.continent.band_coast",
                        BAND_THRESHOLD, bandsBuilder.coastThreshold(),
                        v -> bandsBuilder.coastThreshold((float) v)),
                sliderSpec("endterraforged.gui.continent.band_inland",
                        BAND_THRESHOLD, bandsBuilder.inlandThreshold(),
                        v -> bandsBuilder.inlandThreshold((float) v)),
                sliderSpec("endterraforged.gui.continent.continent_noise_octaves",
                        NOISE_OCTAVES, builder.continentNoiseOctaves(), v -> builder.continentNoiseOctaves((int) v)),
                sliderSpec("endterraforged.gui.continent.continent_noise_gain",
                        UNIT, builder.continentNoiseGain(), v -> builder.continentNoiseGain((float) v)),
                sliderSpec("endterraforged.gui.continent.continent_noise_lacunarity",
                        NOISE_LACUNARITY, builder.continentNoiseLacunarity(),
                        v -> builder.continentNoiseLacunarity((float) v)),
                sliderSpec("endterraforged.gui.continent.rift_threshold",
                        UNIT, builder.riftThreshold(), v -> builder.riftThreshold((float) v)),
                sliderSpec("endterraforged.gui.continent.rift_strength",
                        UNIT, builder.riftStrength(), v -> builder.riftStrength((float) v)),
                sliderSpec("endterraforged.gui.continent.warp_scale",
                        WARP_SCALE, builder.warpScale(), v -> builder.warpScale((int) v)),
                sliderSpec("endterraforged.gui.continent.warp_strength",
                        WARP_STRENGTH, builder.warpStrength(), v -> builder.warpStrength((float) v))
        };
    }

    private static SliderSpec sliderSpec(String labelKey, SliderScale scale,
                                         double initial, DoubleConsumer onChange) {
        return new SliderSpec(Component.translatable(labelKey), scale, initial, onChange);
    }

    private void addSliders(EditorColumnLayout column, SliderSpec[] specs) {
        for (SliderSpec spec : specs) {
            addSlider(column, spec);
        }
    }

    private void addSlider(EditorColumnLayout column, SliderSpec spec) {
        ActionButtonLayout.Bounds row = column.nextRow();
        addRenderableWidget(slider(row.x(), row.y(), row.width(), row.height(),
                spec.label(), spec.scale(), spec.initial(), spec.onChange()));
    }

    private static EndSlider slider(int x, int y, int width, int height,
                                    Component label, SliderScale scale,
                                    double initial, DoubleConsumer onChange) {
        return new EndSlider(x, y, width, height, label, scale, initial, onChange);
    }

    private static Component volumeModeName(LandmassVolumeMode mode) {
        return Component.translatable("endterraforged.gui.continent.volume_mode."
                + mode.name().toLowerCase(Locale.ROOT));
    }

    private static Component continentAlgorithmName(ContinentAlgorithm algorithm) {
        return Component.translatable("endterraforged.gui.continent.algorithm."
                + algorithm.name().toLowerCase(Locale.ROOT));
    }

    private static Component coastShapeName(ContinentCoastShape shape) {
        return Component.translatable("endterraforged.gui.continent.coast_shape."
                + shape.name().toLowerCase(Locale.ROOT));
    }

    private void addLandmassSlicePreview(int previewX) {
        LandmassSlicePreviewLayout.Placement placement =
                LandmassSlicePreviewLayout.place(this.width, this.height, previewX).orElse(null);
        if (placement == null) {
            return;
        }
        ActionButtonLayout.Bounds axisBounds = placement.axisBounds();
        addRenderableWidget(CycleButton.<LandmassSlicePreviewSettings.Axis>builder(
                        ContinentConfigEditorScreen::sliceAxisName)
                .withValues(LandmassSlicePreviewSettings.Axis.values())
                .withInitialValue(landmassSliceSettings.axis())
                .create(axisBounds.x(), axisBounds.y(), axisBounds.width(), axisBounds.height(),
                        Component.translatable("endterraforged.gui.landmass_slice.axis"),
                        (btn, axis) -> landmassSliceSettings = landmassSliceSettings.withAxis(axis)));
        ActionButtonLayout.Bounds offsetBounds = placement.offsetBounds();
        addRenderableWidget(new EndSlider(offsetBounds.x(), offsetBounds.y(),
                offsetBounds.width(), offsetBounds.height(),
                Component.translatable("endterraforged.gui.landmass_slice.offset"),
                LANDMASS_SLICE_OFFSET, landmassSliceSettings.offsetBlocks(),
                value -> landmassSliceSettings = landmassSliceSettings.withOffsetBlocks((int) value)));
        ActionButtonLayout.Bounds sliceBounds = placement.sliceBounds();
        addRenderableWidget(new LandmassSlicePreviewWidget(sliceBounds.x(), sliceBounds.y(),
                sliceBounds.width(), sliceBounds.height(), this::buildPreviewPreset,
                () -> landmassSliceSettings));
    }

    private static Component sliceAxisName(LandmassSlicePreviewSettings.Axis axis) {
        return Component.translatable("endterraforged.gui.landmass_slice.axis."
                + axis.name().toLowerCase(Locale.ROOT));
    }

    private void resetToDefaults() {
        builder.resetForCurrentAlgorithm();
        bandsBuilder.reset();
        lastValidPreset = previewBuilder.continentConfig(buildContinentConfig()).build();
        statusMessage = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(this);
        }
    }

    private void applyAlgorithmProfile(ContinentAlgorithm algorithm) {
        builder.applyRecommendedProfile(algorithm);
        if (algorithm == ContinentAlgorithm.RTF_MULTI) {
            statusMessage = Component.translatable("endterraforged.gui.continent.rtf_multi_profile_applied");
        } else {
            statusMessage = null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(this);
        }
    }

    private EndPreset buildPreviewPreset() {
        try {
            ContinentConfig built = buildContinentConfig();
            lastValidPreset = previewBuilder.continentConfig(built).build();
            statusMessage = null;
        } catch (IllegalStateException e) {
            statusMessage = Component.literal(e.getMessage());
        }
        return lastValidPreset;
    }

    private void setBandsEnabled(boolean enabled) {
        if (enabled && !bandsBuilder.enabled()) {
            bandsBuilder.reset();
        }
        bandsBuilder.enabled(enabled);
    }

    private ContinentConfig buildContinentConfig() {
        return builder.continentBands(bandsBuilder.build()).build();
    }

    private record SliderSpec(Component label, SliderScale scale, double initial, DoubleConsumer onChange) {
    }
}
