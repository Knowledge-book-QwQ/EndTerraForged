package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetBuilder;
import endterraforged.world.config.ContinentConfig;
import endterraforged.world.config.SeaMode;
import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.config.TerrainLayoutMode;
import endterraforged.world.config.TerrainShape;
import endterraforged.world.config.TestProfile;
import endterraforged.world.config.TopologyMode;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.Perlin;

/**
 * Contract tests for {@link EndHeightmap}: the composition
 * {@code continent × mountains}, the world-height scaling, and the seam
 * between the continent layer and the mountain layer.
 *
 * <p>Asserts invariants rather than magic numbers: height stays in
 * {@code [surface, 1]}, landness in {@code [0, 1]}, void (landness 0) maps
 * to exactly the surface, and both topologies produce their characteristic
 * shape (islands scatter to void, shattered stays mostly solid). The
 * {@code mapAll} reach test proves the whole noise tree is traversable for
 * future {@code Cache2d} insertion.</p>
 */
class EndHeightmapTest {

    private static final int SEED = 42;
    private static final int SAMPLES = 400;

    private static float x(int i) { return i * 7.3F; }
    private static float z(int i) { return i * 11.1F; }

    // ----- range & determinism --------------------------------------------

    @Test
    void heightStaysAboveSurfaceAndBelowWorldTop() {
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        float surface = map.levels().surface;
        for (int i = 0; i < SAMPLES; i++) {
            float h = map.getHeight(x(i), z(i), SEED);
            assertTrue(Float.isFinite(h), "height not finite: " + h);
            assertTrue(h >= surface - 1e-4f, "height below surface: " + h + " < " + surface);
            assertTrue(h <= 1.0f + 1e-4f, "height above world top: " + h);
        }
    }

    @Test
    void heightIsDeterministic() {
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        for (int i = 0; i < SAMPLES; i++) {
            float a = map.getHeight(x(i), z(i), SEED);
            float b = map.getHeight(x(i), z(i), SEED);
            assertEquals(a, b, 0.0f, "same args should yield same height");
        }
    }

    @Test
    void heightIsSeedSensitive() {
        EndHeightmap a = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndHeightmap b = new EndHeightmap(TestProfile.defaultEnd(), SEED + 1);
        boolean anyDifference = false;
        for (int i = 0; i < SAMPLES; i++) {
            if (Float.floatToIntBits(a.getHeight(x(i), z(i), SEED))
                    != Float.floatToIntBits(b.getHeight(x(i), z(i), SEED + 1))) {
                anyDifference = true;
                break;
            }
        }
        assertTrue(anyDifference, "different seeds should produce different terrain");
    }

    @Test
    void globalVerticalScaleControlsTerrainAmplitude() {
        EndPreset profile = new EndPresetBuilder()
                .topologyMode(TopologyMode.CONTINENTAL)
                .terrainConfig(new TerrainConfig(0.5F, 1.0F))
                .build();
        EndHeightmap map = new EndHeightmap(profile, SEED, constantNoise(0.5F));

        float expected = map.levels().surface + map.levels().elevationRange * 0.25F;

        assertEquals(expected, map.getTerrainHeight(64, 64, SEED), 1e-6F);
    }

    @Test
    void globalHorizontalScaleControlsTerrainSampleCoordinates() {
        EndPreset profile = new EndPresetBuilder()
                .topologyMode(TopologyMode.CONTINENTAL)
                .terrainConfig(new TerrainConfig(1.0F, 2.0F))
                .build();
        EndHeightmap map = new EndHeightmap(profile, SEED, xCoordinateNoise());

        float expectedTerrain = 0.25F;
        float expected = map.levels().surface + map.levels().elevationRange * expectedTerrain;

        assertEquals(expected, map.getTerrainHeight(50, 0, SEED), 1e-6F);
    }

    @Test
    void landnessStaysInUnitRange() {
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        for (int i = 0; i < SAMPLES; i++) {
            float l = map.getLandness(x(i), z(i), SEED);
            assertTrue(Float.isFinite(l), "landness not finite: " + l);
            assertTrue(l >= 0.0f - 1e-5f && l <= 1.0f + 1e-5f,
                    "landness out of [0,1]: " + l);
        }
    }

