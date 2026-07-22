package endterraforged.client.gui.widget;

/**
 * Pure layout math for the scrollable named-preset list.
 *
 * <p>The screen only creates widgets for rows returned by this layout so
 * off-screen entries cannot receive clicks outside the visible list area.</p>
 */
public final class PresetLibraryEntryLayout {

    private PresetLibraryEntryLayout() {
    }

    public static int visibleRows(
            int screenHeight, int listTop, int bottomMargin, int rowHeight, int rowStep) {
        if (screenHeight <= 0) {
            throw new IllegalArgumentException("screenHeight must be positive");
        }
        if (listTop < 0 || bottomMargin < 0) {
            throw new IllegalArgumentException("list bounds must not be negative");
        }
        if (rowHeight <= 0 || rowStep < rowHeight) {
            throw new IllegalArgumentException("row dimensions are invalid");
        }

        int availableHeight = screenHeight - bottomMargin - listTop;
        if (availableHeight < rowHeight) {
            return 0;
        }
        return 1 + (availableHeight - rowHeight) / rowStep;
    }

    public static int maxScrollRows(int entryCount, int visibleRows) {
        if (entryCount < 0 || visibleRows < 0) {
            throw new IllegalArgumentException("entryCount and visibleRows must not be negative");
        }
        return Math.max(0, entryCount - visibleRows);
    }

    public static int clampScrollRows(int scrollRows, int maxScrollRows) {
        if (maxScrollRows < 0) {
            throw new IllegalArgumentException("maxScrollRows must not be negative");
        }
        return Math.clamp(scrollRows, 0, maxScrollRows);
    }

    public static int scrollAfterWheel(int scrollRows, int maxScrollRows, double scrollY) {
        return clampScrollRows(scrollRows - (int) Math.signum(scrollY), maxScrollRows);
    }

    public static int rowY(int listTop, int rowStep, int entryIndex, int scrollRows) {
        if (listTop < 0 || rowStep <= 0 || entryIndex < 0 || scrollRows < 0) {
            throw new IllegalArgumentException("row position arguments are invalid");
        }
        return listTop + (entryIndex - scrollRows) * rowStep;
    }
}
