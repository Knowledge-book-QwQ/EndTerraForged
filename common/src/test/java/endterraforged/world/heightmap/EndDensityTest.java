package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import endterraforged.world.climate.ClimateModulator;
import endterraforged.world.climate.EndClimate;
import endterraforged.world.config.AbyssPitConfig;
import endterraforged.world.config.CaveChamberConfig;
import endterraforged.world.config.CaveNetworkConfig;
import endterraforged.world.config.CaveSystemConfig;
import endterraforged.world.config.CaveTunnelConfig;
import endterraforged.world.config.ContinentAlgorithm;
import endterraforged.world.config.ContinentConfigBuilder;
import endterraforged.world.config.ContinentConfig;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetBuilder;
import endterraforged.world.config.SeaMode;
import endterraforged.world.config.SubsurfaceConfig;
import endterraforged.world.config.TestProfile;
import endterraforged.world.config.TopologyMode;
import endterraforged.world.lake.EndLakeMap;
import endterraforged.world.river.EndRiverMap;

/**
 * Contract tests for {@link EndDensity}: the 3D solid/void column decision
 * that realises the three {@link SeaMode} behaviours below the reference
 * surface.
 *
 * <p>Asserts the column structure per sea mode (WITH_FLOOR fills a seabed,
 * NONE / NO_FLOOR carve void below the surface), the continent gate (void
 * where landness is 0), the terrain-top air boundary, determinism, and the
 * discrete 0/1 output contract. Tests sample a real {@link EndHeightmap} so
 * the integration between heightmap and density is exercised end-to-end.</p>
 */
class EndDensityTest {

    private static final int SEED = 42;
    private static final int SAMPLES = 400;

    private static float x(int i) { return i * 7.3F; }
    private static float z(int i) { return i * 11.1F; }

    /**
     * Finds a sample point where the continent reports solid land (landness > 0)
     * and the terrain top is comfortably above the surface, so the column has a
     * non-trivial solid band to test against.
     */
    private static int findSolidSample(EndHeightmap map) {
        for (int i = 0; i < SAMPLES; i++) {
            float landness = map.getLandness(x(i), z(i), SEED);
            float height = map.getHeight(x(i), z(i), SEED);
            if (landness > 0.5F && height > map.levels().surface + 0.05F) {
                return i;
            }
        }
        throw new AssertionError("no solid sample with terrain above surface found");
    }

    private static int findAbyssSample(EndHeightmap map, EndSubsurface subsurface) {
        for (int i = 0; i < SAMPLES; i++) {
            float landness = map.getLandness(x(i), z(i), SEED);
            float height = map.getHeight(x(i), z(i), SEED);
            if (landness > 0.5F && height > map.levels().surface + 0.05F
                    && subsurface.abyssStrength(x(i), z(i), landness) > 0.9F) {
                return i;
            }
        }
        throw new AssertionError("no abyss sample with terrain above surface found");
    }

    private static CaveSample findCaveSample(EndHeightmap map, EndSubsurface subsurface) {
        for (int i = 0; i < SAMPLES * 3; i++) {
            float landness = map.getLandness(x(i), z(i), SEED);
            float height = map.getHeight(x(i), z(i), SEED);
            if (landness <= 0.5F || height <= map.levels().surface + 0.2F) {
                continue;
            }
            int topY = map.levels().scale(height);
            for (int depth = 160; depth <= 720; depth += 32) {
                int y = topY - depth;
                float yNorm = map.levels().scale(y);
                if (subsurface.caveStrength(x(i), z(i), landness,
                        yNorm, height, map.levels().worldHeight) >= 0.35F) {
                    return new CaveSample(i, y);
                }
            }
        }
        throw new AssertionError("no cave sample with terrain above surface found");
    }

    // ----- output contract ------------------------------------------------

