package endterraforged.world.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;

import endterraforged.world.config.AbyssPitConfig;
import endterraforged.world.config.BiomeLayoutConfig;
import endterraforged.world.config.BiomeVariantBlendConfig;
import endterraforged.world.config.CaveChamberConfig;
import endterraforged.world.config.CaveNetworkConfig;
import endterraforged.world.config.CaveSystemConfig;
import endterraforged.world.config.CaveTunnelConfig;
import endterraforged.world.config.ClimateConfig;
import endterraforged.world.config.ContinentCoastShape;
import endterraforged.world.config.ContinentAlgorithm;
import endterraforged.world.config.ContinentBandsConfig;
import endterraforged.world.config.ContinentConfig;
import endterraforged.world.config.ContinentConfigBuilder;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetBuilder;
import endterraforged.world.config.SubsurfaceConfig;
import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.config.TerrainShape;
import endterraforged.world.config.TopologyMode;
import endterraforged.world.filter.ErosionConfig;
import endterraforged.world.heightmap.EndTerrainLayer;
import endterraforged.world.noise.DistanceFunction;

class TerrainPreviewSamplerTest {

    @Test
    void sampleProducesSquareArgbRaster() {
        TerrainPreview preview = TerrainPreviewSampler.sample(EndPreset.defaults(), 1, 16, 32);
        assertEquals(16, preview.size());
        assertEquals(16 * 16, preview.colors().length);
        assertTrue(preview.layerStats().total() > 0);
        for (int color : preview.colors()) {
            assertEquals(0xFF000000, color & 0xFF000000);
        }
    }

    @Test
    void completeContinentPreviewDiffersFromIslands() {
        EndPreset complete = new EndPresetBuilder(EndPreset.defaults())
                .topologyMode(TopologyMode.CONTINENTAL)
                .build();
        TerrainPreview islandsPreview = TerrainPreviewSampler.sample(EndPreset.defaults(), 1, 24, 32);
        TerrainPreview completePreview = TerrainPreviewSampler.sample(complete, 1, 24, 32);
        assertNotEquals(signature(islandsPreview), signature(completePreview));
    }

    @Test
    void outerContinentsPreviewUsesARepresentativeExteriorWindow() {
        EndPreset outer = new EndPresetBuilder(EndPreset.defaults())
                .topologyMode(TopologyMode.OUTER_CONTINENTS)
                .build();

        TerrainPreview first = TerrainPreviewSampler.sample(outer, 7, 48, 32,
                TerrainPreviewMode.LANDNESS);
        TerrainPreview second = TerrainPreviewSampler.sample(outer, 7, 48, 32,
                TerrainPreviewMode.LANDNESS);

        assertTrue(first.layerStats().total() > 0,
                "outer-continent preview must show exterior land instead of only the protected centre");
        assertEquals(signature(first), signature(second),
                "representative exterior window selection must be deterministic");
    }

    @Test
    void outerContinentScaleChangesPreviewSignature() {
        EndPreset base = legacyOuterPreset();
        EndPreset compact = new EndPresetBuilder(base)
                .continentConfig(new ContinentConfigBuilder(base.continentConfig())
                        .outerContinentScale(2048)
                        .build())
                .build();
        EndPreset broad = new EndPresetBuilder(base)
                .continentConfig(new ContinentConfigBuilder(base.continentConfig())
                        .outerContinentScale(8192)
                        .build())
                .build();

        TerrainPreview compactPreview = TerrainPreviewSampler.sample(compact, 7, 48, 32,
                TerrainPreviewMode.LANDNESS);
        TerrainPreview broadPreview = TerrainPreviewSampler.sample(broad, 7, 48, 32,
                TerrainPreviewMode.LANDNESS);

        assertNotEquals(signature(compactPreview), signature(broadPreview),
                "outer_continent_scale must change the live landness preview");
    }

    @Test
    void organicCoastChangesOuterLandnessPreviewSignature() {
        EndPreset organic = legacyOuterPreset();
        EndPreset radialLegacy = new EndPresetBuilder(organic)
                .continentConfig(new ContinentConfigBuilder(organic.continentConfig())
                        .coastShape(ContinentCoastShape.RADIAL_LEGACY)
                        .build())
                .build();

        TerrainPreview organicPreview = TerrainPreviewSampler.sampleForViewport(organic, 96,
                TerrainPreviewSettings.DEFAULT.withMode(TerrainPreviewMode.LANDNESS)
                        .withScale(TerrainPreviewScale.WIDE));
        TerrainPreview radialPreview = TerrainPreviewSampler.sampleForViewport(radialLegacy, 96,
                TerrainPreviewSettings.DEFAULT.withMode(TerrainPreviewMode.LANDNESS)
                        .withScale(TerrainPreviewScale.WIDE));

        assertNotEquals(signature(radialPreview), signature(organicPreview),
                "organic coast settings must change the live outer-continent preview");
    }

