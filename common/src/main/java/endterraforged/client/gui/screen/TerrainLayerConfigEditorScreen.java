package endterraforged.client.gui.screen;

import java.util.function.BiFunction;
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
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetBuilder;
import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.preview.TerrainPreviewMode;
import endterraforged.world.preview.TerrainPreviewSettings;

/**
 * Generic editor for one RTF-style terrain layer.
 */
public class TerrainLayerConfigEditorScreen extends Screen {

    private static final int EDITOR_TOP = 50;
    private static final int EDITOR_BOTTOM_MARGIN = 34;
    private static final int WIDGET_HEIGHT = 20;
    private static final int ROW_HEIGHT = 26;
    private static final int SCROLL_STEP = 32;
    private static final int SLIDER_ROWS = 4;
    private static final int TRAILING_ROWS = 1;

    private static final FloatSliderScale WEIGHT_SCALE = new FloatSliderScale(0.0F, 10.0F, 0.01F, 2);
    private static final FloatSliderScale BASE_SCALE = new FloatSliderScale(0.0F, 2.0F, 0.01F, 2);
    private static final FloatSliderScale VERTICAL_SCALE = new FloatSliderScale(0.0F, 10.0F, 0.01F, 2);
    private static final FloatSliderScale HORIZONTAL_SCALE = new FloatSliderScale(0.0F, 10.0F, 0.01F, 2);

    private final EndPresetBuilder previewBuilder;
    private final Screen parent;
    private final Consumer<TerrainLayerConfig> onDone;
    private final BiFunction<TerrainConfig, TerrainLayerConfig, TerrainConfig> layerUpdater;
    private final TerrainLayerConfig resetLayer;
    private TerrainLayerConfig layer;
    private EndPreset lastValidPreset;
    private TerrainPreviewSettings previewSettings =
            TerrainPreviewSettings.DEFAULT.withMode(TerrainPreviewMode.LAYERS);
    private int scrollOffset;
    private int maxScroll;
    private Component statusMessage;

    public TerrainLayerConfigEditorScreen(Component title, EndPreset initial, Screen parent,
                                          TerrainLayerConfig layer,
                                          TerrainLayerConfig resetLayer,
                                          BiFunction<TerrainConfig, TerrainLayerConfig, TerrainConfig> layerUpdater,
                                          Consumer<TerrainLayerConfig> onDone) {
        super(title);
        this.previewBuilder = new EndPresetBuilder(initial);
        this.parent = parent;
        this.layer = layer;
        this.resetLayer = resetLayer;
        this.layerUpdater = layerUpdater;
        this.onDone = onDone;
        this.lastValidPreset = previewBuilder.build();
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = Math.max(20, cx - 230);
        int widgetWidth = 200;
        this.maxScroll = EditorScrollLayout.maxScroll(
                EditorScrollLayout.contentBottom(EDITOR_TOP, ROW_HEIGHT, WIDGET_HEIGHT,
                        SLIDER_ROWS, TRAILING_ROWS),
                this.height, EDITOR_BOTTOM_MARGIN);
        this.scrollOffset = EditorScrollLayout.clampScroll(this.scrollOffset, this.maxScroll);
        int top = EDITOR_TOP - this.scrollOffset;
        EditorColumnLayout column =
                new EditorColumnLayout(left, top, widgetWidth, WIDGET_HEIGHT, ROW_HEIGHT);

        ActionButtonLayout.Bounds row = column.nextRow();
        addRenderableWidget(new EndSlider(row.x(), row.y(), row.width(), row.height(),
                Component.translatable("endterraforged.gui.terrain.layer.weight"),
                WEIGHT_SCALE, layer.weight(),
                v -> updateLayer((float) v, layer.baseScale(), layer.verticalScale(), layer.horizontalScale())));

        row = column.nextRow();
        addRenderableWidget(new EndSlider(row.x(), row.y(), row.width(), row.height(),
                Component.translatable("endterraforged.gui.terrain.layer.base_scale"),
                BASE_SCALE, layer.baseScale(),
                v -> updateLayer(layer.weight(), (float) v, layer.verticalScale(), layer.horizontalScale())));

        row = column.nextRow();
        addRenderableWidget(new EndSlider(row.x(), row.y(), row.width(), row.height(),
                Component.translatable("endterraforged.gui.terrain.layer.vertical_scale"),
                VERTICAL_SCALE, layer.verticalScale(),
                v -> updateLayer(layer.weight(), layer.baseScale(), (float) v, layer.horizontalScale())));

        row = column.nextRow();
        addRenderableWidget(new EndSlider(row.x(), row.y(), row.width(), row.height(),
                Component.translatable("endterraforged.gui.terrain.layer.horizontal_scale"),
                HORIZONTAL_SCALE, layer.horizontalScale(),
                v -> updateLayer(layer.weight(), layer.baseScale(), layer.verticalScale(), (float) v)));

        ActionButtonLayout.Bounds[] actions = column.nextActionRow(3);
        addRenderableWidget(Button.builder(Component.translatable("controls.reset"),
                        btn -> resetLayer())
                .bounds(actions[0].x(), actions[0].y(), actions[0].width(), actions[0].height())
                .build());
        addRenderableWidget(Button.builder(Component.translatable("endterraforged.gui.done"),
                        btn -> {
                            onDone.accept(layer);
                            onClose();
                        })
                .bounds(actions[1].x(), actions[1].y(), actions[1].width(), actions[1].height())
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), btn -> onClose())
                .bounds(actions[2].x(), actions[2].y(), actions[2].width(), actions[2].height())
                .build());

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

    private void resetLayer() {
        updateLayer(resetLayer.weight(), resetLayer.baseScale(),
                resetLayer.verticalScale(), resetLayer.horizontalScale());
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(this);
        }
    }

    private void updateLayer(float weight, float baseScale, float verticalScale, float horizontalScale) {
        layer = new TerrainLayerConfig(weight, baseScale, verticalScale, horizontalScale);
        TerrainConfig current = previewBuilder.terrainConfig();
        previewBuilder.terrainConfig(layerUpdater.apply(current, layer));
    }

    private EndPreset buildPreviewPreset() {
        try {
            lastValidPreset = previewBuilder.build();
            statusMessage = null;
        } catch (IllegalStateException e) {
            statusMessage = Component.literal(e.getMessage());
        }
        return lastValidPreset;
    }
}