    // ----- composition contract -------------------------------------------

    @Test
    void voidWhereNoLandHasHeightAtSurface() {
        // Where the continent says landness == 0, the terrain product is 0
        // (Multiply short-circuits), so height must be exactly the surface.
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        float surface = map.levels().surface;
        boolean foundVoid = false;
        for (int i = 0; i < SAMPLES; i++) {
            float landness = map.getLandness(x(i), z(i), SEED);
            if (landness <= 0.0f) {
                float h = map.getHeight(x(i), z(i), SEED);
                assertEquals(surface, h, 1e-5f,
                        "void (landness=0) should map to surface, got " + h);
                foundVoid = true;
            }
        }
        assertTrue(foundVoid, "ISLANDS topology should produce at least one void sample");
    }

    @Test
    void terrainProductMatchesContinentTimesMountains() {
        // terrain.compute should equal continent.compute * mountains.compute
        // (the documented composition). Verifies the wiring, not a new
        // composition rule.
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        Noise mountains = EndMountains.mountains2(SEED);
        for (int i = 0; i < SAMPLES; i++) {
            float c = map.continent().compute(x(i), z(i), SEED);
            float m = mountains.compute(x(i), z(i), SEED);
            float terrain = map.terrain().compute(x(i), z(i), SEED);
            if (c <= 0.0f) {
                // Multiply short-circuit: mountains not sampled, terrain == 0
                assertEquals(0.0f, terrain, 0.0f,
                        "terrain should be 0 where continent is 0");
            } else {
                assertEquals(c * m, terrain, 1e-4f,
                        "terrain should equal continent * mountains");
            }
        }
    }

    // ----- topology shape -------------------------------------------------

    @Test
    void islandsTopologyProducesVoidsBetweenIslands() {
        // ISLANDS should have both void (landness 0) and land (landness > 0)
        // across the sample set — scatter=0.5 gates half the cells, and the
        // falloff reaches 0 at island rims.
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        boolean hasVoid = false;
        boolean hasLand = false;
        for (int i = 0; i < SAMPLES; i++) {
            float l = map.getLandness(x(i), z(i), SEED);
            if (l <= 0.0f) hasVoid = true;
            if (l > 0.5f) hasLand = true;
        }
        assertTrue(hasVoid, "ISLANDS should produce void samples");
        assertTrue(hasLand, "ISLANDS should produce solid land samples");
    }

    @Test
    void shatteredTopologyStaysMostlySolid() {
        // CONTINENTAL_SHATTERED carves rifts but keeps solid centres, so the
        // vast majority of samples should be land.riftStrength=0.85 means
        // rifts drop to 0.15, not 0 — but the threshold still leaves most of
        // the field near 1.
        TestProfile profile = new TestProfile(4064, -2032, 0, 0,
                SeaMode.NONE, TopologyMode.CONTINENTAL_SHATTERED, false);
        EndHeightmap map = new EndHeightmap(profile, SEED);
        int solidCount = 0;
        for (int i = 0; i < SAMPLES; i++) {
            if (map.getLandness(x(i), z(i), SEED) > 0.5f) solidCount++;
        }
        assertTrue(solidCount > SAMPLES * 0.5,
                "shattered should be mostly solid, got " + solidCount + "/" + SAMPLES);
    }

    // ----- sea-mode surface selection -------------------------------------

    @Test
    void seaModeAffectsSurfaceBaseline() {
        // NONE: surface = islandBaselineY-derived; WITH_FLOOR: surface = seaLevelY-derived.
        // With baseline=64, sea=0, the two surfaces must differ.
        TestProfile none = new TestProfile(4064, -2032, 0, 64,
                SeaMode.NONE, TopologyMode.ISLANDS, false);
        TestProfile sea = new TestProfile(4064, -2032, 0, 64,
                SeaMode.WITH_FLOOR, TopologyMode.ISLANDS, false);
        EndHeightmap noneMap = new EndHeightmap(none, SEED);
        EndHeightmap seaMap = new EndHeightmap(sea, SEED);
        assertTrue(noneMap.levels().surface > seaMap.levels().surface,
                "NONE surface (baseline=64) should be above WITH_FLOOR surface (sea=0)");
    }