    @Test
    void rtfBandsChangeTheLiveLandnessAndHeightPreview() {
        ContinentConfig activeConfig = new ContinentConfigBuilder(ContinentConfig.defaults())
                .continentScale(3000)
                .continentJitter(0.7F)
                .continentAlgorithm(ContinentAlgorithm.RTF_MULTI)
                .build();
        EndPreset active = new EndPresetBuilder(EndPreset.defaults())
                .continentConfig(activeConfig)
                .build();
        ContinentConfig legacyConfig = new ContinentConfigBuilder(activeConfig)
                .continentBands(ContinentBandsConfig.LEGACY_PASSTHROUGH)
                .build();
        EndPreset defaults = EndPreset.defaults();
        EndPreset legacy = new EndPreset(defaults.worldHeight(), defaults.minY(), defaults.seaLevelY(),
                defaults.islandBaselineY(), defaults.seaMode(), defaults.topologyMode(),
                defaults.floatingIslandsEnabled(), legacyConfig, defaults.terrainConfig(),
                defaults.climateConfig(), defaults.biomeLayoutConfig(), defaults.subsurfaceConfig(),
                defaults.erosionConfig(), 2);

        TerrainPreviewSettings landness = TerrainPreviewSettings.DEFAULT
                .withMode(TerrainPreviewMode.LANDNESS)
                .withScale(TerrainPreviewScale.WIDE);
        TerrainPreviewSettings height = TerrainPreviewSettings.DEFAULT
                .withMode(TerrainPreviewMode.HEIGHT)
                .withScale(TerrainPreviewScale.WIDE);

        assertNotEquals(signature(TerrainPreviewSampler.sampleForViewport(legacy, 96, landness)),
                signature(TerrainPreviewSampler.sampleForViewport(active, 96, landness)),
                "preview landness must use the same R2 signal mapping as runtime");
        assertNotEquals(signature(TerrainPreviewSampler.sampleForViewport(legacy, 96, height)),
                signature(TerrainPreviewSampler.sampleForViewport(active, 96, height)),
                "preview height must consume inland relief rather than only serialising the bands");
    }

    @Test
    void volumeModeReflectsShelfThickness() {
        EndPreset thin = new EndPresetBuilder(EndPreset.defaults())
                .continentConfig(new ContinentConfigBuilder(EndPreset.defaults().continentConfig())
                        .shelfThickness(64)
                        .shelfEdgeThickness(32)
                        .build())
                .build();
        EndPreset thick = new EndPresetBuilder(EndPreset.defaults())
                .continentConfig(new ContinentConfigBuilder(EndPreset.defaults().continentConfig())
                        .shelfThickness(384)
                        .shelfEdgeThickness(96)
                        .build())
                .build();

        TerrainPreview thinPreview = TerrainPreviewSampler.sample(thin, 7, 48, 32,
                TerrainPreviewMode.VOLUME);
        TerrainPreview thickPreview = TerrainPreviewSampler.sample(thick, 7, 48, 32,
                TerrainPreviewMode.VOLUME);

        assertNotEquals(signature(thinPreview), signature(thickPreview),
                "volume preview must use the same shelf thickness as runtime density");
    }

    @Test
    void volumeModeDoesNotChangeExistingHeightPreview() {
        EndPreset preset = EndPreset.defaults();
        TerrainPreview heightBefore = TerrainPreviewSampler.sample(preset, 7, 48, 32,
                TerrainPreviewMode.HEIGHT);
        TerrainPreview volume = TerrainPreviewSampler.sample(preset, 7, 48, 32,
                TerrainPreviewMode.VOLUME);
        TerrainPreview heightAfter = TerrainPreviewSampler.sample(preset, 7, 48, 32,
                TerrainPreviewMode.HEIGHT);

        assertNotEquals(signature(heightBefore), signature(volume));
        assertEquals(signature(heightBefore), signature(heightAfter));
    }

    @Test
    void continentConfigChangesPreview() {
        EndPreset tuned = new EndPresetBuilder(EndPreset.defaults())
                .continentConfig(new ContinentConfig(1024, 800, DistanceFunction.EUCLIDEAN,
                        1.0F, 0.2F, 0.25F, 5, 0.26F, 4.33F,
                        1.0F, 0.85F, 0.6F, 0.6F, 0.85F, 300, 40.0F))
                .build();
        TerrainPreview defaultsPreview = TerrainPreviewSampler.sample(EndPreset.defaults(), 1, 24, 32);
        TerrainPreview tunedPreview = TerrainPreviewSampler.sample(tuned, 1, 24, 32);
        assertNotEquals(signature(defaultsPreview), signature(tunedPreview));
    }

    @Test
    void auxiliaryLayerChangesPreviewSignature() {
        TerrainLayerConfig plains = new TerrainLayerConfig(1.0F, 1.0F, 1.0F, 1.0F);
        EndPreset withPlains = new EndPresetBuilder(EndPreset.defaults())
                .topologyMode(TopologyMode.CONTINENTAL)
                .terrainConfig(new TerrainConfig(0, 1200, 1.0F, 1.0F,
                        TerrainShape.SHATTERED_RIDGES,
                        plains, TerrainLayerConfig.DISABLED, TerrainLayerConfig.DISABLED,
                        TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED))
                .build();
        EndPreset withoutAuxiliary = new EndPresetBuilder(withPlains)
                .terrainConfig(new TerrainConfig(0, 1200, 1.0F, 1.0F,
                        TerrainShape.SHATTERED_RIDGES,
                        TerrainLayerConfig.DISABLED, TerrainLayerConfig.DISABLED,
                        TerrainLayerConfig.DISABLED, TerrainLayerConfig.DEFAULT,
                        TerrainLayerConfig.DISABLED))
                .build();

        TerrainPreview plainsPreview = TerrainPreviewSampler.sample(withPlains, 1, 24, 32);
        TerrainPreview basePreview = TerrainPreviewSampler.sample(withoutAuxiliary, 1, 24, 32);
        TerrainPreview explicitCombined = TerrainPreviewSampler.sample(withPlains, 1, 24, 32,
                TerrainPreviewMode.COMBINED);
        TerrainPreview heightPreview = TerrainPreviewSampler.sample(withPlains, 1, 24, 32,
                TerrainPreviewMode.HEIGHT);
        TerrainPreview landnessPreview = TerrainPreviewSampler.sample(withPlains, 1, 24, 32,
                TerrainPreviewMode.LANDNESS);
        TerrainPreview layersPreview = TerrainPreviewSampler.sample(withPlains, 1, 24, 32,
                TerrainPreviewMode.LAYERS);

        assertNotEquals(signature(basePreview), signature(plainsPreview));
        assertEquals(signature(plainsPreview), signature(explicitCombined));
        assertNotEquals(signature(heightPreview), signature(plainsPreview));
        assertNotEquals(signature(heightPreview), signature(landnessPreview));
        assertNotEquals(signature(heightPreview), signature(layersPreview));
        assertTrue(plainsPreview.layerStats().count(EndTerrainLayer.PLAINS) > 0);
        assertTrue(plainsPreview.layerStats().hasAuxiliaryLayers());
        assertEquals(0, basePreview.layerStats().count(EndTerrainLayer.PLAINS));
    }