    @Test
    void densityIsAlwaysZeroOrOne() {
        EndDensity density = new EndDensity(new EndHeightmap(TestProfile.defaultEnd(), SEED));
        for (int i = 0; i < SAMPLES; i++) {
            for (int y = -100; y <= 2000; y += 137) {
                float d = density.density(x(i), y, z(i), SEED);
                assertTrue(d == 0.0F || d == 1.0F,
                        "density must be discrete 0 or 1, got " + d + " at y=" + y);
            }
        }
    }

    @Test
    void isSolidAgreesWithDensity() {
        EndDensity density = new EndDensity(new EndHeightmap(TestProfile.defaultEnd(), SEED));
        for (int i = 0; i < SAMPLES; i++) {
            int y = 64;
            boolean solid = density.isSolid(x(i), y, z(i), SEED);
            float d = density.density(x(i), y, z(i), SEED);
            assertEquals(d >= 0.5F, solid, "isSolid must agree with density threshold");
        }
    }

    @Test
    void densityIsDeterministic() {
        EndDensity density = new EndDensity(new EndHeightmap(TestProfile.defaultEnd(), SEED));
        for (int i = 0; i < SAMPLES; i++) {
            int y = 100;
            float a = density.density(x(i), y, z(i), SEED);
            float b = density.density(x(i), y, z(i), SEED);
            assertEquals(a, b, 0.0F, "same args should yield same density");
        }
    }

    @Test
    void parallelSamplingMatchesSequentialReference() throws Exception {
        EndHeightmap map = new EndHeightmap(EndPreset.defaults(), SEED)
                .withClimate(ClimateModulator.defaults(EndClimate.defaults(SEED)))
                .withRivers(EndRiverMap.defaults())
                .withLakes(EndLakeMap.defaults());
        EndDensity density = new EndDensity(map, caveSubsurface());
        int sampleCount = 512;
        int workerCount = 4;
        int[] sequential = new int[sampleCount];
        int[] parallel = new int[sampleCount];
        int solidSamples = 0;

        for (int sample = 0; sample < sampleCount; sample++) {
            sequential[sample] = defaultOuterShelfDensityBits(density, sample);
            if (sequential[sample] != 0) {
                solidSamples++;
            }
        }
        assertTrue(solidSamples > 0, "the concurrent reference must include active shelf columns");

        try (ExecutorService workers = Executors.newFixedThreadPool(workerCount)) {
            Future<?>[] futures = new Future<?>[workerCount];
            for (int worker = 0; worker < workerCount; worker++) {
                int firstSample = worker;
                futures[worker] = workers.submit(() -> {
                    for (int sample = firstSample; sample < sampleCount; sample += workerCount) {
                        parallel[sample] = defaultOuterShelfDensityBits(density, sample);
                    }
                });
            }
            for (Future<?> future : futures) {
                future.get();
            }
        }

        assertArrayEquals(sequential, parallel,
                "parallel density sampling must match the sequential reference bit-for-bit");
    }

    @Test
    void cachedColumnsMatchUncachedSamplingAcrossInterleavedColumnsAndSeeds() {
        TestProfile profile = new TestProfile(4064, -2032, 0, 0,
                SeaMode.WITH_FLOOR, TopologyMode.CONTINENTAL_SHATTERED, false);
        EndHeightmap map = new EndHeightmap(profile, SEED);
        EndSubsurface subsurface = caveSubsurface();
        EndDensity density = new EndDensity(map, subsurface);

        for (int sample = 0; sample < 24; sample++) {
            int index = (sample * 37) % SAMPLES;
            int seed = sample % 2 == 0 ? SEED : SEED + 101;
            if (map.getLandness(x(index), z(index), seed) <= 0.0F) {
                continue;
            }
            int oceanFloorY = density.oceanFloorY(x(index), z(index), seed);
            for (int y = -256; y <= 1536; y += 97) {
                if (map.seaMode().hasFloor() && y <= oceanFloorY) {
                    continue;
                }
                assertEquals(uncachedDensity(map, subsurface, x(index), y, z(index), seed),
                        density.density(x(index), y, z(index), seed), 0.0F,
                        "cached density must match uncached sampling at sample=" + sample + ", y=" + y);
            }
        }
    }

