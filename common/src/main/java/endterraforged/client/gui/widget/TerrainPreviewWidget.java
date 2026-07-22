package endterraforged.client.gui.widget;

import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import endterraforged.world.config.EndPreset;
import endterraforged.world.heightmap.EndTerrainLayer;
import endterraforged.world.preview.TerrainPreview;
import endterraforged.world.preview.TerrainPreviewLayerStats;
import endterraforged.world.preview.TerrainPreviewMode;
import endterraforged.world.preview.TerrainPreviewPalette;
import endterraforged.world.preview.TerrainPreviewSampler;
import endterraforged.world.preview.TerrainPreviewSettings;

/**
 * WYSIWYG terrain preview panel. The widget caches the last preset snapshot and
 * resamples only when slider/cycle changes produce a different preset value.
 */
public final class TerrainPreviewWidget extends AbstractWidget {
    private static final int LEGEND_HEIGHT = 18;
    private static final EndTerrainLayer[] LEGEND_LAYERS = {
            EndTerrainLayer.PLAINS,
            EndTerrainLayer.HILLS,
            EndTerrainLayer.PLATEAU,
            EndTerrainLayer.MOUNTAINS,
            EndTerrainLayer.VOLCANO
    };

    private final Supplier<EndPreset> presetSupplier;
    private final Supplier<TerrainPreviewSettings> settingsSupplier;
    private EndPreset cachedPreset;
    private TerrainPreviewSettings cachedSettings;
    private TerrainPreview cachedPreview;
    private int cachedSampleSize;

    public TerrainPreviewWidget(int x, int y, int width, int height,
                                Supplier<EndPreset> presetSupplier) {
        this(x, y, width, height, presetSupplier, () -> TerrainPreviewSettings.DEFAULT);
    }

    public TerrainPreviewWidget(int x, int y, int width, int height,
                                Supplier<EndPreset> presetSupplier,
                                Supplier<TerrainPreviewSettings> settingsSupplier) {
        super(x, y, width, height, Component.translatable("endterraforged.gui.preview"));
        this.presetSupplier = Objects.requireNonNull(presetSupplier, "presetSupplier");
        this.settingsSupplier = Objects.requireNonNull(settingsSupplier, "settingsSupplier");
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int drawSize = Math.min(getWidth() - 2, getHeight() - 2);
        if (drawSize <= 0) {
            return;
        }
        TerrainPreview preview = preview(drawSize);
        boolean showLegend = shouldRenderLegend(preview, drawSize, cachedSettings.mode());
        if (showLegend) {
            drawSize = Math.min(getWidth() - 2, getHeight() - LEGEND_HEIGHT - 4);
            if (drawSize <= 0) {
                return;
            }
            preview = preview(drawSize);
            showLegend = shouldRenderLegend(preview, drawSize, cachedSettings.mode());
        }
        int size = preview.size();
        int left = getX() + (getWidth() - drawSize) / 2;
        int contentHeight = drawSize + (showLegend ? LEGEND_HEIGHT + 2 : 0);
        int top = getY() + (getHeight() - contentHeight) / 2;
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xAA05040A);
        for (int z = 0; z < drawSize; z++) {
            int sampleZ = z * size / drawSize;
            for (int x = 0; x < drawSize; x++) {
                int sampleX = x * size / drawSize;
                graphics.fill(left + x, top + z, left + x + 1, top + z + 1,
                        preview.colorAt(sampleX, sampleZ));
            }
        }
        graphics.renderOutline(left - 1, top - 1, drawSize + 2, drawSize + 2, 0xFF9E8FDB);
        if (showLegend) {
            renderLayerLegend(graphics, preview.layerStats(), left, top + drawSize + 4, drawSize);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    private TerrainPreview preview(int drawSize) {
        EndPreset preset = presetSupplier.get();
        TerrainPreviewSettings settings = Objects.requireNonNull(settingsSupplier.get(), "previewSettings");
        int sampleSize = TerrainPreviewSampler.sampleSizeForViewport(drawSize);
        if (!preset.equals(cachedPreset) || !settings.equals(cachedSettings)
                || sampleSize != cachedSampleSize) {
            cachedPreset = preset;
            cachedSettings = settings;
            cachedSampleSize = sampleSize;
            cachedPreview = TerrainPreviewSampler.sampleForViewport(preset, drawSize, settings);
        }
        return cachedPreview;
    }

    private static boolean shouldRenderLegend(TerrainPreview preview, int drawSize,
                                              TerrainPreviewMode mode) {
        return (mode == TerrainPreviewMode.COMBINED || mode == TerrainPreviewMode.LAYERS)
                && drawSize >= 96
                && preview.layerStats().hasAuxiliaryLayers();
    }

    private static void renderLayerLegend(GuiGraphics graphics, TerrainPreviewLayerStats stats,
                                          int left, int top, int width) {
        int total = stats.total();
        if (total <= 0) {
            return;
        }
        graphics.fill(left, top, left + width, top + 4, 0xFF242036);
        for (EndTerrainLayer layer : LEGEND_LAYERS) {
            int count = stats.count(layer);
            if (count <= 0) {
                continue;
            }
            int segmentWidth = Math.max(1, Math.round(width * stats.coverage(layer)));
            int segmentLeft = left + Math.round(width * cumulativeCoverageBefore(stats, layer));
            graphics.fill(segmentLeft, top, Math.min(left + width, segmentLeft + segmentWidth),
                    top + 4, TerrainPreviewPalette.layerColor(layer));
        }
        if (stats.hasBlendedSamples()) {
            int blendWidth = Math.max(1, Math.round(width * stats.blendCoverage()));
            int blendLeft = left + (width - blendWidth) / 2;
            graphics.fill(blendLeft, top + 5, blendLeft + blendWidth, top + 6, 0xFFE9E4FF);
        }

        Minecraft minecraft = Minecraft.getInstance();
        int slotWidth = width / LEGEND_LAYERS.length;
        for (int i = 0; i < LEGEND_LAYERS.length; i++) {
            EndTerrainLayer layer = LEGEND_LAYERS[i];
            int slotX = left + i * slotWidth;
            graphics.fill(slotX, top + 8, slotX + 5, top + 13,
                    TerrainPreviewPalette.layerColor(layer));
            String label = layerLabel(layer) + Math.round(stats.coverage(layer) * 100.0F);
            graphics.drawString(minecraft.font, label, slotX + 7, top + 6, 0xFFE9E4FF, false);
        }
    }

    private static float cumulativeCoverageBefore(TerrainPreviewLayerStats stats, EndTerrainLayer target) {
        float coverage = 0.0F;
        for (EndTerrainLayer layer : EndTerrainLayer.values()) {
            if (layer == target) {
                return coverage;
            }
            coverage += stats.coverage(layer);
        }
        return coverage;
    }

    private static String layerLabel(EndTerrainLayer layer) {
        return switch (layer) {
            case PLAINS -> "P";
            case HILLS -> "H";
            case PLATEAU -> "T";
            case MOUNTAINS -> "M";
            case VOLCANO -> "V";
            case NONE -> "";
        };
    }
}
