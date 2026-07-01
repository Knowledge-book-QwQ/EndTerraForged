package endterraforged.world.noise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.noise.domain.Domain;
import endterraforged.world.noise.domain.Domains;

/**
 * Contract tests for the ported composer modules, the Domain Warp system, and
 * the {@link Noises} factory.
 *
 * <p>Asserts invariants rather than magic numbers: each test pins one piece of
 * the contract that, if broken, would silently corrupt the makeMountains
 * recipes. Notably: {@link Multiply}'s zero short-circuit, {@link Map}'s
 * non-degenerate-alpha precondition, {@link ShiftSeed}'s seed isolation, the
 * {@link Noises#map} identity short-circuit, and {@link Noise#mapAll}'s
 * recursive rewrite. The final test assembles a slice of makeMountains2 via the
 * factory to prove the whole chain composes and stays in range.</p>
 */
class ComposerTest {

    private static final int SEED = 42;
    private static final int SAMPLES = 300;

    private static float x(int i) { return i * 7.3F; }
    private static float z(int i) { return i * 11.1F; }

    @Test
    void constantReturnsValueAndIsItsOwnRange() {
        Constant c = new Constant(0.37F);
        assertEquals(0.37F, c.compute(1F, 2F, SEED), 0.0F);
        assertEquals(0.37F, c.minValue(), 0.0F);
        assertEquals(0.37F, c.maxValue(), 0.0F);
    }

    @Test
    void addSumsPointwiseAndRange() {
        Add add = new Add(new Constant(0.25F), new Constant(0.5F));
        assertEquals(0.75F, add.compute(0F, 0F, SEED), 0.0F);
        assertEquals(0.75F, add.maxValue(), 0.0F);
        assertEquals(0.75F, add.minValue(), 0.0F);
    }

    @Test
    void multiplyShortCircuitsOnZeroInput1() {
        // Contract: when input1 == 0, input2.compute must NOT be called.
        // A throwing input2 proves the short-circuit fires.
        Noise input2 = new Noise() {
            @Override public float compute(float x, float z, int seed) {
                throw new AssertionError("input2 must not be sampled when input1==0");
            }
            @Override public float minValue() { return 0F; }
            @Override public float maxValue() { return 1F; }
            @Override public Noise mapAll(Visitor v) { return v.apply(this); }
        };
        Multiply mul = new Multiply(new Constant(0.0F), input2);
        assertEquals(0.0F, mul.compute(1F, 2F, SEED), 0.0F);
    }

    @Test
    void multiplyProductOfUnitRangeNoisesStaysUnit() {
        Noise a = Noises.perlin(SEED, 200, 4);
        Noise b = Noises.perlin(SEED + 1, 200, 4);
        Noise mul = Noises.mul(a, b);
        for (int i = 0; i < SAMPLES; i++) {
            float v = mul.compute(x(i), z(i), SEED);
            assertTrue(v >= mul.minValue() - 1e-4f && v <= mul.maxValue() + 1e-4f,
                    "Multiply out of range: " + v);
        }
    }

    @Test
    void alphaPassthroughAtOneAndPinAtZero() {
        Noise input = new Constant(0.4F);
        // alpha == 1 -> output == input
        Alpha passthrough = new Alpha(input, new Constant(1.0F));
        assertEquals(0.4F, passthrough.compute(0F, 0F, SEED), 1e-5F);
        // alpha == 0 -> output pinned to 1.0
        Alpha pinned = new Alpha(input, new Constant(0.0F));
        assertEquals(1.0F, pinned.compute(0F, 0F, SEED), 1e-5F);
    }

    @Test
    void powerPassthroughAtExponentOne() {
        Power pow = new Power(new Constant(0.6F), 1.0F);
        assertEquals(0.6F, pow.compute(0F, 0F, SEED), 1e-5F);
    }