    @Test
    void terrainBlendRangeChangesLayerPreviewSignature() {
        EndPreset hardBoundaries = auxiliaryBlendPreset(0.0F);
        EndPreset smoothBoundaries = auxiliaryBlendPreset(1.0F);

        boolean foundDifferentPreview = false;
        TerrainPreview differentPreview = null;
        for (int seed = 1; seed <= 8; seed++) {
            TerrainPreview hard = TerrainPreviewSampler.sample(hardBoundaries, seed, 48, 16,
                    TerrainPreviewMode.LAYERS);
            TerrainPreview smooth = TerrainPreviewSampler.sample(smoothBoundaries, seed, 48, 16,
                    TerrainPreviewMode.LAYERS);
            if (signature(hard) != signature(smooth)) {
                foundDifferentPreview = true;
                differentPreview = smooth;
                break;
            }
        }

        assertTrue(foundDifferentPreview,
                "layer preview should show smoothed colors when terrain blending is enabled");
        assertTrue(differentPreview.layerStats().hasBlendedSamples(),
                "preview stats should report visible smoothed terrain-layer samples");
    }

    @Test
    void regionPlannedRidgesChangeHeightAndLayerPreviews() {
        EndPreset defaults = EndPreset.defaults();
        TerrainConfig ridgeTerrain = new TerrainConfig(
                0, 1600, 1.0F, 1.0F, 0.0F,
                endterraforged.world.config.TerrainLayoutMode.REGION_PLANNED,
                TerrainShape.SHATTERED_RIDGES,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED);
        TerrainConfig areasOnlyTerrain = new TerrainConfig(
                0, 1600, 1.0F, 1.0F, 0.0F,
                endterraforged.world.config.TerrainLayoutMode.REGION_PLANNED,
                TerrainShape.SHATTERED_RIDGES,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DISABLED, TerrainLayerConfig.DISABLED);
        EndPreset ridges = new EndPreset(
                defaults.worldHeight(), defaults.minY(), defaults.seaLevelY(), defaults.islandBaselineY(),
                defaults.seaMode(), defaults.topologyMode(), defaults.floatingIslandsEnabled(),
                defaults.continentConfig(), ridgeTerrain, defaults.climateConfig(), defaults.biomeLayoutConfig(),
                defaults.subsurfaceConfig(), defaults.erosionConfig(), 4);
        EndPreset areasOnly = new EndPreset(
                defaults.worldHeight(), defaults.minY(), defaults.seaLevelY(), defaults.islandBaselineY(),
                defaults.seaMode(), defaults.topologyMode(), defaults.floatingIslandsEnabled(),
                defaults.continentConfig(), areasOnlyTerrain, defaults.climateConfig(), defaults.biomeLayoutConfig(),
                defaults.subsurfaceConfig(), defaults.erosionConfig(), 4);

        TerrainPreview ridgeLayers = TerrainPreviewSampler.sample(ridges, 17, 96, 32, TerrainPreviewMode.LAYERS);
        TerrainPreview ridgeHeight = TerrainPreviewSampler.sample(ridges, 17, 96, 32, TerrainPreviewMode.HEIGHT);
        TerrainPreview areaHeight = TerrainPreviewSampler.sample(areasOnly, 17, 96, 32, TerrainPreviewMode.HEIGHT);

        assertTrue(ridgeLayers.layerStats().count(EndTerrainLayer.MOUNTAINS) > 0,
                "layer preview must consume the bounded ridge runtime");
        assertNotEquals(signature(ridgeHeight), signature(areaHeight),
                "height preview must use the same ridge runtime as worldgen");
    }

    @Test
    void previewRejectsTheFrozenRegionPlannedVolcanoDraft() {
        EndPreset defaults = EndPreset.defaults();
        TerrainConfig volcanoTerrain = new TerrainConfig(
                0, 1600, 1.0F, 1.0F, 0.0F,
                endterraforged.world.config.TerrainLayoutMode.REGION_PLANNED,
                TerrainShape.SHATTERED_RIDGES,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DISABLED, TerrainLayerConfig.DEFAULT);
        EndPreset volcanoes = new EndPreset(
                defaults.worldHeight(), defaults.minY(), defaults.seaLevelY(), defaults.islandBaselineY(),
                defaults.seaMode(), TopologyMode.CONTINENTAL, defaults.floatingIslandsEnabled(),
                defaults.continentConfig(), volcanoTerrain, defaults.climateConfig(), defaults.biomeLayoutConfig(),
                defaults.subsurfaceConfig(), defaults.erosionConfig(), 4);

        assertThrows(IllegalArgumentException.class, () ->
                TerrainPreviewSampler.sample(volcanoes, 1, 96, 32, TerrainPreviewMode.LAYERS));
    }

    @Test
    void abyssModeReflectsSubsurfaceAbyssParameters() {
        EndPreset disabled = EndPreset.defaults();
        EndPreset enabled = new EndPresetBuilder(EndPreset.defaults())
                .subsurfaceConfig(new SubsurfaceConfig(
                        new AbyssPitConfig(true, 1600, 96, 0.1F, 0.2F, 512, 0.0F)))
                .build();

        boolean foundDifferentPreview = false;
        for (int seed = 1; seed <= 8; seed++) {
            TerrainPreview base = TerrainPreviewSampler.sample(disabled, seed, 48, 16,
                    TerrainPreviewMode.ABYSS);
            TerrainPreview abyss = TerrainPreviewSampler.sample(enabled, seed, 48, 16,
                    TerrainPreviewMode.ABYSS);
            if (signature(base) != signature(abyss)) {
                foundDifferentPreview = true;
                break;
            }
        }

        assertTrue(foundDifferentPreview,
                "abyss preview must reflect configured subsurface abyss parameters");
    }

