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

import endterraforged.client.gui.screen.SubsurfacePreviewModePolicy.ModeMemory;
import endterraforged.client.gui.screen.SubsurfacePreviewModePolicy.Section;
import endterraforged.client.gui.widget.ActionButtonLayout;
import endterraforged.client.gui.widget.CaveSlicePreviewLayout;
import endterraforged.client.gui.widget.CaveSlicePreviewWidget;
import endterraforged.client.gui.widget.EndSlider;
import endterraforged.client.gui.widget.EditorColumnLayout;
import endterraforged.client.gui.widget.EditorScrollLayout;
import endterraforged.client.gui.widget.FloatSliderScale;
import endterraforged.client.gui.widget.IntSliderScale;
import endterraforged.client.gui.widget.SliderScale;
import endterraforged.world.config.AbyssPitConfigValidator;
import endterraforged.world.config.CaveChamberConfig;
import endterraforged.world.config.CaveChamberConfigBuilder;
import endterraforged.world.config.CaveChamberConfigValidator;
import endterraforged.world.config.CaveNetworkConfig;
import endterraforged.world.config.CaveNetworkConfigBuilder;
import endterraforged.world.config.CaveNetworkConfigValidator;
import endterraforged.world.config.CaveSystemConfig;
import endterraforged.world.config.CaveSystemConfigBuilder;
import endterraforged.world.config.CaveSystemConfigValidator;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetBuilder;
import endterraforged.world.config.SubsurfaceConfig;
import endterraforged.world.config.SubsurfaceConfigBuilder;
import endterraforged.world.preview.CaveSlicePreviewSettings;
import endterraforged.world.preview.TerrainPreviewMode;
import endterraforged.world.preview.TerrainPreviewSettings;

/**
 * Sub-screen for underground terrain modifier controls with live preview.
 */
public final class SubsurfaceConfigEditorScreen extends Screen {

    private static final int EDITOR_TOP = 44;
    private static final int EDITOR_BOTTOM_MARGIN = 34;
    private static final int WIDGET_HEIGHT = 18;
    private static final int ROW_HEIGHT = 23;
    private static final int SCROLL_STEP = 32;
    private static final int TRAILING_ROWS = 2;
    private static final IntSliderScale CAVE_SLICE_OFFSET =
            new IntSliderScale(-2048, 2048, 64);

