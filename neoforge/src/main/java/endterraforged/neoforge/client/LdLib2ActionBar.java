package endterraforged.neoforge.client;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;

import net.minecraft.network.chat.Component;

import endterraforged.client.gui.widget.ActionButtonLayout;

/**
 * NeoForge-only LDLib2 bridge for simple client action controls.
 *
 * <p>The common editor locates this class reflectively so Fabric and common
 * compilation never take a direct dependency on NeoForge-only LDLib2 types.</p>
 */
public final class LdLib2ActionBar {

    private LdLib2ActionBar() {
    }

    public static ModularUI.ModularUIWidget create(int x, int y, int width, int height,
                                            Component doneLabel, Runnable onDone,
                                            Component cancelLabel, Runnable onCancel) {
        UIElement root = new UIElement()
                .layout(layout -> layout.left(x).top(y).width(width).height(height));
        ActionButtonLayout.Bounds[] bounds = ActionButtonLayout.row(0, 0, width, height, 2);

        root.addChild(button(doneLabel, onDone, bounds[0]));
        root.addChild(button(cancelLabel, onCancel, bounds[1]));

        return ModularUI.of(UI.of(root)).shouldCloseOnEsc(false).getWidget();
    }

    private static Button button(Component label, Runnable action, ActionButtonLayout.Bounds bounds) {
        return (Button) new Button()
                .setText(label)
                .setOnClick(event -> action.run())
                .layout(layout -> layout.left(bounds.x()).top(bounds.y())
                        .width(bounds.width()).height(bounds.height()));
    }
}
