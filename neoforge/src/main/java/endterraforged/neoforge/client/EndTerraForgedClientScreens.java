package endterraforged.neoforge.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import endterraforged.EndTerraForged;
import endterraforged.client.gui.screen.PauseMenuPresetEditorButtonPlan;
import endterraforged.client.gui.widget.ActionButtonLayout;

/**
 * Adds the existing-world preset editor entry only to local pause menus.
 */
@EventBusSubscriber(modid = EndTerraForged.MOD_ID, value = Dist.CLIENT)
public final class EndTerraForgedClientScreens {

    private EndTerraForgedClientScreens() {
    }

    @SubscribeEvent
    private static void addPresetEditorButton(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        boolean isPauseScreen = screen instanceof PauseScreen;
        boolean showsPauseMenu = isPauseScreen && ((PauseScreen) screen).showsPauseMenu();
        Optional<PauseMenuPresetEditorButtonPlan.Plan> plan = PauseMenuPresetEditorButtonPlan.create(
                isPauseScreen,
                showsPauseMenu,
                ExistingWorldPresetEditorLauncher.canOpenFromCurrentClient(),
                screen.width,
                screen.height,
                occupiedBounds(event));
        if (plan.isEmpty()) {
            return;
        }

        PauseMenuPresetEditorButtonPlan.Plan buttonPlan = plan.get();
        ActionButtonLayout.Bounds bounds = buttonPlan.bounds();
        event.addListener(Button.builder(
                        Component.translatable(buttonPlan.translationKey()),
                        button -> ExistingWorldPresetEditorLauncher.open(screen))
                .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height())
                .build());
    }

    private static List<ActionButtonLayout.Bounds> occupiedBounds(ScreenEvent.Init.Post event) {
        List<ActionButtonLayout.Bounds> bounds = new ArrayList<>();
        for (GuiEventListener listener : event.getListenersList()) {
            if (listener instanceof AbstractWidget widget) {
                bounds.add(new ActionButtonLayout.Bounds(
                        widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
            }
        }
        return bounds;
    }
}
