package endterraforged.client.gui.widget;

import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import endterraforged.world.config.EndPreset;
import endterraforged.world.preview.CaveSlicePreview;
import endterraforged.world.preview.CaveSlicePreviewSampler;
import endterraforged.world.preview.CaveSlicePreviewSettings;
import endterraforged.world.preview.TerrainPreviewSampler;

/**
 * Vertical cave slice preview panel backed by the common cave slice sampler.
 */
public final class CaveSlicePreviewWidget extends AbstractWidget {

    private final Supplier<EndPreset> presetSupplier;
    private final Supplier<CaveSlicePreviewSettings> settingsSupplier;
    private EndPreset cachedPreset;
    private CaveSlicePreviewSettings cachedSettings;
    private CaveSlicePreview cachedPreview;
    private int cachedWidth;
    private int cachedHeight;

    public CaveSlicePreviewWidget(int x, int y, int width, int height,
                                  Supplier<EndPreset> presetSupplier) {
        this(x, y, width, height, presetSupplier, () -> CaveSlicePreviewSettings.DEFAULT);
    }

    public CaveSlicePreviewWidget(int x, int y, int width, int height,
                                  Supplier<EndPreset> presetSupplier,
                                  Supplier<CaveSlicePreviewSettings> settingsSupplier) {
        super(x, y, width, height, Component.translatable("endterraforged.gui.cave_slice_preview"));
        this.presetSupplier = Objects.requireNonNull(presetSupplier, "presetSupplier");
        this.settingsSupplier = Objects.requireNonNull(settingsSupplier, "settingsSupplier");
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int drawWidth = Math.max(1, getWidth() - 2);
        int drawHeight = Math.max(1, getHeight() - 2);
        CaveSlicePreview preview = preview(drawWidth, drawHeight);
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xAA05040A);
        for (int y = 0; y < drawHeight; y++) {
            int sampleY = y * preview.height() / drawHeight;
            for (int x = 0; x < drawWidth; x++) {
                int sampleX = x * preview.width() / drawWidth;
                graphics.fill(getX() + 1 + x, getY() + 1 + y,
                        getX() + 2 + x, getY() + 2 + y,
                        preview.colorAt(sampleX, sampleY));
            }
        }
        graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF9E8FDB);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    private CaveSlicePreview preview(int drawWidth, int drawHeight) {
        EndPreset preset = presetSupplier.get();
        CaveSlicePreviewSettings settings = Objects.requireNonNull(settingsSupplier.get(), "settings");
        if (!preset.equals(cachedPreset) || !settings.equals(cachedSettings)
                || drawWidth != cachedWidth || drawHeight != cachedHeight) {
            cachedPreset = preset;
            cachedSettings = settings;
            cachedWidth = drawWidth;
            cachedHeight = drawHeight;
            cachedPreview = CaveSlicePreviewSampler.sample(preset, TerrainPreviewSampler.DEFAULT_SEED,
                    drawWidth, drawHeight, settings);
        }
        return cachedPreview;
    }
}