    @Test
    void sharedWorkerCacheInvalidatesWhenDensityRuntimeChanges() {
        EndHeightmap shelfMap = new EndHeightmap(EndPreset.defaults(), SEED);
        EndDensity shelf = new EndDensity(shelfMap);
        TestProfile legacyProfile = new TestProfile(512, -256, 0, 0,
                SeaMode.WITH_FLOOR, TopologyMode.CONTINENTAL, false);
        EndHeightmap legacyMap = new EndHeightmap(legacyProfile, SEED);
        EndDensity legacy = new EndDensity(legacyMap);
        boolean foundDifferentColumn = false;

        for (int sample = 0; sample < 1024 && !foundDifferentColumn; sample++) {
            float worldX = outerX(sample);
            float worldZ = outerZ(sample);
            for (int y = shelfMap.levels().minY; y <= shelfMap.levels().maxY; y += 7) {
                if (shelfMap.levels().scale(y) < shelfMap.landmassVolume().minimumUnderside()) {
                    continue;
                }
                float shelfExpected = uncachedDensity(shelfMap, EndSubsurface.DISABLED,
                        worldX, y, worldZ, SEED);
                float legacyExpected = uncachedDensity(legacyMap, EndSubsurface.DISABLED,
                        worldX, y, worldZ, SEED);
                if (shelfExpected == legacyExpected) {
                    continue;
                }

                assertEquals(shelfExpected, shelf.density(worldX, y, worldZ, SEED), 0.0F);
                assertEquals(legacyExpected, legacy.density(worldX, y, worldZ, SEED), 0.0F);
                assertEquals(shelfExpected, shelf.density(worldX, y, worldZ, SEED), 0.0F);
                foundDifferentColumn = true;
                break;
            }
        }

        assertTrue(foundDifferentColumn,
                "the owner-switch regression must exercise different shelf and legacy density values");
    }

    @Test
    void defaultSubsurfaceMatchesLegacyConstructor() {
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndDensity legacy = new EndDensity(map);
        EndDensity explicitDefault = new EndDensity(map,
                SubsurfaceConfig.DEFAULT.buildRuntime(SEED));

        for (int i = 0; i < SAMPLES; i++) {
            for (int y = -100; y <= 2000; y += 137) {
                assertEquals(legacy.density(x(i), y, z(i), SEED),
                        explicitDefault.density(x(i), y, z(i), SEED), 0.0F);
            }
        }
    }

    @Test
    void configuredAbyssCarvesFromTerrainTopDown() {
        TestProfile profile = new TestProfile(4064, -2032, 0, 0,
                SeaMode.WITH_FLOOR, TopologyMode.CONTINENTAL_SHATTERED, false);
        EndHeightmap map = new EndHeightmap(profile, SEED);
        EndSubsurface subsurface = new SubsurfaceConfig(
                new AbyssPitConfig(true, 1600, 64, 0.0F, 0.001F, 64, 0.0F))
                .buildRuntime(SEED);
        EndDensity density = new EndDensity(map, subsurface);
        int i = findAbyssSample(map, subsurface);
        int terrainTopY = map.levels().scale(map.getHeight(x(i), z(i), SEED));

        assertEquals(0.0F, density.density(x(i), terrainTopY, z(i), SEED), 0.0F,
                "abyss must carve the terrain top at an active pit sample");
        assertEquals(1.0F, density.density(x(i), terrainTopY - 128, z(i), SEED), 0.0F,
                "abyss must stop below its configured depth instead of voiding the whole column");
    }

    @Test
    void configuredCavesCarveInsideTerrainVolume() {
        TestProfile profile = new TestProfile(4064, -2032, 0, 0,
                SeaMode.WITH_FLOOR, TopologyMode.CONTINENTAL_SHATTERED, false);
        EndHeightmap map = new EndHeightmap(profile, SEED);
        EndSubsurface subsurface = caveSubsurface();
        EndDensity density = new EndDensity(map, subsurface);
        CaveSample sample = findCaveSample(map, subsurface);

        assertEquals(0.0F, density.density(x(sample.index), sample.y, z(sample.index), SEED),
                0.0F, "enabled cave field must carve an active 3D sample");
    }

