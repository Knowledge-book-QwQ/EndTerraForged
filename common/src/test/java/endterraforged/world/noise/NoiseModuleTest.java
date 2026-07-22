package endterraforged.world.noise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Contract tests for the ported gradient / ridge noise modules.
 *
 * <p>Asserts invariants (range, reproducibility, seed sensitivity, interpolation
 * distinctness, octaves-clamp safety) rather than magic numbers: the algorithms
 * are byte-identical to upstream RTF, so any drift would still fail the
 * invariants while a clean re-port passes. The octaves-clamp cases specifically
 * guard the SimplexRidge bugfix (upstream omitted the clamp that PerlinRidge
 * had).</p>
 */
class NoiseModuleTest {

    private static final int SEED = 42;
    private static final int SAMPLES = 300;

    private static float x(int i) { return i * 7.3F; }
    private static float z(int i) { return i * 11.1F; }

    @Test
    void perlinInRange() {
        Perlin perlin = new Perlin(SEED, 1.0F / 200.0F, 4, 2.0F, 0.5F, Interpolation.CURVE4);
        for (int i = 0; i < SAMPLES; i++) {
            float v = perlin.compute(x(i), z(i), SEED);
            assertTrue(v >= perlin.minValue() - 1e-4f && v <= perlin.maxValue() + 1e-4f,
                    "Perlin out of range: " + v);
        }
    }

    @Test
    void perlin2InRangeAndUsesItsStoredSeed() {
        Perlin2 perlin = new Perlin2(SEED, 1.0F / 200.0F, 4, 2.0F, 0.5F, Interpolation.CURVE4);
        boolean differsFromPerlin = false;
        Perlin standard = new Perlin(SEED, 1.0F / 200.0F, 4, 2.0F, 0.5F, Interpolation.CURVE4);
        for (int i = 0; i < SAMPLES; i++) {
            float value = perlin.compute(x(i), z(i), 1);
            assertTrue(value >= perlin.minValue() - 1e-4F && value <= perlin.maxValue() + 1e-4F,
                    "Perlin2 out of range: " + value);
            assertEquals(Float.floatToIntBits(value),
                    Float.floatToIntBits(perlin.compute(x(i), z(i), 2)),
                    "Perlin2 should ignore the passed seed");
            differsFromPerlin |= Float.floatToIntBits(value)
                    != Float.floatToIntBits(standard.compute(x(i), z(i), 0));
        }
        assertTrue(differsFromPerlin, "Perlin2 must retain its distinct 24-gradient kernel");
    }

    @Test
    void perlin2FactoryPreservesRequestedParametersAndMapAll() {
        Noise noise = Noises.perlin2(SEED, 320, 5, 4.33F, 0.26F);
        assertTrue(noise instanceof Perlin2);
        Perlin2 perlin = (Perlin2) noise;
        assertEquals(SEED, perlin.seed());
        assertEquals(1.0F / 320.0F, perlin.frequency(), 0.0F);
        assertEquals(5, perlin.octaves());
        assertEquals(4.33F, perlin.lacunarity(), 0.0F);
        assertEquals(0.26F, perlin.gain(), 0.0F);
        assertEquals(Interpolation.CURVE3, perlin.interpolation());
        assertEquals(perlin, perlin.mapAll(input -> input));
    }

    @Test
    void simplexInRange() {
        Simplex simplex = new Simplex(1.0F / 200.0F, 4, 2.0F, 0.5F, Interpolation.CURVE4);
        for (int i = 0; i < SAMPLES; i++) {
            float v = simplex.compute(x(i), z(i), SEED);
            assertTrue(v >= simplex.minValue() - 1e-4f && v <= simplex.maxValue() + 1e-4f,
                    "Simplex out of range: " + v);
        }
    }

    @Test
    void perlinRidgeInRange() {
        // RTF makeMountains1 recipe: perlinRidge(seed, 410, 4, 2.35, 1.15)
        PerlinRidge ridge = new PerlinRidge(1.0F / 410.0F, 4, 2.35F, 1.15F, Interpolation.CURVE4);
        for (int i = 0; i < SAMPLES; i++) {
            float v = ridge.compute(x(i), z(i), SEED);
            assertTrue(v >= ridge.minValue() - 1e-4f && v <= ridge.maxValue() + 1e-4f,
                    "PerlinRidge out of range: " + v);
        }
    }