    @Test
    void abyssModeDoesNotChangeExistingModeSamples() {
        EndPreset preset = new EndPresetBuilder(EndPreset.defaults())
                .topologyMode(TopologyMode.CONTINENTAL_SHATTERED)
                .build();
        TerrainPreview combinedBefore = TerrainPreviewSampler.sample(preset, 4, 40, 24,
                TerrainPreviewMode.COMBINED);
        TerrainPreview heightBefore = TerrainPreviewSampler.sample(preset, 4, 40, 24,
                TerrainPreviewMode.HEIGHT);

        TerrainPreview abyss = TerrainPreviewSampler.sample(preset, 4, 40, 24,
                TerrainPreviewMode.ABYSS);
        TerrainPreview combinedAfter = TerrainPreviewSampler.sample(preset, 4, 40, 24,
                TerrainPreviewMode.COMBINED);
        TerrainPreview heightAfter = TerrainPreviewSampler.sample(preset, 4, 40, 24,
                TerrainPreviewMode.HEIGHT);

        assertNotEquals(signature(combinedBefore), signature(abyss));
        assertEquals(signature(combinedBefore), signature(combinedAfter));
        assertEquals(signature(heightBefore), signature(heightAfter));
    }

    @Test
    void cavesModeProducesArgbRaster() {
        TerrainPreview preview = TerrainPreviewSampler.sample(EndPreset.defaults(), 7, 32, 32,
                TerrainPreviewMode.CAVES);

        assertEquals(32, preview.size());
        for (int color : preview.colors()) {
            assertEquals(0xFF000000, color & 0xFF000000);
        }
    }

    @Test
    void caveSubModesProduceDistinctMaskRasters() {
        EndPreset preset = new EndPresetBuilder(EndPreset.defaults())
                .subsurfaceConfig(caveConfig(true, 0.9F, 0.9F, 0.95F))
                .build();

        TerrainPreview chambers = TerrainPreviewSampler.sample(preset, 4, 48, 48,
                TerrainPreviewMode.CAVE_CHAMBERS);
        TerrainPreview network = TerrainPreviewSampler.sample(preset, 4, 48, 48,
                TerrainPreviewMode.CAVE_NETWORK);

        assertArgbRaster(chambers);
        assertArgbRaster(network);
        assertNotEquals(signature(chambers), signature(network),
                "chamber and network cave preview modes must expose separate masks");
    }

    @Test
    void caveGraphSubModesProduceDistinctMaskRasters() {
        EndPreset preset = new EndPresetBuilder(EndPreset.defaults())
                .subsurfaceConfig(caveConfig(true, 0.95F, 0.95F, 1.0F))
                .build();

        TerrainPreview rifts = TerrainPreviewSampler.sample(preset, 4, 48, 48,
                TerrainPreviewMode.CAVE_RIFTS);
        TerrainPreview flows = TerrainPreviewSampler.sample(preset, 4, 48, 48,
                TerrainPreviewMode.CAVE_FLOWS);

        assertArgbRaster(rifts);
        assertArgbRaster(flows);
        assertNotEquals(signature(rifts), signature(flows),
                "rift and flow cave preview modes must expose separate graph masks");
    }

    @Test
    void caveLiquidCandidateModesProduceDistinctMaskRasters() {
        EndPreset preset = new EndPresetBuilder(EndPreset.defaults())
                .subsurfaceConfig(caveConfig(true, 0.95F, 0.95F, 1.0F))
                .build();

        TerrainPreview water = TerrainPreviewSampler.sample(preset, 4, 48, 48,
                TerrainPreviewMode.CAVE_WATER);
        TerrainPreview lava = TerrainPreviewSampler.sample(preset, 4, 48, 48,
                TerrainPreviewMode.CAVE_LAVA);
        TerrainPreview flows = TerrainPreviewSampler.sample(preset, 4, 48, 48,
                TerrainPreviewMode.CAVE_FLOWS);

        assertArgbRaster(water);
        assertArgbRaster(lava);
        assertNotEquals(signature(water), signature(lava),
                "water and lava candidate modes must expose separate graph-derived masks");
        assertNotEquals(signature(water), signature(flows),
                "liquid candidate previews should add classification over raw flow channels");
    }

    @Test
    void caveDepthModeProducesArgbRaster() {
        EndPreset preset = new EndPresetBuilder(EndPreset.defaults())
                .subsurfaceConfig(caveConfig(true, 0.95F, 0.95F, 1.0F))
                .build();

        TerrainPreview depth = TerrainPreviewSampler.sample(preset, 4, 48, 48,
                TerrainPreviewMode.CAVE_DEPTH);

        assertArgbRaster(depth);
    }

    @Test
    void caveDepthModeReflectsThreeDimensionalCaveParameters() {
        EndPreset disabled = EndPreset.defaults();
        EndPreset enabled = new EndPresetBuilder(EndPreset.defaults())
                .subsurfaceConfig(caveConfig(true, 0.95F, 0.95F, 1.0F))
                .build();

        boolean foundDifferentPreview = false;
        for (int seed = 1; seed <= 8; seed++) {
            TerrainPreview base = TerrainPreviewSampler.sample(disabled, seed, 48, 48,
                    TerrainPreviewMode.CAVE_DEPTH);
            TerrainPreview depth = TerrainPreviewSampler.sample(enabled, seed, 48, 48,
                    TerrainPreviewMode.CAVE_DEPTH);
            if (signature(base) != signature(depth)) {
                foundDifferentPreview = true;
                break;
            }
        }

        assertTrue(foundDifferentPreview,
                "cave depth preview must reflect configured 3D cave field parameters");
    }

