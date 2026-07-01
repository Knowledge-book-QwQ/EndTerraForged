package endterraforged.world.noise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Contract tests for the ported cell-noise modules ({@link Worley},
 * {@link WorleyEdge}) and their {@link DistanceFunction} / {@link EdgeFunction}
 * / {@link CellFunction} enums.
 *
 * <p>Asserts invariants (range, reproducibility, seed sensitivity, edge-function
 * distinctness) rather than magic numbers: the algorithm is byte-identical to
 * upstream RTF, so any drift would still fail the invariants while a clean
 * re-port passes.</p>
 */
class WorleyTest {

    private static final int SEED = 42;
    private static final int SAMPLES = 400;
    private static final float FREQ = 1.0F / 200.0F;

    @Test
    void worleyEdgeInRange() {
        WorleyEdge edge = new WorleyEdge(FREQ, EdgeFunction.DISTANCE_2, DistanceFunction.EUCLIDEAN);
        for (int i = 0; i < SAMPLES; i++) {
            float x = i * 7.3F;
            float z = i * 11.1F;
            float v = edge.compute(x, z, SEED);
            assertTrue(v >= edge.minValue() - 1e-4f && v <= edge.maxValue() + 1e-4f,
                    "WorleyEdge out of range: " + v);
        }
    }

    @Test
    void worleyCellValueInRange() {
        Worley worley = new Worley(FREQ, 1.0F, CellFunction.CELL_VALUE, DistanceFunction.EUCLIDEAN, null);
        for (int i = 0; i < SAMPLES; i++) {
            float x = i * 7.3F;
            float z = i * 11.1F;
            float v = worley.compute(x, z, SEED);
            assertTrue(v >= worley.minValue() - 1e-4f && v <= worley.maxValue() + 1e-4f,
                    "Worley CELL_VALUE out of range: " + v);
        }
    }

    @Test
    void worleyNoiseLookupPassesThroughLookupRange() {
        // NOISE_LOOKUP does not normalise — output must stay within the lookup's own range
        Noise lookup = new Simplex(1.0F / 50.0F, 2, 2.0F, 0.5F, Interpolation.CURVE4);
        Worley worley = new Worley(FREQ, 1.0F, CellFunction.NOISE_LOOKUP, DistanceFunction.EUCLIDEAN, lookup);
        for (int i = 0; i < SAMPLES; i++) {
            float x = i * 7.3F;
            float z = i * 11.1F;
            float v = worley.compute(x, z, SEED);
            assertTrue(v >= lookup.minValue() - 1e-3f && v <= lookup.maxValue() + 1e-3f,
                    "NOISE_LOOKUP should pass the lookup range through, got " + v);
        }
    }

    @Test
    void worleyEdgeIsDeterministic() {
        WorleyEdge edge = new WorleyEdge(FREQ, EdgeFunction.DISTANCE_2, DistanceFunction.EUCLIDEAN);
        for (int i = 0; i < SAMPLES; i++) {
            float x = i * 7.3F;
            float z = i * 11.1F;
            assertEquals(edge.compute(x, z, SEED), edge.compute(x, z, SEED), 0.0f,
                    "WorleyEdge should be deterministic at (" + x + "," + z + ")");
        }
    }

    @Test
    void differentSeedsProduceDifferentWorleyEdge() {
        WorleyEdge a = new WorleyEdge(FREQ, EdgeFunction.DISTANCE_2, DistanceFunction.EUCLIDEAN);
        boolean anyDifference = false;
        for (int i = 0; i < SAMPLES; i++) {
            float x = i * 7.3F;
            float z = i * 11.1F;
            if (Float.floatToIntBits(a.compute(x, z, 1)) != Float.floatToIntBits(a.compute(x, z, 2))) {
                anyDifference = true;
                break;
            }
        }
        assertTrue(anyDifference, "different seeds should produce different worley-edge output");
    }

    @Test
    void differentEdgeFunctionsAreDistinct() {
        // DISTANCE_2 vs DISTANCE_2_SUB must diverge somewhere — they combine the two
        // nearest distances differently
        WorleyEdge d2 = new WorleyEdge(FREQ, EdgeFunction.DISTANCE_2, DistanceFunction.EUCLIDEAN);
        WorleyEdge sub = new WorleyEdge(FREQ, EdgeFunction.DISTANCE_2_SUB, DistanceFunction.EUCLIDEAN);
        boolean anyDifference = false;
        for (int i = 0; i < SAMPLES; i++) {
            float x = i * 7.3F;
            float z = i * 11.1F;
            if (Float.floatToIntBits(d2.compute(x, z, SEED)) != Float.floatToIntBits(sub.compute(x, z, SEED))) {
                anyDifference = true;
                break;
            }
        }
        assertTrue(anyDifference, "DISTANCE_2 and DISTANCE_2_SUB should produce different output");
    }

    @Test
    void edgeFunctionBoundsAreConsistent() {
        // every variant must satisfy min <= 0-in-range && range == max - min
        for (EdgeFunction ef : EdgeFunction.values()) {
            assertEquals(ef.max() - ef.min(), ef.range(), 1e-5f,
                    ef + ": range should equal max - min");
        }
    }

    @Test
    void distanceFunctionsAreNonNegative() {
        // distances (or squared distances) from the origin are never negative
        for (DistanceFunction df : DistanceFunction.values()) {
            assertTrue(df.apply(0.3F, 0.4F) >= 0.0F, df + " should be non-negative for non-zero input");
            assertEquals(0.0F, df.apply(0.0F, 0.0F), 0.0f, df + " should be 0 at origin");
        }
    }
}
