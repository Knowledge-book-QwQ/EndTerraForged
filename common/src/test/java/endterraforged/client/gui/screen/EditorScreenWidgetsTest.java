package endterraforged.client.gui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

class EditorScreenWidgetsTest {

    @Test
    void actionBarAlwaysUsesVanillaButtons() {
        CapturingWidgetSink sink = new CapturingWidgetSink();

        EditorScreenWidgets.addActionBar(sink, EditorScreenWidgetsTest.class,
                10, 20, 210, 20,
                Component.literal("Done"), () -> {},
                Component.literal("Cancel"), () -> {});

        assertEquals(2, sink.widgets.size());
        assertTrue(sink.widgets.stream().allMatch(Button.class::isInstance),
                "vanilla editor screens must not embed an LDLib2 ModularUIWidget");
        Button done = (Button) sink.widgets.get(0);
        Button cancel = (Button) sink.widgets.get(1);
        assertEquals(10, done.getX());
        assertEquals(103, done.getWidth());
        assertEquals(117, cancel.getX());
        assertEquals(103, cancel.getWidth());
    }

    private static final class CapturingWidgetSink implements EditorScreenWidgets.WidgetSink {
        private final List<Object> widgets = new ArrayList<>();

        @Override
        public <T extends GuiEventListener & Renderable & NarratableEntry> T add(T widget) {
            widgets.add(widget);
            return widget;
        }
    }
}
