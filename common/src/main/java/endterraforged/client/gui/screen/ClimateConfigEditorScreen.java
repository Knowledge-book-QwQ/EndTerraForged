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
import endterraforged.world.config.ClimateConfig;
import endterraforged.world.config.ClimateConfigBuilder;
import endterraforged.world.config.ClimateConfigValidator;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetBuilder;
import endterraforged.world.preview.TerrainPreviewMode;
import endterraforged.world.preview.TerrainPreviewSettings;

/**
 * Sub-screen for detailed climate-field tuning with live preview.
 */
public final class ClimateConfigEditorScreen extends Screen {

    private static final int EDITOR_TOP = 44;
    private static final int EDITOR_BOTTOM_MARGIN = 34;
    private static final int WIDGET_HEIGHT = 18;
    private static final int ROW_HEIGHT = 23;
    private static final int SCROLL_STEP = 32;
    private static final int TRAILING_ROWS = 2;

    private static final FloatSliderScale CLIMATE_RADIUS_SCALE = new FloatSliderScale(
            ClimateConfigValidator.MIN_CLIMATE_RADIUS,
            ClimateConfigValidator.MAX_CLIMATE_RADIUS,
            64.0F, 0);
    private static final IntSliderScale NOISE_SCALE = new IntSliderScale(
            ClimateConfigValidator.MIN_NOISE_SCALE,
            ClimateConfigValidator.MAX_NOISE_SCALE,
            1);
    private static final IntSliderScale SEED_OFFSET_SCALE = new IntSliderScale(
            ClimateConfigValidator.MIN_SEED_OFFSET,
            ClimateConfigValidator.MAX_SEED_OFFSET,
            1);
    private static final FloatSliderScale PERTURBATION_SCALE =
            new FloatSliderScale(0.0F, 1.0F, 0.01F, 2);
    private static final FloatSliderScale CHANNEL_VALUE_SCALE = new FloatSliderScale(
            ClimateConfigValidator.MIN_CHANNEL_VALUE,
            ClimateConfigValidator.MAX_CHANNEL_VALUE,
            0.01F, 2);
    private static final FloatSliderScale CHANNEL_BIAS_SCALE = new FloatSliderScale(
            ClimateConfigValidator.MIN_CHANNEL_BIAS,
            ClimateConfigValidator.MAX_CHANNEL_BIAS,
            0.01F, 2);
    private static final FloatSliderScale FALLOFF_SCALE = new FloatSliderScale(
            ClimateConfigValidator.MIN_FALLOFF,
            ClimateConfigValidator.MAX_FALLOFF,
            0.1F, 1);

    private final Screen parent;
    private final EndPresetBuilder previewBuilder;
    private final ClimateConfigBuilder builder;
    private final Consumer<ClimateConfig> onDone;
    private EndPreset lastValidPreset;
    private TerrainPreviewSettings previewSettings =
            TerrainPreviewSettings.DEFAULT.withMode(TerrainPreviewMode.TEMPERATURE);
    private int scrollOffset;
    private int maxScroll;
    private Component statusMessage;

