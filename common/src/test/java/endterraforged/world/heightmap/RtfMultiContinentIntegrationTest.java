package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.ContinentAlgorithm;
import endterraforged.world.config.ContinentBandsConfig;
import endterraforged.world.config.ContinentConfig;
import endterraforged.world.config.ContinentConfigBuilder;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetBuilder;
import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.config.TerrainLayoutMode;
import endterraforged.world.continent.BandedContinent;
import endterraforged.world.continent.ContinentSignals;
import endterraforged.world.continent.EndCentralRegionPolicy;
import endterraforged.world.continent.OuterContinentsContinent;
import endterraforged.world.continent.IslandsContinent;
import endterraforged.world.continent.RtfMultiContinent;

class RtfMultiContinentIntegrationTest {

    @Test
    void explicitRtfMultiUsesTheBandedRuntimePrimitiveAndPreservesCentralVoid() {
        EndPreset preset = rtfPreset();
        EndHeightmap heightmap = new EndHeightmap(preset, 123456789);

        OuterContinentsContinent outer = assertInstanceOf(OuterContinentsContinent.class, heightmap.continent());
        assertInstanceOf(BandedContinent.class, outer.continents());
        assertEquals(0.0F, heightmap.getLandness(0.0F, 0.0F, 123456789), 0.0F);
        assertFalse(heightmap.getContinentSignals(0.0F, 0.0F, 123456789).identified());
    }

    @Test
    void preVersionThreeRtfPresetKeepsRawRtfLandness() {
        EndPreset preset = legacyRtfPreset();
        EndHeightmap heightmap = new EndHeightmap(preset, 123456789);
        OuterContinentsContinent outer = assertInstanceOf(OuterContinentsContinent.class, heightmap.continent());
        RtfMultiContinent raw = assertInstanceOf(RtfMultiContinent.class, outer.continents());

        for (int z = -16000; z <= 16000; z += 1024) {
            for (int x = -16000; x <= 16000; x += 1024) {
                if (endterraforged.world.continent.EndCentralRegionPolicy.outerActivation(x, z) < 1.0F) {
                    continue;
                }
                assertEquals(Float.floatToIntBits(raw.compute(x, z, 0)),
                        Float.floatToIntBits(heightmap.getLandness(x, z, 123456789)));
            }
        }
    }

    @Test
    void activeBandsExposeFiniteLandnessAndSeparateInlandRelief() {
        EndHeightmap heightmap = new EndHeightmap(rtfPreset(), 123456789);
        boolean sawShelf = false;
        boolean sawInland = false;
        for (int z = -16000; z <= 16000; z += 256) {
            for (int x = -16000; x <= 16000; x += 256) {
                ContinentSignals signals = heightmap.getContinentSignals(x, z, 123456789);
                assertEquals(signals.landness(), heightmap.getLandness(x, z, 123456789), 0.0F);
                assertTrue(signals.edge() >= 0.0F && signals.edge() <= 1.0F);
                assertTrue(signals.landness() >= 0.0F && signals.landness() <= 1.0F);
                assertTrue(signals.inlandness() >= 0.0F && signals.inlandness() <= 1.0F);
                if (EndCentralRegionPolicy.outerActivation(x, z) > 0.0F) {
                    assertTrue(signals.identified(), "band and outer wrappers must preserve ownership");
                }
                sawShelf |= signals.landness() > 0.02F && signals.landness() < 0.45F
                        && signals.inlandness() < 0.18F;
                sawInland |= signals.landness() > 0.95F && signals.inlandness() > 0.95F;
            }
        }

        assertTrue(sawShelf, "R2 bands must expose a low-relief finite shelf between void and inland");
        assertTrue(sawInland, "R2 bands must retain full terrain strength in continent interiors");
    }

    @Test
    void defaultPresetUsesTheRtfMultiProductionBaseline() {
        EndPreset defaults = EndPreset.defaults();
        assertEquals(ContinentAlgorithm.RTF_MULTI, defaults.continentConfig().continentAlgorithm());

        OuterContinentsContinent outer = assertInstanceOf(
                OuterContinentsContinent.class, new EndHeightmap(defaults, 123456789).continent());
        assertInstanceOf(BandedContinent.class, outer.continents());
    }

