package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.climate.ClimateModulator;
import endterraforged.world.climate.EndClimate;
import endterraforged.world.config.TestProfile;
import endterraforged.world.lake.EndLakeMap;
import endterraforged.world.river.EndRiverMap;

/**
 * Stage-4.2 integration: {@link EndHeightmap#withRivers} wires the river
 * post-processor into the public {@link EndHeightmap#getHeight} channel, and
 * {@link EndHeightmap#getTerrainHeight} exposes the raw pre-river field the
 * carver samples from — without recursing.
 */
class EndHeightmapRiverIntegrationTest {

    private static final int SEED = 42;
    private static final int SAMPLES = 600;

    private static float x(int i) { return i * 7.3F; }
    private static float z(int i) { return i * 11.1F; }

    @Test
    void withoutRiversGetHeightEqualsGetTerrainHeight() {
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        for (int i = 0; i < SAMPLES; i++) {
            assertEquals(map.getTerrainHeight(x(i), z(i), SEED),
                    map.getHeight(x(i), z(i), SEED), 0.0F,
                    "without a river map, getHeight must equal getTerrainHeight");
        }
    }

    @Test
    void withRiversGetHeightCarvesAtLeastSomeLand() {
        EndHeightmap base = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndHeightmap withRivers = base.withRivers(new EndRiverMap(380, 1.0F, 12, 90, 0.04F));

        int carved = 0;
        int land = 0;
        for (int i = 0; i < SAMPLES; i++) {
            if (base.getLandness(x(i), z(i), SEED) <= 0.0F) continue;
            land++;
            float raw = base.getTerrainHeight(x(i), z(i), SEED);
            float cut = withRivers.getHeight(x(i), z(i), SEED);
            if (cut < raw - 1e-4F) carved++;
        }
        assertTrue(land > 0, "should have some land samples");
        assertTrue(carved > 0,
                "with riverChance=1, getHeight should carve some land, got 0/" + land);
    }

    @Test
    void withRiversNeverRaisesTerrain() {
        EndHeightmap base = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndHeightmap withRivers = base.withRivers(EndRiverMap.defaults());
        for (int i = 0; i < SAMPLES; i++) {
            float raw = base.getTerrainHeight(x(i), z(i), SEED);
            float cut = withRivers.getHeight(x(i), z(i), SEED);
            assertTrue(cut <= raw + 1e-5F,
                    "river-carved getHeight should not exceed raw terrain: " + cut + " > " + raw);
        }
    }

    @Test
    void withRiversLeavesVoidUntouched() {
        EndHeightmap base = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndHeightmap withRivers = base.withRivers(EndRiverMap.defaults());
        for (int i = 0; i < SAMPLES; i++) {
            if (base.getLandness(x(i), z(i), SEED) > 0.0F) continue;
            assertEquals(base.getTerrainHeight(x(i), z(i), SEED),
                    withRivers.getHeight(x(i), z(i), SEED), 0.0F,
                    "void columns must not be carved");
        }
    }

    @Test
    void withRiversIsImmutableAndDoesNotMutateBase() {
        EndHeightmap base = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndHeightmap withRivers = base.withRivers(EndRiverMap.defaults());
        // The original base must still behave as raw (no rivers): getHeight ==
        // getTerrainHeight everywhere. withRivers returns a new instance; the
        // caller's base is untouched.
        for (int i = 0; i < SAMPLES; i++) {
            assertEquals(base.getTerrainHeight(x(i), z(i), SEED),
                    base.getHeight(x(i), z(i), SEED), 0.0F,
                    "withRivers must not mutate the original heightmap");
        }
        // withRivers itself is a distinct instance — sanity check it actually
        // holds a reference to a river map (otherwise the test below is vacuous).
        // The proof that withRivers *does something* is in
        // withRiversGetHeightCarvesAtLeastSomeLand (riverChance=1 forces carving).
        assertTrue(withRivers != base, "withRivers must return a new instance");
    }

    @Test
    void withRiversNullDetaches() {
        EndHeightmap base = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndHeightmap withRivers = base.withRivers(EndRiverMap.defaults());
        EndHeightmap detached = withRivers.withRivers(null);
        for (int i = 0; i < SAMPLES; i++) {
            assertEquals(detached.getTerrainHeight(x(i), z(i), SEED),
                    detached.getHeight(x(i), z(i), SEED), 0.0F,
                    "withRivers(null) should detach the river post-processor");
        }
    }

