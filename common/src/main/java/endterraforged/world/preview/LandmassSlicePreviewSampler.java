package endterraforged.world.preview;

import java.util.Objects;

import endterraforged.world.config.EndPreset;
import endterraforged.world.heightmap.EndDensity;
import endterraforged.world.heightmap.EndHeightmap;

/**
 * CPU-side X/Z slice sampler for the terrain volume before subsurface carving.
 *
 * <p>The sampler delegates solid/void decisions to {@link EndDensity}, so the
 * top, underside and landness rules stay aligned with runtime generation. It
 * intentionally uses the density constructor without subsurface modifiers:
 * this view explains the continental body itself, while the cave editor owns
 * carved-space diagnostics.</p>
 */
public final class LandmassSlicePreviewSampler {

    public static final int DEFAULT_WIDTH = 96;
    public static final int DEFAULT_HEIGHT = 72;
    public static final int DEFAULT_BLOCKS_PER_PIXEL = 32;

    private static final int MAX_SIZE = 512;

    private LandmassSlicePreviewSampler() {
    }

    public static LandmassSlicePreview sample(EndPreset preset) {
        return sample(preset, TerrainPreviewSampler.DEFAULT_SEED,
                DEFAULT_WIDTH, DEFAULT_HEIGHT, LandmassSlicePreviewSettings.DEFAULT);
    }

    public static LandmassSlicePreview sample(EndPreset preset, int seed,
                                               int width, int height, int blocksPerPixel) {
        return sample(preset, seed, width, height,
                new LandmassSlicePreviewSettings(LandmassSlicePreviewSettings.Axis.X, 0,
                        blocksPerPixel));
    }

    public static LandmassSlicePreview sample(EndPreset preset, int seed,
                                               int width, int height,
                                               LandmassSlicePreviewSettings settings) {
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(settings, "settings");
        if (width <= 0 || width > MAX_SIZE) {
            throw new IllegalArgumentException("width must be in [1, " + MAX_SIZE + "], got " + width);
        }
        if (height <= 0 || height > MAX_SIZE) {
            throw new IllegalArgumentException("height must be in [1, " + MAX_SIZE + "], got " + height);
        }

        EndHeightmap heightmap = new EndHeightmap(preset, seed);
        EndDensity density = new EndDensity(heightmap);
        TerrainPreviewSampler.PreviewCenter center =
                TerrainPreviewSampler.representativeCenter(preset, heightmap, seed);
        float origin = (width - 1) * settings.blocksPerPixel() * 0.5F;
        boolean xAxis = settings.axis() == LandmassSlicePreviewSettings.Axis.X;
        int[] colors = new int[width * height];
        int solidSamples = 0;

        for (int y = 0; y < height; y++) {
            int worldY = worldY(heightmap, y, height);
            float verticalAlpha = height == 1 ? 1.0F : 1.0F - y / (float) (height - 1);
            for (int x = 0; x < width; x++) {
                float horizontal = x * settings.blocksPerPixel() - origin;
                float worldX = xAxis ? center.x() + horizontal : center.x() + settings.offsetBlocks();
                float worldZ = xAxis ? center.z() + settings.offsetBlocks() : center.z() + horizontal;
                boolean solid = density.isSolid(worldX, worldY, worldZ, seed);
                if (solid) {
                    solidSamples++;
                }
                colors[y * width + x] = TerrainPreviewPalette.landmassSliceColor(solid, verticalAlpha);
            }
        }
        return new LandmassSlicePreview(width, height, colors, solidSamples);
    }

    private static int worldY(EndHeightmap heightmap, int row, int height) {
        if (height == 1) {
            return heightmap.levels().maxY;
        }
        float alpha = row / (float) (height - 1);
        return Math.round(heightmap.levels().maxY
                + (heightmap.levels().minY - heightmap.levels().maxY) * alpha);
    }
}