    @Test
    void invertMirrorsWithinInputDeclaredRange() {
        // Invert mirrors the input within ITS DECLARED range, not a fixed
        // [0,1]: Invert(value at max) == min, Invert(value at min) == max.
        // A Constant has a degenerate [v,v] range that would make Invert a
        // no-op (max - clamp(v,v,v) == 0), so we use a stub declaring [0,1]
        // but returning a controlled value.
        assertEquals(0.0F, new Invert(stub(1.0F, 0.0F, 1.0F)).compute(0F, 0F, SEED), 1e-5F);
        assertEquals(1.0F, new Invert(stub(0.0F, 0.0F, 1.0F)).compute(0F, 0F, SEED), 1e-5F);
    }

    /** A noise that always returns {@code value} while declaring [{@code min}, {@code max}]. */
    private static Noise stub(float value, float min, float max) {
        return new Noise() {
            @Override public float compute(float x, float z, int seed) { return value; }
            @Override public float minValue() { return min; }
            @Override public float maxValue() { return max; }
            @Override public Noise mapAll(Visitor v) { return v.apply(this); }
        };
    }

    @Test
    void clampEnforcesBounds() {
        Clamp clamp = new Clamp(new Constant(5.0F), new Constant(0.0F), new Constant(1.0F));
        assertEquals(1.0F, clamp.compute(0F, 0F, SEED), 0.0F);
        assertEquals(0.0F, clamp.minValue(), 0.0F);
        assertEquals(1.0F, clamp.maxValue(), 0.0F);
    }

    @Test
    void mapRemapsRangeWithoutNaN() {
        // map([0,1] noise, -0.5, 0.5) must land in [-0.5, 0.5] and never NaN.
        // A real varying alpha (Perlin) has alphaMin != alphaMax, so the
        // normalisation divide is safe — this guards against a regression that
        // would feed a degenerate (constant) alpha and divide by zero.
        Noise remapped = Noises.map(Noises.perlin(SEED, 200, 4), -0.5F, 0.5F);
        for (int i = 0; i < SAMPLES; i++) {
            float v = remapped.compute(x(i), z(i), SEED);
            assertTrue(Float.isFinite(v), "Map produced non-finite: " + v);
            assertTrue(v >= -0.5F - 1e-4f && v <= 0.5F + 1e-4f, "Map out of [-0.5,0.5]: " + v);
        }
    }

    @Test
    void shiftSeedIsolatesSeed() {
        // Two ShiftSeed-wrapped Simplex with different shifts must diverge
        // somewhere — this is the whole point of ShiftSeed (per-instance seed
        // isolation from one world seed).
        Noise a = Noises.shiftSeed(new Simplex(1.0F / 200.0F, 4, 2.0F, 0.5F, Interpolation.CURVE4), 100);
        Noise b = Noises.shiftSeed(new Simplex(1.0F / 200.0F, 4, 2.0F, 0.5F, Interpolation.CURVE4), 200);
        boolean anyDifference = false;
        for (int i = 0; i < SAMPLES; i++) {
            if (Float.floatToIntBits(a.compute(x(i), z(i), SEED))
                    != Float.floatToIntBits(b.compute(x(i), z(i), SEED))) {
                anyDifference = true;
                break;
            }
        }
        assertTrue(anyDifference, "different ShiftSeed offsets should produce different output");
    }

    @Test
    void warpChangesOutputVsUnwarped() {
        // Warping must actually relocate features: the warped field should
        // differ from the raw field somewhere.
        Noise raw = Noises.perlinRidge(SEED, 410, 4, 2.35F, 1.15F);
        Noise warped = Noises.warpPerlin(raw, SEED + 50, 200, 4, 300F);
        boolean anyDifference = false;
        for (int i = 0; i < SAMPLES; i++) {
            if (Float.floatToIntBits(raw.compute(x(i), z(i), SEED))
                    != Float.floatToIntBits(warped.compute(x(i), z(i), SEED))) {
                anyDifference = true;
                break;
            }
        }
        assertTrue(anyDifference, "warp should change the sampled output");
        // Range passes through unchanged.
        assertEquals(raw.minValue(), warped.minValue(), 0.0F);
        assertEquals(raw.maxValue(), warped.maxValue(), 0.0F);
    }

