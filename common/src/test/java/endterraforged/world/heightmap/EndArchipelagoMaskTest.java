package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

class EndArchipelagoMaskTest {

    private static final int SEED = 123456789;

    @Test
    void centralProtectionAlwaysSuppressesTheFeature() {
        EndArchipelagoMask mask = new EndArchipelagoMask(SEED);
        EndArchipelagoSignalBuffer output = new EndArchipelagoSignalBuffer();

        mask.sample(0.0F, 0.0F, SEED, 0.5F, 0.0F, output);

        assertEquals(0.0F, output.mask(), 0.0F);
        assertEquals(0.0F, output.landness(), 0.0F);
        assertTrue(!output.identified());
    }

    @Test
    void outputIsBoundedAndDeterministic() {
        EndArchipelagoMask mask = new EndArchipelagoMask(SEED);
        int[] first = sampleBits(mask);
        int[] second = sampleBits(mask);

        assertArrayEquals(first, second);
        for (int bits : first) {
            float value = Float.intBitsToFloat(bits);
            assertTrue(value >= 0.0F && value <= 1.0F);
        }
    }

    @Test
    void samplingIsStableAcrossWorkers() throws Exception {
        EndArchipelagoMask mask = new EndArchipelagoMask(SEED);
        int[] expected = sampleBits(mask);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<int[]>> tasks = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                tasks.add(() -> sampleBits(mask));
            }
            for (Future<int[]> future : executor.invokeAll(tasks)) {
                assertArrayEquals(expected, future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static int[] sampleBits(EndArchipelagoMask mask) {
        EndArchipelagoSignalBuffer output = new EndArchipelagoSignalBuffer();
        int[] values = new int[128];
        for (int i = 0; i < values.length; i++) {
            float x = 2048.0F + i * 173.0F;
            float z = -8192.0F + i * 97.0F;
            mask.sample(x, z, SEED, 0.12F + (i % 7) * 0.03F, 0.02F, output);
            values[i] = Float.floatToIntBits(output.mask());
        }
        return values;
    }
}
