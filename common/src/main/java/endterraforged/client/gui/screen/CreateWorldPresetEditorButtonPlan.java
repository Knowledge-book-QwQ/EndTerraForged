package endterraforged.client.gui.screen;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import endterraforged.client.gui.widget.ActionButtonLayout;

/**
 * Chooses a dedicated create-world entry point without claiming vanilla's
 * globally shared {@code PresetEditor} slot.
 */
public final class CreateWorldPresetEditorButtonPlan {

    private static final int BUTTON_WIDTH = 154;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SCREEN_MARGIN = 10;
    private static final int FOOTER_CLEARANCE = 48;
    private static final int SEARCH_STEP = BUTTON_HEIGHT + ActionButtonLayout.DEFAULT_GAP;
    private static final String OPEN_EDITOR_KEY = "endterraforged.gui.open_editor";

    private CreateWorldPresetEditorButtonPlan() {
    }

    public record Plan(ActionButtonLayout.Bounds bounds, String translationKey) {

        public Plan {
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(translationKey, "translationKey");
        }
    }

    public static Optional<Plan> create(
            int screenWidth, int screenHeight, List<ActionButtonLayout.Bounds> occupiedBounds) {
        Objects.requireNonNull(occupiedBounds, "occupiedBounds");
        if (screenWidth < BUTTON_WIDTH + SCREEN_MARGIN * 2
                || screenHeight < FOOTER_CLEARANCE + BUTTON_HEIGHT + SCREEN_MARGIN) {
            return Optional.empty();
        }

        int x = screenWidth - BUTTON_WIDTH - SCREEN_MARGIN;
        for (int y = screenHeight - FOOTER_CLEARANCE - BUTTON_HEIGHT;
             y >= SCREEN_MARGIN;
             y -= SEARCH_STEP) {
            ActionButtonLayout.Bounds candidate =
                    new ActionButtonLayout.Bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT);
            if (occupiedBounds.stream().noneMatch(bounds -> overlaps(candidate, bounds))) {
                return Optional.of(new Plan(candidate, OPEN_EDITOR_KEY));
            }
        }
        return Optional.empty();
    }

    private static boolean overlaps(ActionButtonLayout.Bounds first, ActionButtonLayout.Bounds second) {
        return first.x() < second.x() + second.width()
                && first.x() + first.width() > second.x()
                && first.y() < second.y() + second.height()
                && first.y() + first.height() > second.y();
    }
}
