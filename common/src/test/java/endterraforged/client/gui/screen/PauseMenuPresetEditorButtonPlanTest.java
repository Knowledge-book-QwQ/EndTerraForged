package endterraforged.client.gui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import endterraforged.client.gui.widget.ActionButtonLayout;

class PauseMenuPresetEditorButtonPlanTest {

    @Test
    void onlyLocalPauseMenuCanShowTheEditorEntry() {
        assertTrue(PauseMenuPresetEditorButtonPlan.create(
                false, true, true, 400, 300, List.of()).isEmpty());
        assertTrue(PauseMenuPresetEditorButtonPlan.create(
                true, false, true, 400, 300, List.of()).isEmpty());
        assertTrue(PauseMenuPresetEditorButtonPlan.create(
                true, true, false, 400, 300, List.of()).isEmpty());
    }

    @Test
    void planUsesBottomCenterWhenItIsFree() {
        PauseMenuPresetEditorButtonPlan.Plan plan = PauseMenuPresetEditorButtonPlan.create(
                true, true, true, 400, 300, List.of()).orElseThrow();

        assertEquals(98, plan.bounds().x());
        assertEquals(270, plan.bounds().y());
        assertEquals("endterraforged.gui.open_editor", plan.translationKey());
    }

    @Test
    void planMovesAboveOccupiedBottomCenter() {
        ActionButtonLayout.Bounds occupied = new ActionButtonLayout.Bounds(98, 270, 204, 20);

        PauseMenuPresetEditorButtonPlan.Plan plan = PauseMenuPresetEditorButtonPlan.create(
                true, true, true, 400, 300, List.of(occupied)).orElseThrow();

        assertEquals(246, plan.bounds().y());
    }

    @Test
    void planSkipsTinyScreens() {
        assertTrue(PauseMenuPresetEditorButtonPlan.create(
                true, true, true, 100, 300, List.of()).isEmpty());
        assertTrue(PauseMenuPresetEditorButtonPlan.create(
                true, true, true, 400, 30, List.of()).isEmpty());
    }
}