    @Test
    void cavesModeReflectsCaveParameters() {
        EndPreset disabled = EndPreset.defaults();
        EndPreset enabled = new EndPresetBuilder(EndPreset.defaults())
                .subsurfaceConfig(caveConfig(true, 0.9F, 0.9F, 0.95F))
                .build();

        boolean foundDifferentPreview = false;
        for (int seed = 1; seed <= 8; seed++) {
            TerrainPreview base = TerrainPreviewSampler.sample(disabled, seed, 48, 48,
                    TerrainPreviewMode.CAVES);
            TerrainPreview caves = TerrainPreviewSampler.sample(enabled, seed, 48, 48,
                    TerrainPreviewMode.CAVES);
            if (signature(base) != signature(caves)) {
                foundDifferentPreview = true;
                break;
            }
        }

        assertTrue(foundDifferentPreview,
                "caves preview must reflect configured cave system parameters");
    }

    @Test
    void caveParameterTuningChangesCavesPreviewSignature() {
        EndPreset sparse = new EndPresetBuilder(EndPreset.defaults())
                .subsurfaceConfig(caveConfig(true, 0.15F, 0.15F, 0.1F))
                .build();
        EndPreset spectacular = new EndPresetBuilder(EndPreset.defaults())
                .subsurfaceConfig(caveConfig(true, 0.9F, 0.9F, 0.95F))
                .build();

        TerrainPreview sparsePreview = TerrainPreviewSampler.sample(sparse, 4, 48, 48,
                TerrainPreviewMode.CAVES);
        TerrainPreview spectacularPreview = TerrainPreviewSampler.sample(spectacular, 4, 48, 48,
                TerrainPreviewMode.CAVES);

        assertNotEquals(signature(sparsePreview), signature(spectacularPreview),
                "caves preview must react to chamber/network/spectacle tuning");
    }

    @Test
    void cavesModeDoesNotChangeExistingModeSamples() {
        EndPreset preset = new EndPresetBuilder(EndPreset.defaults())
                .topologyMode(TopologyMode.CONTINENTAL_SHATTERED)
                .subsurfaceConfig(caveConfig(true, 0.9F, 0.9F, 0.95F))
                .build();
        TerrainPreview combinedBefore = TerrainPreviewSampler.sample(preset, 4, 40, 24,
                TerrainPreviewMode.COMBINED);
        TerrainPreview heightBefore = TerrainPreviewSampler.sample(preset, 4, 40, 24,
                TerrainPreviewMode.HEIGHT);
        TerrainPreview abyssBefore = TerrainPreviewSampler.sample(preset, 4, 40, 24,
                TerrainPreviewMode.ABYSS);

        TerrainPreview caves = TerrainPreviewSampler.sample(preset, 4, 40, 24,
                TerrainPreviewMode.CAVES);
        TerrainPreview caveDepth = TerrainPreviewSampler.sample(preset, 4, 40, 24,
                TerrainPreviewMode.CAVE_DEPTH);
        TerrainPreview combinedAfter = TerrainPreviewSampler.sample(preset, 4, 40, 24,
                TerrainPreviewMode.COMBINED);
        TerrainPreview heightAfter = TerrainPreviewSampler.sample(preset, 4, 40, 24,
                TerrainPreviewMode.HEIGHT);
        TerrainPreview abyssAfter = TerrainPreviewSampler.sample(preset, 4, 40, 24,
                TerrainPreviewMode.ABYSS);

        assertNotEquals(signature(combinedBefore), signature(caves));
        assertNotEquals(signature(combinedBefore), signature(caveDepth));
        assertEquals(signature(combinedBefore), signature(combinedAfter));
        assertEquals(signature(heightBefore), signature(heightAfter));
        assertEquals(signature(abyssBefore), signature(abyssAfter));
    }

    @Test
    void climateConfigChangesTemperaturePreviewSignature() {
        EndPreset defaultClimate = EndPreset.defaults();
        EndPreset broadClimate = new EndPresetBuilder(EndPreset.defaults())
                .climateConfig(new ClimateConfig(8000.0F, 900, 1200, 1500, 0.0F))
                .build();

        TerrainPreview defaultPreview = TerrainPreviewSampler.sample(defaultClimate, 1, 32, 64,
                TerrainPreviewMode.TEMPERATURE);
        TerrainPreview broadPreview = TerrainPreviewSampler.sample(broadClimate, 1, 32, 64,
                TerrainPreviewMode.TEMPERATURE);

        assertNotEquals(signature(defaultPreview), signature(broadPreview),
                "temperature preview must reflect preset climate settings");
    }

    @Test
    void climateRangeConfigChangesMoisturePreviewSignature() {
        EndPreset defaultClimate = EndPreset.defaults();
        EndPreset narrowMoisture = new EndPresetBuilder(EndPreset.defaults())
                .climateConfig(new ClimateConfig(4000.0F, 600, 800, 1000, 0.25F,
                        0.0F, 1.0F, 0.0F,
                        0.65F, 0.8F, 0.0F))
                .build();

        TerrainPreview defaultPreview = TerrainPreviewSampler.sample(defaultClimate, 1, 32, 64,
                TerrainPreviewMode.MOISTURE);
        TerrainPreview narrowPreview = TerrainPreviewSampler.sample(narrowMoisture, 1, 32, 64,
                TerrainPreviewMode.MOISTURE);

        assertNotEquals(signature(defaultPreview), signature(narrowPreview),
                "moisture preview must reflect configured climate range clamps");
    }

