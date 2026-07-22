package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.config.TerrainShape;
import endterraforged.world.noise.Noises;

class EndTerrainComposerTest {
    private static final int SEED = 42;
    private static final int SAMPLES = 128;

    @Test
    void disabledLayersReturnZeroContribution() {
        EndTerrainComposer composer = new EndTerrainComposer(new TerrainConfig(
                0, 1200, 1.0F, 1.0F, TerrainShape.SHATTERED_RIDGES,
                TerrainLayerConfig.DISABLED, TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DISABLED, TerrainLayerConfig.DEFAULT,
                TerrainLayerConfig.DISABLED), SEED);

        for (int i = 0; i < SAMPLES; i++) {
            assertEquals(0.0F, composer.auxiliaryContribution(x(i), z(i), SEED), 0.0F);
            assertEquals(EndTerrainLayer.NONE, composer.selectedLayer(x(i), z(i), SEED));
        }
    }

    @Test
    void zeroWeightLayerDoesNotMaskEnabledLayer() {
        TerrainLayerConfig zeroWeight = new TerrainLayerConfig(
                0.0F, 10.0F, 10.0F, 1.0F);
        TerrainLayerConfig hills = new TerrainLayerConfig(
                1.0F, 1.0F, 1.0F, 1.0F);
        EndTerrainComposer zeroThenHills = new EndTerrainComposer(config(
                zeroWeight, hills, TerrainLayerConfig.DISABLED, TerrainLayerConfig.DISABLED), SEED);
        EndTerrainComposer hillsOnly = new EndTerrainComposer(config(
                TerrainLayerConfig.DISABLED, hills, TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DISABLED), SEED);

        for (int i = 0; i < SAMPLES; i++) {
            assertEquals(hillsOnly.auxiliaryContribution(x(i), z(i), SEED),
                    zeroThenHills.auxiliaryContribution(x(i), z(i), SEED),
                    1e-7F);
            assertEquals(EndTerrainLayer.HILLS, zeroThenHills.selectedLayer(x(i), z(i), SEED));
        }
    }

    @Test
    void mixedLayersSelectOneLayerInsteadOfStackingAllEnabledLayers() {
        TerrainLayerConfig plains = new TerrainLayerConfig(
                1.0F, 0.75F, 1.0F, 1.0F);
        TerrainLayerConfig hills = new TerrainLayerConfig(
                1.0F, 1.50F, 1.0F, 1.0F);
        EndTerrainComposer mixed = new EndTerrainComposer(config(
                plains, hills, TerrainLayerConfig.DISABLED, TerrainLayerConfig.DISABLED), SEED);
        EndTerrainComposer plainsOnly = new EndTerrainComposer(config(
                plains, TerrainLayerConfig.DISABLED, TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DISABLED), SEED);
        EndTerrainComposer hillsOnly = new EndTerrainComposer(config(
                TerrainLayerConfig.DISABLED, hills, TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DISABLED), SEED);

        boolean selectedPlains = false;
        boolean selectedHills = false;
        for (int i = 0; i < SAMPLES; i++) {
            float mixedContribution = mixed.auxiliaryContribution(x(i), z(i), SEED);
            float plainsContribution = plainsOnly.auxiliaryContribution(x(i), z(i), SEED);
            float hillsContribution = hillsOnly.auxiliaryContribution(x(i), z(i), SEED);
            boolean matchesPlains = Math.abs(mixedContribution - plainsContribution) <= 1e-6F;
            boolean matchesHills = Math.abs(mixedContribution - hillsContribution) <= 1e-6F;

            assertTrue(matchesPlains || matchesHills,
                    "mixed contribution should match exactly one enabled terrain layer");
            assertTrue(mixedContribution <= plainsContribution + hillsContribution + 1e-6F,
                    "mixed contribution should not stack all enabled terrain layers");
            EndTerrainLayer selectedLayer = mixed.selectedLayer(x(i), z(i), SEED);
            if (selectedLayer == EndTerrainLayer.PLAINS) {
                assertTrue(matchesPlains, "plains selection should use plains contribution");
            } else {
                assertEquals(EndTerrainLayer.HILLS, selectedLayer);
                assertTrue(matchesHills, "hills selection should use hills contribution");
            }
            selectedPlains |= selectedLayer == EndTerrainLayer.PLAINS;
            selectedHills |= selectedLayer == EndTerrainLayer.HILLS;
        }
        assertTrue(selectedPlains, "selector should sample the plains branch");
        assertTrue(selectedHills, "selector should sample the hills branch");
    }