    private static final IntSliderScale SEED_OFFSET_SCALE = new IntSliderScale(
            AbyssPitConfigValidator.MIN_SEED_OFFSET,
            AbyssPitConfigValidator.MAX_SEED_OFFSET, 1);
    private static final IntSliderScale PIT_SCALE = new IntSliderScale(
            AbyssPitConfigValidator.MIN_PIT_SCALE,
            AbyssPitConfigValidator.MAX_PIT_SCALE, 1);
    private static final IntSliderScale PIT_OCTAVES = new IntSliderScale(
            AbyssPitConfigValidator.MIN_PIT_OCTAVES,
            AbyssPitConfigValidator.MAX_PIT_OCTAVES, 1);
    private static final FloatSliderScale PIT_LACUNARITY = new FloatSliderScale(
            AbyssPitConfigValidator.MIN_PIT_LACUNARITY,
            AbyssPitConfigValidator.MAX_PIT_LACUNARITY, 0.1F, 2);
    private static final FloatSliderScale PIT_GAIN = new FloatSliderScale(
            AbyssPitConfigValidator.MIN_PIT_GAIN,
            AbyssPitConfigValidator.MAX_PIT_GAIN, 0.01F, 2);
    private static final FloatSliderScale THRESHOLD = new FloatSliderScale(
            AbyssPitConfigValidator.MIN_THRESHOLD,
            AbyssPitConfigValidator.MAX_THRESHOLD, 0.01F, 2);
    private static final FloatSliderScale EDGE_FALLOFF = new FloatSliderScale(
            AbyssPitConfigValidator.MIN_EDGE_FALLOFF,
            AbyssPitConfigValidator.MAX_EDGE_FALLOFF, 0.01F, 2);
    private static final IntSliderScale DEPTH = new IntSliderScale(
            AbyssPitConfigValidator.MIN_DEPTH,
            AbyssPitConfigValidator.MAX_DEPTH, 1);
    private static final FloatSliderScale DEPTH_CURVE = new FloatSliderScale(
            AbyssPitConfigValidator.MIN_DEPTH_CURVE,
            AbyssPitConfigValidator.MAX_DEPTH_CURVE, 0.05F, 2);
    private static final FloatSliderScale MIN_LANDNESS = new FloatSliderScale(
            AbyssPitConfigValidator.MIN_LANDNESS,
            AbyssPitConfigValidator.MAX_LANDNESS, 0.01F, 2);
    private static final IntSliderScale CAVE_SEED_OFFSET = new IntSliderScale(
            CaveSystemConfigValidator.MIN_SEED_OFFSET,
            CaveSystemConfigValidator.MAX_SEED_OFFSET, 1);
    private static final IntSliderScale CAVE_DEPTH = new IntSliderScale(
            CaveSystemConfigValidator.MIN_DEPTH,
            CaveSystemConfigValidator.MAX_DEPTH, 1);
    private static final FloatSliderScale UNIT = new FloatSliderScale(
            CaveSystemConfigValidator.MIN_UNIT,
            CaveSystemConfigValidator.MAX_UNIT, 0.01F, 2);
    private static final IntSliderScale REGION_SIZE = new IntSliderScale(
            CaveNetworkConfigValidator.MIN_REGION_SIZE,
            CaveNetworkConfigValidator.MAX_REGION_SIZE, 16);
    private static final IntSliderScale CHAMBER_SPACING = new IntSliderScale(
            CaveNetworkConfigValidator.MIN_CHAMBER_SPACING,
            CaveNetworkConfigValidator.MAX_CHAMBER_SPACING, 16);
    private static final FloatSliderScale BRANCHING = new FloatSliderScale(
            CaveNetworkConfigValidator.MIN_BRANCHING,
            CaveNetworkConfigValidator.MAX_BRANCHING, 0.1F, 2);
    private static final FloatSliderScale MAX_SLOPE = new FloatSliderScale(
            CaveNetworkConfigValidator.MIN_MAX_SLOPE,
            CaveNetworkConfigValidator.MAX_MAX_SLOPE, 0.05F, 2);
    private static final IntSliderScale CHAMBER_RADIUS = new IntSliderScale(
            CaveChamberConfigValidator.MIN_RADIUS,
            CaveChamberConfigValidator.MAX_RADIUS, 1);
    private static final FloatSliderScale VERTICAL_STRETCH = new FloatSliderScale(
            CaveChamberConfigValidator.MIN_VERTICAL_STRETCH,
            CaveChamberConfigValidator.MAX_VERTICAL_STRETCH, 0.05F, 2);

    private final Screen parent;
    private final EndPresetBuilder previewBuilder;
    private final SubsurfaceConfigBuilder builder;
    private final CaveSystemConfigBuilder caveSystemBuilder;
    private final CaveNetworkConfigBuilder caveNetworkBuilder;
    private final CaveChamberConfigBuilder caveChamberBuilder;
    private final Consumer<SubsurfaceConfig> onDone;
    private EndPreset lastValidPreset;
    private TerrainPreviewSettings previewSettings =
            TerrainPreviewSettings.DEFAULT.withMode(TerrainPreviewMode.CAVES);
    private CaveSlicePreviewSettings caveSliceSettings = CaveSlicePreviewSettings.DEFAULT;
    private Section activeSection = Section.CAVES;
    private ModeMemory previewModeMemory = ModeMemory.DEFAULT;
    private int scrollOffset;
    private int maxScroll;
    private Component statusMessage;

