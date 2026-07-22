package endterraforged.client.gui.screen;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import endterraforged.client.gui.widget.ActionButtonLayout;
import endterraforged.client.gui.widget.TerrainPreviewControls;
import endterraforged.client.gui.widget.TerrainPreviewWidget;
import endterraforged.world.config.EndPreset;
import endterraforged.world.preview.TerrainPreviewMode;
import endterraforged.world.preview.TerrainPreviewScale;
import endterraforged.world.preview.TerrainPreviewSettings;

/**
 * Shared editor-screen glue that keeps optional LDLib2 integration and vanilla
 * fallback behavior in one place.
 */
final class EditorScreenWidgets {

    private static final int PREVIEW_MIN_WIDTH = 96;
    private static final int PREVIEW_MAX_SIZE = 150;
    private static final int PREVIEW_RIGHT_MARGIN = 20;
    private static final int PREVIEW_CONTROL_HEIGHT = 20;
    private static final int PREVIEW_ROW_STEP = 24;
    private static final int DEFAULT_PREVIEW_CONTROL_Y = 34;
    private static final int STATUS_COLOR = 0xFFEE7777;

    private EditorScreenWidgets() {
    }

    static void addActionBar(WidgetSink widgets, Class<?> owner,
                             int x, int y, int width, int height,
                             Component doneLabel, Runnable onDone,
                             Component cancelLabel, Runnable onCancel) {
        Objects.requireNonNull(widgets, "widgets");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(doneLabel, "doneLabel");
        Objects.requireNonNull(onDone, "onDone");
        Objects.requireNonNull(cancelLabel, "cancelLabel");
        Objects.requireNonNull(onCancel, "onCancel");

        // A ModularUIWidget cannot be safely embedded into a vanilla Screen:
        // LDLib2 owns a separate Taffy layout lifecycle. Use vanilla buttons
        // until the NeoForge editor is hosted by a native LDLib2 screen.
        ActionButtonLayout.Bounds[] actions = ActionButtonLayout.row(x, y, width, height, 2);
        widgets.add(Button.builder(doneLabel, btn -> onDone.run())
                .bounds(actions[0].x(), actions[0].y(), actions[0].width(), actions[0].height())
                .build());
        widgets.add(Button.builder(cancelLabel, btn -> onCancel.run())
                .bounds(actions[1].x(), actions[1].y(), actions[1].width(), actions[1].height())
                .build());
    }

    static void addLivePreview(WidgetSink widgets,
                               int previewX, int screenWidth,
                               TerrainPreviewSettings settings,
                               Consumer<TerrainPreviewMode> onModeChange,
                               Consumer<TerrainPreviewScale> onScaleChange,
                               Supplier<EndPreset> presetSupplier,
                               Supplier<TerrainPreviewSettings> settingsSupplier) {
        addLivePreview(widgets, previewX, screenWidth, DEFAULT_PREVIEW_CONTROL_Y, settings,
                onModeChange, onScaleChange, presetSupplier, settingsSupplier);
    }

    static void addLivePreview(WidgetSink widgets,
                               int previewX, int screenWidth,
                               TerrainPreviewSettings settings,
                               Consumer<TerrainPreviewMode> onModeChange,
                               Consumer<TerrainPreviewScale> onScaleChange,
                               Supplier<EndPreset> presetSupplier,
                               Supplier<TerrainPreviewSettings> settingsSupplier,
                               TerrainPreviewMode... modes) {
        addLivePreview(widgets, previewX, screenWidth, DEFAULT_PREVIEW_CONTROL_Y, settings,
                onModeChange, onScaleChange, presetSupplier, settingsSupplier, modes);
    }

    static void addLivePreview(WidgetSink widgets,
                               int previewX, int screenWidth, int controlY,
                               TerrainPreviewSettings settings,
                               Consumer<TerrainPreviewMode> onModeChange,
                               Consumer<TerrainPreviewScale> onScaleChange,
                               Supplier<EndPreset> presetSupplier,
                               Supplier<TerrainPreviewSettings> settingsSupplier) {
        addLivePreview(widgets, previewX, screenWidth, controlY, settings, onModeChange,
                onScaleChange, presetSupplier, settingsSupplier, TerrainPreviewMode.values());
    }

    static void addLivePreview(WidgetSink widgets,
                               int previewX, int screenWidth, int controlY,
                               TerrainPreviewSettings settings,
                               Consumer<TerrainPreviewMode> onModeChange,
                               Consumer<TerrainPreviewScale> onScaleChange,
                               Supplier<EndPreset> presetSupplier,
                               Supplier<TerrainPreviewSettings> settingsSupplier,
                               TerrainPreviewMode... modes) {
        Objects.requireNonNull(widgets, "widgets");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(onModeChange, "onModeChange");
        Objects.requireNonNull(onScaleChange, "onScaleChange");
        Objects.requireNonNull(presetSupplier, "presetSupplier");
        Objects.requireNonNull(settingsSupplier, "settingsSupplier");
        Objects.requireNonNull(modes, "modes");

        int previewAvailable = screenWidth - previewX - PREVIEW_RIGHT_MARGIN;
        if (previewAvailable < PREVIEW_MIN_WIDTH) {
            return;
        }

        int previewSize = Math.min(PREVIEW_MAX_SIZE, previewAvailable);
        widgets.add(TerrainPreviewControls.modeButton(previewX, controlY, previewSize,
                PREVIEW_CONTROL_HEIGHT, settings, onModeChange, modes));
        widgets.add(TerrainPreviewControls.scaleButton(
                previewX, controlY + PREVIEW_ROW_STEP, previewSize, PREVIEW_CONTROL_HEIGHT,
                settings, onScaleChange));
        widgets.add(new TerrainPreviewWidget(
                previewX, controlY + PREVIEW_ROW_STEP * 2, previewSize, previewSize,
                presetSupplier, settingsSupplier));
    }

    static void renderStatus(GuiGraphics graphics, Font font, Component statusMessage,
                             int screenWidth, int screenHeight) {
        if (statusMessage == null) {
            return;
        }
        String text = font.plainSubstrByWidth(statusMessage.getString(), screenWidth - 40);
        graphics.drawCenteredString(font, Component.literal(text), screenWidth / 2,
                screenHeight - 18, STATUS_COLOR);
    }

    @FunctionalInterface
    interface WidgetSink {
        <T extends GuiEventListener & Renderable & NarratableEntry> T add(T widget);
    }
}