    @Test
    void defaultOuterContinentsHaveFiniteShelfUndersides() {
        EndPreset preset = EndPreset.defaults();
        EndHeightmap map = new EndHeightmap(preset, SEED);
        EndDensity density = new EndDensity(map);
        int sample = findOuterShelfSample(map);
        float worldX = outerX(sample);
        float worldZ = outerZ(sample);
        float landness = map.getLandness(worldX, worldZ, SEED);
        float terrainTop = map.getHeight(worldX, worldZ, SEED);
        float underside = map.landmassVolume().underside(worldX, worldZ, landness, terrainTop);
        int topY = map.levels().scale(terrainTop);
        int undersideY = map.levels().scale(underside);

        assertEquals(1.0F, density.density(worldX, topY, worldZ, SEED), 0.0F,
                "a finite shelf must remain solid at its terrain top");
        assertEquals(1.0F, density.density(worldX, (topY + undersideY) / 2, worldZ, SEED), 0.0F,
                "a finite shelf must retain its body between top and underside");
        assertEquals(0.0F, density.density(worldX, undersideY - 2, worldZ, SEED), 0.0F,
                "a finite shelf must be void below its underside instead of filling to world bottom");
    }

    @Test
    void outerShelfEdgeColumnsConvergeBeforeTheyBecomeVoid() {
        EndPreset preset = EndPreset.defaults();
        EndHeightmap map = new EndHeightmap(preset, SEED);
        EndDensity density = new EndDensity(map);
        OuterShelfEdge edge = findOuterShelfEdge(map);
        float terrainTop = map.getHeight(edge.x(), edge.z(), SEED, edge.landness());
        float underside = map.landmassVolume().underside(
                edge.x(), edge.z(), edge.landness(), terrainTop);
        float thicknessBlocks = (terrainTop - underside) * map.levels().worldHeight;
        int undersideY = map.levels().scale(underside);
        int topY = map.levels().scale(terrainTop);
        int solidBlocks = 0;
        for (int y = undersideY - 2; y <= topY + 2; y++) {
            if (density.isSolid(edge.x(), y, edge.z(), SEED)) {
                solidBlocks++;
            }
        }

        assertTrue(thicknessBlocks < ContinentConfig.defaults().shelfEdgeThickness() * 0.12F,
                "near-void land must converge instead of retaining a tall shelf edge");
        assertTrue(solidBlocks <= 1,
                "a sub-block shelf edge must not round into a multi-block vertical wall");
        assertEquals(0.0F, density.density(edge.x(), undersideY - 2, edge.z(), SEED), 0.0F,
                "the converged edge must still become void immediately below its underside");
    }

    @Test
    void cachedFiniteShelfColumnsMatchUncachedDensity() {
        EndPreset preset = EndPreset.defaults();
        EndHeightmap map = new EndHeightmap(preset, SEED);
        EndSubsurface subsurface = preset.subsurfaceConfig().buildRuntime(SEED);
        EndDensity density = new EndDensity(map, subsurface);
        int sample = findOuterShelfSample(map);
        float worldX = outerX(sample);
        float worldZ = outerZ(sample);

        for (int y = map.levels().minY; y <= map.levels().maxY; y += 19) {
            assertEquals(uncachedDensity(map, subsurface, worldX, y, worldZ, SEED),
                    density.density(worldX, y, worldZ, SEED), 0.0F,
                    "cached finite shelf must match direct sampling at y=" + y);
        }
    }

    @Test
    void cachedRtfBandColumnsMatchUncachedDensity() {
        EndPreset preset = rtfBandPreset();
        EndHeightmap map = new EndHeightmap(preset, SEED);
        EndDensity density = new EndDensity(map);
        int sample = findRtfBandShelfSample(map);
        float worldX = outerX(sample);
        float worldZ = outerZ(sample);

        for (int y = map.levels().minY; y <= map.levels().maxY; y += 19) {
            assertEquals(uncachedDensity(map, EndSubsurface.DISABLED, worldX, y, worldZ, SEED),
                    density.density(worldX, y, worldZ, SEED), 0.0F,
                    "cached R2 bands must match the uncached signal/height/underside path at y=" + y);
        }
    }

