package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EndRidgeTerrainEnvelopeTest {
    private static final float CORE_HALF_LENGTH = 900.0F;
    private static final float OUTER_HALF_LENGTH = 980.0F;
    private static final float CORE_HALF_WIDTH = 210.0F;
    private static final float OUTER_HALF_WIDTH = 280.0F;
    private static final float FIRST_CONTROL = 0.48F;
    private static final float SECOND_CONTROL = -0.36F;
    private static final float EDGE_BLEND = 0.35F;

    @Test
    void rotationAndTranslationPreserveLocalGeometry() {
        float base = influence(120.0F, -40.0F, 0.0F, 0.0F, 1.0F, 0.0F);
        float angle = 1.17F;
        float cosine = (float) Math.cos(angle);
        float sine = (float) Math.sin(angle);
        float localX = 120.0F;
        float localZ = -40.0F;
        float centerX = 3400.0F;
        float centerZ = -2700.0F;
        float worldX = centerX + localX * cosine - localZ * sine;
        float worldZ = centerZ + localX * sine + localZ * cosine;
        float transformed = influence(worldX, worldZ, centerX, centerZ, cosine, sine);

        assertTrue(base > 0.0F);
        assertEquals(base, transformed, 1.0E-5F);
    }

    @Test
    void endpointAndOuterFootprintAreStrictlyZero() {
        assertEquals(0.0F, influence(OUTER_HALF_LENGTH, 0.0F,
                0.0F, 0.0F, 1.0F, 0.0F), 0.0F);
        float boundingReach = (float) Math.sqrt(
                OUTER_HALF_LENGTH * OUTER_HALF_LENGTH
                        + Math.pow(CORE_HALF_WIDTH
                                + OUTER_HALF_WIDTH
                                * EndRidgeTerrainEnvelope.MAX_ORGANIC_WIDTH_SCALE, 2.0D));
        assertEquals(0.0F, influence(0.0F, boundingReach + 1.0F,
                0.0F, 0.0F, 1.0F, 0.0F), 0.0F);
        assertEquals(0.0F, relief(OUTER_HALF_LENGTH, 0.0F), 0.0F);
    }

    @Test
    void coreApronAndTipTaperRemainContinuous() {
        float previous = influence(-OUTER_HALF_LENGTH - 2.0F, 0.0F,
                0.0F, 0.0F, 1.0F, 0.0F);
        float largestDelta = 0.0F;
        boolean sawCore = false;
        boolean sawApron = false;
        for (float x = -OUTER_HALF_LENGTH - 1.0F;
             x <= OUTER_HALF_LENGTH + 1.0F; x += 1.0F) {
            float current = influence(x, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F);
            largestDelta = Math.max(largestDelta, Math.abs(current - previous));
            sawCore |= current >= 0.99F;
            sawApron |= current > 0.0F && current < 0.99F;
            previous = current;
        }

        assertTrue(sawCore);
        assertTrue(sawApron);
        float finalLargestDelta = largestDelta;
        assertTrue(finalLargestDelta < 0.02F,
                () -> "ridge endpoint changed by " + finalLargestDelta + " per block");
    }

    private static float influence(float x, float z,
                                   float centerX, float centerZ,
                                   float cosine, float sine) {
        return EndRidgeTerrainEnvelope.influence(
                x, z, centerX, centerZ,
                CORE_HALF_LENGTH, OUTER_HALF_LENGTH,
                CORE_HALF_WIDTH, OUTER_HALF_WIDTH,
                cosine, sine, FIRST_CONTROL, SECOND_CONTROL, EDGE_BLEND);
    }

    private static float relief(float x, float z) {
        return EndRidgeTerrainEnvelope.relief(
                x, z, 0.0F, 0.0F,
                CORE_HALF_LENGTH, OUTER_HALF_LENGTH,
                CORE_HALF_WIDTH, OUTER_HALF_WIDTH,
                1.0F, 0.0F, FIRST_CONTROL, SECOND_CONTROL, EDGE_BLEND);
    }
}