    public SubsurfaceConfigEditorScreen(EndPreset initial, Screen parent,
                                        Consumer<SubsurfaceConfig> onDone) {
        super(Component.translatable("endterraforged.gui.subsurface_editor.title"));
        this.parent = parent;
        this.previewBuilder = new EndPresetBuilder(initial);
        this.builder = new SubsurfaceConfigBuilder(initial.subsurfaceConfig());
        this.caveSystemBuilder =
                new CaveSystemConfigBuilder(initial.subsurfaceConfig().caveSystemConfig());
        this.caveNetworkBuilder =
                new CaveNetworkConfigBuilder(initial.subsurfaceConfig().caveNetworkConfig());
        this.caveChamberBuilder =
                new CaveChamberConfigBuilder(initial.subsurfaceConfig().caveChamberConfig());
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
        SliderSpec[] shapeSpecs = shapeSpecs();
        SliderSpec[] carveSpecs = carveSpecs();
        SliderSpec[] caveSystemSpecs = caveSystemSpecs();
        SliderSpec[] caveNetworkSpecs = caveNetworkSpecs();
        SliderSpec[] caveChamberSpecs = caveChamberSpecs();
        int firstColumnRows = firstColumnRows(shapeSpecs, caveSystemSpecs);
        int secondColumnRows = secondColumnRows(carveSpecs, caveNetworkSpecs, caveChamberSpecs);
        int displayedRows =
                EditorScrollLayout.displayedRows(twoColumns, firstColumnRows, secondColumnRows);
        int contentTop = EDITOR_TOP + ROW_HEIGHT;
        this.maxScroll = EditorScrollLayout.maxScroll(
                EditorScrollLayout.contentBottom(contentTop, ROW_HEIGHT, WIDGET_HEIGHT,
                        displayedRows, TRAILING_ROWS),
                this.height, EDITOR_BOTTOM_MARGIN);
        this.scrollOffset = EditorScrollLayout.clampScroll(this.scrollOffset, this.maxScroll);
        addSectionButtons(left, EDITOR_TOP, controlWidth);
        int top = contentTop - this.scrollOffset;
        EditorColumnLayout shapeColumn =
                new EditorColumnLayout(left, top, widgetWidth, WIDGET_HEIGHT, ROW_HEIGHT);
        EditorColumnLayout carveColumn = twoColumns
                ? new EditorColumnLayout(left + widgetWidth + controlGap, top, widgetWidth,
                        WIDGET_HEIGHT, ROW_HEIGHT)
                : shapeColumn;

        if (activeSection == Section.ABYSS) {
            addEnabledToggle(shapeColumn);
            addSliders(shapeColumn, shapeSpecs);
            addSliders(carveColumn, carveSpecs);
        } else {
            addCaveEnabledToggle(shapeColumn);
            addSliders(shapeColumn, caveSystemSpecs);
            addSliders(carveColumn, caveNetworkSpecs);
            addSliders(carveColumn, caveChamberSpecs);
        }

        int actionY = Math.max(shapeColumn.nextY(), carveColumn.nextY()) + 4;
        ActionButtonLayout.Bounds row =
                new ActionButtonLayout.Bounds(left, actionY, controlWidth, WIDGET_HEIGHT);
        addRenderableWidget(Button.builder(
                        Component.translatable("controls.reset"),
                        btn -> resetToDefaults())
                .bounds(row.x(), row.y(), row.width(), row.height())
                .build());

        row = new ActionButtonLayout.Bounds(left, actionY + ROW_HEIGHT,
                controlWidth, WIDGET_HEIGHT);
        EditorScreenWidgets.addActionBar(this::addRenderableWidget,
                SubsurfaceConfigEditorScreen.class,
                row.x(), row.y(), row.width(), row.height(),
                Component.translatable("endterraforged.gui.done"),
                () -> {
                    try {
                        SubsurfaceConfig built = buildConfig();
                        if (onDone != null) {
                            onDone.accept(built);
                        }
                        onClose();
                    } catch (IllegalStateException e) {
                        statusMessage = Component.literal(e.getMessage());
                    }
                },
                Component.translatable("gui.cancel"),
                this::onClose);

        int previewX = left + controlWidth + 28;
        EditorScreenWidgets.addLivePreview(this::addRenderableWidget, previewX,
                this.width, previewSettings,
                this::setPreviewMode,
                scale -> previewSettings = previewSettings.withScale(scale),
                this::buildPreviewPreset, () -> previewSettings,
                SubsurfacePreviewModePolicy.modes(activeSection));
        addCaveSlicePreview(previewX);
    }

    private int firstColumnRows(SliderSpec[] shapeSpecs, SliderSpec[] caveSystemSpecs) {
        if (activeSection == Section.ABYSS) {
            return shapeSpecs.length + 1;
        }
        return caveSystemSpecs.length + 1;
    }

    private int secondColumnRows(SliderSpec[] carveSpecs, SliderSpec[] caveNetworkSpecs,
                                 SliderSpec[] caveChamberSpecs) {
        if (activeSection == Section.ABYSS) {
            return carveSpecs.length;
        }
        return caveNetworkSpecs.length + caveChamberSpecs.length;
    }

