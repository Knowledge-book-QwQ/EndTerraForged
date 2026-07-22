package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.ContinentConfig;
import endterraforged.world.config.ContinentConfigBuilder;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.config.TerrainLayoutMode;
import endterraforged.world.config.TerrainShape;
import endterraforged.world.config.TopologyMode;
import endterraforged.world.terrain.TerrainRegionBuffer;

class EndTerrainEligibilityPolicyTest {
    private static final int SEED = 9173;

    @Test
    void outerTopologyKeepsTheCentralProtectedRegionFreeOfRidgeRelief() {
        EndTerrainEligibilityPolicy policy = new EndTerrainEligibilityPolicy(
                TopologyMode.OUTER_CONTINENTS, defaultVolume());

        assertEquals(0.0F, policy.ridgeMultiplier(0.0F, 0.0F, 1.0F, 1.0F), 0.0F);
        assertEquals(1.0F, policy.ridgeMultiplier(4096.0F, 0.0F, 1.0F, 1.0F), 0.0F);
    }

    @Test
    void weakCoastsAndThinShelvesRejectRidgeRelief() {
        EndTerrainEligibilityPolicy defaultPolicy = new EndTerrainEligibilityPolicy(
                TopologyMode.CONTINENTAL, defaultVolume());
        EndLandmassVolume thinVolume = new EndLandmassVolume(
                new ContinentConfigBuilder(ContinentConfig.defaults())
                        .shelfThickness(32)
                        .shelfEdgeThickness(16)
                        .build(),
                new EndLevels(EndPreset.defaults()), SEED);
        EndTerrainEligibilityPolicy thinShelfPolicy = new EndTerrainEligibilityPolicy(
                TopologyMode.CONTINENTAL, thinVolume);

        assertEquals(0.0F, defaultPolicy.ridgeMultiplier(4096.0F, 0.0F, 0.20F, 1.0F), 0.0F);
        assertEquals(0.0F, thinShelfPolicy.ridgeMultiplier(4096.0F, 0.0F, 1.0F, 1.0F), 0.0F);
        assertEquals(1.0F, defaultPolicy.ridgeMultiplier(4096.0F, 0.0F, 1.0F, 1.0F), 0.0F);
    }

    @Test
    void ridgeEligibilityScalesOnlyPhysicalReliefAndKeepsOwnershipStable() {
        EndTerrainRegionComposer composer = new EndTerrainRegionComposer(regionPlannedTerrain(), SEED);

        for (int z = -16000; z <= 16000; z += 128) {
            for (int x = -16000; x <= 16000; x += 128) {
                TerrainRegionBuffer fullLayerBuffer = new TerrainRegionBuffer();
                EndTerrainLayer fullLayer = composer.selectedLayer(x, z, fullLayerBuffer, 1.0F);
                if (fullLayer != EndTerrainLayer.MOUNTAINS) {
                    continue;
                }

                TerrainRegionBuffer suppressedLayerBuffer = new TerrainRegionBuffer();
                EndTerrainLayer suppressedLayer = composer.selectedLayer(
                        x, z, suppressedLayerBuffer, 0.0F);
                TerrainRegionBuffer fullContributionBuffer = new TerrainRegionBuffer();
                TerrainRegionBuffer suppressedContributionBuffer = new TerrainRegionBuffer();
                float fullContribution = composer.auxiliaryContribution(
                        x, z, SEED, fullContributionBuffer, 1.0F);
                float suppressedContribution = composer.auxiliaryContribution(
                        x, z, SEED, suppressedContributionBuffer, 0.0F);

                if (fullContribution <= suppressedContribution + 1.0E-6F) {
                    continue;
                }

                assertEquals(fullLayerBuffer.regionId(), suppressedLayerBuffer.regionId());
                assertEquals(fullLayerBuffer.ownershipEntryId(), suppressedLayerBuffer.ownershipEntryId());
                assertEquals(fullLayerBuffer.ownershipFamily(), suppressedLayerBuffer.ownershipFamily());
                assertEquals(fullLayerBuffer.underlayFamily(), suppressedLayerBuffer.underlayFamily());
                assertEquals(fullLayerBuffer.ownershipFamily(), fullLayerBuffer.underlayFamily());
                assertEquals(0.0F, suppressedLayerBuffer.physicalInfluence(), 0.0F);
                assertEquals(toLayer(fullLayerBuffer.ownershipFamily()), suppressedLayer,
                        "a zero ridge multiplier must reveal the unchanged AREA owner");
                assertTrue(fullContribution > suppressedContribution,
                        "eligibility must scale the ridge feature without removing its underlay");
                return;
            }
        }

        throw new AssertionError("test terrain layout did not expose a ridge with positive relief");
    }

    private static EndLandmassVolume defaultVolume() {
        return new EndLandmassVolume(ContinentConfig.defaults(), new EndLevels(EndPreset.defaults()), SEED);
    }

    private static TerrainConfig regionPlannedTerrain() {
        return new TerrainConfig(
                0, 1600, 1.0F, 1.0F, 0.0F, TerrainLayoutMode.REGION_PLANNED,
                TerrainShape.SHATTERED_RIDGES,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED);
    }

    private static EndTerrainLayer toLayer(endterraforged.world.terrain.TerrainRegionFamily family) {
        return switch (family) {
            case PLAINS -> EndTerrainLayer.PLAINS;
            case HILLS -> EndTerrainLayer.HILLS;
            case PLATEAU -> EndTerrainLayer.PLATEAU;
            case MOUNTAINS -> EndTerrainLayer.MOUNTAINS;
            case VOLCANO -> EndTerrainLayer.VOLCANO;
        };
    }
}
