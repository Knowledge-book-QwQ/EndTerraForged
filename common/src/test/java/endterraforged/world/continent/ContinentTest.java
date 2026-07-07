package endterraforged.world.continent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import endterraforged.world.noise.Noise;
import endterraforged.world.noise.Noises;
import endterraforged.world.noise.Perlin;
import endterraforged.world.noise.domain.Domain;
import endterraforged.world.noise.domain.Domains;

/**
 * Contract tests for the EndTerraForged continent layer: the {@link Continent}
 * packId helper, the {@link Domains#identity()} no-warp domain, and the two
 * topology implementations.
 *
 * <p>Asserts invariants (range, determinism, seed sensitivity, warp effect,
 * mapAll reach) plus the per-mode shape contract: islands scatter + taper to
 * void between, shattered carves rifts while keeping solid centres. No magic
 * numbers — the algorithms are End-original, so the tests pin the contract
 * that EndHeightmap will rely on (landness in {@code [0,1]}, void where the
 * continent says no land).</p>
 */
class ContinentTest {

    private static final int SEED = 42;
    private static final int SAMPLES = 400;

    private static float x(int i) { return i * 7.3F; }
    private static float z(int i) { return i * 11.1F; }

    /** A perlin-driven warp strong enough to deform the cell grid visibly. */
    private static Domain perlinWarp(int seed) {
        return Domains.domain(
                Noises.perlin(seed, 100, 3),
                Noises.perlin(seed + 1, 100, 3),
                Noises.constant(80.0F));
    }

    private static int countPerlinLeaves(Noise noise) {
        int[] count = {0};
        Noise.Visitor counter = n -> {
            if (n instanceof Perlin) {
                count[0]++;
            }
            return n;
        };
        noise.mapAll(counter);
        return count[0];
    }

    // ----- Continent helper ------------------------------------------------

    @Test
    void zigzagMatchesProtobufOrdering() {
        assertEquals(0, Continent.zigzag(0));
        assertEquals(1, Continent.zigzag(-1));
        assertEquals(2, Continent.zigzag(1));
        assertEquals(3, Continent.zigzag(-2));
        assertEquals(4, Continent.zigzag(2));
    }

    @Test
    void packIdIsInjectiveAcrossSignedCoords() {
        Set<Long> ids = new HashSet<>();
        for (int cx = -64; cx <= 64; cx++) {
            for (int cz = -64; cz <= 64; cz++) {
                long id = Continent.packId(cx, cz);
                assertTrue(ids.add(id), "packId collision at (" + cx + "," + cz + "): " + id);
            }
        }
    }

    // ----- Domains.identity ------------------------------------------------

    @Test
    void identityDomainIsZeroOffsetSingleton() {
        Domain a = Domains.identity();
        Domain b = Domains.identity();
        assertSame(a, b, "identity() should return the same singleton");
        for (int i = 0; i < 20; i++) {
            assertEquals(0.0F, a.getOffsetX(x(i), z(i), SEED), 0.0F);
            assertEquals(0.0F, a.getOffsetZ(x(i), z(i), SEED), 0.0F);
            assertEquals(x(i), a.getX(x(i), z(i), SEED), 0.0F);
            assertEquals(z(i), a.getZ(x(i), z(i), SEED), 0.0F);
        }
        assertSame(a, a.mapAll(n -> n), "identity mapAll must return itself");
    }

    // ----- IslandsContinent ------------------------------------------------

    @Test
    void islandsOutputStaysInUnitRange() {
        IslandsContinent islands = new IslandsContinent(
                1.0F / 300.0F, 1.0F, 0.6F, 0.5F, perlinWarp(SEED));
        for (int i = 0; i < SAMPLES; i++) {
            float v = islands.compute(x(i), z(i), SEED);
            assertTrue(v >= 0.0F - 1e-5F && v <= 1.0F + 1e-5F, "islands out of range: " + v);
        }
    }

    @Test
    void islandsScatterOneYieldsAlmostNoLand() {
        // scatter=1.0: a cell hosts an island only if its hash >= 1.0, which
        // essentially never happens, so the field should be ~all void.
        IslandsContinent islands = new IslandsContinent(
                1.0F / 200.0F, 1.0F, 0.6F, 1.0F, Domains.identity());
        int land = 0;
        for (int i = 0; i < SAMPLES; i++) {
            if (islands.compute(x(i), z(i), SEED) > 1e-4F) {
                land++;
            }
        }
        assertTrue(land < SAMPLES * 0.02, "scatter=1 should produce <2% land, got " + land);
    }