    @Test
    void regionPlannedTerrainDoesNotDependOnContinentCentreDistance() {
        EndHeightmap heightmap = new EndHeightmap(regionPlannedRtfPreset(), 123456789);
        EndLandmassSignalBuffer nearbyCentre = identifiedLandmass(0, 0);
        EndLandmassSignalBuffer remoteCentre = identifiedLandmass(50000, -50000);

        float nearby = heightmap.getTerrainHeight(8192.0F, 8192.0F, 123456789, nearbyCentre);
        float remote = heightmap.getTerrainHeight(8192.0F, 8192.0F, 123456789, remoteCentre);

        assertEquals(nearby, remote, 0.0F,
                "continent centre distance must not attenuate REGION_PLANNED terrain");
    }

    @Test
    void defaultPresetFindsLandWithinTenThousandBlocksOfEveryCardinalOuterCorridor() {
        for (int seed : new int[] {0, 42, 123456789, -987654321}) {
            EndHeightmap heightmap = new EndHeightmap(EndPreset.defaults(), seed);
            assertTrue(hasLandInOuterCorridor(heightmap, seed, 1, 0), "east corridor was void for seed " + seed);
            assertTrue(hasLandInOuterCorridor(heightmap, seed, -1, 0), "west corridor was void for seed " + seed);
            assertTrue(hasLandInOuterCorridor(heightmap, seed, 0, 1), "south corridor was void for seed " + seed);
            assertTrue(hasLandInOuterCorridor(heightmap, seed, 0, -1), "north corridor was void for seed " + seed);
        }
    }

    private static EndPreset rtfPreset() {
        ContinentConfig config = new ContinentConfigBuilder(ContinentConfig.defaults())
                .continentScale(3000)
                .continentJitter(0.7F)
                .continentAlgorithm(ContinentAlgorithm.RTF_MULTI)
                .build();
        return new EndPresetBuilder(EndPreset.defaults()).continentConfig(config).build();
    }

    private static EndPreset legacyRtfPreset() {
        ContinentConfig config = new ContinentConfigBuilder(ContinentConfig.defaults())
                .continentScale(3000)
                .continentJitter(0.7F)
                .continentAlgorithm(ContinentAlgorithm.RTF_MULTI)
                .continentBands(ContinentBandsConfig.LEGACY_PASSTHROUGH)
                .build();
        EndPreset defaults = EndPreset.defaults();
        return new EndPreset(defaults.worldHeight(), defaults.minY(), defaults.seaLevelY(),
                defaults.islandBaselineY(), defaults.seaMode(), defaults.topologyMode(),
                defaults.floatingIslandsEnabled(), config, defaults.terrainConfig(), defaults.climateConfig(),
                defaults.biomeLayoutConfig(), defaults.subsurfaceConfig(), defaults.erosionConfig(), 2);
    }

    private static EndPreset regionPlannedRtfPreset() {
        EndPreset base = rtfPreset();
        TerrainConfig source = base.terrainConfig();
        TerrainConfig terrain = new TerrainConfig(
                source.terrainSeedOffset(), source.terrainRegionSize(),
                source.globalVerticalScale(), source.globalHorizontalScale(),
                source.terrainBlendRange(), TerrainLayoutMode.REGION_PLANNED,
                source.terrainShape(), TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DEFAULT,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED);
        return new EndPreset(
                base.worldHeight(), base.minY(), base.seaLevelY(), base.islandBaselineY(),
                base.seaMode(), base.topologyMode(), base.floatingIslandsEnabled(),
                base.continentConfig(), terrain, base.climateConfig(), base.biomeLayoutConfig(),
                base.subsurfaceConfig(), base.erosionConfig(), 4);
    }

    private static EndLandmassSignalBuffer identifiedLandmass(int centerX, int centerZ) {
        EndLandmassSignalBuffer signals = new EndLandmassSignalBuffer();
        signals.continentSignals().setIdentified(0.9F, 0.9F, 0.9F, 7L, centerX, centerZ);
        signals.combine();
        return signals;
    }

    private static boolean hasLandInOuterCorridor(EndHeightmap heightmap, int seed, int directionX, int directionZ) {
        int lateralX = -directionZ;
        int lateralZ = directionX;
        for (int distance = 2304; distance <= 10000; distance += 128) {
            for (int lateralOffset = -1024; lateralOffset <= 1024; lateralOffset += 256) {
                float x = directionX * distance + lateralX * lateralOffset;
                float z = directionZ * distance + lateralZ * lateralOffset;
                if (heightmap.getLandness(x, z, seed) >= 0.35F) {
                    return true;
                }
            }
        }
        return false;
    }
}