    @Test
    void rtfBandShelfEdgesConvergeBeforeBecomingVoid() {
        EndHeightmap map = new EndHeightmap(rtfBandPreset(), SEED);
        EndDensity density = new EndDensity(map);
        RtfBandEdge edge = findRtfBandEdge(map);
        float terrainTop = map.getHeight(edge.x(), edge.z(), SEED);
        float underside = map.landmassVolume().underside(
                edge.x(), edge.z(), edge.landness(), terrainTop);
        float thicknessBlocks = (terrainTop - underside) * map.levels().worldHeight;
        int undersideY = map.levels().scale(underside);
        int topY = map.levels().scale(terrainTop);
        int solidBlocks = 0;
        for (int y = undersideY - 2; y <= topY + 2; y++) {
            solidBlocks += density.isSolid(edge.x(), y, edge.z(), SEED) ? 1 : 0;
        }

        assertTrue(thicknessBlocks < ContinentConfig.defaults().shelfEdgeThickness() * 0.12F,
                "R2 shelf edges must converge instead of becoming tall solid curtains");
        assertTrue(solidBlocks <= 1,
                "R2 sub-block shelf edges must not quantise into a multi-block wall");
    }

    @Test
    void shelfMinimumUndersideEarlyReturnMatchesFullPostProcessSampling() {
        EndHeightmap map = new EndHeightmap(EndPreset.defaults(), SEED)
                .withClimate(ClimateModulator.defaults(EndClimate.defaults(SEED)))
                .withRivers(EndRiverMap.defaults())
                .withLakes(EndLakeMap.defaults());
        EndDensity density = new EndDensity(map);
        int guaranteedVoidY = map.levels().minY;

        assertTrue(map.levels().scale(guaranteedVoidY) < map.landmassVolume().minimumUnderside(),
                "default Standard shelf must leave a lower void band for the early return");
        for (int i = 0; i < 128; i++) {
            float worldX = outerX(i);
            float worldZ = outerZ(i);
            assertEquals(uncachedDensity(map, EndSubsurface.DISABLED, worldX, guaranteedVoidY, worldZ, SEED),
                    density.density(worldX, guaranteedVoidY, worldZ, SEED), 0.0F,
                    "shelf lower-bound early return must match the full density path");
        }
    }

    // ----- continent gate --------------------------------------------------

    @Test
    void voidWhereNoLandRegardlessOfSeaMode() {
        // Where landness == 0, the whole column must be void in every sea mode.
        for (SeaMode mode : SeaMode.values()) {
            TestProfile profile = new TestProfile(4064, -2032, 0, 0, mode, TopologyMode.ISLANDS, false);
            EndDensity density = new EndDensity(new EndHeightmap(profile, SEED));
            boolean foundVoid = false;
            for (int i = 0; i < SAMPLES; i++) {
                if (density.heightmap().getLandness(x(i), z(i), SEED) <= 0.0F) {
                    assertEquals(0.0F, density.density(x(i), 64, z(i), SEED), 0.0F,
                            mode + ": void expected where landness=0");
                    foundVoid = true;
                }
            }
            assertTrue(foundVoid, mode + ": ISLANDS should produce at least one void sample");
        }
    }

    // ----- terrain top boundary -------------------------------------------

    @Test
    void airAboveTerrainTop() {
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndDensity density = new EndDensity(map);
        int i = findSolidSample(map);
        float heightNorm = map.getHeight(x(i), z(i), SEED);
        int terrainTopY = map.levels().scale(heightNorm);
        // A few blocks above the terrain top must be air.
        float above = density.density(x(i), terrainTopY + 5, z(i), SEED);
        assertEquals(0.0F, above, 0.0F, "air expected above terrain top");
    }