    @Test
    void noneModeSurfaceIsIslandBaseline() {
        TestProfile profile = new TestProfile(4064, -2032, 0, 64,
                SeaMode.NONE, TopologyMode.ISLANDS, false);
        EndHeightmap map = new EndHeightmap(profile, SEED);
        // surfaceFillY = 63; normalisation is relative to min_y=-2032.
        assertEquals((63.0f + 2032.0f) / 4064.0f, map.levels().surface, 1e-5f);
    }

    // ----- tree traversal -------------------------------------------------

    @Test
    void mapAllReachesAllLeaves() {
        // The terrain tree is continent × mountains2. mountains2 has 4 perlin
        // leaves (worleyEdge driver, warp perlin x2, blur perlin, surface
        // perlinRidge) and the continent has 2 warp-driver perlin leaves.
        // A counting visitor should reach >= 6 Perlin instances.
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        int[] count = {0};
        Noise.Visitor counter = n -> {
            if (n instanceof Perlin) count[0]++;
            return n;
        };
        map.terrain().mapAll(counter);
        assertTrue(count[0] >= 6,
                "mapAll should reach >= 6 Perlin leaves (mountains + continent warp), got " + count[0]);
    }

    @Test
    void mountains1AlsoComposesViaPackagePrivateConstructor() {
        // The package-private constructor lets callers swap the mountain
        // recipe. mountains1 should compose without error and stay in range.
        TestProfile profile = TestProfile.defaultEnd();
        EndHeightmap map = new EndHeightmap(profile, SEED, EndMountains.mountains1(SEED));
        float surface = map.levels().surface;
        for (int i = 0; i < SAMPLES; i++) {
            float h = map.getHeight(x(i), z(i), SEED);
            assertTrue(Float.isFinite(h), "mountains1 height not finite: " + h);
            assertTrue(h >= surface - 1e-4f && h <= 1.0f + 1e-4f,
                    "mountains1 height out of range: " + h);
        }
    }

    @Test
    void outerContinentsLeaveTheCentralRegionToVanillaAndCreateLandAndVoidOutside() {
        EndPreset profile = new EndPresetBuilder(EndPreset.defaults())
                .topologyMode(TopologyMode.OUTER_CONTINENTS)
                .build();
        EndHeightmap map = new EndHeightmap(profile, SEED);

        assertEquals(0.0F, map.getLandness(0.0F, 0.0F, SEED), 0.0F,
                "outer continents must not create ETF land at the vanilla centre");

        boolean foundLand = false;
        boolean foundVoid = false;
        for (int z = -16384; z <= 16384; z += 512) {
            for (int x = -16384; x <= 16384; x += 512) {
                float landness = map.getLandness(x, z, SEED);
                foundLand |= landness > 0.5F;
                foundVoid |= landness <= 0.0F;
            }
        }

        assertTrue(foundLand, "outer topology must create at least one macro continent");
        assertTrue(foundVoid, "outer topology must keep End void between macro continents");
    }

    @Test
    void outerContinentScaleChangesTheRuntimeLandnessField() {
        EndPreset compact = new EndPresetBuilder(EndPreset.defaults())
                .topologyMode(TopologyMode.OUTER_CONTINENTS)
                .continentConfig(new ContinentConfig(400, 2048,
                        EndPreset.defaults().continentConfig().continentShape(),
                        1.0F, 0.0F, 0.0F, 5, 0.26F, 4.33F,
                        1.0F, 0.7F, 0.0F, 0.75F, 0.85F, 300, 40.0F, 2048))
                .build();
        EndPreset broad = new EndPresetBuilder(compact)
                .continentConfig(new ContinentConfig(400, 4096,
                        compact.continentConfig().continentShape(),
                        1.0F, 0.0F, 0.0F, 5, 0.26F, 4.33F,
                        1.0F, 0.7F, 0.0F, 0.75F, 0.85F, 300, 40.0F, 4096))
                .build();
        EndHeightmap compactMap = new EndHeightmap(compact, SEED);
        EndHeightmap broadMap = new EndHeightmap(broad, SEED);

        boolean foundDifference = false;
        for (int z = -16384; z <= 16384 && !foundDifference; z += 512) {
            for (int x = -16384; x <= 16384; x += 512) {
                if (Float.floatToIntBits(compactMap.getLandness(x, z, SEED))
                        != Float.floatToIntBits(broadMap.getLandness(x, z, SEED))) {
                    foundDifference = true;
                    break;
                }
            }
        }

        assertTrue(foundDifference,
                "outer_continent_scale must change the outer-continent runtime field");
    }

