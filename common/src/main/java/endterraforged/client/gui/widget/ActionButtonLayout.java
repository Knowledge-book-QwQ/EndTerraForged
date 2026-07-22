package endterraforged.client.gui.widget;

/**
 * Computes stable button bounds for editor action rows.
 */
public final class ActionButtonLayout {

    public static final int DEFAULT_GAP = 4;

    private ActionButtonLayout() {
    }

    public static Bounds[] row(int x, int y, int width, int height, int count) {
        return row(x, y, width, height, count, DEFAULT_GAP);
    }

    public static Bounds[] row(int x, int y, int width, int height, int count, int gap) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }
        if (gap < 0) {
            throw new IllegalArgumentException("gap must not be negative");
        }

        int totalGap = gap * (count - 1);
        int available = width - totalGap;
        if (available < count) {
            throw new IllegalArgumentException("width is too small for button row");
        }

        Bounds[] bounds = new Bounds[count];
        int buttonWidth = available / count;
        int extraPixels = available % count;
        int nextX = x;
        for (int i = 0; i < count; i++) {
            int currentWidth = buttonWidth + (i < extraPixels ? 1 : 0);
            bounds[i] = new Bounds(nextX, y, currentWidth, height);
            nextX += currentWidth + gap;
        }
        return bounds;
    }

    public record Bounds(int x, int y, int width, int height) {
    }
}
