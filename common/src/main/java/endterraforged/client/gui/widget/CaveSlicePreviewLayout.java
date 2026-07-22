package endterraforged.client.gui.widget;

import java.util.Optional;

/**
 * Pure layout math for the cave slice controls beside the terrain preview.
 */
public final class CaveSlicePreviewLayout {

    public static final Metrics DEFAULT = new Metrics(
            20, 150, 96,
            34, 24, 18, 23,
            8, 34, 48, 72);

    private CaveSlicePreviewLayout() {
    }

    public static Optional<Placement> place(int screenWidth, int screenHeight, int previewX) {
        return place(screenWidth, screenHeight, previewX, DEFAULT);
    }

    public static Optional<Placement> place(int screenWidth, int screenHeight, int previewX,
                                            Metrics metrics) {
        if (screenWidth <= 0) {
            throw new IllegalArgumentException("screenWidth must be positive");
        }
        if (screenHeight <= 0) {
            throw new IllegalArgumentException("screenHeight must be positive");
        }
        if (previewX < 0) {
            throw new IllegalArgumentException("previewX must not be negative");
        }
        if (metrics == null) {
            throw new NullPointerException("metrics");
        }

        int previewAvailable = screenWidth - previewX - metrics.rightMargin();
        if (previewAvailable < metrics.minWidth()) {
            return Optional.empty();
        }

        int previewWidth = Math.min(metrics.maxWidth(), previewAvailable);
        int previewBottom = metrics.previewControlY() + metrics.previewRowStep() * 2 + previewWidth;
        int axisY = previewBottom + metrics.gap();
        int offsetY = axisY + metrics.rowHeight();
        int sliceY = offsetY + metrics.rowHeight();
        int availableHeight = screenHeight - metrics.bottomMargin() - sliceY;
        if (availableHeight < metrics.minSliceHeight()) {
            return Optional.empty();
        }

        int sliceHeight = Math.min(metrics.maxSliceHeight(), availableHeight);
        return Optional.of(new Placement(
                previewWidth,
                new ActionButtonLayout.Bounds(previewX, axisY, previewWidth, metrics.controlHeight()),
                new ActionButtonLayout.Bounds(previewX, offsetY, previewWidth, metrics.controlHeight()),
                new ActionButtonLayout.Bounds(previewX, sliceY, previewWidth, sliceHeight)));
    }

    public record Metrics(int rightMargin, int maxWidth, int minWidth,
                          int previewControlY, int previewRowStep, int controlHeight,
                          int rowHeight, int gap, int bottomMargin,
                          int minSliceHeight, int maxSliceHeight) {

        public Metrics {
            if (rightMargin < 0) {
                throw new IllegalArgumentException("rightMargin must not be negative");
            }
            if (maxWidth <= 0) {
                throw new IllegalArgumentException("maxWidth must be positive");
            }
            if (minWidth <= 0) {
                throw new IllegalArgumentException("minWidth must be positive");
            }
            if (minWidth > maxWidth) {
                throw new IllegalArgumentException("minWidth must not exceed maxWidth");
            }
            if (previewControlY < 0) {
                throw new IllegalArgumentException("previewControlY must not be negative");
            }
            if (previewRowStep <= 0) {
                throw new IllegalArgumentException("previewRowStep must be positive");
            }
            if (controlHeight <= 0) {
                throw new IllegalArgumentException("controlHeight must be positive");
            }
            if (rowHeight < controlHeight) {
                throw new IllegalArgumentException("rowHeight must be at least controlHeight");
            }
            if (gap < 0) {
                throw new IllegalArgumentException("gap must not be negative");
            }
            if (bottomMargin < 0) {
                throw new IllegalArgumentException("bottomMargin must not be negative");
            }
            if (minSliceHeight <= 0) {
                throw new IllegalArgumentException("minSliceHeight must be positive");
            }
            if (maxSliceHeight < minSliceHeight) {
                throw new IllegalArgumentException("maxSliceHeight must be at least minSliceHeight");
            }
        }
    }

    public record Placement(int previewWidth,
                            ActionButtonLayout.Bounds axisBounds,
                            ActionButtonLayout.Bounds offsetBounds,
                            ActionButtonLayout.Bounds sliceBounds) {

        public Placement {
            if (previewWidth <= 0) {
                throw new IllegalArgumentException("previewWidth must be positive");
            }
            if (axisBounds == null) {
                throw new NullPointerException("axisBounds");
            }
            if (offsetBounds == null) {
                throw new NullPointerException("offsetBounds");
            }
            if (sliceBounds == null) {
                throw new NullPointerException("sliceBounds");
            }
        }
    }
}
