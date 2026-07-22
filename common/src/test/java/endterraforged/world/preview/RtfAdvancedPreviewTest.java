package endterraforged.world.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.ContinentAlgorithm;
import endterraforged.world.config.ContinentConfig;
import endterraforged.world.config.EndPreset;
import endterraforged.world.heightmap.EndHeightmap;
import endterraforged.world.heightmap.EndLandmassVolume;

class RtfAdvancedPreviewTest {

    @Test
    void volumePreviewConsumesTheSameAdvancedHeightmapPrimitiveAsRuntime() {
        int seed = 123456789;
        EndPreset preset = advancedPreset();
        EndHeightmap heightmap = new EndHeightmap(preset, seed);
        TerrainPreviewSampler.PreviewCenter center =
                TerrainPreviewSampler.representativeCenter(preset, heightmap, seed);
        TerrainPreview preview = TerrainPreviewSampler.sample(
                preset, seed, 1, 16, TerrainPreviewMode.VOLUME);

        float landness = heightmap.getLandness(center.x(), center.z(), seed);
        float terrainTop = heightmap.getHeight(center.x(), center.z(), seed);
        EndLandmassVolume volume = heightmap.landmassVolume();
        int expected = TerrainPreviewPalette.volumeColor(
                landness, volume.isFinite(),
                terrainTop - volume.underside(center.x(), center.z(), landness, terrainTop));

        assertEquals(expected, preview.colorAt(0, 0));
    }

    @Test
    void widePreviewUsesTheRtfTectonicScale() {
        EndPreset preset = advancedPreset();
        int blocksPerPixel = TerrainPreviewSampler.blocksPerPixelForPreset(
                preset, 100, TerrainPreviewScale.WIDE);
        assertEquals(240, blocksPerPixel);
    }

    private static EndPreset advancedPreset() {
        EndPreset defaults = EndPreset.defaults();
        ContinentConfig base = ContinentConfig.rtfMultiDefaults();
        ContinentConfig config = new ContinentConfig(
                base.islandsScale(),
                base.continentScale(),
                base.continentShape(),
                base.continentJitter(),
                base.continentSkipping(),
                base.continentSizeVariance(),
                base.continentNoiseOctaves(),
                base.continentNoiseGain(),
                base.continentNoiseLacunarity(),
                base.featureSpread(),
                base.islandRadius(),
                base.islandScatter(),
                base.riftThreshold(),
                base.riftStrength(),
                base.warpScale(),
                base.warpStrength(),
                base.outerContinentScale(),
                base.landmassVolumeMode(),
                base.shelfThickness(),
                base.shelfEdgeThickness(),
                base.coastShape(),
                base.coastScale(),
                base.coastStrength(),
                base.coastCellBlend(),
                ContinentAlgorithm.RTF_ADVANCED,
                base.continentBands());
        return new EndPreset(
                defaults.worldHeight(),
                defaults.minY(),
                defaults.seaLevelY(),
                defaults.islandBaselineY(),
                defaults.seaMode(),
                defaults.topologyMode(),
                defaults.floatingIslandsEnabled(),
                config,
                defaults.terrainConfig(),
                defaults.climateConfig(),
                defaults.biomeLayoutConfig(),
                defaults.subsurfaceConfig(),
                defaults.erosionConfig(),
                defaults.formatVersion());
    }
}