    @Test
    void configuredBlendRangeSmoothsAdjacentLayerBoundaries() {
        TerrainLayerConfig plains = new TerrainLayerConfig(
                1.0F, 0.75F, 1.0F, 1.0F);
        TerrainLayerConfig hills = new TerrainLayerConfig(
                1.0F, 1.50F, 1.0F, 1.0F);
        EndTerrainComposer blended = new EndTerrainComposer(config(
                1.0F, plains, hills, TerrainLayerConfig.DISABLED, TerrainLayerConfig.DISABLED),
                SEED, Noises.constant(0.49F));
        EndTerrainComposer plainsOnly = new EndTerrainComposer(config(
                plains, TerrainLayerConfig.DISABLED, TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DISABLED), SEED, Noises.constant(0.49F));
        EndTerrainComposer hillsOnly = new EndTerrainComposer(config(
                TerrainLayerConfig.DISABLED, hills, TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DISABLED), SEED, Noises.constant(0.49F));

        boolean foundBlend = false;
        for (int i = 0; i < SAMPLES; i++) {
            float blendedContribution = blended.auxiliaryContribution(x(i), z(i), SEED);
            float plainsContribution = plainsOnly.auxiliaryContribution(x(i), z(i), SEED);
            float hillsContribution = hillsOnly.auxiliaryContribution(x(i), z(i), SEED);
            if (Math.abs(plainsContribution - hillsContribution) <= 1e-6F) {
                continue;
            }

            float min = Math.min(plainsContribution, hillsContribution);
            float max = Math.max(plainsContribution, hillsContribution);
            assertTrue(blendedContribution > min - 1e-6F,
                    "blended contribution should stay inside adjacent layer range");
            assertTrue(blendedContribution < max + 1e-6F,
                    "blended contribution should stay inside adjacent layer range");
            assertTrue(Math.abs(blendedContribution - plainsContribution) > 1e-6F,
                    "blend should differ from the left layer near the boundary");
            assertTrue(Math.abs(blendedContribution - hillsContribution) > 1e-6F,
                    "blend should differ from the right layer near the boundary");
            foundBlend = true;
            break;
        }
        assertTrue(foundBlend, "test samples should include distinct layer contributions");
    }

    @Test
    void selectedBlendDescribesAdjacentBoundary() {
        TerrainLayerConfig plains = new TerrainLayerConfig(
                1.0F, 0.75F, 1.0F, 1.0F);
        TerrainLayerConfig hills = new TerrainLayerConfig(
                1.0F, 1.50F, 1.0F, 1.0F);
        EndTerrainComposer composer = new EndTerrainComposer(config(
                1.0F, plains, hills, TerrainLayerConfig.DISABLED, TerrainLayerConfig.DISABLED),
                SEED, Noises.constant(0.49F));

        EndTerrainBlend blend = composer.selectedBlend(0.0F, 0.0F, SEED);

        assertEquals(EndTerrainLayer.PLAINS, blend.from());
        assertEquals(EndTerrainLayer.HILLS, blend.to());
        assertTrue(blend.alpha() > 0.0F, "boundary blend should have a non-zero alpha");
        assertTrue(blend.alpha() < 0.5F, "sample lies on the plains side of the boundary");
        assertEquals(EndTerrainLayer.PLAINS, blend.dominantLayer());
    }

    private static TerrainConfig config(TerrainLayerConfig plains, TerrainLayerConfig hills,
                                        TerrainLayerConfig plateau, TerrainLayerConfig volcano) {
        return config(0.0F, plains, hills, plateau, volcano);
    }

    private static TerrainConfig config(float terrainBlendRange,
                                        TerrainLayerConfig plains, TerrainLayerConfig hills,
                                        TerrainLayerConfig plateau, TerrainLayerConfig volcano) {
        return new TerrainConfig(0, 1200, 1.0F, 1.0F, terrainBlendRange,
                TerrainShape.SHATTERED_RIDGES,
                plains, hills, plateau, TerrainLayerConfig.DEFAULT, volcano);
    }

    private static float x(int i) {
        return i * 37.0F;
    }

    private static float z(int i) {
        return i * 19.0F;
    }
}