    private void addSectionButtons(int x, int y, int width) {
        ActionButtonLayout.Bounds[] bounds =
                ActionButtonLayout.row(x, y, width, WIDGET_HEIGHT, 2);
        Button abyss = Button.builder(
                        Component.translatable("endterraforged.gui.subsurface.section.abyss"),
                        btn -> selectSection(Section.ABYSS))
                .bounds(bounds[0].x(), bounds[0].y(), bounds[0].width(), bounds[0].height())
                .build();
        Button caves = Button.builder(
                        Component.translatable("endterraforged.gui.subsurface.section.caves"),
                        btn -> selectSection(Section.CAVES))
                .bounds(bounds[1].x(), bounds[1].y(), bounds[1].width(), bounds[1].height())
                .build();
        abyss.active = activeSection != Section.ABYSS;
        caves.active = activeSection != Section.CAVES;
        addRenderableWidget(abyss);
        addRenderableWidget(caves);
    }

    private void selectSection(Section section) {
        if (activeSection == section) {
            return;
        }
        previewModeMemory = previewModeMemory.remember(activeSection, previewSettings.mode());
        activeSection = section;
        previewSettings = previewSettings.withMode(previewModeMemory.modeFor(section));
        scrollOffset = 0;
        statusMessage = null;
        rebuildWidgets();
    }

    private void setPreviewMode(TerrainPreviewMode mode) {
        TerrainPreviewMode normalized = SubsurfacePreviewModePolicy.normalize(activeSection, mode);
        previewSettings = previewSettings.withMode(normalized);
        previewModeMemory = previewModeMemory.remember(activeSection, normalized);
    }

    private void addCaveSlicePreview(int previewX) {
        if (!SubsurfacePreviewModePolicy.showsSlicePreview(activeSection)) {
            return;
        }
        CaveSlicePreviewLayout.Placement placement =
                CaveSlicePreviewLayout.place(this.width, this.height, previewX).orElse(null);
        if (placement == null) {
            return;
        }
        ActionButtonLayout.Bounds axisBounds = placement.axisBounds();
        addRenderableWidget(CycleButton.<CaveSlicePreviewSettings.Axis>builder(this::sliceAxisName)
                .withValues(CaveSlicePreviewSettings.Axis.values())
                .withInitialValue(caveSliceSettings.axis())
                .create(axisBounds.x(), axisBounds.y(), axisBounds.width(), axisBounds.height(),
                        Component.translatable("endterraforged.gui.cave_slice.axis"),
                        (btn, axis) -> caveSliceSettings = caveSliceSettings.withAxis(axis)));
        ActionButtonLayout.Bounds offsetBounds = placement.offsetBounds();
        addRenderableWidget(new EndSlider(offsetBounds.x(), offsetBounds.y(),
                offsetBounds.width(), offsetBounds.height(),
                Component.translatable("endterraforged.gui.cave_slice.offset"),
                CAVE_SLICE_OFFSET, caveSliceSettings.offsetBlocks(),
                value -> caveSliceSettings = caveSliceSettings.withOffsetBlocks((int) value)));
        ActionButtonLayout.Bounds sliceBounds = placement.sliceBounds();
        addRenderableWidget(new CaveSlicePreviewWidget(sliceBounds.x(), sliceBounds.y(),
                sliceBounds.width(), sliceBounds.height(), this::buildPreviewPreset,
                () -> caveSliceSettings));
    }

