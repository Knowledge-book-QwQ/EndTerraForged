package endterraforged.client.gui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import endterraforged.client.gui.widget.ActionButtonLayout;

class CreateWorldPresetEditorButtonPlanTest {

    @Test
    void planUsesDedicatedBottomRightSlotWhenItIsFree() {
        CreateWorldPresetEditorButtonPlan.Plan plan =
                CreateWorldPresetEditorButtonPlan.create(400, 300, List.of()).orElseThrow();

        assertEquals(236, plan.bounds().x());
        assertEquals(232, plan.bounds().y());
        assertEquals("endterraforged.gui.open_editor", plan.translationKey());
    }

    @Test
    void planMovesUpwardWhenDefaultSlotIsOccupied() {
        ActionButtonLayout.Bounds occupied = new ActionButtonLayout.Bounds(236, 232, 154, 20);

        CreateWorldPresetEditorButtonPlan.Plan plan =
                CreateWorldPresetEditorButtonPlan.create(400, 300, List.of(occupied)).orElseThrow();

        assertEquals(208, plan.bounds().y());
    }

    @Test
    void planHidesTheEntryWhenTheScreenCannotFitIt() {
        assertTrue(CreateWorldPresetEditorButtonPlan.create(173, 300, List.of()).isEmpty());
        assertTrue(CreateWorldPresetEditorButtonPlan.create(400, 77, List.of()).isEmpty());
    }
}