    @Test
    void noisesMapIdentityShortCircuitReturnsSameInstance() {
        // map(input, input.min, input.max) is a no-op identity: the factory
        // must short-circuit and return the SAME instance, not a redundant Map
        // node (which would also risk divide-by-zero on a degenerate extent).
        Noise input = Noises.perlin(SEED, 200, 4);
        Noise result = Noises.map(input, input.minValue(), input.maxValue());
        assertSame(input, result, "identity map must return the same instance");
    }

    @Test
    void noisesMapNonIdentityReturnsMapNode() {
        Noise input = Noises.perlin(SEED, 200, 4);
        Noise result = Noises.map(input, -0.5F, 0.5F);
        assertTrue(result instanceof Map, "non-identity map must build a Map node");
    }

    @Test
    void mapAllRecursivelyRewritesComposedChildren() {
        // Visitor contract: a composed module recurses into its children
        // BEFORE applying the visitor to itself. A visitor that doubles every
        // Constant must turn Add(1, 2) into Add(10, 20) == 30.
        Noise.Visitor doubler = input ->
                input instanceof Constant c ? new Constant(c.value() * 10F) : input;
        Noise tree = new Add(new Constant(1F), new Constant(2F));
        Noise rewritten = tree.mapAll(doubler);
        assertEquals(30F, rewritten.compute(0F, 0F, SEED), 0.0F);
    }

    @Test
    void mapAllReachesDomainWarpDrivers() {
        // Warp.mapAll must recurse into both the input noise AND the domain's
        // driver noises. A counting visitor proves every leaf is reached.
        int[] count = {0};
        Noise.Visitor counter = input -> {
            count[0]++;
            return input;
        };
        // DomainWarp holds 3 distinct driver noises (x, z, distance); mappedX/
        // mappedZ are derived Map nodes wrapping x/z, so the full leaf set is:
        // perlin(x), Map->perlin(x)[mappedX], perlin(z), Map->perlin(z)[mappedZ],
        // Constant(distance) = 5 leaves inside the domain, plus the Warp's
        // input leaf. We assert >= 5 to allow for the identity short-circuit
        // collapsing any derived Map when a driver is already in [-0.5,0.5].
        Noise input = Noises.perlin(SEED, 200, 4);
        Domain domain = Domains.domain(
                Noises.perlin(SEED + 1, 200, 4),
                Noises.perlin(SEED + 2, 200, 4),
                Noises.constant(100F));
        Noise warp = new Warp(input, domain);
        warp.mapAll(counter);
        assertTrue(count[0] >= 5, "mapAll should visit >= 5 nodes (input + domain drivers), got " + count[0]);
    }

    @Test
    void makeMountains2SliceComposesAndStaysInRange() {
        // A faithful slice of RTF makeMountains2:
        //   worleyEdge(360, DISTANCE_2, EUCLIDEAN) * 1.2
        //   -> clamp[0,1]
        //   -> warpPerlin(seed+10, 200, 4, strength=300)
        //   -> pow 1.1
        // Proves the factory assembles the real recipe without error and the
        // output stays finite and within the declared range.
        Noise base = Noises.mul(
                Noises.worleyEdge(SEED, 360, EdgeFunction.DISTANCE_2, DistanceFunction.EUCLIDEAN),
                1.2F);
        Noise clamped = Noises.clamp(base, 0F, 1F);
        Noise warped = Noises.warpPerlin(clamped, SEED + 10, 200, 4, 300F);
        Noise mountains = Noises.pow(warped, 1.1F);
        assertNotNull(mountains);
        for (int i = 0; i < SAMPLES; i++) {
            float v = mountains.compute(x(i), z(i), SEED);
            assertTrue(Float.isFinite(v), "makeMountains2 slice not finite: " + v);
            assertTrue(v >= mountains.minValue() - 1e-3f && v <= mountains.maxValue() + 1e-3f,
                    "makeMountains2 slice out of range: " + v);
        }
    }
}
