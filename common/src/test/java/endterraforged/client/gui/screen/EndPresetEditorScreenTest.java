package endterraforged.client.gui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.ContinentBandsConfig;
import endterraforged.world.config.ContinentConfig;
import endterraforged.world.config.ContinentConfigBuilder;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetBuilder;

class EndPresetEditorScreenTest {

    @Test
    void legacyContinentEditPreservesLegacyFormat() {
        EndPresetBuilder builder = new EndPresetBuilder(legacyPreset());
        ContinentConfig legacyConfig = new ContinentConfigBuilder(builder.continentConfig())
                .continentBands(ContinentBandsConfig.LEGACY_PASSTHROUGH)
                .build();

        EndPresetEditorScreen.applyContinentConfig(builder, legacyConfig);

        assertEquals(2, builder.build().formatVersion());
    }

    @Test
    void enablingContinentBandsPromotesPresetToCurrentFormat() {
        EndPresetBuilder builder = new EndPresetBuilder(legacyPreset());

        EndPresetEditorScreen.applyContinentConfig(builder, ContinentConfig.defaults());

        assertEquals(EndPreset.CURRENT_FORMAT_VERSION, builder.build().formatVersion());
    }

    private static EndPreset legacyPreset() {
        EndPreset defaults = EndPreset.defaults();
        ContinentConfig legacyConfig = new ContinentConfigBuilder(defaults.continentConfig())
                .continentBands(ContinentBandsConfig.LEGACY_PASSTHROUGH)
                .build();
        return new EndPreset(defaults.worldHeight(), defaults.minY(), defaults.seaLevelY(),
                defaults.islandBaselineY(), defaults.seaMode(), defaults.topologyMode(),
                defaults.floatingIslandsEnabled(), legacyConfig, defaults.terrainConfig(),
                defaults.climateConfig(), defaults.biomeLayoutConfig(), defaults.subsurfaceConfig(),
                defaults.erosionConfig(), 2);
    }
}
