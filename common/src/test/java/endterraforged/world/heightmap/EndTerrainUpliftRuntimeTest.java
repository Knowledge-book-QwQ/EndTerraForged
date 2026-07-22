package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.ContinentAlgorithm;
import endterraforged.world.config.ContinentConfig;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainLayoutMode;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.continent.ContinentSignalBuffer;

class EndTerrainUpliftRuntimeTest {

    private static final float EPSILON = 1.0E-6F;

    @Test
    void upliftPeaksAtTheCorrectedCentroidAndFallsAtTheCoast() {
        EndTerrainUpliftRuntime runtime = new EndTerrainUpliftRuntime(ContinentConfig.rtfMultiDefaults());
        ContinentSignalBuffer interior = identified(1.0F, 1.0F, 1.0F, 1200, -900);
        ContinentSignalBuffer coast = identified(0.35F, 0.35F, 0.12F, 1200, -900);

        assertEquals(1.0F, runtime.sample(1200.0F, -900.0F, interior), EPSILON);
        assertTrue(runtime.sample(1200.0F, -900.0F, interior)
                > runtime.sample(1200.0F, -900.0F, coast));
        assertEquals(0.0F, runtime.sample(1200.0F, -900.0F,
                identified(0.0F, 0.0F, 0.0F, 1200, -900)), EPSILON);
    }

    @Test
    void centroidEnvelopeIsTranslationInvariantAndBounded() {
        EndTerrainUpliftRuntime runtime = new EndTerrainUpliftRuntime(ContinentConfig.rtfMultiDefaults());
        ContinentSignalBuffer first = identified(0.8F, 0.8F, 0.9F, 1000, 2000);
        ContinentSignalBuffer translated = identified(0.8F, 0.8F, 0.9F, -7000, 9000);

        float firstValue = runtime.sample(2500.0F, 1800.0F, first);
        float translatedValue = runtime.sample(-5500.0F, 8800.0F, translated);
        assertEquals(firstValue, translatedValue, EPSILON);

        for (int x = -20000; x <= 20000; x += 257) {
            float value = runtime.sample(x, 0.0F, first);
            assertTrue(value >= 0.0F && value <= 1.0F);
        }
    }

    @Test
    void legacyAlgorithmDoesNotProduceUplift() {
        EndTerrainUpliftRuntime runtime = new EndTerrainUpliftRuntime(ContinentConfig.defaults());
        assertTrue(!runtime.enabled());
        assertEquals(0.0F, runtime.sample(0.0F, 0.0F,
                identified(1.0F, 1.0F, 1.0F, 0, 0)), EPSILON);
        assertEquals(ContinentAlgorithm.LEGACY_RADIAL, ContinentConfig.defaults().continentAlgorithm());
    }

    @Test
    void regionPlannedHeightAndDensityCachePathsShareTheUpliftEnvelope() {
        EndPreset preset = regionPlannedPreset();
        EndHeightmap heightmap = new EndHeightmap(preset, 123456789);
        EndTerrainComposer terrainComposer = new EndTerrainComposer(preset.terrainConfig(), 123456789);
        ContinentSignalBuffer continentSignals = new ContinentSignalBuffer();
        EndTerrainSignalBuffer terrainSignals = new EndTerrainSignalBuffer();

        for (int z = -18000; z <= 18000; z += 256) {
            for (int x = -18000; x <= 18000; x += 256) {
                heightmap.sampleContinentSignals(x, z, 123456789, continentSignals);
                if (continentSignals.landness() < 0.5F
                        || heightmap.getUplift(x, z, 123456789) < 0.05F) {
                    continue;
                }
                heightmap.sampleTerrainSignals(x, z, 123456789, terrainSignals);
                float envelope = Math.min(
                        terrainComposer.reliefEnvelope(continentSignals.inlandness()),
                        0.18F + 0.82F * terrainSignals.uplift());
                float terrainHeight = Math.clamp(
                        terrainSignals.height()
                                * heightmap.landmassVolume().edgeFade(continentSignals.landness())
                                * envelope,
                        0.0F, 1.0F);
                float expected = heightmap.levels().surface
                        + heightmap.levels().elevationRange * terrainHeight;

                assertEquals(expected, heightmap.getTerrainHeight(x, z, 123456789), EPSILON);
                assertEquals(heightmap.getHeight(x, z, 123456789),
                        heightmap.getHeight(x, z, 123456789, continentSignals), 0.0F);
                return;
            }
        }

        throw new AssertionError("no region-planned RTF uplift sample found");
    }

    private static EndPreset regionPlannedPreset() {
        EndPreset defaults = EndPreset.defaults();
        TerrainConfig source = defaults.terrainConfig();
        TerrainConfig terrain = new TerrainConfig(
                source.terrainSeedOffset(), source.terrainRegionSize(),
                source.globalVerticalScale(), source.globalHorizontalScale(), source.terrainBlendRange(),
                TerrainLayoutMode.REGION_PLANNED, source.terrainShape(),
                source.plains(), source.hills(), source.plateau(), source.mountains(),
                TerrainLayerConfig.DISABLED);
        return new EndPreset(
                defaults.worldHeight(), defaults.minY(), defaults.seaLevelY(), defaults.islandBaselineY(),
                defaults.seaMode(), defaults.topologyMode(), defaults.floatingIslandsEnabled(),
                defaults.continentConfig(), terrain, defaults.climateConfig(), defaults.biomeLayoutConfig(),
                defaults.subsurfaceConfig(), defaults.erosionConfig(), 4);
    }

    private static ContinentSignalBuffer identified(float edge, float landness, float inlandness,
                                                    int centerX, int centerZ) {
        ContinentSignalBuffer result = new ContinentSignalBuffer();
        result.setIdentified(edge, landness, inlandness, 1L, centerX, centerZ);
        return result;
    }
}