    public ClimateConfigEditorScreen(EndPreset initial, Screen parent,
                                     Consumer<ClimateConfig> onDone) {
        super(Component.translatable("endterraforged.gui.climate_editor.title"));
        this.parent = parent;
        this.previewBuilder = new EndPresetBuilder(initial);
        this.builder = new ClimateConfigBuilder(initial.climateConfig());
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
        SliderSpec[] primarySpecs = primarySliderSpecs();
        SliderSpec[] rangeSpecs = rangeSliderSpecs();
        int displayedRows = EditorScrollLayout.displayedRows(
                twoColumns, primarySpecs.length, rangeSpecs.length);
        this.maxScroll = EditorScrollLayout.maxScroll(
                EditorScrollLayout.contentBottom(EDITOR_TOP, ROW_HEIGHT, WIDGET_HEIGHT,
                        displayedRows, TRAILING_ROWS),
                this.height, EDITOR_BOTTOM_MARGIN);
        this.scrollOffset = EditorScrollLayout.clampScroll(this.scrollOffset, this.maxScroll);
        int top = EDITOR_TOP - this.scrollOffset;
        EditorColumnLayout primaryColumn =
                new EditorColumnLayout(left, top, widgetWidth, WIDGET_HEIGHT, ROW_HEIGHT);
        EditorColumnLayout rangeColumn = twoColumns
                ? new EditorColumnLayout(left + widgetWidth + controlGap, top, widgetWidth,
                        WIDGET_HEIGHT, ROW_HEIGHT)
                : primaryColumn;

        addSliders(primaryColumn, primarySpecs);
        addSliders(rangeColumn, rangeSpecs);

        int actionY = Math.max(primaryColumn.nextY(), rangeColumn.nextY()) + 4;
        ActionButtonLayout.Bounds row = new ActionButtonLayout.Bounds(left, actionY, controlWidth, WIDGET_HEIGHT);
        addRenderableWidget(Button.builder(
                        Component.translatable("controls.reset"),
                        btn -> resetToDefaults())
                .bounds(row.x(), row.y(), row.width(), row.height())
                .build());

        row = new ActionButtonLayout.Bounds(left, actionY + ROW_HEIGHT, controlWidth, WIDGET_HEIGHT);
        EditorScreenWidgets.addActionBar(this::addRenderableWidget, ClimateConfigEditorScreen.class,
                row.x(), row.y(), row.width(), row.height(),
                Component.translatable("endterraforged.gui.done"),
                () -> {
                    try {
                        ClimateConfig built = builder.build();
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

    private SliderSpec[] primarySliderSpecs() {
        return new SliderSpec[] {
                sliderSpec("endterraforged.gui.climate.climate_radius",
                        CLIMATE_RADIUS_SCALE, builder.climateRadius(), v -> builder.climateRadius((float) v)),
                sliderSpec("endterraforged.gui.climate.temperature_scale",
                        NOISE_SCALE, builder.temperatureScale(), v -> builder.temperatureScale((int) v)),
                sliderSpec("endterraforged.gui.climate.temperature_seed_offset",
                        SEED_OFFSET_SCALE, builder.temperatureSeedOffset(),
                        v -> builder.temperatureSeedOffset((int) v)),
                sliderSpec("endterraforged.gui.climate.moisture_scale",
                        NOISE_SCALE, builder.moistureScale(), v -> builder.moistureScale((int) v)),
                sliderSpec("endterraforged.gui.climate.moisture_seed_offset",
                        SEED_OFFSET_SCALE, builder.moistureSeedOffset(),
                        v -> builder.moistureSeedOffset((int) v)),
                sliderSpec("endterraforged.gui.climate.wind_scale",
                        NOISE_SCALE, builder.windScale(), v -> builder.windScale((int) v)),
                sliderSpec("endterraforged.gui.climate.perturbation",
                        PERTURBATION_SCALE, builder.perturbation(), v -> builder.perturbation((float) v))
        };
    }

    private SliderSpec[] rangeSliderSpecs() {
        return new SliderSpec[] {
                sliderSpec("endterraforged.gui.climate.temperature_falloff",
                        FALLOFF_SCALE, builder.temperatureFalloff(),
                        v -> builder.temperatureFalloff((float) v)),
                sliderSpec("endterraforged.gui.climate.temperature_min",
                        CHANNEL_VALUE_SCALE, builder.temperatureMin(), v -> builder.temperatureMin((float) v)),
                sliderSpec("endterraforged.gui.climate.temperature_max",
                        CHANNEL_VALUE_SCALE, builder.temperatureMax(), v -> builder.temperatureMax((float) v)),
                sliderSpec("endterraforged.gui.climate.temperature_bias",
                        CHANNEL_BIAS_SCALE, builder.temperatureBias(), v -> builder.temperatureBias((float) v)),
                sliderSpec("endterraforged.gui.climate.moisture_falloff",
                        FALLOFF_SCALE, builder.moistureFalloff(),
                        v -> builder.moistureFalloff((float) v)),
                sliderSpec("endterraforged.gui.climate.moisture_min",
                        CHANNEL_VALUE_SCALE, builder.moistureMin(), v -> builder.moistureMin((float) v)),
                sliderSpec("endterraforged.gui.climate.moisture_max",
                        CHANNEL_VALUE_SCALE, builder.moistureMax(), v -> builder.moistureMax((float) v)),
                sliderSpec("endterraforged.gui.climate.moisture_bias",
                        CHANNEL_BIAS_SCALE, builder.moistureBias(), v -> builder.moistureBias((float) v))
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
        lastValidPreset = previewBuilder.climateConfig(ClimateConfig.DEFAULT).build();
        statusMessage = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(this);
        }
    }

    private EndPreset buildPreviewPreset() {
        try {
            ClimateConfig built = builder.build();
            lastValidPreset = previewBuilder.climateConfig(built).build();
            statusMessage = null;
        } catch (IllegalStateException e) {
            statusMessage = Component.literal(e.getMessage());
        }
        return lastValidPreset;
    }

    private record SliderSpec(Component label, SliderScale scale, double initial, DoubleConsumer onChange) {
    }
}
