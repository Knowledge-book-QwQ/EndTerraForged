package endterraforged.world.preview;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.ContinentConfigBuilder;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetBuilder;

class LandmassSlicePreviewSamplerTest {

    @Test
    void previewDefensivelyCopiesColorsAndValidatesShape() {
        int[] colors = {0xFF000001, 0xFF000002, 0xFF000003, 0xFF000004};
        LandmassSlicePreview preview = new LandmassSlicePreview(2, 2, colors, 2);
        colors[0] = 0xFFFFFFFF;
        assertEquals(0xFF000001, preview.colorAt(0, 0));

        assertThrows(IllegalArgumentException.class,
                () -> new LandmassSlicePreview(2, 2, new int[3], 0));
        assertThrows(IllegalArgumentException.class,
                () -> new LandmassSlicePreview(2, 2, new int[4], 5));
        assertThrows(IndexOutOfBoundsException.class, () -> preview.colorAt(2, 0));
    }

    @Test
    void samplerRejectsInvalidInputs() {
        EndPreset preset = EndPreset.defaults();
        assertThrows(IllegalArgumentException.class,
                () -> LandmassSlicePreviewSampler.sample(preset, 1, 0, 16, 24));
        assertThrows(IllegalArgumentException.class,
                () -> LandmassSlicePreviewSampler.sample(preset, 1, 16, 0, 24));
        assertThrows(IllegalArgumentException.class,
                () -> new LandmassSlicePreviewSettings(LandmassSlicePreviewSettings.Axis.X, 0, 0));
    }

    @Test
    void defaultShelfProducesDeterministicVisibleExteriorSlice() {
        LandmassSlicePreview first = LandmassSlicePreviewSampler.sample(
                EndPreset.defaults(), 7, 64, 48, 32);
        LandmassSlicePreview second = LandmassSlicePreviewSampler.sample(
                EndPreset.defaults(), 7, 64, 48, 32);

        assertEquals(64, first.width());
        assertEquals(48, first.height());
        assertTrue(first.solidSamples() > 0,
                "outer-continent slice must sample a visible exterior shelf instead of the protected centre");
        assertArrayEquals(first.colors(), second.colors());
        assertEquals(first.solidSamples(), second.solidSamples());
        assertArgb(first);
    }

    @Test
    void shelfThicknessChangesSliceAndLegacyColumnsRemainDistinct() {
        EndPreset thin = new EndPresetBuilder(EndPreset.defaults())
                .continentConfig(new ContinentConfigBuilder(EndPreset.defaults().continentConfig())
                        .shelfThickness(64).shelfEdgeThickness(32).build())
                .build();
        EndPreset thick = new EndPresetBuilder(EndPreset.defaults())
                .continentConfig(new ContinentConfigBuilder(EndPreset.defaults().continentConfig())
                        .shelfThickness(384).shelfEdgeThickness(96).build())
                .build();
        EndPreset defaults = EndPreset.defaults();
        EndPreset legacy = new EndPreset(defaults.worldHeight(), defaults.minY(), defaults.seaLevelY(),
                defaults.islandBaselineY(), defaults.seaMode(), defaults.topologyMode(),
                defaults.floatingIslandsEnabled(),
                endterraforged.world.config.ContinentConfig.legacyDefaults(), defaults.terrainConfig(),
                defaults.climateConfig(), defaults.biomeLayoutConfig(), defaults.subsurfaceConfig(),
                defaults.erosionConfig(), 2);

        LandmassSlicePreview thinSlice = LandmassSlicePreviewSampler.sample(thin, 7, 64, 48, 32);
        LandmassSlicePreview thickSlice = LandmassSlicePreviewSampler.sample(thick, 7, 64, 48, 32);
        LandmassSlicePreview legacySlice = LandmassSlicePreviewSampler.sample(legacy, 7, 64, 48, 32);

        assertNotEquals(signature(thinSlice), signature(thickSlice));
        assertNotEquals(signature(thinSlice), signature(legacySlice));
    }

    @Test
    void axisAndOffsetChangeTheSamplePath() {
        EndPreset preset = EndPreset.defaults();
        LandmassSlicePreview xSlice = LandmassSlicePreviewSampler.sample(preset, 7, 64, 48,
                new LandmassSlicePreviewSettings(LandmassSlicePreviewSettings.Axis.X, 192, 32));
        LandmassSlicePreview zSlice = LandmassSlicePreviewSampler.sample(preset, 7, 64, 48,
                new LandmassSlicePreviewSettings(LandmassSlicePreviewSettings.Axis.Z, 192, 32));
        LandmassSlicePreview offsetSlice = LandmassSlicePreviewSampler.sample(preset, 7, 64, 48,
                new LandmassSlicePreviewSettings(LandmassSlicePreviewSettings.Axis.X, 768, 32));

        assertNotEquals(signature(xSlice), signature(zSlice));
        assertNotEquals(signature(xSlice), signature(offsetSlice));
    }

    private static void assertArgb(LandmassSlicePreview preview) {
        for (int color : preview.colors()) {
            assertEquals(0xFF000000, color & 0xFF000000);
        }
    }

    private static long signature(LandmassSlicePreview preview) {
        long hash = 1125899906842597L;
        for (int color : preview.colors()) {
            hash = hash * 31 + color;
        }
        return hash * 31 + preview.solidSamples();
    }
}