    @Test
    void knownLandnessHeightPathMatchesRegularHeight() {
        EndHeightmap defaultMap = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndPreset scaledProfile = new EndPresetBuilder()
                .terrainConfig(new TerrainConfig(0, 1200, 1.0F, 1.25F,
                        TerrainShape.SHATTERED_RIDGES))
                .build();
        EndHeightmap scaledMap = new EndHeightmap(scaledProfile, SEED);

        for (int i = 0; i < SAMPLES; i++) {
            assertEquals(defaultMap.getHeight(x(i), z(i), SEED),
                    defaultMap.getHeight(x(i), z(i), SEED,
                            defaultMap.getLandness(x(i), z(i), SEED)),
                    0.0F, "default coordinate fast path must preserve height");
            assertEquals(scaledMap.getHeight(x(i), z(i), SEED),
                    scaledMap.getHeight(x(i), z(i), SEED,
                            scaledMap.getLandness(x(i), z(i), SEED)),
                    0.0F, "scaled coordinate fallback must preserve height");
        }
    }

    @Test
    void terrainShapeConfigSelectsRollingRidgesRecipe() {
        EndPreset profile = new EndPresetBuilder()
                .topologyMode(TopologyMode.CONTINENTAL)
                .terrainConfig(new TerrainConfig(1.0F, 1.0F, TerrainShape.ROLLING_RIDGES))
                .build();
        EndHeightmap map = new EndHeightmap(profile, SEED);
        Noise expected = EndMountains.mountains1(SEED);

        for (int i = 0; i < SAMPLES; i++) {
            assertEquals(expected.compute(x(i), z(i), SEED),
                    map.terrain().compute(x(i), z(i), SEED),
                    1e-5F);
        }
    }

    @Test
    void terrainSeedOffsetControlsConfiguredMountainSeed() {
        int offset = 37;
        EndPreset profile = new EndPresetBuilder()
                .topologyMode(TopologyMode.CONTINENTAL)
                .terrainConfig(new TerrainConfig(offset, 1200, 1.0F, 1.0F, TerrainShape.SHATTERED_RIDGES))
                .build();
        EndHeightmap map = new EndHeightmap(profile, SEED);
        Noise expected = EndMountains.mountains2(SEED + offset);

        for (int i = 0; i < SAMPLES; i++) {
            assertEquals(expected.compute(x(i), z(i), SEED),
                    map.terrain().compute(x(i), z(i), SEED),
                    1e-5F);
        }
    }

    @Test
    void terrainRegionSizeControlsTerrainSampleCoordinates() {
        EndPreset profile = new EndPresetBuilder()
                .topologyMode(TopologyMode.CONTINENTAL)
                .terrainConfig(new TerrainConfig(0, 2400, 1.0F, 1.0F, TerrainShape.SHATTERED_RIDGES))
                .build();
        EndHeightmap map = new EndHeightmap(profile, SEED, xCoordinateNoise());

        float expectedTerrain = 0.25F;
        float expected = map.levels().surface + map.levels().elevationRange * expectedTerrain;

        assertEquals(expected, map.getTerrainHeight(50, 0, SEED), 1e-6F);
    }

