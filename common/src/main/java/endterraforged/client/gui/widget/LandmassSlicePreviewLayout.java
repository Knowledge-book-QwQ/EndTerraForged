package endterraforged.client.gui.widget;

import java.util.Optional;

/** Pure layout math for macro-landmass slice controls below the terrain preview. */
public final class LandmassSlicePreviewLayout {

    public static final Metrics DEFAULT = new Metrics(
            20, 150, 96,
            30, 24, 18, 23,
            8, 34, 48, 72);

    private LandmassSlicePreviewLayout() {
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
            if (rightMargin < 0 || previewControlY < 0 || gap < 0 || bottomMargin < 0) {
                throw new IllegalArgumentException("layout margins must not be negative");
            }
            if (maxWidth <= 0 || minWidth <= 0 || minWidth > maxWidth) {
                throw new IllegalArgumentException("preview widths must be positive and ordered");
            }
            if (previewRowStep <= 0 || controlHeight <= 0 || rowHeight < controlHeight) {
                throw new IllegalArgumentException("control rows are invalid");
            }
            if (minSliceHeight <= 0 || maxSliceHeight < minSliceHeight) {
                throw new IllegalArgumentException("slice heights are invalid");
            }
        }
    }

    public record Placement(int previewWidth,
                            ActionButtonLayout.Bounds axisBounds,
                            ActionButtonLayout.Bounds offsetBounds,
                            ActionButtonLayout.Bounds sliceBounds) {

        public Placement {
            if (previewWidth <= 0 || axisBounds == null || offsetBounds == null || sliceBounds == null) {
                throw new IllegalArgumentException("slice placement is invalid");
            }
        }
    }
}