    @Test
    void solidAtTerrainTop() {
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndDensity density = new EndDensity(map);
        int i = findSolidSample(map);
        float heightNorm = map.getHeight(x(i), z(i), SEED);
        int terrainTopY = map.levels().scale(heightNorm);
        // At or just below the terrain top must be solid (within the solid band
        // and above the surface, since findSolidSample guarantees height > surface).
        float at = density.density(x(i), terrainTopY, z(i), SEED);
        assertEquals(1.0F, at, 0.0F, "solid expected at terrain top");
    }

    // ----- sea-mode column shape ------------------------------------------

    @Test
    void withFloorFillsSolidBelowSurface() {
        // WITH_FLOOR: below the surface is solid seabed (where land exists).
        TestProfile profile = new TestProfile(4064, -2032, 0, 0,
                SeaMode.WITH_FLOOR, TopologyMode.CONTINENTAL_SHATTERED, false);
        EndHeightmap map = new EndHeightmap(profile, SEED);
        EndDensity density = new EndDensity(map);
        int i = findSolidSample(map);
        int surfaceY = map.levels().surfaceY;
        // A point well below the surface, on land, must be solid (seabed).
        float below = density.density(x(i), surfaceY - 100, z(i), SEED);
        assertEquals(1.0F, below, 0.0F,
                "WITH_FLOOR: solid seabed expected below surface on land");
    }

    @Test
    void noneCarvesVoidBelowSurface() {
        // NONE: below the island baseline is void (floating islands).
        TestProfile profile = new TestProfile(4064, -2032, 0, 64,
                SeaMode.NONE, TopologyMode.CONTINENTAL_SHATTERED, false);
        EndHeightmap map = new EndHeightmap(profile, SEED);
        EndDensity density = new EndDensity(map);
        int i = findSolidSample(map);
        int surfaceY = map.levels().surfaceY;
        // Below the baseline (surfaceY = 64) must be void.
        float below = density.density(x(i), surfaceY - 10, z(i), SEED);
        assertEquals(0.0F, below, 0.0F,
                "NONE: void expected below island baseline");
    }

    @Test
    void noFloorCarvesVoidBelowSeaLevel() {
        // NO_FLOOR: below sea level is void (floorless sea), even on land.
        TestProfile profile = new TestProfile(4064, -2032, 0, 0,
                SeaMode.NO_FLOOR, TopologyMode.CONTINENTAL_SHATTERED, false);
        EndHeightmap map = new EndHeightmap(profile, SEED);
        EndDensity density = new EndDensity(map);
        int i = findSolidSample(map);
        int surfaceY = map.levels().surfaceY;
        float below = density.density(x(i), surfaceY - 10, z(i), SEED);
        assertEquals(0.0F, below, 0.0F,
                "NO_FLOOR: void expected below sea level");
    }

    @Test
    void noneAndNoFloorProduceSameColumnShape() {
        // NONE and NO_FLOOR have identical solid/void structure; the difference
        // is only semantic (water above the void in NO_FLOOR). With the same
        // surface Y, both must agree at every sampled Y.
        int surfaceY = 64;
        TestProfile none = new TestProfile(4064, -2032, surfaceY, surfaceY,
                SeaMode.NONE, TopologyMode.CONTINENTAL_SHATTERED, false);
        TestProfile noFloor = new TestProfile(4064, -2032, surfaceY, surfaceY,
                SeaMode.NO_FLOOR, TopologyMode.CONTINENTAL_SHATTERED, false);
        EndDensity noneD = new EndDensity(new EndHeightmap(none, SEED));
        EndDensity noFloorD = new EndDensity(new EndHeightmap(noFloor, SEED));
        for (int i = 0; i < SAMPLES; i++) {
            for (int y = -200; y <= 2000; y += 73) {
                assertEquals(noneD.density(x(i), y, z(i), SEED),
                        noFloorD.density(x(i), y, z(i), SEED), 0.0F,
                        "NONE and NO_FLOOR must agree at y=" + y);
            }
        }
    }