    @Test
    void regionPlannedRuntimeUsesDistinctFamilyMathWithoutChangingTheLegacyPath() {
        TerrainLayerConfig enabled = TerrainLayerConfig.DEFAULT;
        TerrainConfig plannedTerrain = new TerrainConfig(
                0, 1200, 1.0F, 1.0F, 0.0F, TerrainLayoutMode.REGION_PLANNED,
                TerrainShape.SHATTERED_RIDGES, enabled, enabled, enabled,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED);
        TerrainConfig legacyTerrain = new TerrainConfig(
                0, 1200, 1.0F, 1.0F, 0.0F, TerrainLayoutMode.LEGACY_SELECTOR,
                TerrainShape.SHATTERED_RIDGES, enabled, enabled, enabled,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED);
        TestProfile plannedProfile = new TestProfile(
                512, -256, 0, 0, SeaMode.NONE, TopologyMode.CONTINENTAL, false, plannedTerrain);
        TestProfile legacyProfile = new TestProfile(
                512, -256, 0, 0, SeaMode.NONE, TopologyMode.CONTINENTAL, false, legacyTerrain);
        EndHeightmap planned = new EndHeightmap(plannedProfile, SEED, constantNoise(0.2F));
        EndHeightmap legacy = new EndHeightmap(legacyProfile, SEED, constantNoise(0.2F));

        boolean foundHeightDifference = false;
        boolean sawPlannedFamily = false;
        for (int z = -6000; z <= 6000; z += 400) {
            for (int x = -6000; x <= 6000; x += 400) {
                EndTerrainLayer family = planned.auxiliaryTerrainAt(x, z, SEED);
                sawPlannedFamily |= family != EndTerrainLayer.NONE;
                foundHeightDifference |= Float.floatToIntBits(planned.getTerrainHeight(x, z, SEED))
                        != Float.floatToIntBits(legacy.getTerrainHeight(x, z, SEED));
            }
        }

        assertTrue(sawPlannedFamily, "region-planned runtime must expose an AREA terrain family");
        assertTrue(foundHeightDifference,
                "region-planned family math must differ from the legacy generic Perlin layer path");
    }

    @Test
    void regionPlannedRuntimeDoesNotRetainTheLegacyGlobalMountainBase() {
        TerrainConfig plannedTerrain = new TerrainConfig(
                0, 1600, 1.0F, 1.0F, 0.0F, TerrainLayoutMode.REGION_PLANNED,
                TerrainShape.SHATTERED_RIDGES,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DEFAULT,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED);
        TestProfile profile = new TestProfile(
                512, -256, 0, 0, SeaMode.NONE, TopologyMode.CONTINENTAL, false, plannedTerrain);
        EndHeightmap lowLegacyNoise = new EndHeightmap(profile, SEED, constantNoise(0.0F));
        EndHeightmap highLegacyNoise = new EndHeightmap(profile, SEED, constantNoise(1.0F));

        for (int z = -12000; z <= 12000; z += 307) {
            for (int x = -12000; x <= 12000; x += 307) {
                assertEquals(lowLegacyNoise.getTerrainHeight(x, z, SEED),
                        highLegacyNoise.getTerrainHeight(x, z, SEED), 0.0F,
                        "REGION_PLANNED must not retain the legacy global mountain noise base");
            }
        }
    }

    @Test
    void mountainLayerScalesTerrainAmplitude() {
        TerrainLayerConfig mountains = new TerrainLayerConfig(0.5F, 0.5F, 2.0F, 1.0F);
        EndPreset profile = new EndPresetBuilder()
                .topologyMode(TopologyMode.CONTINENTAL)
                .terrainConfig(new TerrainConfig(0, 1200, 1.0F, 1.0F,
                        TerrainShape.SHATTERED_RIDGES, mountains))
                .build();
        EndHeightmap map = new EndHeightmap(profile, SEED, constantNoise(0.5F));

        float expected = map.levels().surface + map.levels().elevationRange * 0.25F;

        assertEquals(expected, map.getTerrainHeight(64, 64, SEED), 1e-6F);
    }

    @Test
    void mountainLayerHorizontalScaleControlsTerrainSampleCoordinates() {
        TerrainLayerConfig mountains = new TerrainLayerConfig(1.0F, 1.0F, 1.0F, 2.0F);
        EndPreset profile = new EndPresetBuilder()
                .topologyMode(TopologyMode.CONTINENTAL)
                .terrainConfig(new TerrainConfig(0, 1200, 1.0F, 1.0F,
                        TerrainShape.SHATTERED_RIDGES, mountains))
                .build();
        EndHeightmap map = new EndHeightmap(profile, SEED, xCoordinateNoise());

        float expectedTerrain = 0.25F;
        float expected = map.levels().surface + map.levels().elevationRange * expectedTerrain;

        assertEquals(expected, map.getTerrainHeight(50, 0, SEED), 1e-6F);
    }

