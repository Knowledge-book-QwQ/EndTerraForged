package endterraforged.mixin;

import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;

import endterraforged.client.gui.screen.CreateWorldPresetEditorButtonPlan;
import endterraforged.client.gui.screen.EndPresetEditorScreen;
import endterraforged.client.gui.widget.ActionButtonLayout;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetAccess;
import endterraforged.world.config.EndWorldDimensionsAdapter;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds an ETF-specific entry point to the create-world screen.
 *
 * <p>Vanilla's {@code PresetEditor.EDITORS} map is a global one-editor-per-world-preset
 * registry. Registering ETF on {@code WorldPresets.NORMAL} prevents another generator mod
 * from owning its normal-world customizer, so ETF deliberately uses a separate button instead.
 * The button is added after vanilla lays out its widgets and moves upward only when that slot is
 * occupied. It is client-only and does not persist into a world directory that does not yet exist.</p>
 */
@Mixin(CreateWorldScreen.class)
public abstract class MixinCreateWorldScreen {

    @Shadow
    @Final
    private WorldCreationUiState uiState;

    @Unique
    private EndPreset endTerraForged$selectedPreset;

    @Shadow
    protected abstract <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T widget);

    @Inject(method = "init", at = @At("TAIL"))
    private void endTerraForged$addDedicatedEditorButton(CallbackInfo ci) {
        CreateWorldScreen screen = (CreateWorldScreen) (Object) this;
        List<ActionButtonLayout.Bounds> occupiedBounds = screen.children().stream()
                .filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast)
                .map(widget -> new ActionButtonLayout.Bounds(
                        widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()))
                .collect(Collectors.toList());
        CreateWorldPresetEditorButtonPlan.create(screen.width, screen.height, occupiedBounds)
                .ifPresent(plan -> addRenderableWidget(Button.builder(
                                Component.translatable(plan.translationKey()),
                                button -> openPresetEditor())
                        .bounds(plan.bounds().x(), plan.bounds().y(),
                                plan.bounds().width(), plan.bounds().height())
                        .build()));
    }

    private void openPresetEditor() {
        EndPreset initial = endTerraForged$selectedPreset != null
                ? endTerraForged$selectedPreset
                : EndPresetAccess.getEditableOrDefault();
        Minecraft.getInstance().setScreen(new EndPresetEditorScreen(
                initial, (CreateWorldScreen) (Object) this, () -> {
                    EndPreset selected = EndPresetAccess.getEditableOrDefault();
                    endTerraForged$applyWorldBounds(selected);
                    endTerraForged$selectedPreset = selected;
                }));
    }

    @Inject(method = "onCreate", at = @At("HEAD"))
    private void endTerraForged$reapplyWorldBoundsBeforeCreate(CallbackInfo ci) {
        if (endTerraForged$selectedPreset != null) {
            endTerraForged$applyWorldBounds(endTerraForged$selectedPreset);
        }
    }

    @Unique
    private void endTerraForged$applyWorldBounds(EndPreset preset) {
        uiState.updateDimensions((registries, dimensions) ->
                EndWorldDimensionsAdapter.withEndBounds(dimensions, preset.worldBounds()));
    }
}