    @Test
    void climateFalloffChangesTemperaturePreviewSignature() {
        EndPreset defaultClimate = EndPreset.defaults();
        EndPreset curvedTemperature = new EndPresetBuilder(EndPreset.defaults())
                .climateConfig(new ClimateConfig(4000.0F, 600, 800, 1000, 0.25F,
                        4.0F, 0.0F, 1.0F, 0.0F,
                        1.0F, 0.0F, 1.0F, 0.0F))
                .build();

        TerrainPreview defaultPreview = TerrainPreviewSampler.sample(defaultClimate, 1, 32, 64,
                TerrainPreviewMode.TEMPERATURE);
        TerrainPreview curvedPreview = TerrainPreviewSampler.sample(curvedTemperature, 1, 32, 64,
                TerrainPreviewMode.TEMPERATURE);

        assertNotEquals(signature(defaultPreview), signature(curvedPreview),
                "temperature preview must reflect configured climate falloff");
    }

    @Test
    void erosionConfigChangesHeightPreviewSignature() {
        EndPreset base = new EndPresetBuilder(EndPreset.defaults())
                .topologyMode(TopologyMode.CONTINENTAL)
                .erosionConfig(new ErosionConfig(0, 48, 1.0F, 1.0F, 0.5F, 0.5F))
                .build();
        EndPreset eroded = new EndPresetBuilder(base)
                .erosionConfig(new ErosionConfig(256, 48, 1.0F, 1.0F, 0.85F, 0.35F))
                .build();

        TerrainPreview basePreview = TerrainPreviewSampler.sample(base, 3, 48, 16,
                TerrainPreviewMode.HEIGHT);
        TerrainPreview erodedPreview = TerrainPreviewSampler.sample(eroded, 3, 48, 16,
                TerrainPreviewMode.HEIGHT);

        assertNotEquals(signature(basePreview), signature(erodedPreview),
                "height preview must reflect erosion droplet settings");
    }

    @Test
    void biomeModeProducesArgbRaster() {
        TerrainPreview preview = TerrainPreviewSampler.sample(EndPreset.defaults(), 7, 32, 32,
                TerrainPreviewMode.BIOMES);

        assertEquals(32, preview.size());
        assertArgbRaster(preview);
    }

    @Test
    void biomeClimateModeProducesArgbRaster() {
        TerrainPreview preview = TerrainPreviewSampler.sample(EndPreset.defaults(), 7, 32, 32,
                TerrainPreviewMode.BIOME_CLIMATE);

        assertEquals(32, preview.size());
        assertArgbRaster(preview);
    }

    @Test
    void biomePreviewUsesVanillaQuartCoordinateConversion() {
        float[] worldCoordinates = {
                -8.1F, -8.0F, -7.9F, -4.1F, -4.0F, -3.9F,
                -0.1F, 0.0F, 0.1F, 3.9F, 4.0F, 4.1F,
                7.9F, 8.0F, 8.1F
        };

        for (float world : worldCoordinates) {
            assertEquals(QuartPos.fromBlock(Mth.floor(world)),
                    BiomePreviewLayout.biomeCoordinate(world),
                    "BIOMES preview must match runtime block-to-quart conversion");
        }
    }

    @Test
    void biomeLayoutConfigChangesBiomePreviewSignature() {
        EndPreset preset = EndPreset.defaults();
        EndPreset compact = new EndPresetBuilder(preset)
                .biomeLayoutConfig(new BiomeLayoutConfig(10, 16.0F, 30.0F, -10.0F,
                        80, 2, 2.0F, 0.5F, 5.0F,
                        100, 0.0F,
                        100, 2, 0.8F))
                .build();
        EndPreset broad = new EndPresetBuilder(preset)
                .biomeLayoutConfig(new BiomeLayoutConfig(50, 2.0F, 50.0F, 10.0F,
                        400, 5, 2.0F, 0.5F, 80.0F,
                        300, 0.0F,
                        400, 5, -0.8F))
                .build();
        TerrainPreview compactPreview = TerrainPreviewSampler.sample(compact, 7, 48, 32,
                TerrainPreviewMode.BIOMES);
        TerrainPreview broadPreview = TerrainPreviewSampler.sample(broad, 7, 48, 32,
                TerrainPreviewMode.BIOMES);

        assertNotEquals(signature(compactPreview), signature(broadPreview),
                "biome preview must reflect EndPreset.biomeLayoutConfig()");
    }

    @Test
    void biomeClimateModeReflectsClimateRangeSettings() {
        EndPreset defaultClimate = EndPreset.defaults();
        EndPreset hotWetClimate = new EndPresetBuilder(EndPreset.defaults())
                .climateConfig(new ClimateConfig(4000.0F, 600, 800, 1000, 0.25F,
                        1.0F, 0.85F, 1.0F, 0.0F,
                        1.0F, 0.85F, 1.0F, 0.0F))
                .build();

        TerrainPreview defaultPreview = TerrainPreviewSampler.sample(defaultClimate, 2, 48, 32,
                TerrainPreviewMode.BIOME_CLIMATE);
        TerrainPreview hotWetPreview = TerrainPreviewSampler.sample(hotWetClimate, 2, 48, 32,
                TerrainPreviewMode.BIOME_CLIMATE);

        assertNotEquals(signature(defaultPreview), signature(hotWetPreview),
                "biome climate preview must reflect climate range settings");
    }

    @Test
    void biomeClimateModeDoesNotChangeBiomeRingPreview() {
        EndPreset preset = new EndPresetBuilder(EndPreset.defaults())
                .climateConfig(new ClimateConfig(4000.0F, 600, 800, 1000, 0.25F,
                        1.0F, 0.85F, 1.0F, 0.0F,
                        1.0F, 0.85F, 1.0F, 0.0F))
                .build();
        TerrainPreview biomesBefore = TerrainPreviewSampler.sample(preset, 2, 48, 32,
                TerrainPreviewMode.BIOMES);

        TerrainPreview biomeClimate = TerrainPreviewSampler.sample(preset, 2, 48, 32,
                TerrainPreviewMode.BIOME_CLIMATE);
        TerrainPreview biomesAfter = TerrainPreviewSampler.sample(preset, 2, 48, 32,
                TerrainPreviewMode.BIOMES);

        assertNotEquals(signature(biomesBefore), signature(biomeClimate));
        assertEquals(signature(biomesBefore), signature(biomesAfter));
    }