    @Test
    void disabledAuxiliaryLayersPreserveExistingTerrainHeight() {
        EndPreset profile = new EndPresetBuilder()
                .topologyMode(TopologyMode.CONTINENTAL)
                .terrainConfig(new TerrainConfig(0, 1200, 1.0F, 1.0F,
                        TerrainShape.SHATTERED_RIDGES, TerrainLayerConfig.DEFAULT))
                .build();
        EndHeightmap map = new EndHeightmap(profile, SEED, constantNoise(0.5F));

        float expected = map.levels().surface + map.levels().elevationRange * 0.5F;

        assertEquals(expected, map.getTerrainHeight(64, 64, SEED), 1e-6F);
    }

    @Test
    void enabledPlainsLayerContributesToTerrainHeight() {
        TerrainLayerConfig plains = new TerrainLayerConfig(1.0F, 1.0F, 1.0F, 1.0F);
        EndPreset disabled = new EndPresetBuilder()
                .topologyMode(TopologyMode.CONTINENTAL)
                .terrainConfig(new TerrainConfig(0, 1200, 1.0F, 1.0F,
                        TerrainShape.SHATTERED_RIDGES, TerrainLayerConfig.DEFAULT))
                .build();
        EndPreset enabled = new EndPresetBuilder(disabled)
                .terrainConfig(new TerrainConfig(0, 1200, 1.0F, 1.0F,
                        TerrainShape.SHATTERED_RIDGES,
                        plains, TerrainLayerConfig.DISABLED, TerrainLayerConfig.DISABLED,
                        TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED))
                .build();
        EndHeightmap disabledMap = new EndHeightmap(disabled, SEED, constantNoise(0.25F));
        EndHeightmap enabledMap = new EndHeightmap(enabled, SEED, constantNoise(0.25F));

        boolean anyDifference = false;
        for (int i = 0; i < SAMPLES; i++) {
            float baseline = disabledMap.getTerrainHeight(x(i), z(i), SEED);
            float layered = enabledMap.getTerrainHeight(x(i), z(i), SEED);
            assertTrue(layered >= baseline - 1e-6F, "plains layer should not reduce terrain height");
            anyDifference |= layered > baseline + 1e-5F;
        }
        assertTrue(anyDifference, "enabled plains layer should affect at least one sampled height");
    }

    @Test
    void regionPlannedAreasProduceStableFamiliesAndReplaceLegacySelectorMath() {
        TerrainLayerConfig plains = new TerrainLayerConfig(1.0F, 0.8F, 1.0F, 1.0F);
        TerrainLayerConfig hills = new TerrainLayerConfig(0.8F, 1.3F, 1.0F, 1.0F);
        TerrainLayerConfig plateau = new TerrainLayerConfig(0.6F, 1.7F, 1.0F, 1.0F);
        TerrainConfig legacyTerrain = new TerrainConfig(0, 1600, 1.0F, 1.0F, 0.0F,
                TerrainShape.SHATTERED_RIDGES, plains, hills, plateau,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED);
        TerrainConfig plannedTerrain = new TerrainConfig(0, 1600, 1.0F, 1.0F, 0.0F,
                TerrainLayoutMode.REGION_PLANNED, TerrainShape.SHATTERED_RIDGES,
                plains, hills, plateau, TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED);

        TestProfile legacyProfile = new TestProfile(512, -256, 0, 0,
                SeaMode.NONE, TopologyMode.CONTINENTAL, false, legacyTerrain);
        TestProfile plannedProfile = new TestProfile(512, -256, 0, 0,
                SeaMode.NONE, TopologyMode.CONTINENTAL, false, plannedTerrain);
        EndHeightmap legacy = new EndHeightmap(legacyProfile, SEED, constantNoise(0.25F));
        EndHeightmap planned = new EndHeightmap(plannedProfile, SEED, constantNoise(0.25F));

        boolean sawPlains = false;
        boolean sawHills = false;
        boolean sawPlateau = false;
        boolean sawBlend = false;
        boolean sawHeightDifference = false;
        for (int z = -16000; z <= 16000; z += 256) {
            for (int x = -16000; x <= 16000; x += 256) {
                EndTerrainLayer family = planned.auxiliaryTerrainAt(x, z, SEED);
                sawPlains |= family == EndTerrainLayer.PLAINS;
                sawHills |= family == EndTerrainLayer.HILLS;
                sawPlateau |= family == EndTerrainLayer.PLATEAU;
                sawBlend |= planned.auxiliaryTerrainBlendAt(x, z, SEED).isBlended();
                sawHeightDifference |= Float.floatToIntBits(planned.getTerrainHeight(x, z, SEED))
                        != Float.floatToIntBits(legacy.getTerrainHeight(x, z, SEED));
            }
        }

        assertTrue(sawPlains, "region plan must allocate plains areas");
        assertTrue(sawHills, "region plan must allocate hills areas");
        assertTrue(sawPlateau, "region plan must allocate plateau areas");
        assertTrue(sawBlend, "region plan must expose continuous family boundaries");
        assertTrue(sawHeightDifference,
                "region-planned sampling must not silently fall back to legacy selector math");
    }