    // ----- solid band bounds ----------------------------------------------

    @Test
    void solidBandLiesBetweenSurfaceAndTerrainTop() {
        // For NONE/NO_FLOOR, solid blocks must satisfy surfaceY <= y <= terrainTopY.
        // Walking the column on a known-land sample, every solid block must be
        // inside that band and every void block outside it.
        TestProfile profile = new TestProfile(4064, -2032, 0, 64,
                SeaMode.NONE, TopologyMode.CONTINENTAL_SHATTERED, false);
        EndHeightmap map = new EndHeightmap(profile, SEED);
        EndDensity density = new EndDensity(map);
        int i = findSolidSample(map);
        float heightNorm = map.getHeight(x(i), z(i), SEED);
        int terrainTopY = map.levels().scale(heightNorm);
        int surfaceY = map.levels().surfaceY;
        for (int y = surfaceY - 200; y <= terrainTopY + 200; y++) {
            float d = density.density(x(i), y, z(i), SEED);
            boolean inBand = y >= surfaceY && y <= terrainTopY;
            if (d > 0.5F) {
                assertTrue(inBand, "solid block outside [surface, terrainTop] at y=" + y);
            } else {
                assertTrue(!inBand, "void block inside [surface, terrainTop] at y=" + y);
            }
        }
    }

    @Test
    void finiteWithFloorBuildsAnExteriorSeabedBelowTheWaterColumn() {
        EndPreset preset = new EndPresetBuilder(EndPreset.defaults())
                .seaMode(SeaMode.WITH_FLOOR)
                .build();
        EndHeightmap map = new EndHeightmap(preset, SEED);
        EndDensity density = new EndDensity(map);
        OuterOcean ocean = findOuterOcean(map);
        int floorY = density.oceanFloorY(ocean.x(), ocean.z(), SEED);

        assertTrue(floorY < map.levels().surfaceFillY,
                "WITH_FLOOR ocean needs a water column above its seabed");
        assertEquals(1.0F, density.density(ocean.x(), floorY, ocean.z(), SEED), 0.0F,
                "the exterior ocean floor must be solid at its floor height");
        assertEquals(0.0F, density.density(ocean.x(), floorY + 1, ocean.z(), SEED), 0.0F,
                "the space above the seabed must remain empty for the fluid picker");
        assertEquals(1.0F, density.density(ocean.x(), map.levels().minY, ocean.z(), SEED), 0.0F,
                "WITH_FLOOR must close the ocean above the world bottom");

        int landSample = findOuterShelfSample(map);
        float landX = outerX(landSample);
        float landZ = outerZ(landSample);
        int floorBelowLandY = density.oceanFloorY(landX, landZ, SEED);
        assertEquals(1.0F, density.density(landX, floorBelowLandY, landZ, SEED), 0.0F,
                "WITH_FLOOR seabed must continue below the continental projection");
    }

    private static EndSubsurface caveSubsurface() {
        return new SubsurfaceConfig(
                AbyssPitConfig.DISABLED,
                CaveTunnelConfig.DISABLED,
                new CaveSystemConfig(true, 2400, 96, 768,
                        0.9F, 0.9F, 0.0F),
                new CaveNetworkConfig(384, 0.95F, 128,
                        4.0F, 0.6F, 0.45F, 0.6F),
                new CaveChamberConfig(0.95F, 96, 384,
                        2.2F, 0.35F, 0.7F))
                .buildRuntime(SEED);
    }

    private static int findOuterShelfSample(EndHeightmap map) {
        for (int i = 0; i < 1024; i++) {
            float worldX = outerX(i);
            float worldZ = outerZ(i);
            float landness = map.getLandness(worldX, worldZ, SEED);
            float height = map.getHeight(worldX, worldZ, SEED);
            if (landness > 0.55F && height > map.levels().surface + 0.08F) {
                return i;
            }
        }
        throw new AssertionError("no solid OUTER_CONTINENTS sample found");
    }

