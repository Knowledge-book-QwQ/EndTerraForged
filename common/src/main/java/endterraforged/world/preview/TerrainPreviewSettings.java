package endterraforged.world.preview;

import java.util.Objects;

/**
 * Immutable preview sampling settings shared by GUI controls and sampler APIs.
 */
public record TerrainPreviewSettings(TerrainPreviewMode mode, TerrainPreviewScale scale) {
    public static final TerrainPreviewSettings DEFAULT =
            new TerrainPreviewSettings(TerrainPreviewMode.COMBINED, TerrainPreviewScale.NORMAL);

    public TerrainPreviewSettings {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(scale, "scale");
    }

    public TerrainPreviewSettings withMode(TerrainPreviewMode mode) {
        return new TerrainPreviewSettings(mode, this.scale);
    }

    public TerrainPreviewSettings withScale(TerrainPreviewScale scale) {
        return new TerrainPreviewSettings(this.mode, scale);
    }
}
