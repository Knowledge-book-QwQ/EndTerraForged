package endterraforged.world.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.ContinentAlgorithm;
import endterraforged.world.config.ContinentConfig;
import endterraforged.world.config.ContinentConfigBuilder;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetBuilder;
import endterraforged.world.heightmap.EndHeightmap;
import endterraforged.world.heightmap.EndLandmassVolume;

class RtfMultiPreviewTest {

    @Test
    void volumePreviewConsumesTheSameRtfMultiHeightmapPrimitiveAsRuntime() {
        int seed = 123456789;
        EndPreset preset = rtfPreset();
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

    private static EndPreset rtfPreset() {
        ContinentConfig config = new ContinentConfigBuilder(ContinentConfig.defaults())
                .continentScale(3000)
                .continentJitter(0.7F)
                .continentAlgorithm(ContinentAlgorithm.RTF_MULTI)
                .build();
        return new EndPresetBuilder(EndPreset.defaults()).continentConfig(config).build();
    }
}
