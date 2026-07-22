package endterraforged.client.gui.widget;

/**
 * Pure scroll math for detail editor screens.
 */
public final class EditorScrollLayout {

    private EditorScrollLayout() {
    }

    public static int contentBottom(int top, int rowStep, int rowHeight,
                                    int longestColumnRows, int trailingRows) {
        if (top < 0) {
            throw new IllegalArgumentException("top must not be negative");
        }
        if (rowStep < rowHeight) {
            throw new IllegalArgumentException("rowStep must be at least rowHeight");
        }
        if (longestColumnRows < 0) {
            throw new IllegalArgumentException("longestColumnRows must not be negative");
        }
        if (trailingRows < 0) {
            throw new IllegalArgumentException("trailingRows must not be negative");
        }
        int rows = longestColumnRows + trailingRows;
        if (rows == 0) {
            return top;
        }
        return top + (rows - 1) * rowStep + rowHeight;
    }

    public static int displayedRows(boolean twoColumns, int firstColumnRows, int secondColumnRows) {
        if (firstColumnRows < 0) {
            throw new IllegalArgumentException("firstColumnRows must not be negative");
        }
        if (secondColumnRows < 0) {
            throw new IllegalArgumentException("secondColumnRows must not be negative");
        }
        return twoColumns
                ? Math.max(firstColumnRows, secondColumnRows)
                : firstColumnRows + secondColumnRows;
    }

    public static int maxScroll(int contentBottom, int screenHeight, int bottomMargin) {
        if (screenHeight <= 0) {
            throw new IllegalArgumentException("screenHeight must be positive");
        }
        if (bottomMargin < 0) {
            throw new IllegalArgumentException("bottomMargin must not be negative");
        }
        return Math.max(0, contentBottom - (screenHeight - bottomMargin));
    }

    public static int clampScroll(int scrollOffset, int maxScroll) {
        if (maxScroll < 0) {
            throw new IllegalArgumentException("maxScroll must not be negative");
        }
        return Math.clamp(scrollOffset, 0, maxScroll);
    }

    public static int scrollOffsetAfterWheel(int scrollOffset, int maxScroll,
                                             double scrollY, int scrollStep) {
        if (scrollStep <= 0) {
            throw new IllegalArgumentException("scrollStep must be positive");
        }
        return clampScroll(scrollOffset - (int) Math.signum(scrollY) * scrollStep, maxScroll);
    }
}