    @Test
    void getHeightDoesNotStackOverflowOnDenseRivers() {
        // riverChance=1 forces every cell to host a river, so getHeight hits
        // modifyHeight on every land sample. If modifyHeight recursed through
        // getHeight instead of getTerrainHeight, this would stack-overflow.
        EndHeightmap withRivers = new EndHeightmap(TestProfile.defaultEnd(), SEED)
                .withRivers(new EndRiverMap(380, 1.0F, 12, 90, 0.04F));
        for (int i = 0; i < SAMPLES; i++) {
            float h = withRivers.getHeight(x(i), z(i), SEED);
            assertTrue(Float.isFinite(h), "getHeight must be finite (no recursion / NaN)");
        }
    }

    @Test
    void withRiversPreservesLevelsAndSeaMode() {
        EndHeightmap base = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndHeightmap withRivers = base.withRivers(EndRiverMap.defaults());
        assertEquals(base.levels(), withRivers.levels(),
                "withRivers must preserve EndLevels");
        assertEquals(base.seaMode(), withRivers.seaMode(),
                "withRivers must preserve SeaMode");
        assertEquals(base.continent().getClass(), withRivers.continent().getClass(),
                "withRivers must preserve the continent type");
    }

    @Test
    void withRiversIsDeterministic() {
        EndHeightmap a = new EndHeightmap(TestProfile.defaultEnd(), SEED)
                .withRivers(EndRiverMap.defaults());
        EndHeightmap b = new EndHeightmap(TestProfile.defaultEnd(), SEED)
                .withRivers(EndRiverMap.defaults());
        for (int i = 0; i < SAMPLES; i++) {
            // Compare bits, not floats — deterministic means bit-identical.
            assertEquals(Float.floatToIntBits(a.getHeight(x(i), z(i), SEED)),
                    Float.floatToIntBits(b.getHeight(x(i), z(i), SEED)),
                    "withRivers must be deterministic across instances");
        }
    }

    @Test
    void knownLandnessCarversMatchStandaloneLookupPath() {
        EndHeightmap map = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndRiverMap rivers = EndRiverMap.defaults();
        EndLakeMap lakes = EndLakeMap.defaults();

        for (int i = 0; i < SAMPLES; i++) {
            float x = x(i);
            float z = z(i);
            float input = map.getTerrainHeight(x, z, SEED);
            float landness = map.getLandness(x, z, SEED);

            assertEquals(rivers.modifyHeight(x, z, SEED, map, input),
                    rivers.modifyHeight(x, z, SEED, map, input, landness), 0.0F,
                    "known landness must preserve river carving");
            assertEquals(lakes.modifyHeight(x, z, SEED, map, input),
                    lakes.modifyHeight(x, z, SEED, map, input, landness), 0.0F,
                    "known landness must preserve lake carving");
        }
    }

    // ----- stage-4.5: lake integration + river→lake chaining --------------

    @Test
    void withLakesGetHeightCarvesAtLeastSomeLand() {
        EndHeightmap base = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndHeightmap withLakes = base.withLakes(new EndLakeMap(620, 1.0F, 28, 75, 0.06F));

        int carved = 0;
        int land = 0;
        for (int i = 0; i < SAMPLES; i++) {
            if (base.getLandness(x(i), z(i), SEED) <= 0.0F) continue;
            land++;
            float raw = base.getTerrainHeight(x(i), z(i), SEED);
            float cut = withLakes.getHeight(x(i), z(i), SEED);
            if (cut < raw - 1e-4F) carved++;
        }
        assertTrue(land > 0, "should have some land samples");
        assertTrue(carved > 0,
                "with lakeChance=1, getHeight should carve some land, got 0/" + land);
    }

    @Test
    void withLakesNeverRaisesTerrain() {
        EndHeightmap base = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndHeightmap withLakes = base.withLakes(EndLakeMap.defaults());
        for (int i = 0; i < SAMPLES; i++) {
            float raw = base.getTerrainHeight(x(i), z(i), SEED);
            float cut = withLakes.getHeight(x(i), z(i), SEED);
            assertTrue(cut <= raw + 1e-5F,
                    "lake-carved getHeight should not exceed raw terrain: " + cut + " > " + raw);
        }
    }

