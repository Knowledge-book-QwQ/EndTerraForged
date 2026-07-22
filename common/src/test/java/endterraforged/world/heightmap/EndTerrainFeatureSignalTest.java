package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.config.TerrainLayoutMode;
import endterraforged.world.config.TerrainShape;
import endterraforged.world.terrain.TerrainRegionBuffer;
import endterraforged.world.terrain.TerrainRegionFamily;

class EndTerrainFeatureSignalTest {
    private static final int SEED = 9173;
    private static final int MOUNTAIN_TAG = 1 << TerrainRegionFamily.MOUNTAINS.ordinal();

    @Test
    void ridgeChannelsReturnExactlyToAreaSignalsOutsideTheirFootprints() {
        EndTerrainRegionComposer composer = new EndTerrainRegionComposer(config(true), SEED);
        EndTerrainRegionComposer areaOnly = new EndTerrainRegionComposer(config(false), SEED);
        TerrainRegionBuffer ridgeRegion = new TerrainRegionBuffer();
        TerrainRegionBuffer areaRegion = new TerrainRegionBuffer();
        EndTerrainSignalBuffer ridgeSignals = new EndTerrainSignalBuffer();
        EndTerrainSignalBuffer areaSignals = new EndTerrainSignalBuffer();
        boolean sawOutside = false;
        boolean sawTransition = false;
        boolean sawFeature = false;

        for (int z = -16000; z <= 16000; z += 48) {
            for (int x = -16000; x <= 16000; x += 48) {
                composer.sampleSignals(x, z, SEED, ridgeRegion, ridgeSignals, 1.0F);
                areaOnly.sampleSignals(x, z, SEED, areaRegion, areaSignals, 1.0F);
                assertEquals(ridgeRegion.regionId(), areaRegion.regionId());
                assertEquals(ridgeRegion.ownershipFamily(), areaRegion.ownershipFamily());

                float influence = ridgeRegion.physicalInfluence();
                if (influence == 0.0F) {
                    sawOutside = true;
                    assertEquals(areaSignals.height(), ridgeSignals.height(), 0.0F);
                    assertEquals(areaSignals.roughness(), ridgeSignals.roughness(), 0.0F);
                    assertEquals(areaSignals.erosionResistance(), ridgeSignals.erosionResistance(), 0.0F);
                    assertEquals(areaSignals.terrainTags(), ridgeSignals.terrainTags());
                } else {
                    sawTransition |= influence < 1.0F;
                    assertTrue(ridgeSignals.height() >= areaSignals.height());
                    assertTrue(ridgeSignals.roughness() >= 0.0F && ridgeSignals.roughness() <= 1.0F);
                    assertTrue(ridgeSignals.erosionResistance() >= 0.0F
                            && ridgeSignals.erosionResistance() <= 1.0F);
                    if (influence >= EndTerrainRegionComposer.FEATURE_TAG_THRESHOLD) {
                        sawFeature = true;
                        assertTrue((ridgeSignals.terrainTags() & MOUNTAIN_TAG) != 0);
                    }
                }
            }
        }

        assertTrue(sawOutside);
        assertTrue(sawTransition);
        assertTrue(sawFeature);
    }

    @Test
    void zeroEligibilityRestoresEveryAreaSignalExactly() {
        EndTerrainRegionComposer composer = new EndTerrainRegionComposer(config(true), SEED);
        EndTerrainRegionComposer areaOnly = new EndTerrainRegionComposer(config(false), SEED);
        TerrainRegionBuffer suppressedRegion = new TerrainRegionBuffer();
        TerrainRegionBuffer areaRegion = new TerrainRegionBuffer();
        EndTerrainSignalBuffer suppressed = new EndTerrainSignalBuffer();
        EndTerrainSignalBuffer area = new EndTerrainSignalBuffer();

        for (int z = -8000; z <= 8000; z += 257) {
            for (int x = -8000; x <= 8000; x += 257) {
                composer.sampleSignals(x, z, SEED, suppressedRegion, suppressed, 0.0F);
                areaOnly.sampleSignals(x, z, SEED, areaRegion, area, 1.0F);
                assertEquals(area.height(), suppressed.height(), 0.0F);
                assertEquals(area.roughness(), suppressed.roughness(), 0.0F);
                assertEquals(area.erosionResistance(), suppressed.erosionResistance(), 0.0F);
                assertEquals(area.terrainTags(), suppressed.terrainTags());
                assertEquals(areaRegion.regionId(), suppressedRegion.regionId());
                assertEquals(0.0F, suppressedRegion.physicalInfluence(), 0.0F);
            }
        }
    }

    private static TerrainConfig config(boolean ridges) {
        return new TerrainConfig(
                0, 1600, 1.0F, 1.0F, 0.0F, TerrainLayoutMode.REGION_PLANNED,
                TerrainShape.SHATTERED_RIDGES,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED,
                ridges ? TerrainLayerConfig.DEFAULT : TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DISABLED);
    }
}
