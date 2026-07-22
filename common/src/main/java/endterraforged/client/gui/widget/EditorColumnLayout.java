package endterraforged.client.gui.widget;

/**
 * Tracks a vertical editor column without coupling layout code to Minecraft screens.
 */
public final class EditorColumnLayout {

    private final int x;
    private final int width;
    private final int rowHeight;
    private final int rowStep;
    private int nextY;

    public EditorColumnLayout(int x, int y, int width, int rowHeight, int rowStep) {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive");
        }
        if (rowHeight <= 0) {
            throw new IllegalArgumentException("rowHeight must be positive");
        }
        if (rowStep < rowHeight) {
            throw new IllegalArgumentException("rowStep must be at least rowHeight");
        }
        this.x = x;
        this.width = width;
        this.rowHeight = rowHeight;
        this.rowStep = rowStep;
        this.nextY = y;
    }

    public ActionButtonLayout.Bounds nextRow() {
        ActionButtonLayout.Bounds bounds =
                new ActionButtonLayout.Bounds(x, nextY, width, rowHeight);
        nextY += rowStep;
        return bounds;
    }

    public ActionButtonLayout.Bounds[] nextActionRow(int count) {
        ActionButtonLayout.Bounds[] bounds =
                ActionButtonLayout.row(x, nextY, width, rowHeight, count);
        nextY += rowStep;
        return bounds;
    }

    public void space(int pixels) {
        if (pixels < 0) {
            throw new IllegalArgumentException("pixels must not be negative");
        }
        nextY += pixels;
    }

    public int x() {
        return x;
    }

    public int width() {
        return width;
    }

    public int nextY() {
        return nextY;
    }
}
