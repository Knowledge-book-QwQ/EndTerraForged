package endterraforged.client.gui.screen;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import endterraforged.client.gui.widget.ActionButtonLayout;

/**
 * Chooses a non-overlapping pause-menu entry point for the existing-world
 * preset editor without depending on a platform screen event.
 */
public final class PauseMenuPresetEditorButtonPlan {

    private static final int BUTTON_WIDTH = 204;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SCREEN_MARGIN = 10;
    private static final int SEARCH_STEP = BUTTON_HEIGHT + ActionButtonLayout.DEFAULT_GAP;
    private static final String OPEN_EDITOR_KEY = "endterraforged.gui.open_editor";

    private PauseMenuPresetEditorButtonPlan() {
    }

    public record Plan(ActionButtonLayout.Bounds bounds, String translationKey) {

        public Plan {
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(translationKey, "translationKey");
        }
    }

    public static Optional<Plan> create(
            boolean pauseScreen,
            boolean showsPauseMenu,
            boolean canOpenExistingWorldEditor,
            int screenWidth,
            int screenHeight,
            List<ActionButtonLayout.Bounds> occupiedBounds) {
        Objects.requireNonNull(occupiedBounds, "occupiedBounds");
        if (!pauseScreen || !showsPauseMenu || !canOpenExistingWorldEditor
                || screenWidth < BUTTON_WIDTH + SCREEN_MARGIN * 2
                || screenHeight < BUTTON_HEIGHT + SCREEN_MARGIN * 2) {
            return Optional.empty();
        }

        int x = (screenWidth - BUTTON_WIDTH) / 2;
        for (int y = screenHeight - BUTTON_HEIGHT - SCREEN_MARGIN;
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
