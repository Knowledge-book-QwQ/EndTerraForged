package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.config.TerrainLayoutMode;
import endterraforged.world.config.TerrainShape;

class EndTerrainVolcanoRuntimeTest {
    private static final int SEED = 9173;

    @Test
    void frozenVolcanoGeometryRemainsFiniteAndDeterministic() {
        EndTerrainVolcanoRuntime volcano = new EndTerrainVolcanoRuntime(config(), SEED);
        float centerX = 1200.0F;
        float centerZ = -800.0F;
        float orientation = 0.73F;
        long regionId = 27L;

        for (int z = -1200; z <= 1200; z += 32) {
            for (int x = -1200; x <= 1200; x += 32) {
                float first = volcano.contribution(regionId, centerX, centerZ, orientation,
                        centerX + x, centerZ + z);
                float second = volcano.contribution(regionId, centerX, centerZ, orientation,
                        centerX + x, centerZ + z);
                assertEquals(first, second, 0.0F);
                assertTrue(Float.isFinite(first));
                assertTrue(first >= 0.0F);
            }
        }
    }

    @Test
    void volcanoRimRisesAboveItsCentralCrater() {
        EndTerrainVolcanoRuntime volcano = new EndTerrainVolcanoRuntime(config(), SEED);
        float centerX = 1200.0F;
        float centerZ = -800.0F;
        float orientation = 0.73F;
        long regionId = 27L;
        float crater = volcano.contribution(
                regionId, centerX, centerZ, orientation, centerX, centerZ);
        float maximumRim = 0.0F;
        float cosine = (float) Math.cos(orientation);
        float sine = (float) Math.sin(orientation);
        for (int offset = 8; offset <= 1024; offset += 8) {
            maximumRim = Math.max(maximumRim, volcano.contribution(
                    regionId, centerX, centerZ, orientation,
                    centerX + cosine * offset, centerZ + sine * offset));
        }

        assertTrue(crater > 0.0F);
        assertTrue(maximumRim > crater + 0.01F);
    }

    @Test
    void regionPlannedComposerRejectsTheFrozenCompactDraft() {
        assertThrows(IllegalArgumentException.class,
                () -> new EndTerrainRegionComposer(config(), SEED));
    }

    private static TerrainConfig config() {
        return new TerrainConfig(
                0, 1600, 1.0F, 1.0F, 0.0F, TerrainLayoutMode.REGION_PLANNED,
                TerrainShape.SHATTERED_RIDGES,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DISABLED, TerrainLayerConfig.DEFAULT);
    }
}