    @Test
    void billowInRangeAndDeterministic() {
        Billow billow = new Billow(1.0F / 300.0F, 4, 2.0F, 0.5F, Interpolation.CURVE3);
        for (int i = 0; i < SAMPLES; i++) {
            float value = billow.compute(x(i), z(i), SEED);
            assertTrue(value >= billow.minValue() - 1e-4F && value <= billow.maxValue() + 1e-4F,
                    "Billow out of range: " + value);
            assertEquals(value, billow.compute(x(i), z(i), SEED), 0.0F,
                    "Billow must be deterministic");
        }
    }

    @Test
    void cubicInRangeAndSeedSensitive() {
        Cubic cubic = new Cubic(1.0F / 128.0F, 3, 2.0F, 0.5F);
        boolean anyDifference = false;
        for (int i = 0; i < SAMPLES; i++) {
            float first = cubic.compute(x(i), z(i), SEED);
            assertTrue(first >= cubic.minValue() - 1e-4F && first <= cubic.maxValue() + 1e-4F,
                    "Cubic out of range: " + first);
            anyDifference |= Float.floatToIntBits(first)
                    != Float.floatToIntBits(cubic.compute(x(i), z(i), SEED + 1));
        }
        assertTrue(anyDifference, "Cubic must respect the passed seed");
    }

    @Test
    void terraceStaysFiniteAndWithinSourceRange() {
        Noise terrace = Noises.terrace(
                Noises.perlin(SEED, 500, 2),
                0.9F, 0.15F, 0.35F, 0.4F, 4);
        for (int i = 0; i < SAMPLES; i++) {
            float value = terrace.compute(x(i), z(i), SEED);
            assertTrue(Float.isFinite(value), "Terrace must stay finite");
            assertTrue(value >= terrace.minValue() - 1e-4F && value <= terrace.maxValue() + 1e-4F,
                    "Terrace out of source range: " + value);
        }
    }

    @Test
    void simplexRidgeInRange() {
        SimplexRidge ridge = new SimplexRidge(1.0F / 200.0F, 4, 2.0F, 0.5F, Interpolation.CURVE4);
        for (int i = 0; i < SAMPLES; i++) {
            float v = ridge.compute(x(i), z(i), SEED);
            assertTrue(v >= ridge.minValue() - 1e-4f && v <= ridge.maxValue() + 1e-4f,
                    "SimplexRidge out of range: " + v);
        }
    }

    @Test
    void perlinIsDeterministic() {
        Perlin perlin = new Perlin(SEED, 1.0F / 200.0F, 4, 2.0F, 0.5F, Interpolation.CURVE4);
        for (int i = 0; i < SAMPLES; i++) {
            assertEquals(perlin.compute(x(i), z(i), SEED), perlin.compute(x(i), z(i), SEED), 0.0f,
                    "Perlin should be deterministic");
        }
    }

    @Test
    void perlinRidgeIsDeterministic() {
        PerlinRidge ridge = new PerlinRidge(1.0F / 410.0F, 4, 2.35F, 1.15F, Interpolation.CURVE4);
        for (int i = 0; i < SAMPLES; i++) {
            assertEquals(ridge.compute(x(i), z(i), SEED), ridge.compute(x(i), z(i), SEED), 0.0f,
                    "PerlinRidge should be deterministic");
        }
    }

    @Test
    void simplexSeedSensitive() {
        // Simplex uses the passed seed (offset per octave) — different seeds
        // must diverge somewhere.
        Simplex simplex = new Simplex(1.0F / 200.0F, 4, 2.0F, 0.5F, Interpolation.CURVE4);
        boolean anyDifference = false;
        for (int i = 0; i < SAMPLES; i++) {
            if (Float.floatToIntBits(simplex.compute(x(i), z(i), 1))
                    != Float.floatToIntBits(simplex.compute(x(i), z(i), 2))) {
                anyDifference = true;
                break;
            }
        }
        assertTrue(anyDifference, "different passed seeds should produce different Simplex output");
    }

