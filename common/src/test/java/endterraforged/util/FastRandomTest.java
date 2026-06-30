package endterraforged.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Locks down {@link FastRandom}'s determinism and value-range contracts.
 * Tests assert invariants rather than hardcoded magic numbers so they stay
 * meaningful as long as the algorithm is unchanged; the determinism test
 * still guards against any accidental stream drift.
 */
class FastRandomTest {

    private static final long SEED = 42L;
    private static final long GAMMA = -7046029254386353131L;
    private static final int SAMPLES = 1000;

    @Test
    void sameSeedProducesSameSequence() {
        int[] first = draw(new FastRandom(SEED, GAMMA));
        int[] second = draw(new FastRandom(SEED, GAMMA));
        for (int i = 0; i < SAMPLES; i++) {
            assertEquals(first[i], second[i], "stream diverged at index " + i);
        }
    }

    @Test
    void nextIntRespectsBound() {
        FastRandom rng = new FastRandom(SEED, GAMMA);
        for (int i = 0; i < SAMPLES; i++) {
            int v = rng.nextInt(16);
            assertTrue(v >= 0 && v < 16, "nextInt(16) out of range: " + v);
        }
    }

    @Test
    void nextFloatInRange() {
        FastRandom rng = new FastRandom(SEED, GAMMA);
        for (int i = 0; i < SAMPLES; i++) {
            float v = rng.nextFloat();
            assertTrue(v >= 0.0f && v < 1.0f, "nextFloat out of [0,1): " + v);
        }
    }

    @Test
    void seedResetReplaysStream() {
        FastRandom rng = new FastRandom(SEED, GAMMA);
        int a = rng.nextInt();
        int b = rng.nextInt();
        // single-arg seed(long) keeps gamma unchanged, so the stream replays;
        // the two-arg seed(long,long) intentionally re-mixes gamma (upstream
        // behaviour), which would NOT replay — see FastRandom javadoc.
        rng.seed(SEED);
        assertEquals(a, rng.nextInt(), "first draw after reset mismatch");
        assertEquals(b, rng.nextInt(), "second draw after reset mismatch");
    }

    private static int[] draw(FastRandom rng) {
        int[] out = new int[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            out[i] = rng.nextInt();
        }
        return out;
    }
}