    @Test
    void biomeWarpConfigChangesBiomePreviewSignature() {
        EndPreset preset = EndPreset.defaults();
        EndPreset warped = new EndPresetBuilder(preset)
                .biomeLayoutConfig(new BiomeLayoutConfig(18, 8.0F, 40.0F, 0.0F,
                        200, 4, 2.0F, 0.5F, 15.0F,
                        80, 80.0F,
                        400, 4, 0.2F))
                .build();

        TerrainPreview defaultPreview = TerrainPreviewSampler.sample(preset, 7, 48, 32,
                TerrainPreviewMode.BIOMES);
        TerrainPreview warpedPreview = TerrainPreviewSampler.sample(warped, 7, 48, 32,
                TerrainPreviewMode.BIOMES);

        assertNotEquals(signature(defaultPreview), signature(warpedPreview),
                "biome preview must reflect configured biome warp");
    }

    @Test
    void variantBlendConfigChangesBiomePreviewSignature() {
        EndPreset preset = EndPreset.defaults();
        EndPreset coarseBlend = new EndPresetBuilder(preset)
                .biomeLayoutConfig(new BiomeLayoutConfig(18, 8.0F, 40.0F, 0.0F,
                        200, 4, 2.0F, 0.5F, 15.0F,
                        300, 0.0F,
                        400, 4, 0.2F,
                        new BiomeVariantBlendConfig(180, 1)))
                .build();

        TerrainPreview defaultPreview = TerrainPreviewSampler.sample(preset, 7, 48, 32,
                TerrainPreviewMode.BIOMES);
        TerrainPreview coarsePreview = TerrainPreviewSampler.sample(coarseBlend, 7, 48, 32,
                TerrainPreviewMode.BIOMES);

        assertNotEquals(signature(defaultPreview), signature(coarsePreview),
                "biome preview must reflect configured variant blend noise");
    }

    @Test
    void layerTintChangesLandColorButNotVoidColor() {
        int base = TerrainPreviewPalette.color(1.0F, 0.5F, EndTerrainLayer.NONE);
        int plains = TerrainPreviewPalette.color(1.0F, 0.5F, EndTerrainLayer.PLAINS);
        int volcano = TerrainPreviewPalette.color(1.0F, 0.5F, EndTerrainLayer.VOLCANO);

        assertNotEquals(base, plains);
        assertNotEquals(plains, volcano);
        assertEquals(base, TerrainPreviewPalette.color(1.0F, 0.5F,
                EndTerrainLayer.VOLCANO, TerrainPreviewMode.HEIGHT));
        assertEquals(TerrainPreviewPalette.layerColor(EndTerrainLayer.VOLCANO),
                TerrainPreviewPalette.color(1.0F, 0.5F,
                        EndTerrainLayer.VOLCANO, TerrainPreviewMode.LAYERS));
        assertEquals(
                TerrainPreviewPalette.color(0.0F, 0.5F, EndTerrainLayer.NONE),
                TerrainPreviewPalette.color(0.0F, 0.5F, EndTerrainLayer.VOLCANO));
    }

    @Test
    void landnessModeIgnoresHeightAndLayer() {
        int lowLand = TerrainPreviewPalette.color(0.25F, 0.1F,
                EndTerrainLayer.NONE, TerrainPreviewMode.LANDNESS);
        int highLandLowHeight = TerrainPreviewPalette.color(1.0F, 0.1F,
                EndTerrainLayer.NONE, TerrainPreviewMode.LANDNESS);
        int highLandHighHeight = TerrainPreviewPalette.color(1.0F, 0.9F,
                EndTerrainLayer.VOLCANO, TerrainPreviewMode.LANDNESS);

        assertNotEquals(lowLand, highLandLowHeight);
        assertEquals(highLandLowHeight, highLandHighHeight);
    }

