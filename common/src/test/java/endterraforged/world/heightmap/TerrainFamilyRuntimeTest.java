package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.config.TerrainLayoutMode;
import endterraforged.world.config.TerrainShape;
import endterraforged.world.terrain.TerrainRegionFamily;

class TerrainFamilyRuntimeTest {
    private static final int SEED = 9173;

    @Test
    void familyContributionIsStableForOneOwnershipRegion() {
        TerrainFamilyRuntime runtime = new TerrainFamilyRuntime(config(), SEED);

        for (TerrainRegionFamily family : new TerrainRegionFamily[] {
                TerrainRegionFamily.PLAINS, TerrainRegionFamily.HILLS, TerrainRegionFamily.PLATEAU
        }) {
            float first = runtime.contribution(family, 0x0000001200000034L, family.ordinal(), 1800.5F, -950.25F);
            float second = runtime.contribution(family, 0x0000001200000034L, family.ordinal(), 1800.5F, -950.25F);
            assertEquals(first, second, 0.0F, family + " must be stable within one ownership region");
        }
    }

    @Test
    void familyVariantsAreSelectedByRegionIdentityInsteadOfPerColumnRandomness() {
        TerrainFamilyRuntime runtime = new TerrainFamilyRuntime(config(), SEED);
        float reference = runtime.contribution(TerrainRegionFamily.HILLS, 1L, 1, 640.0F, -1280.0F);
        boolean foundDifferentVariant = false;

        for (long regionId = 2L; regionId < 128L; regionId++) {
            float candidate = runtime.contribution(TerrainRegionFamily.HILLS, regionId, 1, 640.0F, -1280.0F);
            if (Float.floatToIntBits(reference) != Float.floatToIntBits(candidate)) {
                foundDifferentVariant = true;
                break;
            }
        }

        assertTrue(foundDifferentVariant,
                "different ownership regions must be able to select different stable hill variants");
    }

    @Test
    void enabledFamiliesProduceFiniteBoundedRelief() {
        TerrainFamilyRuntime runtime = new TerrainFamilyRuntime(config(), SEED);

        for (TerrainRegionFamily family : new TerrainRegionFamily[] {
                TerrainRegionFamily.PLAINS, TerrainRegionFamily.HILLS, TerrainRegionFamily.PLATEAU
        }) {
            for (int i = 0; i < 80; i++) {
                float value = runtime.contribution(family, i, family.ordinal(), i * 97.0F, i * -131.0F);
                assertTrue(Float.isFinite(value), family + " contribution must stay finite");
                assertTrue(value >= 0.0F, family + " contribution must not lower the base terrain");
                assertTrue(value <= 0.25F, family + " contribution exceeded the bounded Standard relief budget");
            }
        }
    }

    @Test
    void familySignalsKeepHeightAndStableChannelsTogether() {
        TerrainFamilyRuntime runtime = new TerrainFamilyRuntime(config(), SEED);
        EndTerrainSignalBuffer output = new EndTerrainSignalBuffer();

        runtime.sample(TerrainRegionFamily.HILLS, 0x0000001200000034L, 1,
                1800.5F, -950.25F, output);
        float contribution = runtime.contribution(
                TerrainRegionFamily.HILLS, 0x0000001200000034L, 1, 1800.5F, -950.25F);

        assertEquals(contribution, output.height(), 0.0F);
        assertTrue(output.roughness() >= 0.0F && output.roughness() <= 1.0F);
        assertTrue(output.erosionResistance() >= 0.0F && output.erosionResistance() <= 1.0F);
        assertEquals(1 << TerrainRegionFamily.HILLS.ordinal(), output.terrainTags());
    }

    private static TerrainConfig config() {
        return new TerrainConfig(
                0, 1200, 1.0F, 1.0F, 0.0F, TerrainLayoutMode.REGION_PLANNED,
                TerrainShape.SHATTERED_RIDGES,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DEFAULT,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED);
    }
}