    @Test
    void islandsScatterZeroProducesIslandsWithVoidsBetween() {
        // scatter=0.0: every cell hosts an island, so centres reach high
        // landness and inter-cell boundaries fall back toward void.
        IslandsContinent islands = new IslandsContinent(
                1.0F / 120.0F, 1.0F, 0.6F, 0.0F, Domains.identity());
        float max = 0.0F;
        float min = 1.0F;
        for (int i = 0; i < SAMPLES; i++) {
            float v = islands.compute(x(i), z(i), SEED);
            max = Math.max(max, v);
            min = Math.min(min, v);
        }
        assertTrue(max > 0.8F, "island centres should reach high landness, max=" + max);
        assertTrue(min < 0.1F, "inter-cell voids should drop near 0, min=" + min);
    }

    @Test
    void islandsIsDeterministic() {
        IslandsContinent islands = new IslandsContinent(
                1.0F / 250.0F, 1.0F, 0.6F, 0.5F, perlinWarp(SEED));
        for (int i = 0; i < SAMPLES; i++) {
            assertEquals(islands.compute(x(i), z(i), SEED), islands.compute(x(i), z(i), SEED), 0.0F);
        }
    }

    @Test
    void islandsIsSeedSensitive() {
        IslandsContinent islands = new IslandsContinent(
                1.0F / 250.0F, 1.0F, 0.6F, 0.3F, perlinWarp(SEED));
        boolean anyDifference = false;
        for (int i = 0; i < SAMPLES; i++) {
            if (Float.floatToIntBits(islands.compute(x(i), z(i), 1))
                    != Float.floatToIntBits(islands.compute(x(i), z(i), 2))) {
                anyDifference = true;
                break;
            }
        }
        assertTrue(anyDifference, "different seeds should produce different island layouts");
    }

    @Test
    void islandsWarpChangesOutputVsIdentity() {
        IslandsContinent plain = new IslandsContinent(
                1.0F / 250.0F, 1.0F, 0.6F, 0.4F, Domains.identity());
        IslandsContinent warped = new IslandsContinent(
                1.0F / 250.0F, 1.0F, 0.6F, 0.4F, perlinWarp(SEED));
        boolean anyDifference = false;
        for (int i = 0; i < SAMPLES; i++) {
            if (Float.floatToIntBits(plain.compute(x(i), z(i), SEED))
                    != Float.floatToIntBits(warped.compute(x(i), z(i), SEED))) {
                anyDifference = true;
                break;
            }
        }
        assertTrue(anyDifference, "warp should deform the island grid vs identity");
    }

    @Test
    void islandsMapAllReachesWarpDrivers() {
        IslandsContinent islands = new IslandsContinent(
                1.0F / 250.0F, 1.0F, 0.6F, 0.4F, perlinWarp(SEED));
        // perlinWarp wires two perlin drivers; mapAll must reach both.
        assertTrue(countPerlinLeaves(islands) >= 2, "mapAll should reach the warp's perlin drivers");
    }

    // ----- ContinentalShatteredContinent -----------------------------------

    @Test
    void shatteredOutputStaysInUnitRange() {
        ContinentalShatteredContinent continent = new ContinentalShatteredContinent(
                1.0F / 600.0F, 1.0F, 0.6F, 1.0F, perlinWarp(SEED));
        for (int i = 0; i < SAMPLES; i++) {
            float v = continent.compute(x(i), z(i), SEED);
            assertTrue(v >= 0.0F - 1e-5F && v <= 1.0F + 1e-5F, "shattered out of range: " + v);
        }
    }

    @Test
    void shatteredNoCarveReturnsSolidContinent() {
        // riftStrength=0 and riftThreshold=1 both short-circuit to solid land.
        ContinentalShatteredContinent noStrength = new ContinentalShatteredContinent(
                1.0F / 600.0F, 1.0F, 0.6F, 0.0F, perlinWarp(SEED));
        ContinentalShatteredContinent fullThreshold = new ContinentalShatteredContinent(
                1.0F / 600.0F, 1.0F, 1.0F, 1.0F, perlinWarp(SEED));
        for (int i = 0; i < SAMPLES; i++) {
            assertEquals(1.0F, noStrength.compute(x(i), z(i), SEED), 0.0F);
            assertEquals(1.0F, fullThreshold.compute(x(i), z(i), SEED), 0.0F);
        }
    }