    @Test
    void invalidBlocksPerPixelFailsFast() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> TerrainPreviewSampler.sample(EndPreset.defaults(), 1, 16, 0));
        assertTrue(e.getMessage().contains("blocksPerPixel"));
    }

    @Test
    void invalidSampleSizeFailsFast() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> TerrainPreviewSampler.sample(EndPreset.defaults(), 1, 0, 16));
        assertTrue(e.getMessage().contains("size"));
    }

    @Test
    void viewportSamplingKeepsInteractiveRasterBounded() {
        assertEquals(32, TerrainPreviewSampler.sampleSizeForViewport(12));
        assertEquals(32, TerrainPreviewSampler.sampleSizeForViewport(32));
        assertEquals(64, TerrainPreviewSampler.sampleSizeForViewport(67));
        assertEquals(96, TerrainPreviewSampler.sampleSizeForViewport(150));
    }

    @Test
    void viewportSamplingKeepsRoughlyStableWorldSpan() {
        int smallSize = TerrainPreviewSampler.sampleSizeForViewport(32);
        int largeSize = TerrainPreviewSampler.sampleSizeForViewport(150);
        assertEquals(2304, smallSize * TerrainPreviewSampler.blocksPerPixelForSize(smallSize));
        assertEquals(2304, largeSize * TerrainPreviewSampler.blocksPerPixelForSize(largeSize));
    }

    @Test
    void previewScaleChangesRepresentedWorldSpan() {
        int sampleSize = TerrainPreviewSampler.sampleSizeForViewport(96);

        assertEquals(12, TerrainPreviewSampler.blocksPerPixelForSize(sampleSize, TerrainPreviewScale.CLOSE));
        assertEquals(24, TerrainPreviewSampler.blocksPerPixelForSize(sampleSize, TerrainPreviewScale.NORMAL));
        assertEquals(48, TerrainPreviewSampler.blocksPerPixelForSize(sampleSize, TerrainPreviewScale.WIDE));
    }

    @Test
    void outerWideViewportUsesAContinentSizedObservationWindow() {
        EndPreset outer = new EndPresetBuilder(EndPreset.defaults())
                .topologyMode(TopologyMode.OUTER_CONTINENTS)
                .build();
        int sampleSize = TerrainPreviewSampler.sampleSizeForViewport(96);
        int representedSpan = sampleSize * TerrainPreviewSampler.blocksPerPixelForPreset(
                outer, sampleSize, TerrainPreviewScale.WIDE);

        assertTrue(representedSpan >= outer.continentConfig().outerContinentScale() * 1.5F,
                "wide outer-continent preview must not crop the macro landmass into a small ellipse");
    }

    @Test
    void rtfMultiWideViewportCoversTwoMacroCells() {
        EndPreset base = new EndPresetBuilder(EndPreset.defaults())
                .topologyMode(TopologyMode.OUTER_CONTINENTS)
                .build();
        EndPreset rtfMulti = new EndPresetBuilder(base)
                .continentConfig(new ContinentConfigBuilder(base.continentConfig())
                        .applyRecommendedProfile(ContinentAlgorithm.RTF_MULTI)
                        .build())
                .build();
        int sampleSize = TerrainPreviewSampler.sampleSizeForViewport(96);
        int representedSpan = sampleSize * TerrainPreviewSampler.blocksPerPixelForPreset(
                rtfMulti, sampleSize, TerrainPreviewScale.WIDE);

        assertTrue(representedSpan >= rtfMulti.continentConfig().continentScale() * 8,
                "RTF multi wide preview must show more than one macro-continent cell");
    }

    @Test
    void viewportSamplingUsesPreviewSettings() {
        EndPreset complete = new EndPresetBuilder(EndPreset.defaults())
                .topologyMode(TopologyMode.CONTINENTAL)
                .build();
        TerrainPreview close = TerrainPreviewSampler.sampleForViewport(complete, 96,
                TerrainPreviewSettings.DEFAULT.withScale(TerrainPreviewScale.CLOSE));
        TerrainPreview wide = TerrainPreviewSampler.sampleForViewport(complete, 96,
                TerrainPreviewSettings.DEFAULT.withScale(TerrainPreviewScale.WIDE));
        TerrainPreview layers = TerrainPreviewSampler.sampleForViewport(complete, 96,
                TerrainPreviewSettings.DEFAULT.withMode(TerrainPreviewMode.LAYERS));

        assertNotEquals(signature(close), signature(wide));
        assertNotEquals(signature(close), signature(layers));
    }

    @Test
    void previewDefensivelyCopiesColorArray() {
        int[] colors = {
                0xFF000001, 0xFF000002,
                0xFF000003, 0xFF000004
        };
        TerrainPreview preview = new TerrainPreview(2, colors, 0.0F, 1.0F);
        colors[0] = 0xFFFFFFFF;
        assertEquals(0xFF000001, preview.colorAt(0, 0));

        int[] exposed = preview.colors();
        exposed[1] = 0xFFFFFFFF;
        assertEquals(0xFF000002, preview.colorAt(1, 0));
    }

    @Test
    void previewRejectsOutOfBoundsCoordinates() {
        TerrainPreview preview = new TerrainPreview(2, new int[4], 0.0F, 1.0F);
        assertThrows(IndexOutOfBoundsException.class, () -> preview.colorAt(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> preview.colorAt(0, 2));
    }

    private static long signature(TerrainPreview preview) {
        long hash = 1125899906842597L;
        for (int color : preview.colors()) {
            hash = hash * 31 + color;
        }
        return hash;
    }

    private static void assertArgbRaster(TerrainPreview preview) {
        for (int color : preview.colors()) {
            assertEquals(0xFF000000, color & 0xFF000000);
        }
    }

    private static EndPreset auxiliaryBlendPreset(float terrainBlendRange) {
        TerrainLayerConfig plains = new TerrainLayerConfig(1.0F, 0.75F, 1.0F, 1.0F);
        TerrainLayerConfig hills = new TerrainLayerConfig(1.0F, 1.50F, 1.0F, 1.0F);
        return new EndPresetBuilder(EndPreset.defaults())
                .topologyMode(TopologyMode.CONTINENTAL)
                .terrainConfig(new TerrainConfig(0, 1200, 1.0F, 1.0F, terrainBlendRange,
                        TerrainShape.SHATTERED_RIDGES,
                        plains, hills, TerrainLayerConfig.DISABLED,
                        TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED))
                .build();
    }

    private static EndPreset legacyOuterPreset() {
        return new EndPresetBuilder(EndPreset.defaults())
                .topologyMode(TopologyMode.OUTER_CONTINENTS)
                .continentConfig(ContinentConfig.defaults())
                .build();
    }

    private static SubsurfaceConfig caveConfig(boolean enabled,
                                               float chamberProbability,
                                               float networkDensity,
                                               float spectacleBias) {
        return new SubsurfaceConfig(
                AbyssPitConfig.DISABLED,
                CaveTunnelConfig.DISABLED,
                new CaveSystemConfig(enabled, 2400, 128, 1536,
                        spectacleBias, 0.8F, 0.03F),
                new CaveNetworkConfig(512, networkDensity, 160,
                        3.0F, 0.5F, 0.45F, 0.0F),
                new CaveChamberConfig(chamberProbability, 64, 256,
                        1.6F, 0.35F, 0.6F));
    }

}