    @Test
    void perlinRidgeSeedSensitive() {
        // PerlinRidge forwards seed+octave to Perlin.sample — seed-sensitive.
        PerlinRidge ridge = new PerlinRidge(1.0F / 410.0F, 4, 2.35F, 1.15F, Interpolation.CURVE4);
        boolean anyDifference = false;
        for (int i = 0; i < SAMPLES; i++) {
            if (Float.floatToIntBits(ridge.compute(x(i), z(i), 1))
                    != Float.floatToIntBits(ridge.compute(x(i), z(i), 2))) {
                anyDifference = true;
                break;
            }
        }
        assertTrue(anyDifference, "different passed seeds should produce different PerlinRidge output");
    }

    @Test
    void differentInterpolationsAreDistinct() {
        // CURVE3 vs CURVE4 should diverge somewhere — quintic vs hermite.
        Perlin curve3 = new Perlin(SEED, 1.0F / 200.0F, 4, 2.0F, 0.5F, Interpolation.CURVE3);
        Perlin curve4 = new Perlin(SEED, 1.0F / 200.0F, 4, 2.0F, 0.5F, Interpolation.CURVE4);
        boolean anyDifference = false;
        for (int i = 0; i < SAMPLES; i++) {
            if (Float.floatToIntBits(curve3.compute(x(i), z(i), SEED))
                    != Float.floatToIntBits(curve4.compute(x(i), z(i), SEED))) {
                anyDifference = true;
                break;
            }
        }
        assertTrue(anyDifference, "CURVE3 and CURVE4 should produce different output");
    }

    @Test
    void perlinRidgeOctavesClampIsSafe() {
        // The clamp's job is to prevent ArrayIndexOutOfBoundsException when
        // octaves > 30 (the spectral-weights array length). We assert the
        // clamp took effect and that compute runs without throwing.
        //
        // Note: we use a gentle lacunarity (1.5) and sample near the origin
        // because gradient noise has an inherent float-precision wall at high
        // octave counts — x *= lacunarity for 30 iterations with lacunarity
        // 2.35 pushes x past Integer.MAX_VALUE, saturating floor() and
        // producing NaN via interpQuintic(huge)*0. That wall is upstream
        // behaviour (RTF uses octaves<=4 in practice), not our clamp's concern.
        PerlinRidge ridge = new PerlinRidge(1.0F, 64, 1.5F, 0.5F, Interpolation.CURVE4);
        assertEquals(30, ridge.octaves(), "octaves should be clamped to 30");
        for (int i = 0; i < 20; i++) {
            float v = ridge.compute(i * 0.13F, i * 0.17F, SEED);
            assertTrue(Float.isFinite(v), "clamped PerlinRidge should be finite: " + v);
            assertTrue(v >= ridge.minValue() - 1e-4f && v <= ridge.maxValue() + 1e-4f,
                    "clamped PerlinRidge out of range: " + v);
        }
    }

    @Test
    void simplexRidgeOctavesClampIsSafe() {
        // Bugfix guard: upstream SimplexRidge omitted the clamp that PerlinRidge
        // had; octaves > 30 would ArrayIndexOutOfBoundsException. We clamp, so
        // constructing with octaves=64 must not throw and compute must succeed.
        SimplexRidge ridge = new SimplexRidge(1.0F, 64, 1.5F, 0.5F, Interpolation.CURVE4);
        assertEquals(30, ridge.octaves(), "octaves should be clamped to 30 (bugfix)");
        for (int i = 0; i < 20; i++) {
            float v = ridge.compute(i * 0.13F, i * 0.17F, SEED);
            assertTrue(Float.isFinite(v), "clamped SimplexRidge should be finite: " + v);
            assertTrue(v >= ridge.minValue() - 1e-4f && v <= ridge.maxValue() + 1e-4f,
                    "clamped SimplexRidge out of range: " + v);
        }
    }
}
