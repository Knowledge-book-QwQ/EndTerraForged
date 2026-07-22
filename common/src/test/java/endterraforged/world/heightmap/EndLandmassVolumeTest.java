package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.ContinentConfig;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.LandmassVolumeMode;
import endterraforged.world.config.TestProfile;

class EndLandmassVolumeTest {

    @Test
    void defaultPresetBuildsFiniteFloatingShelf() {
        EndHeightmap heightmap = new EndHeightmap(EndPreset.defaults(), 42);
        EndLandmassVolume volume = heightmap.landmassVolume();

        assertEquals(LandmassVolumeMode.FLOATING_SHELF, volume.mode());
        assertTrue(volume.isFinite());
    }

    @Test
    void shelfThickensFromEdgeToInterior() {
        EndLandmassVolume volume = new EndLandmassVolume(ContinentConfig.defaults(),
                new EndLevels(EndPreset.defaults()));
        float top = 0.8F;
        float edgeUnderside = volume.underside(2048.0F, -1024.0F, 0.0F, top);
        float interiorUnderside = volume.underside(2048.0F, -1024.0F, 1.0F, top);

        assertTrue(edgeUnderside > interiorUnderside,
                "the interior must extend lower than the thin landmass edge");
        assertTrue(volume.contains(2048.0F, -1024.0F, 0.0F, edgeUnderside, top));
        assertFalse(volume.contains(2048.0F, -1024.0F, 0.0F, edgeUnderside - 0.001F, top));
    }

    @Test
    void interiorUndersideDoesNotCopyHighFrequencyTerrainRelief() {
        EndLevels levels = new EndLevels(EndPreset.defaults());
        EndLandmassVolume volume = new EndLandmassVolume(ContinentConfig.defaults(), levels);
        float lowerTop = levels.surface + 0.05F;
        float higherTop = levels.surface + 0.30F;
        float expected = volume.underside(4096.0F, -2048.0F, 1.0F, lowerTop);

        assertEquals(expected, volume.underside(4096.0F, -2048.0F, 1.0F, higherTop), 1.0E-6F);
    }

    @Test
    void interiorUndersideHasBroadSpatialRelief() {
        EndLevels levels = new EndLevels(EndPreset.defaults());
        EndLandmassVolume volume = new EndLandmassVolume(ContinentConfig.defaults(), levels, 42);
        float terrainTop = levels.surface + 0.25F;
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;

        for (int x = 0; x <= 16384; x += 512) {
            float underside = volume.underside(x, 1377.0F, 1.0F, terrainTop);
            min = Math.min(min, underside);
            max = Math.max(max, underside);
        }

        assertTrue((max - min) * levels.worldHeight >= 8.0F,
                "the interior underside must vary at a visible macro scale");
    }

    @Test
    void shelfThicknessConvergesAtTheVoidBoundary() {
        EndLevels levels = new EndLevels(EndPreset.defaults());
        ContinentConfig config = ContinentConfig.defaults();
        EndLandmassVolume volume = new EndLandmassVolume(config, levels);
        float top = 0.8F;

        assertEquals(0.0F, volume.edgeFade(0.0F), 0.0F);
        assertEquals(1.0F, volume.edgeFade(1.0F), 0.0F);
        assertEquals(top, volume.underside(2048.0F, -1024.0F, 0.0F, top), 0.0F,
                "a void-boundary sample must not retain a fixed shelf thickness");

        float shallowThickness = top - volume.underside(2048.0F, -1024.0F, 0.05F, top);
        float interiorThickness = top - volume.underside(2048.0F, -1024.0F, 1.0F, top);
        assertTrue(shallowThickness > 0.0F && shallowThickness < interiorThickness * 0.01F,
                "a near-void sample must stay close to the terrain top before tapering into the shelf");
    }

    @Test
    void availableShelfSupportUsesReferenceSurfaceInsteadOfTerrainTop() {
        EndLevels levels = new EndLevels(EndPreset.defaults());
        EndLandmassVolume volume = new EndLandmassVolume(ContinentConfig.defaults(), levels, 42);

        assertEquals(0.0F, volume.availableShelfThicknessBlocks(4096.0F, -2048.0F, 0.0F), 0.0F);
        assertTrue(volume.availableShelfThicknessBlocks(4096.0F, -2048.0F, 1.0F) >= 96.0F,
                "the default interior shelf must provide enough independent support for bounded ridges");
    }

    @Test
    void minimumUndersideUsesTheThickestPossibleShelfDepth() {
        EndHeightmap heightmap = new EndHeightmap(EndPreset.defaults(), 42);
        EndLandmassVolume volume = heightmap.landmassVolume();
        for (int x = -8192; x <= 8192; x += 512) {
            for (float landness = 0.0F; landness <= 1.0F; landness += 0.1F) {
                assertTrue(volume.underside(x, 733.0F, landness, heightmap.levels().surface)
                                >= volume.minimumUnderside(),
                        "the minimum underside must bound every landness value and macro relief sample");
            }
        }
    }

    @Test
    void lightweightProfilesKeepLegacyColumnSemantics() {
        EndLandmassVolume volume = new EndHeightmap(TestProfile.defaultEnd(), 42).landmassVolume();

        assertEquals(LandmassVolumeMode.LEGACY_COLUMN, volume.mode());
        assertFalse(volume.isFinite());
    }
}
