package endterraforged.world.preview;

import java.util.Objects;

/**
 * View-only controls for vertical cave slice previews.
 */
public record CaveSlicePreviewSettings(Axis axis, int offsetBlocks, int blocksPerPixel) {

    public static final CaveSlicePreviewSettings DEFAULT = new CaveSlicePreviewSettings(
            Axis.X, 0, CaveSlicePreviewSampler.DEFAULT_BLOCKS_PER_PIXEL);

    public CaveSlicePreviewSettings {
        Objects.requireNonNull(axis, "axis");
        if (blocksPerPixel <= 0) {
            throw new IllegalArgumentException("blocksPerPixel must be > 0, got "
                    + blocksPerPixel);
        }
    }

    public CaveSlicePreviewSettings withAxis(Axis axis) {
        return new CaveSlicePreviewSettings(axis, offsetBlocks, blocksPerPixel);
    }

    public CaveSlicePreviewSettings withOffsetBlocks(int offsetBlocks) {
        return new CaveSlicePreviewSettings(axis, offsetBlocks, blocksPerPixel);
    }

    public CaveSlicePreviewSettings withBlocksPerPixel(int blocksPerPixel) {
        return new CaveSlicePreviewSettings(axis, offsetBlocks, blocksPerPixel);
    }

    public enum Axis {
        X,
        Z
    }
}
