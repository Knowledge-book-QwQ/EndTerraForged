package endterraforged.client.gui.widget;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.Component;

import endterraforged.world.preview.TerrainPreviewMode;
import endterraforged.world.preview.TerrainPreviewScale;
import endterraforged.world.preview.TerrainPreviewSettings;

/**
 * Small shared controls for preview mode and preview scale selection.
 */
public final class TerrainPreviewControls {

    private TerrainPreviewControls() {
    }

    public static CycleButton<TerrainPreviewMode> modeButton(int x, int y, int width, int height,
                                                            TerrainPreviewSettings settings,
                                                            Consumer<TerrainPreviewMode> onChange) {
        return modeButton(x, y, width, height, settings, onChange, TerrainPreviewMode.values());
    }

    public static CycleButton<TerrainPreviewMode> modeButton(int x, int y, int width, int height,
                                                            TerrainPreviewSettings settings,
                                                            Consumer<TerrainPreviewMode> onChange,
                                                            TerrainPreviewMode... modes) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(onChange, "onChange");
        Objects.requireNonNull(modes, "modes");
        if (modes.length == 0) {
            throw new IllegalArgumentException("modes must not be empty");
        }
        return CycleButton.<TerrainPreviewMode>builder(TerrainPreviewControls::modeName)
                .withValues(modes)
                .withInitialValue(settings.mode())
                .create(x, y, width, height,
                        Component.translatable("endterraforged.gui.preview.mode"),
                        (btn, mode) -> onChange.accept(mode));
    }

    public static CycleButton<TerrainPreviewScale> scaleButton(int x, int y, int width, int height,
                                                              TerrainPreviewSettings settings,
                                                              Consumer<TerrainPreviewScale> onChange) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(onChange, "onChange");
        return CycleButton.<TerrainPreviewScale>builder(TerrainPreviewControls::scaleName)
                .withValues(TerrainPreviewScale.values())
                .withInitialValue(settings.scale())
                .create(x, y, width, height,
                        Component.translatable("endterraforged.gui.preview.scale"),
                        (btn, scale) -> onChange.accept(scale));
    }

    private static Component modeName(TerrainPreviewMode mode) {
        return Component.translatable("endterraforged.gui.preview.mode."
                + mode.name().toLowerCase(Locale.ROOT));
    }

    private static Component scaleName(TerrainPreviewScale scale) {
        return Component.translatable("endterraforged.gui.preview.scale."
                + scale.name().toLowerCase(Locale.ROOT));
    }
}