    @Test
    void withLakesLeavesVoidUntouched() {
        EndHeightmap base = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndHeightmap withLakes = base.withLakes(EndLakeMap.defaults());
        for (int i = 0; i < SAMPLES; i++) {
            if (base.getLandness(x(i), z(i), SEED) > 0.0F) continue;
            assertEquals(base.getTerrainHeight(x(i), z(i), SEED),
                    withLakes.getHeight(x(i), z(i), SEED), 0.0F,
                    "void columns must not be carved by lakes");
        }
    }

    @Test
    void withLakesDoesNotMutateBase() {
        EndHeightmap base = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndHeightmap withLakes = base.withLakes(EndLakeMap.defaults());
        for (int i = 0; i < SAMPLES; i++) {
            assertEquals(base.getTerrainHeight(x(i), z(i), SEED),
                    base.getHeight(x(i), z(i), SEED), 0.0F,
                    "withLakes must not mutate the original heightmap");
        }
        assertTrue(withLakes != base, "withLakes must return a new instance");
    }

    @Test
    void withLakesNullDetaches() {
        EndHeightmap withLakes = new EndHeightmap(TestProfile.defaultEnd(), SEED)
                .withLakes(EndLakeMap.defaults());
        EndHeightmap detached = withLakes.withLakes(null);
        for (int i = 0; i < SAMPLES; i++) {
            assertEquals(detached.getTerrainHeight(x(i), z(i), SEED),
                    detached.getHeight(x(i), z(i), SEED), 0.0F,
                    "withLakes(null) should detach the lake post-processor");
        }
    }

    @Test
    void riverAndLakeChainDoesNotOverflowAndBothCanCarve() {
        // Chain river → lake: getHeight runs river carver, then lake carver on
        // the river-carved height. With both at chance=1, every land sample
        // hits both carvers. If either recursed through getHeight instead of
        // getTerrainHeight for its raw sampling, this would stack-overflow.
        EndHeightmap chained = new EndHeightmap(TestProfile.defaultEnd(), SEED)
                .withRivers(new EndRiverMap(380, 1.0F, 12, 90, 0.04F))
                .withLakes(new EndLakeMap(620, 1.0F, 28, 75, 0.06F));
        int carvedBySomething = 0;
        for (int i = 0; i < SAMPLES; i++) {
            float h = chained.getHeight(x(i), z(i), SEED);
            assertTrue(Float.isFinite(h), "chained getHeight must be finite (no recursion)");
            if (chained.getLandness(x(i), z(i), SEED) > 0.0F
                    && h < chained.getTerrainHeight(x(i), z(i), SEED) - 1e-4F) {
                carvedBySomething++;
            }
        }
        assertTrue(carvedBySomething > 0,
                "river+lake chain should carve at least some land");
    }

    @Test
    void fullHeightChainNeverDropsBelowReferenceSurface() {
        EndHeightmap chained = new EndHeightmap(TestProfile.defaultEnd(), SEED)
                .withClimate(ClimateModulator.defaults(EndClimate.defaults(SEED)))
                .withRivers(new EndRiverMap(380, 1.0F, 12, 90, 0.4F))
                .withLakes(new EndLakeMap(620, 1.0F, 28, 75, 0.4F));
        float surface = chained.levels().surface;

        for (int i = 0; i < SAMPLES; i++) {
            assertTrue(chained.getHeight(x(i), z(i), SEED) >= surface,
                    "climate, river, and lake chaining must preserve the surface lower bound");
        }
    }

    @Test
    void withRiversAndLakesPreservesLevelsAndSeaMode() {
        EndHeightmap base = new EndHeightmap(TestProfile.defaultEnd(), SEED);
        EndHeightmap both = base
                .withRivers(EndRiverMap.defaults())
                .withLakes(EndLakeMap.defaults());
        assertEquals(base.levels(), both.levels(), "chain must preserve EndLevels");
        assertEquals(base.seaMode(), both.seaMode(), "chain must preserve SeaMode");
    }
}
