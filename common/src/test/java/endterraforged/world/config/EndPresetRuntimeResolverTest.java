package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EndPresetRuntimeResolverTest {

    private static final WorldVerticalBounds STANDARD_BOUNDS =
            new WorldVerticalBounds(-256, 512);

    @Test
    void matchingPresetIsReturnedUnchanged() {
        EndPreset preset = EndPreset.defaults();

        EndPresetRuntimeResolver.Resolution resolution =
                EndPresetRuntimeResolver.resolve(preset, STANDARD_BOUNDS);

        assertSame(preset, resolution.preset());
        assertFalse(resolution.boundsAdjusted());
    }

    @Test
    void legacyEditorEnvelopeUsesActualDimensionBoundsWithoutLosingTerrainSettings() {
        EndPreset legacy = new EndPresetBuilder(EndPreset.defaults())
                .worldHeight(2128)
                .minY(-192)
                .seaLevelY(0)
                .islandBaselineY(0)
                .build();

        EndPresetRuntimeResolver.Resolution resolution =
                EndPresetRuntimeResolver.resolve(legacy, STANDARD_BOUNDS);

        assertTrue(resolution.boundsAdjusted());
        assertEquals(STANDARD_BOUNDS, resolution.preset().worldBounds());
        assertEquals(legacy.continentConfig(), resolution.preset().continentConfig());
        assertEquals(legacy.terrainConfig(), resolution.preset().terrainConfig());
        assertEquals(legacy.climateConfig(), resolution.preset().climateConfig());
        assertEquals(legacy.subsurfaceConfig(), resolution.preset().subsurfaceConfig());
    }

    @Test
    void rejectsReferenceHeightsOutsideActualDimension() {
        EndPreset invalidForStandardWorld = new EndPresetBuilder(EndPreset.defaults())
                .worldHeight(2048)
                .minY(-1024)
                .seaLevelY(768)
                .islandBaselineY(0)
                .build();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> EndPresetRuntimeResolver.resolve(invalidForStandardWorld, STANDARD_BOUNDS));
        assertTrue(error.getMessage().contains("sea_level_y"));
    }
}
