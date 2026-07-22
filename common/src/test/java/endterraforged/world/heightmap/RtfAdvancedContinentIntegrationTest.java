package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.ContinentAlgorithm;
import endterraforged.world.config.ContinentConfig;
import endterraforged.world.config.ContinentConfigValidator;
import endterraforged.world.config.EndPreset;
import endterraforged.world.continent.BandedContinent;
import endterraforged.world.continent.Continent;
import endterraforged.world.continent.ContinentSignals;
import endterraforged.world.continent.OuterContinentsContinent;
import endterraforged.world.continent.RtfAdvancedContinent;

class RtfAdvancedContinentIntegrationTest {

    private static final int SEED = 123456789;

    @Test
    void controlledRuntimeUsesAdvancedBandsAndOuterProtection() {
        EndPreset preset = advancedPreset();
        EndHeightmap heightmap = new EndHeightmap(preset, SEED);

        OuterContinentsContinent outer = assertInstanceOf(
                OuterContinentsContinent.class, heightmap.continent());
        BandedContinent banded = assertInstanceOf(BandedContinent.class, outer.continents());
        int[] advancedNodes = {0};
        banded.mapAll(noise -> {
            if (noise instanceof RtfAdvancedContinent) {
                advancedNodes[0]++;
            }
            return noise;
        });
        assertEquals(1, advancedNodes[0]);

        assertEquals(0.0F, heightmap.getLandness(0.0F, 0.0F, SEED), 0.0F);
        assertFalse(heightmap.getContinentSignals(0.0F, 0.0F, SEED).identified());
    }

    @Test
    void heightmapSignalsMatchTheExplicitAdvancedWrapperChainBitForBit() {
        EndPreset preset = advancedPreset();
        ContinentConfig config = preset.continentConfig();
        EndHeightmap heightmap = new EndHeightmap(preset, SEED);
        Continent expected = new OuterContinentsContinent(new BandedContinent(
                new RtfAdvancedContinent(SEED, config), config.continentBands()));

        for (int z = -16000; z <= 16000; z += 2048) {
            for (int x = -16000; x <= 16000; x += 2048) {
                ContinentSignals expectedSignals = expected.signalsAt(x, z, SEED);
                ContinentSignals actualSignals = heightmap.getContinentSignals(x, z, SEED);
                assertEquals(expectedSignals, actualSignals);
                assertEquals(Float.floatToIntBits(expected.compute(x, z, SEED)),
                        Float.floatToIntBits(heightmap.getLandness(x, z, SEED)));
            }
        }
    }

    @Test
    void activeBandsProvideShelfAndInlandReliefSignals() {
        EndHeightmap heightmap = new EndHeightmap(advancedPreset(), SEED);
        boolean sawShelf = false;
        boolean sawInland = false;
        for (int z = -18000; z <= 18000; z += 256) {
            for (int x = -18000; x <= 18000; x += 256) {
                ContinentSignals signals = heightmap.getContinentSignals(x, z, SEED);
                sawShelf |= signals.landness() > 0.02F && signals.landness() < 0.45F
                        && signals.inlandness() < 0.18F;
                sawInland |= signals.landness() > 0.95F && signals.inlandness() > 0.95F;
            }
        }
        assertTrue(sawShelf, "advanced bands must expose a finite low-relief shelf");
        assertTrue(sawInland, "advanced bands must retain full inland relief");
    }

    @Test
    void finiteAdvancedShelfDoesNotFillTheWorldBottom() {
        EndHeightmap heightmap = new EndHeightmap(advancedPreset(), SEED);
        EndDensity density = new EndDensity(heightmap);
        LandSample land = findLand(heightmap);

        assertTrue(heightmap.landmassVolume().isFinite());
        assertEquals(0.0F, density.density(
                land.x(), heightmap.levels().minY, land.z(), SEED), 0.0F);

        for (int y = heightmap.levels().minY; y <= heightmap.levels().maxY; y += 17) {
            float value = density.density(land.x(), y, land.z(), SEED);
            assertTrue(value == 0.0F || value == 1.0F);
        }
    }

    @Test
    void advancedUndersideUsesTheRtfTectonicScale() {
        EndLandmassVolume baseline = new EndHeightmap(
                advancedPreset(advancedConfig(3000, 4096)), SEED).landmassVolume();
        EndLandmassVolume differentLegacyOuterScale = new EndHeightmap(
                advancedPreset(advancedConfig(3000, 12000)), SEED).landmassVolume();
        EndLandmassVolume differentTectonicScale = new EndHeightmap(
                advancedPreset(advancedConfig(1800, 4096)), SEED).landmassVolume();

        boolean sawTectonicDifference = false;
        for (int z = -12000; z <= 12000; z += 1500) {
            for (int x = -12000; x <= 12000; x += 1500) {
                float expected = baseline.underside(x, z, 0.8F, 0.75F);
                assertEquals(Float.floatToIntBits(expected), Float.floatToIntBits(
                        differentLegacyOuterScale.underside(x, z, 0.8F, 0.75F)));
                sawTectonicDifference |= Float.floatToIntBits(expected) != Float.floatToIntBits(
                        differentTectonicScale.underside(x, z, 0.8F, 0.75F));
            }
        }
        assertTrue(sawTectonicDifference,
                "advanced underside noise must be derived from continent_scale");
    }

    @Test
    void advancedRemainsUnavailableToPersistedPresets() {
        ContinentConfig config = advancedConfig();
        assertFalse(ContinentAlgorithm.RTF_ADVANCED.isImplemented());
        assertTrue(ContinentConfigValidator.validate(config).error().isPresent());
    }

    private static LandSample findLand(EndHeightmap heightmap) {
        for (int z = -18000; z <= 18000; z += 256) {
            for (int x = -18000; x <= 18000; x += 256) {
                ContinentSignals signals = heightmap.getContinentSignals(x, z, SEED);
                if (signals.identified() && signals.landness() > 0.95F) {
                    return new LandSample(x, z);
                }
            }
        }
        throw new AssertionError("no advanced continent interior found");
    }

    private static EndPreset advancedPreset() {
        return advancedPreset(advancedConfig());
    }

    private static EndPreset advancedPreset(ContinentConfig config) {
        EndPreset defaults = EndPreset.defaults();
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

    private static ContinentConfig advancedConfig() {
        ContinentConfig base = ContinentConfig.rtfMultiDefaults();
        return advancedConfig(base.continentScale(), base.outerContinentScale());
    }

    private static ContinentConfig advancedConfig(int continentScale, int outerContinentScale) {
        ContinentConfig base = ContinentConfig.rtfMultiDefaults();
        return new ContinentConfig(
                base.islandsScale(),
                continentScale,
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
                outerContinentScale,
                base.landmassVolumeMode(),
                base.shelfThickness(),
                base.shelfEdgeThickness(),
                base.coastShape(),
                base.coastScale(),
                base.coastStrength(),
                base.coastCellBlend(),
                ContinentAlgorithm.RTF_ADVANCED,
                base.continentBands());
    }

    private record LandSample(float x, float z) {
    }
}