    @Test
    void regionPlannedRidgesExposeFiniteMountainClassification() {
        TerrainConfig terrain = new TerrainConfig(
                0, 1600, 1.0F, 1.0F, 0.0F, TerrainLayoutMode.REGION_PLANNED,
                TerrainShape.SHATTERED_RIDGES,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED);
        TestProfile profile = new TestProfile(512, -256, 0, 0,
                SeaMode.NONE, TopologyMode.CONTINENTAL, false, terrain);
        EndHeightmap map = new EndHeightmap(profile, SEED, constantNoise(0.0F));
        boolean sawMountain = false;
        boolean sawMountainBlend = false;

        for (int z = -16000; z <= 16000; z += 128) {
            for (int x = -16000; x <= 16000; x += 128) {
                sawMountain |= map.auxiliaryTerrainAt(x, z, SEED) == EndTerrainLayer.MOUNTAINS;
                EndTerrainBlend blend = map.auxiliaryTerrainBlendAt(x, z, SEED);
                sawMountainBlend |= (blend.from() == EndTerrainLayer.MOUNTAINS
                        || blend.to() == EndTerrainLayer.MOUNTAINS) && blend.isBlended();
            }
        }

        assertTrue(sawMountain, "REGION_PLANNED must expose bounded ridge mountains to preview callers");
        assertTrue(sawMountainBlend, "ridge footprint edges must expose a visible underlay transition");
    }

    @Test
    void outerCentralPolicySuppressesRegionPlannedRidgeClassification() {
        TerrainConfig terrain = new TerrainConfig(
                0, 1600, 1.0F, 1.0F, 0.0F, TerrainLayoutMode.REGION_PLANNED,
                TerrainShape.SHATTERED_RIDGES,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED);
        EndHeightmap continental = new EndHeightmap(
                new TestProfile(512, -256, 0, 0, SeaMode.NONE, TopologyMode.CONTINENTAL, false, terrain),
                SEED, constantNoise(0.0F));
        EndHeightmap outer = new EndHeightmap(
                new TestProfile(512, -256, 0, 0, SeaMode.NONE, TopologyMode.OUTER_CONTINENTS, false, terrain),
                SEED, constantNoise(0.0F));

        for (int z = -1536; z <= 1536; z += 64) {
            for (int x = -1536; x <= 1536; x += 64) {
                if (x * x + z * z > 1536 * 1536
                        || continental.auxiliaryTerrainAt(x, z, SEED) != EndTerrainLayer.MOUNTAINS) {
                    continue;
                }
                assertTrue(outer.auxiliaryTerrainAt(x, z, SEED) != EndTerrainLayer.MOUNTAINS,
                        "outer central protection must suppress ridge visibility in diagnostics and preview");
                return;
            }
        }

        throw new AssertionError("test terrain layout did not expose a central ridge classification");
    }

