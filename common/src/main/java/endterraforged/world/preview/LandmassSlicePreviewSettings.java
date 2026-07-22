package endterraforged.world.preview;

import java.util.Objects;

/** View-only controls for vertical macro-landmass slice previews. */
public record LandmassSlicePreviewSettings(Axis axis, int offsetBlocks, int blocksPerPixel) {

    public static final LandmassSlicePreviewSettings DEFAULT = new LandmassSlicePreviewSettings(
            Axis.X, 0, LandmassSlicePreviewSampler.DEFAULT_BLOCKS_PER_PIXEL);

    public LandmassSlicePreviewSettings {
        Objects.requireNonNull(axis, "axis");
        if (blocksPerPixel <= 0) {
            throw new IllegalArgumentException("blocksPerPixel must be > 0, got " + blocksPerPixel);
        }
    }

    public LandmassSlicePreviewSettings withAxis(Axis axis) {
        return new LandmassSlicePreviewSettings(axis, offsetBlocks, blocksPerPixel);
    }

    public LandmassSlicePreviewSettings withOffsetBlocks(int offsetBlocks) {
        return new LandmassSlicePreviewSettings(axis, offsetBlocks, blocksPerPixel);
    }

    public LandmassSlicePreviewSettings withBlocksPerPixel(int blocksPerPixel) {
        return new LandmassSlicePreviewSettings(axis, offsetBlocks, blocksPerPixel);
    }

    public enum Axis {
        X,
        Z
    }
}
