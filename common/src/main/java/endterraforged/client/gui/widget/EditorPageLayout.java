package endterraforged.client.gui.widget;

/**
 * Computes compact vertical spacing for paged editor screens.
 */
public final class EditorPageLayout {

    private EditorPageLayout() {
    }

    public static int rowStepForHeight(int screenHeight, int top, int bottomMargin,
                                       int rowHeight, int rowCount, int preferredStep) {
        if (screenHeight <= 0) {
            throw new IllegalArgumentException("screenHeight must be positive");
        }
        if (top < 0) {
            throw new IllegalArgumentException("top must not be negative");
        }
        if (bottomMargin < 0) {
            throw new IllegalArgumentException("bottomMargin must not be negative");
        }
        if (rowHeight <= 0) {
            throw new IllegalArgumentException("rowHeight must be positive");
        }
        if (rowCount <= 0) {
            throw new IllegalArgumentException("rowCount must be positive");
        }
        if (preferredStep < rowHeight) {
            throw new IllegalArgumentException("preferredStep must be at least rowHeight");
        }
        if (rowCount == 1) {
            return preferredStep;
        }

        int availableBetweenFirstRows = screenHeight - bottomMargin - top - rowHeight;
        if (availableBetweenFirstRows <= 0) {
            return rowHeight;
        }
        int compactStep = availableBetweenFirstRows / (rowCount - 1);
        return Math.max(rowHeight, Math.min(preferredStep, compactStep));
    }
}
