package endterraforged.client.gui.screen;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

/**
 * Common-side reflection helper for optional NeoForge LDLib2 action controls.
 */
final class LdLib2ActionBars {

    private static final String LD_LIB2_UI_CLASS = "com.lowdragmc.lowdraglib2.gui.ui.ModularUI";
    private static final String BRIDGE_CLASS = "endterraforged.neoforge.client.LdLib2ActionBar";

    private LdLib2ActionBars() {
    }

    static <T extends GuiEventListener & Renderable & NarratableEntry> T create(
            Class<?> owner, int x, int y, int width, int height,
            Component doneLabel, Runnable onDone,
            Component cancelLabel, Runnable onCancel) {
        try {
            ClassLoader loader = owner.getClassLoader();
            Class.forName(LD_LIB2_UI_CLASS, false, loader);
            Class<?> bridge = Class.forName(BRIDGE_CLASS, true, loader);
            Object widget = bridge.getDeclaredMethod("create",
                            int.class, int.class, int.class, int.class,
                            Component.class, Runnable.class,
                            Component.class, Runnable.class)
                    .invoke(null, x, y, width, height, doneLabel, onDone, cancelLabel, onCancel);
            return asRenderableWidget(widget);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends GuiEventListener & Renderable & NarratableEntry> T asRenderableWidget(Object widget) {
        if (widget instanceof GuiEventListener
                && widget instanceof Renderable
                && widget instanceof NarratableEntry) {
            return (T) widget;
        }
        return null;
    }
}