    private static int findRtfBandShelfSample(EndHeightmap map) {
        for (int i = 0; i < 1024; i++) {
            float worldX = outerX(i);
            float worldZ = outerZ(i);
            float landness = map.getLandness(worldX, worldZ, SEED);
            if (landness > 0.55F && map.getInlandness(worldX, worldZ, SEED) > 0.5F) {
                return i;
            }
        }
        throw new AssertionError("no RTF band shelf sample found");
    }

    private static OuterShelfEdge findOuterShelfEdge(EndHeightmap map) {
        for (int z = 2560; z <= 18944; z += 128) {
            for (int x = 2560; x <= 18944; x += 128) {
                float landness = map.getLandness(x, z, SEED);
                if (landness > 0.0F && landness <= 0.05F) {
                    return new OuterShelfEdge(x, z, landness);
                }
            }
        }
        throw new AssertionError("no near-void OUTER_CONTINENTS edge sample found");
    }

    private static RtfBandEdge findRtfBandEdge(EndHeightmap map) {
        for (int z = -16000; z <= 16000; z += 128) {
            for (int x = -16000; x <= 16000; x += 128) {
                float landness = map.getLandness(x, z, SEED);
                if (landness > 0.0F && landness <= 0.05F) {
                    return new RtfBandEdge(x, z, landness);
                }
            }
        }
        throw new AssertionError("no near-void RTF band edge sample found");
    }

    private static OuterOcean findOuterOcean(EndHeightmap map) {
        for (int z = 4096; z <= 20000; z += 128) {
            for (int x = 4096; x <= 20000; x += 128) {
                if (map.getLandness(x, z, SEED) <= 0.0F) {
                    return new OuterOcean(x, z);
                }
            }
        }
        throw new AssertionError("no exterior ocean sample found");
    }

    private static EndPreset rtfBandPreset() {
        ContinentConfig config = new ContinentConfigBuilder(ContinentConfig.defaults())
                .continentScale(3000)
                .continentJitter(0.7F)
                .continentAlgorithm(ContinentAlgorithm.RTF_MULTI)
                .build();
        return new endterraforged.world.config.EndPresetBuilder(EndPreset.defaults())
                .continentConfig(config)
                .build();
    }

    private static float outerX(int index) {
        return 6400.0F + (index % 32) * 512.0F;
    }

    private static float outerZ(int index) {
        return 6400.0F + (index / 32) * 512.0F;
    }

    private static int defaultOuterShelfDensityBits(EndDensity density, int sample) {
        float worldX = 6400.0F + (sample & 31) * 192.0F;
        float worldZ = 6400.0F + ((sample >>> 5) & 15) * 192.0F;
        int worldY = -256 + ((sample * 73) & 511);
        return Float.floatToIntBits(density.density(worldX, worldY, worldZ, SEED));
    }

    private static float uncachedDensity(EndHeightmap heightmap, EndSubsurface subsurface,
                                         float x, int worldY, float z, int seed) {
        EndLandmassVolume volume = heightmap.landmassVolume();
        float landness = heightmap.getLandness(x, z, seed);
        if (landness <= 0.0F) {
            return 0.0F;
        }
        float heightNorm = heightmap.getHeight(x, z, seed);
        float yNorm = heightmap.levels().scale(worldY);
        if (yNorm > heightNorm) {
            return 0.0F;
        }
        if (!volume.isFinite() && !heightmap.seaMode().hasFloor() && yNorm <= heightmap.levels().surface) {
            return 0.0F;
        }
        if (volume.isFinite() && yNorm < volume.underside(x, z, landness, heightNorm)) {
            return 0.0F;
        }
        return subsurface.carves(x, z, landness, yNorm, heightNorm,
                heightmap.levels().worldHeight) ? 0.0F : 1.0F;
    }

    private record CaveSample(int index, int y) {
    }

    private record OuterShelfEdge(float x, float z, float landness) {
    }

    private record RtfBandEdge(float x, float z, float landness) {
    }

    private record OuterOcean(float x, float z) {
    }
}
