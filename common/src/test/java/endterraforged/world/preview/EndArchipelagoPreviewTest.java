package endterraforged.world.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.ContinentConfig;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.config.TerrainLayoutMode;

class EndArchipelagoPreviewTest {

    @Test
    void archipelagoModeUsesTheRuntimePreviewPath() {
        EndPreset preset = controlledPreset();
        TerrainPreview first = TerrainPreviewSampler.sample(preset, 123456789, 64, 512,
                TerrainPreviewMode.ARCHIPELAGO);
        TerrainPreview second = TerrainPreviewSampler.sample(preset, 123456789, 64, 512,
                TerrainPreviewMode.ARCHIPELAGO);

        assertEquals(Arrays.hashCode(first.colors()), Arrays.hashCode(second.colors()));
        assertTrue(Arrays.stream(first.colors()).distinct().count() > 2,
                "archipelago preview must expose void, coast and land diagnostics");
    }

    private static EndPreset controlledPreset() {
        EndPreset defaults = EndPreset.defaults();
        TerrainConfig base = TerrainConfig.DEFAULT;
        TerrainConfig terrain = new TerrainConfig(
                base.terrainSeedOffset(), base.terrainRegionSize(), base.globalVerticalScale(),
                base.globalHorizontalScale(), base.terrainBlendRange(), TerrainLayoutMode.REGION_PLANNED,
                base.terrainShape(), TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DEFAULT,
                base.plateau(), base.mountains(), base.volcano());
        return new EndPreset(
                defaults.worldHeight(), defaults.minY(), defaults.seaLevelY(), defaults.islandBaselineY(),
                defaults.seaMode(), defaults.topologyMode(), defaults.floatingIslandsEnabled(),
                ContinentConfig.rtfMultiDefaults(), terrain, defaults.climateConfig(),
                defaults.biomeLayoutConfig(), defaults.subsurfaceConfig(), defaults.erosionConfig(),
                defaults.formatVersion());
    }
}