    @Test
    void regionPlannedRejectsTheFrozenCompactVolcanoDraft() {
        TerrainConfig volcanoTerrain = new TerrainConfig(
                0, 1600, 1.0F, 1.0F, 0.0F, TerrainLayoutMode.REGION_PLANNED,
                TerrainShape.SHATTERED_RIDGES,
                TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DISABLED, TerrainLayerConfig.DEFAULT);
        TestProfile volcanoProfile = new TestProfile(512, -256, 0, 0,
                SeaMode.NONE, TopologyMode.CONTINENTAL, false, volcanoTerrain);

        assertThrows(IllegalArgumentException.class,
                () -> new EndHeightmap(volcanoProfile, SEED, constantNoise(0.0F)));
    }

    @Test
    void auxiliaryTerrainConvergesWithTheFiniteShelfEdge() {
        TerrainConfig defaults = TerrainConfig.DEFAULT;
        TerrainConfig terrain = new TerrainConfig(
                defaults.terrainSeedOffset(),
                defaults.terrainRegionSize(),
                defaults.globalVerticalScale(),
                defaults.globalHorizontalScale(),
                defaults.terrainBlendRange(),
                defaults.terrainShape(),
                new TerrainLayerConfig(1.0F, 1.0F, 1.0F, 1.0F),
                TerrainLayerConfig.DISABLED,
                TerrainLayerConfig.DISABLED,
                defaults.mountains(),
                TerrainLayerConfig.DISABLED);
        EndPreset preset = new EndPresetBuilder(EndPreset.defaults())
                .continentConfig(ContinentConfig.defaults())
                .terrainConfig(terrain)
                .build();
        EndHeightmap map = new EndHeightmap(preset, SEED);
        EndTerrainComposer composer = new EndTerrainComposer(terrain, SEED);
        OuterShelfEdge edge = findOuterShelfEdgeWithAuxiliaryContribution(map, composer);
        float rawTerrain = map.terrain().compute(edge.x(), edge.z(), SEED);
        float rawAuxiliary = composer.auxiliaryContribution(edge.x(), edge.z(), SEED);
        float terrainHeight = Math.clamp(rawTerrain
                        * terrain.globalVerticalScale()
                        * terrain.mountains().weight()
                        * terrain.mountains().baseScale()
                        * terrain.mountains().verticalScale(),
                0.0F, 1.0F);
        float expectedHeight = map.levels().surface + map.levels().elevationRange * Math.clamp(
                terrainHeight + rawAuxiliary * map.landmassVolume().edgeFade(edge.landness()),
                0.0F, 1.0F);

        assertEquals(expectedHeight, map.getTerrainHeight(edge.x(), edge.z(), SEED), 1.0E-6F,
                "optional terrain layers must use the same finite-shelf edge fade as the volume");
    }

    private static OuterShelfEdge findOuterShelfEdgeWithAuxiliaryContribution(EndHeightmap map,
                                                                               EndTerrainComposer composer) {
        for (int z = 2560; z <= 18944; z += 128) {
            for (int x = 2560; x <= 18944; x += 128) {
                float landness = map.getLandness(x, z, SEED);
                if (landness > 0.0F && landness <= 0.05F
                        && composer.auxiliaryContribution(x, z, SEED) > 0.001F) {
                    return new OuterShelfEdge(x, z, landness);
                }
            }
        }
        throw new AssertionError("no near-void outer shelf sample with auxiliary terrain found");
    }

    private static Noise constantNoise(float value) {
        return new Noise() {
            @Override
            public float compute(float x, float z, int seed) {
                return value;
            }

            @Override
            public float minValue() {
                return value;
            }

            @Override
            public float maxValue() {
                return value;
            }

            @Override
            public Noise mapAll(Visitor visitor) {
                return visitor.apply(this);
            }
        };
    }

    private static Noise xCoordinateNoise() {
        return new Noise() {
            @Override
            public float compute(float x, float z, int seed) {
                return Math.clamp(x / 100.0F, 0.0F, 1.0F);
            }

            @Override
            public float minValue() {
                return 0.0F;
            }

            @Override
            public float maxValue() {
                return 1.0F;
            }

            @Override
            public Noise mapAll(Visitor visitor) {
                return visitor.apply(this);
            }
        };
    }

    private record OuterShelfEdge(float x, float z, float landness) {
    }
}
