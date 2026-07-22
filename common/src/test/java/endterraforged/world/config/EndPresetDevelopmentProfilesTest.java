package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.heightmap.EndDensity;
import endterraforged.world.heightmap.EndHeightmap;
import endterraforged.world.heightmap.EndTerrainLayer;

class EndPresetDevelopmentProfilesTest {

    @Test
    void smokeProfileUsesTheControlledP46RuntimePathWithoutChangingDefault() {
        EndPreset profile = EndPresetDevelopmentProfiles.archipelagoSmokeTest();

        assertEquals(TopologyMode.OUTER_CONTINENTS, profile.topologyMode());
        assertEquals(ContinentAlgorithm.RTF_MULTI,
                profile.continentConfig().continentAlgorithm());
        assertEquals(TerrainLayoutMode.REGION_PLANNED,
                profile.terrainConfig().terrainLayoutMode());
        assertEquals(SeaMode.WITH_FLOOR, profile.seaMode());
        assertEquals(EndPreset.CURRENT_FORMAT_VERSION, profile.formatVersion());
        assertTrue(profile.terrainConfig().plains().weight() > 0.0F);
        assertTrue(profile.terrainConfig().hills().weight() > 0.0F);
        assertTrue(profile.terrainConfig().plateau().weight() > 0.0F);
        assertTrue(profile.terrainConfig().mountains().verticalScale() > 1.0F);
        assertNotEquals(EndPreset.defaults().terrainConfig().terrainLayoutMode(),
                profile.terrainConfig().terrainLayoutMode());
    }

    @Test
    void smokeProfileOuterMainlandHasVisibleRelief() {
        int seed = 123456789;
        EndHeightmap heightmap = new EndHeightmap(
                EndPresetDevelopmentProfiles.archipelagoSmokeTest(), seed);
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        int samples = 0;

        for (int z = -6000; z <= 6000; z += 512) {
            for (int x = 2048; x <= 12000; x += 256) {
                if (heightmap.getMainlandLandness(x, z, seed) < 0.55F) {
                    continue;
                }
                float height = heightmap.getMainlandTerrainHeight(x, z, seed);
                min = Math.min(min, height);
                max = Math.max(max, height);
                samples++;
            }
        }

        assertTrue(samples >= 16, "the smoke fixture must include enough mainland samples");
        assertTrue(max - min >= 0.02F,
                "REGION_PLANNED smoke terrain must not collapse to the reference surface");
        assertTrue(max > heightmap.levels().surface + 0.04F,
                "the smoke mainland must rise visibly above Y=0");
    }

    @Test
    void smokeProfileIncludesVisibleMountainRidgesAndExteriorOcean() {
        int seed = 123456789;
        EndPreset profile = EndPresetDevelopmentProfiles.archipelagoSmokeTest();
        EndHeightmap heightmap = new EndHeightmap(profile, seed);
        EndDensity density = new EndDensity(heightmap);
        boolean sawMountain = false;
        boolean sawOcean = false;
        float highestMountain = heightmap.levels().surface;

        for (int z = -12000; z <= 12000; z += 256) {
            for (int x = 4096; x <= 14000; x += 256) {
                float landness = heightmap.getMainlandLandness(x, z, seed);
                if (landness > 0.60F
                        && heightmap.auxiliaryTerrainAt(x, z, seed) == EndTerrainLayer.MOUNTAINS) {
                    sawMountain = true;
                    highestMountain = Math.max(
                            highestMountain, heightmap.getMainlandTerrainHeight(x, z, seed));
                }
                if (density.hasOceanAt(x, -1, z, seed)) {
                    sawOcean = true;
                }
            }
        }

        assertTrue(sawMountain, "the smoke fixture must expose a visible mountain ridge");
        assertTrue(highestMountain > heightmap.levels().surface + 0.16F,
                "the smoke mountain ridge must rise clearly above the reference surface");
        assertTrue(sawOcean, "the smoke fixture must expose an exterior ocean sample");
    }

    @Test
    void propertyIsDisabledByDefaultAndNormalFallbackRemainsDefault() {
        String previous = System.getProperty(
                EndPresetDevelopmentProfiles.P46_ARCHIPELAGO_SMOKE_TEST_PROPERTY);
        try {
            System.clearProperty(EndPresetDevelopmentProfiles.P46_ARCHIPELAGO_SMOKE_TEST_PROPERTY);
            assertEquals(EndPreset.defaults(), EndPresetDevelopmentProfiles.defaultFallback());
        } finally {
            restoreProperty(previous);
        }
    }

    @Test
    void explicitPropertySelectsSmokeProfileButPublishedPresetStillWins() {
        String previous = System.getProperty(
                EndPresetDevelopmentProfiles.P46_ARCHIPELAGO_SMOKE_TEST_PROPERTY);
        EndPreset previousPreset = EndPresetAccess.get();
        EndPreset published = EndPreset.legacyDefaults();
        try {
            EndPresetAccess.clear();
            System.setProperty(
                    EndPresetDevelopmentProfiles.P46_ARCHIPELAGO_SMOKE_TEST_PROPERTY, "true");
            assertEquals(EndPresetDevelopmentProfiles.archipelagoSmokeTest(),
                    EndPresetAccess.getOrDefault());
            assertEquals(EndPreset.defaults(), EndPresetAccess.getEditableOrDefault(),
                    "the non-persisted smoke profile must never enter the v3 editor");

            EndPresetAccess.set(published);
            assertEquals(published, EndPresetAccess.getOrDefault());
            assertEquals(published, EndPresetAccess.getEditableOrDefault());
        } finally {
            EndPresetAccess.set(previousPreset);
            restoreProperty(previous);
        }
    }

    private static void restoreProperty(String value) {
        if (value == null) {
            System.clearProperty(EndPresetDevelopmentProfiles.P46_ARCHIPELAGO_SMOKE_TEST_PROPERTY);
        } else {
            System.setProperty(
                    EndPresetDevelopmentProfiles.P46_ARCHIPELAGO_SMOKE_TEST_PROPERTY, value);
        }
    }
}