    @Test
    void shatteredCarvesRiftsButKeepsSolidCentres() {
        // Defaults: solid centres (landness==1) must survive while rifts carve
        // toward 0 somewhere — otherwise the topology is degenerate.
        ContinentalShatteredContinent continent = new ContinentalShatteredContinent(
                1.0F / 400.0F, 1.0F, 0.55F, 1.0F, perlinWarp(SEED));
        float max = 0.0F;
        float min = 1.0F;
        for (int i = 0; i < SAMPLES; i++) {
            float v = continent.compute(x(i), z(i), SEED);
            max = Math.max(max, v);
            min = Math.min(min, v);
        }
        assertEquals(1.0F, max, 1e-5F, "solid centres should remain at full landness");
        assertTrue(min < 0.3F, "rifts should carve below 0.3, min=" + min);
    }

    @Test
    void shatteredIsDeterministic() {
        ContinentalShatteredContinent continent = new ContinentalShatteredContinent(
                1.0F / 500.0F, 1.0F, 0.6F, 1.0F, perlinWarp(SEED));
        for (int i = 0; i < SAMPLES; i++) {
            assertEquals(continent.compute(x(i), z(i), SEED),
                    continent.compute(x(i), z(i), SEED), 0.0F);
        }
    }

    @Test
    void shatteredIsSeedSensitive() {
        ContinentalShatteredContinent continent = new ContinentalShatteredContinent(
                1.0F / 500.0F, 1.0F, 0.6F, 1.0F, perlinWarp(SEED));
        boolean anyDifference = false;
        for (int i = 0; i < SAMPLES; i++) {
            if (Float.floatToIntBits(continent.compute(x(i), z(i), 1))
                    != Float.floatToIntBits(continent.compute(x(i), z(i), 2))) {
                anyDifference = true;
                break;
            }
        }
        assertTrue(anyDifference, "different seeds should produce different rift networks");
    }

    @Test
    void shatteredWarpChangesOutputVsIdentity() {
        ContinentalShatteredContinent plain = new ContinentalShatteredContinent(
                1.0F / 500.0F, 1.0F, 0.6F, 1.0F, Domains.identity());
        ContinentalShatteredContinent warped = new ContinentalShatteredContinent(
                1.0F / 500.0F, 1.0F, 0.6F, 1.0F, perlinWarp(SEED));
        boolean anyDifference = false;
        for (int i = 0; i < SAMPLES; i++) {
            if (Float.floatToIntBits(plain.compute(x(i), z(i), SEED))
                    != Float.floatToIntBits(warped.compute(x(i), z(i), SEED))) {
                anyDifference = true;
                break;
            }
        }
        assertTrue(anyDifference, "warp should deform the rift network vs identity");
    }

    @Test
    void shatteredMapAllReachesWarpDrivers() {
        ContinentalShatteredContinent continent = new ContinentalShatteredContinent(
                1.0F / 500.0F, 1.0F, 0.6F, 1.0F, perlinWarp(SEED));
        assertTrue(countPerlinLeaves(continent) >= 2, "mapAll should reach the warp's perlin drivers");
    }

    @Test
    void continentModuleIsANoise() {
        // Sanity: the interface contract is satisfied and both impls report a
        // [0,1] range, which is what EndHeightmap will assume when multiplying.
        IslandsContinent islands = new IslandsContinent(
                1.0F / 300.0F, 1.0F, 0.6F, 0.5F, Domains.identity());
        ContinentalShatteredContinent continent = new ContinentalShatteredContinent(
                1.0F / 500.0F, 1.0F, 0.6F, 1.0F, Domains.identity());
        assertNotNull(islands.mapAll(n -> n));
        assertNotNull(continent.mapAll(n -> n));
        assertEquals(0.0F, islands.minValue(), 0.0F);
        assertEquals(1.0F, islands.maxValue(), 0.0F);
        assertEquals(0.0F, continent.minValue(), 0.0F);
        assertEquals(1.0F, continent.maxValue(), 0.0F);
    }
}