    private Component sliceAxisName(CaveSlicePreviewSettings.Axis axis) {
        return Component.translatable("endterraforged.gui.cave_slice.axis."
                + axis.name().toLowerCase(Locale.ROOT));
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

    private void addEnabledToggle(EditorColumnLayout column) {
        ActionButtonLayout.Bounds row = column.nextRow();
        addRenderableWidget(CycleButton.booleanBuilder(
                        Component.translatable("endterraforged.gui.subsurface.abyss.enabled.on"),
                        Component.translatable("endterraforged.gui.subsurface.abyss.enabled.off"))
                .withInitialValue(builder.abyssEnabled())
                .create(row.x(), row.y(), row.width(), row.height(),
                        Component.translatable("endterraforged.gui.subsurface.abyss.enabled"),
                        (btn, enabled) -> builder.abyssEnabled(enabled)));
    }

    private void addCaveEnabledToggle(EditorColumnLayout column) {
        ActionButtonLayout.Bounds row = column.nextRow();
        addRenderableWidget(CycleButton.booleanBuilder(
                        Component.translatable("endterraforged.gui.subsurface.caves.enabled.on"),
                        Component.translatable("endterraforged.gui.subsurface.caves.enabled.off"))
                .withInitialValue(caveSystemBuilder.enabled())
                .create(row.x(), row.y(), row.width(), row.height(),
                        Component.translatable("endterraforged.gui.subsurface.caves.enabled"),
                        (btn, enabled) -> caveSystemBuilder.enabled(enabled)));
    }

    private SliderSpec[] shapeSpecs() {
        return new SliderSpec[] {
                sliderSpec("endterraforged.gui.subsurface.abyss.seed_offset",
                        SEED_OFFSET_SCALE, builder.abyssSeedOffset(),
                        v -> builder.abyssSeedOffset((int) v)),
                sliderSpec("endterraforged.gui.subsurface.abyss.pit_scale",
                        PIT_SCALE, builder.abyssPitScale(), v -> builder.abyssPitScale((int) v)),
                sliderSpec("endterraforged.gui.subsurface.abyss.pit_octaves",
                        PIT_OCTAVES, builder.abyssPitOctaves(),
                        v -> builder.abyssPitOctaves((int) v)),
                sliderSpec("endterraforged.gui.subsurface.abyss.pit_lacunarity",
                        PIT_LACUNARITY, builder.abyssPitLacunarity(),
                        v -> builder.abyssPitLacunarity((float) v)),
                sliderSpec("endterraforged.gui.subsurface.abyss.pit_gain",
                        PIT_GAIN, builder.abyssPitGain(),
                        v -> builder.abyssPitGain((float) v))
        };
    }

    private SliderSpec[] carveSpecs() {
        return new SliderSpec[] {
                sliderSpec("endterraforged.gui.subsurface.abyss.threshold",
                        THRESHOLD, builder.abyssThreshold(), v -> builder.abyssThreshold((float) v)),
                sliderSpec("endterraforged.gui.subsurface.abyss.edge_falloff",
                        EDGE_FALLOFF, builder.abyssEdgeFalloff(),
                        v -> builder.abyssEdgeFalloff((float) v)),
                sliderSpec("endterraforged.gui.subsurface.abyss.depth",
                        DEPTH, builder.abyssDepth(), v -> builder.abyssDepth((int) v)),
                sliderSpec("endterraforged.gui.subsurface.abyss.depth_curve",
                        DEPTH_CURVE, builder.abyssDepthCurve(),
                        v -> builder.abyssDepthCurve((float) v)),
                sliderSpec("endterraforged.gui.subsurface.abyss.min_landness",
                        MIN_LANDNESS, builder.abyssMinLandness(),
                        v -> builder.abyssMinLandness((float) v))
        };
    }

    private SliderSpec[] caveSystemSpecs() {
        return new SliderSpec[] {
                sliderSpec("endterraforged.gui.subsurface.caves.seed_offset",
                        CAVE_SEED_OFFSET, caveSystemBuilder.seedOffset(),
                        v -> caveSystemBuilder.seedOffset((int) v)),
                sliderSpec("endterraforged.gui.subsurface.caves.depth_start",
                        CAVE_DEPTH, caveSystemBuilder.depthStart(),
                        v -> caveSystemBuilder.depthStart((int) v)),
                sliderSpec("endterraforged.gui.subsurface.caves.depth_end",
                        CAVE_DEPTH, caveSystemBuilder.depthEnd(),
                        v -> caveSystemBuilder.depthEnd((int) v)),
                sliderSpec("endterraforged.gui.subsurface.caves.spectacle_bias",
                        UNIT, caveSystemBuilder.spectacleBias(),
                        v -> caveSystemBuilder.spectacleBias((float) v)),
                sliderSpec("endterraforged.gui.subsurface.caves.connectivity",
                        UNIT, caveSystemBuilder.connectivity(),
                        v -> caveSystemBuilder.connectivity((float) v)),
                sliderSpec("endterraforged.gui.subsurface.caves.surface_opening_chance",
                        UNIT, caveSystemBuilder.surfaceOpeningChance(),
                        v -> caveSystemBuilder.surfaceOpeningChance((float) v))
        };
    }

    private SliderSpec[] caveNetworkSpecs() {
        return new SliderSpec[] {
                sliderSpec("endterraforged.gui.subsurface.caves.region_size",
                        REGION_SIZE, caveNetworkBuilder.regionSize(),
                        v -> caveNetworkBuilder.regionSize((int) v)),
                sliderSpec("endterraforged.gui.subsurface.caves.network_density",
                        UNIT, caveNetworkBuilder.networkDensity(),
                        v -> caveNetworkBuilder.networkDensity((float) v)),
                sliderSpec("endterraforged.gui.subsurface.caves.chamber_spacing",
                        CHAMBER_SPACING, caveNetworkBuilder.chamberSpacing(),
                        v -> caveNetworkBuilder.chamberSpacing((int) v)),
                sliderSpec("endterraforged.gui.subsurface.caves.branching_factor",
                        BRANCHING, caveNetworkBuilder.branchingFactor(),
                        v -> caveNetworkBuilder.branchingFactor((float) v)),
                sliderSpec("endterraforged.gui.subsurface.caves.loop_chance",
                        UNIT, caveNetworkBuilder.loopChance(),
                        v -> caveNetworkBuilder.loopChance((float) v)),
                sliderSpec("endterraforged.gui.subsurface.caves.max_slope",
                        MAX_SLOPE, caveNetworkBuilder.maxSlope(),
                        v -> caveNetworkBuilder.maxSlope((float) v)),
                sliderSpec("endterraforged.gui.subsurface.caves.min_landness",
                        UNIT, caveNetworkBuilder.minLandness(),
                        v -> caveNetworkBuilder.minLandness((float) v))
        };
    }

    private SliderSpec[] caveChamberSpecs() {
        return new SliderSpec[] {
                sliderSpec("endterraforged.gui.subsurface.caves.chamber_probability",
                        UNIT, caveChamberBuilder.chamberProbability(),
                        v -> caveChamberBuilder.chamberProbability((float) v)),
                sliderSpec("endterraforged.gui.subsurface.caves.min_radius",
                        CHAMBER_RADIUS, caveChamberBuilder.minRadius(),
                        v -> caveChamberBuilder.minRadius((int) v)),
                sliderSpec("endterraforged.gui.subsurface.caves.max_radius",
                        CHAMBER_RADIUS, caveChamberBuilder.maxRadius(),
                        v -> caveChamberBuilder.maxRadius((int) v)),
                sliderSpec("endterraforged.gui.subsurface.caves.vertical_stretch",
                        VERTICAL_STRETCH, caveChamberBuilder.verticalStretch(),
                        v -> caveChamberBuilder.verticalStretch((float) v)),
                sliderSpec("endterraforged.gui.subsurface.caves.floor_bias",
                        UNIT, caveChamberBuilder.floorBias(),
                        v -> caveChamberBuilder.floorBias((float) v)),
                sliderSpec("endterraforged.gui.subsurface.caves.roughness",
                        UNIT, caveChamberBuilder.roughness(),
                        v -> caveChamberBuilder.roughness((float) v))
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
        caveSystemBuilder.reset();
        caveNetworkBuilder.reset();
        caveChamberBuilder.reset();
        lastValidPreset = previewBuilder.subsurfaceConfig(SubsurfaceConfig.DEFAULT).build();
        statusMessage = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(this);
        }
    }

    private EndPreset buildPreviewPreset() {
        try {
            SubsurfaceConfig built = buildConfig();
            lastValidPreset = previewBuilder.subsurfaceConfig(built).build();
            statusMessage = null;
        } catch (IllegalStateException e) {
            statusMessage = Component.literal(e.getMessage());
        }
        return lastValidPreset;
    }

    private SubsurfaceConfig buildConfig() {
        CaveSystemConfig caveSystem = caveSystemBuilder.build();
        CaveNetworkConfig caveNetwork = caveNetworkBuilder.build();
        CaveChamberConfig caveChambers = caveChamberBuilder.build();
        return builder.caveSystemConfig(caveSystem)
                .caveNetworkConfig(caveNetwork)
                .caveChamberConfig(caveChambers)
                .build();
    }

    private record SliderSpec(Component label, SliderScale scale, double initial, DoubleConsumer onChange) {
    }

}
