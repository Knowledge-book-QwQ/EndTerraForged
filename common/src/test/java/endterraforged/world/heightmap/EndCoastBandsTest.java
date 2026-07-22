package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EndCoastBandsTest {

    @Test
    void zeroMaskIsVoidAndAllSignalsStayBounded() {
        assertEquals(0.0F, EndCoastBands.landness(0.0F), 0.0F);
        assertEquals(0.0F, EndCoastBands.inlandness(0.0F), 0.0F);
        assertEquals(EndCoastBand.VOID_EDGE, EndCoastBands.band(0.0F));
        for (int i = 0; i <= 100; i++) {
            float mask = i / 100.0F;
            assertTrue(EndCoastBands.landness(mask) >= 0.0F
                    && EndCoastBands.landness(mask) <= 1.0F);
            assertTrue(EndCoastBands.inlandness(mask) >= 0.0F
                    && EndCoastBands.inlandness(mask) <= 1.0F);
        }
    }

    @Test
    void bandsAreMonotonicAndContinuous() {
        float previousLandness = 0.0F;
        float previousInlandness = 0.0F;
        for (int i = 0; i <= 1000; i++) {
            float mask = i / 1000.0F;
            float landness = EndCoastBands.landness(mask);
            float inlandness = EndCoastBands.inlandness(mask);
            assertTrue(landness + 1.0E-6F >= previousLandness);
            assertTrue(inlandness + 1.0E-6F >= previousInlandness);
            assertTrue(landness - previousLandness < 0.02F);
            assertTrue(inlandness - previousInlandness < 0.02F);
            previousLandness = landness;
            previousInlandness = inlandness;
        }
        assertEquals(EndCoastBand.INLAND, EndCoastBands.band(1.0F));
    }
}
