package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.ContinentConfig;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.config.TerrainLayoutMode;

class EndArchipelagoIntegrationTest {

    private static final int SEED = 123456789;

    @Test
    void defaultPresetDoesNotEnableExperimentalArchipelago() {
        EndHeightmap heightmap = new EndHeightmap(EndPreset.defaults(), SEED);

        assertFalse(heightmap.archipelagoActive());
        for (int i = 0; i < 32; i++) {
            float x = 2048.0F + i * 257.0F;
            float z = -4096.0F + i * 113.0F;
            assertEquals(heightmap.getMainlandLandness(x, z, SEED),
                    heightmap.getLandness(x, z, SEED), 0.0F);
        }
    }

    @Test
    void regionPlannedRtfRuntimeFindsAttachedLandAndPreservesMainlandIdentity() {
        EndHeightmap heightmap = new EndHeightmap(archipelagoPreset(), SEED);
        EndLandmassSignalBuffer output = new EndLandmassSignalBuffer();
        Sample sample = findArchipelagoSample(heightmap, output);

        assertTrue(heightmap.archipelagoActive());
        assertTrue(sample.output().archipelagoMask() > 0.001F);
        assertTrue(sample.output().landness() >= sample.output().mainlandLandness());
        assertEquals(heightmap.getContinentSignals(sample.x(), sample.z(), SEED).continentId(),
                sample.output().continentId());
        assertTrue(sample.output().archipelagoIdentified());
        assertTrue(sample.output().archipelagoChainId() != 0L);
        assertTrue(heightmap.getTerrainHeight(sample.x(), sample.z(), SEED)
                >= heightmap.getMainlandTerrainHeight(sample.x(), sample.z(), SEED));
    }

    @Test
    void centralRegionRemainsVoidForTheAttachedLayer() {
        EndHeightmap heightmap = new EndHeightmap(archipelagoPreset(), SEED);
        EndLandmassSignalBuffer output = new EndLandmassSignalBuffer();

        heightmap.sampleLandmassSignals(0.0F, 0.0F, SEED, output);

        assertEquals(0.0F, output.archipelagoMask(), 0.0F);
        assertEquals(0.0F, output.archipelagoLandness(), 0.0F);
        assertFalse(output.archipelagoIdentified());
    }

    @Test
    void combinedSamplingIsStableAcrossWorkers() throws Exception {
        EndHeightmap heightmap = new EndHeightmap(archipelagoPreset(), SEED);
        int[] expected = sampleBits(heightmap);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<int[]>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                tasks.add(() -> sampleBits(heightmap));
            }
            for (Future<int[]> future : executor.invokeAll(tasks)) {
                assertArrayEquals(expected, future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static int[] sampleBits(EndHeightmap heightmap) {
        EndLandmassSignalBuffer output = new EndLandmassSignalBuffer();
        int[] values = new int[64];
        for (int i = 0; i < values.length; i++) {
            float x = 2048.0F + i * 311.0F;
            float z = -7000.0F + i * 197.0F;
            heightmap.sampleLandmassSignals(x, z, SEED, output);
            values[i] = Float.floatToIntBits(output.landness());
        }
        return values;
    }

    private static Sample findArchipelagoSample(EndHeightmap heightmap,
                                                EndLandmassSignalBuffer output) {
        for (int z = -16000; z <= 16000; z += 64) {
            for (int x = 2048; x <= 20000; x += 64) {
                heightmap.sampleLandmassSignals(x, z, SEED, output);
                if (output.archipelagoMask() > 0.08F
                        && output.archipelagoLandness() > 0.05F) {
                    return new Sample(x, z, output);
                }
            }
        }
        throw new AssertionError("no attached archipelago sample found");
    }

    private static EndPreset archipelagoPreset() {
        TerrainConfig defaults = TerrainConfig.DEFAULT;
        TerrainConfig terrain = new TerrainConfig(
                defaults.terrainSeedOffset(), defaults.terrainRegionSize(),
                defaults.globalVerticalScale(), defaults.globalHorizontalScale(),
                defaults.terrainBlendRange(), TerrainLayoutMode.REGION_PLANNED,
                defaults.terrainShape(), TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DEFAULT,
                defaults.plateau(),
                defaults.mountains(), defaults.volcano());
        EndPreset preset = EndPreset.defaults();
        return new EndPreset(
                preset.worldHeight(), preset.minY(), preset.seaLevelY(), preset.islandBaselineY(),
                preset.seaMode(), preset.topologyMode(), preset.floatingIslandsEnabled(),
                ContinentConfig.rtfMultiDefaults(), terrain, preset.climateConfig(),
                preset.biomeLayoutConfig(), preset.subsurfaceConfig(), preset.erosionConfig(),
                preset.formatVersion());
    }

    private record Sample(float x, float z, EndLandmassSignalBuffer output) {
    }
}
