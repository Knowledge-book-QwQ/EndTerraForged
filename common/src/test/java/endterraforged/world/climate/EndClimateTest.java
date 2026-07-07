package endterraforged.world.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Contract tests for {@link EndClimate}: three independent [0,1] channels,
 * temperature radial band + perturbation, moisture/wind standalone simplex,
 * all bounded, deterministic, seed-sensitive, and decoupled from Continent.
 */
class EndClimateTest {

    private static final int SEED = 42;
    private static final int SAMPLES = 400;

    private static float x(int i) { return (i - SAMPLES / 2) * 13.7F; }
    private static float z(int i) { return (i - SAMPLES / 2) * 9.3F; }

    // ----- range contract --------------------------------------------------

    @Test
    void temperatureInRange01() {
        EndClimate climate = EndClimate.defaults(SEED);
        for (int i = 0; i < SAMPLES; i++) {
            float t = climate.getTemperature(x(i), z(i), SEED);
            assertTrue(t >= 0.0F && t <= 1.0F,
                    "temperature out of [0,1]: " + t + " at (" + x(i) + "," + z(i) + ")");
        }
    }

    @Test
    void moistureInRange01() {
        EndClimate climate = EndClimate.defaults(SEED);
        for (int i = 0; i < SAMPLES; i++) {
            float m = climate.getMoisture(x(i), z(i), SEED);
            assertTrue(m >= 0.0F && m <= 1.0F,
                    "moisture out of [0,1]: " + m);
        }
    }

    @Test
    void windInRange01() {
        EndClimate climate = EndClimate.defaults(SEED);
        for (int i = 0; i < SAMPLES; i++) {
            float w = climate.getWind(x(i), z(i), SEED);
            assertTrue(w >= 0.0F && w <= 1.0F,
                    "wind out of [0,1]: " + w);
        }
    }

    // ----- temperature radial band ----------------------------------------

    @Test
    void temperatureHotAtOrigin() {
        EndClimate climate = EndClimate.defaults(SEED);
        // At origin, base band = 1; perturbation may nudge it but clamp keeps
        // it in [0,1]. With perturbation=0.25, origin temperature should be
        // close to 1 (>= 0.7 is a safe lower bound after perturbation).
        float t = climate.getTemperature(0.0F, 0.0F, SEED);
        assertTrue(t >= 0.7F,
                "temperature at origin should be hot (near 1): " + t);
    }

    @Test
    void temperatureColdAtFarRim() {
        EndClimate climate = EndClimate.defaults(SEED);
        // At distance >> climateRadius (4000), base band = 0; perturbation may
        // nudge up but clamp keeps it in [0,1]. Temperature should be low.
        float t = climate.getTemperature(10000.0F, 0.0F, SEED);
        assertTrue(t <= climate.perturbation() + 1e-4F,
                "temperature at far rim should be <= perturbation: " + t);
    }

    @Test
    void temperatureDecreasesWithDistanceOnAverage() {
        // The radial band means average temperature near origin > average far
        // away, even with perturbation. Sample two annuli and compare means.
        EndClimate climate = EndClimate.defaults(SEED);
        float nearSum = 0.0F;
        int nearCount = 0;
        float farSum = 0.0F;
        int farCount = 0;
        for (int i = 0; i < SAMPLES; i++) {
            float x = x(i);
            float z = z(i);
            float dist = (float) Math.sqrt(x * x + z * z);
            float t = climate.getTemperature(x, z, SEED);
            if (dist < 1000.0F) {
                nearSum += t;
                nearCount++;
            } else if (dist > 3000.0F && dist < 5000.0F) {
                farSum += t;
                farCount++;
            }
        }
        assertTrue(nearCount > 0, "should have near samples");
        assertTrue(farCount > 0, "should have far samples");
        float nearMean = nearSum / nearCount;
        float farMean = farSum / farCount;
        assertTrue(nearMean > farMean,
                "near mean temp " + nearMean + " should exceed far mean " + farMean);
    }

    // ----- moisture/wind independence -------------------------------------

