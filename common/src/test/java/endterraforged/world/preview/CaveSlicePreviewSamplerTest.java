package endterraforged.world.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.cave.EndCaveField;
import endterraforged.world.config.AbyssPitConfig;
import endterraforged.world.config.CaveChamberConfig;
import endterraforged.world.config.CaveNetworkConfig;
import endterraforged.world.config.CaveSystemConfig;
import endterraforged.world.config.CaveTunnelConfig;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetBuilder;
import endterraforged.world.config.SubsurfaceConfig;
import endterraforged.world.heightmap.EndHeightmap;

class CaveSlicePreviewSamplerTest {

    @Test
    void previewDefensivelyCopiesColorArray() {
        int[] colors = {
                0xFF000001, 0xFF000002,
                0xFF000003, 0xFF000004
        };
        CaveSlicePreview preview = new CaveSlicePreview(2, 2, colors, 0.5F);
        colors[0] = 0xFFFFFFFF;
        assertEquals(0xFF000001, preview.colorAt(0, 0));

        int[] exposed = preview.colors();
        exposed[1] = 0xFFFFFFFF;
        assertEquals(0xFF000002, preview.colorAt(1, 0));
    }

    @Test
    void previewRejectsInvalidRasterShapeAndCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> new CaveSlicePreview(0, 2, new int[2], 0.0F));
        assertThrows(IllegalArgumentException.class,
                () -> new CaveSlicePreview(2, 0, new int[2], 0.0F));
        assertThrows(IllegalArgumentException.class,
                () -> new CaveSlicePreview(2, 2, new int[3], 0.0F));

        CaveSlicePreview preview = new CaveSlicePreview(2, 2, new int[4], 0.0F);
        assertThrows(IndexOutOfBoundsException.class, () -> preview.colorAt(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> preview.colorAt(0, 2));
    }

    @Test
    void samplerRejectsInvalidDimensions() {
        EndPreset preset = EndPreset.defaults();

        assertThrows(IllegalArgumentException.class,
                () -> CaveSlicePreviewSampler.sample(preset, 1, 0, 16, 24));
        assertThrows(IllegalArgumentException.class,
                () -> CaveSlicePreviewSampler.sample(preset, 1, 16, 0, 24));
        assertThrows(IllegalArgumentException.class,
                () -> CaveSlicePreviewSampler.sample(preset, 1, 16, 16, 0));
        assertThrows(NullPointerException.class,
                () -> new CaveSlicePreviewSettings(null, 0, 24));
        assertThrows(IllegalArgumentException.class,
                () -> new CaveSlicePreviewSettings(CaveSlicePreviewSettings.Axis.X, 0, 0));
    }

    @Test
    void disabledCavesProduceEmptySlice() {
        CaveSlicePreview preview = CaveSlicePreviewSampler.sample(
                EndPreset.defaults(), 4, 32, 24, 32);

        assertEquals(32, preview.width());
        assertEquals(24, preview.height());
        assertEquals(0.0F, preview.maxStrength(), 0.0F);
        assertArgbRaster(preview);
    }

    @Test
    void enabledCavesProduceVisibleSliceStrength() {
        EndPreset preset = new EndPresetBuilder(EndPreset.defaults())
                .subsurfaceConfig(caveConfig())
                .build();

        boolean foundVisibleSlice = false;
        for (int seed = 1; seed <= 12; seed++) {
            CaveSlicePreview preview = CaveSlicePreviewSampler.sample(preset, seed, 48, 40, 48);
            assertArgbRaster(preview);
            if (preview.maxStrength() > 0.0F) {
                foundVisibleSlice = true;
                break;
            }
        }

        assertTrue(foundVisibleSlice,
                "enabled cave slice should expose non-zero 3D cave strength");
    }

    @Test
    void cachedColumnSamplingMatchesUncachedXSlice() {
        EndPreset preset = new EndPresetBuilder(EndPreset.defaults())
                .subsurfaceConfig(caveConfig())
                .build();
        CaveSlicePreviewSettings settings =
                new CaveSlicePreviewSettings(CaveSlicePreviewSettings.Axis.X, 192, 48);

        assertPreviewEquals(sampleWithoutColumnCache(preset, 7, 48, 40, settings),
                CaveSlicePreviewSampler.sample(preset, 7, 48, 40, settings));
    }

    @Test
    void cachedColumnSamplingMatchesUncachedZSlice() {
        EndPreset preset = new EndPresetBuilder(EndPreset.defaults())
                .subsurfaceConfig(caveConfig())
                .build();
        CaveSlicePreviewSettings settings =
                new CaveSlicePreviewSettings(CaveSlicePreviewSettings.Axis.Z, -192, 48);

        assertPreviewEquals(sampleWithoutColumnCache(preset, 7, 48, 40, settings),
                CaveSlicePreviewSampler.sample(preset, 7, 48, 40, settings));
    }

    @Test
    void sliceSamplingIsDeterministic() {
        EndPreset preset = new EndPresetBuilder(EndPreset.defaults())
                .subsurfaceConfig(caveConfig())
                .build();

        CaveSlicePreview first = CaveSlicePreviewSampler.sample(preset, 7, 48, 40, 48);
        CaveSlicePreview second = CaveSlicePreviewSampler.sample(preset, 7, 48, 40, 48);

        assertEquals(signature(first), signature(second));
    }

    @Test
    void caveParametersChangeSliceSignature() {
        EndPreset sparse = new EndPresetBuilder(EndPreset.defaults())
                .subsurfaceConfig(caveConfig(0.15F, 0.15F, 0.1F))
                .build();
        EndPreset spectacular = new EndPresetBuilder(EndPreset.defaults())
                .subsurfaceConfig(caveConfig(0.95F, 0.95F, 1.0F))
                .build();

        CaveSlicePreview sparseSlice = CaveSlicePreviewSampler.sample(sparse, 4, 48, 40, 48);
        CaveSlicePreview spectacularSlice = CaveSlicePreviewSampler.sample(spectacular, 4, 48, 40, 48);

        assertNotEquals(signature(sparseSlice), signature(spectacularSlice));
    }

    @Test
    void sliceAxisChangesSamplePath() {
        EndPreset preset = new EndPresetBuilder(EndPreset.defaults())
                .subsurfaceConfig(caveConfig())
                .build();
        CaveSlicePreview xSlice = CaveSlicePreviewSampler.sample(preset, 7, 48, 40,
                new CaveSlicePreviewSettings(CaveSlicePreviewSettings.Axis.X, 192, 48));
        CaveSlicePreview zSlice = CaveSlicePreviewSampler.sample(preset, 7, 48, 40,
                new CaveSlicePreviewSettings(CaveSlicePreviewSettings.Axis.Z, 192, 48));

        assertNotEquals(signature(xSlice), signature(zSlice),
                "slice axis should rotate the sampled vertical cross-section");
    }

    @Test
    void sliceOffsetChangesSamplePath() {
        EndPreset preset = new EndPresetBuilder(EndPreset.defaults())
                .subsurfaceConfig(caveConfig())
                .build();
        CaveSlicePreview center = CaveSlicePreviewSampler.sample(preset, 7, 48, 40,
                CaveSlicePreviewSettings.DEFAULT.withBlocksPerPixel(48));
        CaveSlicePreview offset = CaveSlicePreviewSampler.sample(preset, 7, 48, 40,
                CaveSlicePreviewSettings.DEFAULT.withBlocksPerPixel(48).withOffsetBlocks(384));

        assertNotEquals(signature(center), signature(offset),
                "slice offset should move the sampled vertical cross-section");
    }

    private static CaveSlicePreview sampleWithoutColumnCache(
            EndPreset preset, int seed, int width, int height, CaveSlicePreviewSettings settings) {
        EndHeightmap heightmap = new EndHeightmap(preset, seed);
        EndCaveField caveField = EndCaveField.fromConfig(preset.subsurfaceConfig(), seed);
        CaveSystemConfig caveSystem = preset.subsurfaceConfig().caveSystemConfig();
        int worldHeight = Math.max(2, preset.worldHeight());
        int start = Math.clamp(caveSystem.depthStart(), 0, worldHeight - 1);
        int end = Math.clamp(caveSystem.depthEnd(), start + 1, worldHeight);
        float origin = (width - 1) * settings.blocksPerPixel() * 0.5F;
        int[] colors = new int[width * height];
        float maxStrength = 0.0F;

        for (int y = 0; y < height; y++) {
            float depthAlpha = height == 1 ? 0.0F : y / (float) (height - 1);
            float depth = start + (end - start) * depthAlpha;
            for (int x = 0; x < width; x++) {
                float horizontal = x * settings.blocksPerPixel() - origin;
                float worldX = settings.axis() == CaveSlicePreviewSettings.Axis.X
                        ? horizontal : settings.offsetBlocks();
                float worldZ = settings.axis() == CaveSlicePreviewSettings.Axis.X
                        ? settings.offsetBlocks() : horizontal;
                float landness = heightmap.getLandness(worldX, worldZ, seed);
                float terrainTop = heightmap.getHeight(worldX, worldZ, seed);
                float yNorm = terrainTop - depth / worldHeight;
                float strength = caveField.strength(worldX, worldZ, landness,
                        yNorm, terrainTop, worldHeight);
                maxStrength = Math.max(maxStrength, strength);
                colors[y * width + x] = TerrainPreviewPalette.caveDepthColor(
                        landness, strength, depthAlpha);
            }
        }
        return new CaveSlicePreview(width, height, colors, maxStrength);
    }

    private static void assertPreviewEquals(CaveSlicePreview expected, CaveSlicePreview actual) {
        assertEquals(expected.width(), actual.width());
        assertEquals(expected.height(), actual.height());
        assertEquals(expected.maxStrength(), actual.maxStrength(), 0.0F);
        assertArrayEquals(expected.colors(), actual.colors());
    }

    private static void assertArgbRaster(CaveSlicePreview preview) {
        for (int color : preview.colors()) {
            assertEquals(0xFF000000, color & 0xFF000000);
        }
    }

    private static long signature(CaveSlicePreview preview) {
        long hash = 1125899906842597L;
        for (int color : preview.colors()) {
            hash = hash * 31 + color;
        }
        hash = hash * 31 + Float.floatToIntBits(preview.maxStrength());
        return hash;
    }

    private static SubsurfaceConfig caveConfig() {
        return caveConfig(0.95F, 0.95F, 1.0F);
    }

    private static SubsurfaceConfig caveConfig(float chamberProbability,
                                               float networkDensity,
                                               float spectacleBias) {
        return new SubsurfaceConfig(
                AbyssPitConfig.DISABLED,
                CaveTunnelConfig.DISABLED,
                new CaveSystemConfig(true, 2400, 96, 768,
                        spectacleBias, 1.0F, 0.0F),
                new CaveNetworkConfig(384, networkDensity, 128,
                        4.0F, 1.0F, 0.45F, 0.0F),
                new CaveChamberConfig(chamberProbability, 96, 384,
                        2.2F, 0.35F, 0.7F));
    }
}
