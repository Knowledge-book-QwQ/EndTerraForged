package endterraforged.world.preview;

import java.util.Objects;

import endterraforged.world.cave.EndCaveField;
import endterraforged.world.config.CaveSystemConfig;
import endterraforged.world.config.EndPreset;
import endterraforged.world.heightmap.EndHeightmap;

/**
 * CPU-side vertical cave slice sampler for debug previews.
 */
public final class CaveSlicePreviewSampler {

    public static final int DEFAULT_WIDTH = 96;
    public static final int DEFAULT_HEIGHT = 64;
    public static final int DEFAULT_BLOCKS_PER_PIXEL = 24;

    private static final int MAX_SIZE = 512;

    private CaveSlicePreviewSampler() {
    }

    public static CaveSlicePreview sample(EndPreset preset) {
        return sample(preset, TerrainPreviewSampler.DEFAULT_SEED,
                DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_BLOCKS_PER_PIXEL);
    }

    public static CaveSlicePreview sample(EndPreset preset, int seed,
                                          int width, int height, int blocksPerPixel) {
        return sample(preset, seed, width, height,
                new CaveSlicePreviewSettings(CaveSlicePreviewSettings.Axis.X, 0, blocksPerPixel));
    }

    public static CaveSlicePreview sample(EndPreset preset, int seed,
                                          int width, int height,
                                          CaveSlicePreviewSettings settings) {
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(settings, "settings");
        if (width <= 0 || width > MAX_SIZE) {
            throw new IllegalArgumentException("width must be in [1, "
                    + MAX_SIZE + "], got " + width);
        }
        if (height <= 0 || height > MAX_SIZE) {
            throw new IllegalArgumentException("height must be in [1, "
                    + MAX_SIZE + "], got " + height);
        }
        int blocksPerPixel = settings.blocksPerPixel();
        if (blocksPerPixel <= 0) {
            throw new IllegalArgumentException("blocksPerPixel must be > 0, got "
                    + blocksPerPixel);
        }

        EndHeightmap heightmap = new EndHeightmap(preset, seed);
        EndCaveField caveField = EndCaveField.fromConfig(preset.subsurfaceConfig(), seed);
        CaveSystemConfig caveSystem = preset.subsurfaceConfig().caveSystemConfig();
        int worldHeight = Math.max(2, preset.worldHeight());
        int start = Math.clamp(caveSystem.depthStart(), 0, worldHeight - 1);
        int end = Math.clamp(caveSystem.depthEnd(), start + 1, worldHeight);
        float origin = (width - 1) * blocksPerPixel * 0.5F;
        int[] colors = new int[width * height];
        float[] worldXByColumn = new float[width];
        float[] worldZByColumn = new float[width];
        float[] landnessByColumn = new float[width];
        float[] terrainTopByColumn = new float[width];
        boolean xAxis = settings.axis() == CaveSlicePreviewSettings.Axis.X;
        for (int x = 0; x < width; x++) {
            float horizontal = x * blocksPerPixel - origin;
            float worldX = xAxis ? horizontal : settings.offsetBlocks();
            float worldZ = xAxis ? settings.offsetBlocks() : horizontal;
            worldXByColumn[x] = worldX;
            worldZByColumn[x] = worldZ;
            landnessByColumn[x] = heightmap.getLandness(worldX, worldZ, seed);
            terrainTopByColumn[x] = heightmap.getHeight(worldX, worldZ, seed);
        }

        float maxStrength = 0.0F;
        for (int y = 0; y < height; y++) {
            float depthAlpha = height == 1 ? 0.0F : y / (float) (height - 1);
            float depth = start + (end - start) * depthAlpha;
            for (int x = 0; x < width; x++) {
                float terrainTop = terrainTopByColumn[x];
                float yNorm = terrainTop - depth / worldHeight;
                float strength = caveField.strength(worldXByColumn[x], worldZByColumn[x],
                        landnessByColumn[x], yNorm, terrainTop, worldHeight);
                maxStrength = Math.max(maxStrength, strength);
                colors[y * width + x] = TerrainPreviewPalette.caveDepthColor(
                        landnessByColumn[x], strength, depthAlpha);
            }
        }

        return new CaveSlicePreview(width, height, colors, maxStrength);
    }
}