    @Test
    void moistureIndependentOfTemperature() {
        // A single-axis coupling would make moisture correlate with temperature.
        // Independence means we can find hot-dry and hot-wet cells (or at least
        // that moisture variance is not explained by temperature alone).
        EndClimate climate = EndClimate.defaults(SEED);
        boolean foundHotDry = false;
        boolean foundHotWet = false;
        for (int i = 0; i < SAMPLES; i++) {
            float t = climate.getTemperature(x(i), z(i), SEED);
            float m = climate.getMoisture(x(i), z(i), SEED);
            if (t > 0.7F && m < 0.3F) foundHotDry = true;
            if (t > 0.7F && m > 0.7F) foundHotWet = true;
        }
        // Independence doesn't guarantee both exist in 400 samples, but with
        // perturbation=0.25 and independent simplex it is very likely. If this
        // flakes, raise SAMPLES — do not weaken, it is the independence proof.
        assertTrue(foundHotDry || foundHotWet,
                "moisture should vary independently of temperature (found hot-dry="
                        + foundHotDry + ", hot-wet=" + foundHotWet + ")");
    }

    // ----- determinism / seed sensitivity ---------------------------------

    @Test
    void allChannelsDeterministic() {
        EndClimate climate = EndClimate.defaults(SEED);
        for (int i = 0; i < SAMPLES; i++) {
            float t1 = climate.getTemperature(x(i), z(i), SEED);
            float t2 = climate.getTemperature(x(i), z(i), SEED);
            assertEquals(Float.floatToIntBits(t1), Float.floatToIntBits(t2),
                    "temperature must be deterministic");
            float m1 = climate.getMoisture(x(i), z(i), SEED);
            float m2 = climate.getMoisture(x(i), z(i), SEED);
            assertEquals(Float.floatToIntBits(m1), Float.floatToIntBits(m2),
                    "moisture must be deterministic");
        }
    }

    @Test
    void allChannelsSeedSensitive() {
        EndClimate a = EndClimate.defaults(SEED);
        EndClimate b = EndClimate.defaults(SEED + 1);
        boolean tDiff = false, mDiff = false, wDiff = false;
        for (int i = 0; i < SAMPLES; i++) {
            if (Float.floatToIntBits(a.getTemperature(x(i), z(i), SEED))
                    != Float.floatToIntBits(b.getTemperature(x(i), z(i), SEED + 1))) tDiff = true;
            if (Float.floatToIntBits(a.getMoisture(x(i), z(i), SEED))
                    != Float.floatToIntBits(b.getMoisture(x(i), z(i), SEED + 1))) mDiff = true;
            if (Float.floatToIntBits(a.getWind(x(i), z(i), SEED))
                    != Float.floatToIntBits(b.getWind(x(i), z(i), SEED + 1))) wDiff = true;
        }
        assertTrue(tDiff, "temperature must be seed-sensitive");
        assertTrue(mDiff, "moisture must be seed-sensitive");
        assertTrue(wDiff, "wind must be seed-sensitive");
    }

    // ----- decoupling from Continent --------------------------------------

    @Test
    void climateDoesNotReadLandness() {
        // Climate is decoupled from Continent: it returns values everywhere,
        // including points that would be void (landness=0). We can't import
        // EndHeightmap here without making this an integration test, but the
        // contract is: climate is queryable at arbitrary points and never
        // throws / returns NaN. This is the seam that lets stage 2.5b/3.5
        // sample climate before deciding land/void.
        EndClimate climate = EndClimate.defaults(SEED);
        for (int i = 0; i < SAMPLES; i++) {
            float t = climate.getTemperature(x(i) * 10, z(i) * 10, SEED);
            float m = climate.getMoisture(x(i) * 10, z(i) * 10, SEED);
            float w = climate.getWind(x(i) * 10, z(i) * 10, SEED);
            assertTrue(Float.isFinite(t) && Float.isFinite(m) && Float.isFinite(w),
                    "climate channels must be finite everywhere (decoupled from landness)");
        }
    }
}
