package endterraforged.world.climate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import endterraforged.world.noise.Noise;

/**
 * Unit tests for {@link ClimatePredicate}: the pure-logic climate condition
 * checks behind the MC-integrated surface-rule conditions and feature
 * placement filter.
 *
 * <p>Uses a test-only {@link PointNoise} (a {@code BiFunction<Float, Float,
 * Float>} wrapped as a {@link Noise}) so each test can pin exact
 * temperature/moisture values — same pattern as {@code EndBiomeSelectorTest}
 * in stage 5.2.1.</p>
 */
class ClimatePredicateTest {

    /**
     * Test-only {@link Noise} backed by a lambda, so tests can pin exact
     * per-position values without depending on real simplex channels.
     */
    private record PointNoise(BiFunction<Float, Float, Float> fn) implements Noise {
        @Override
        public float compute(float x, float z, int seed) {
            return fn.apply(x, z);
        }

        @Override
        public float minValue() {
            return 0.0F;
        }

        @Override
        public float maxValue() {
            return 1.0F;
        }

        @Override
        public Noise mapAll(Visitor visitor) {
            return visitor.apply(this);
        }

        static PointNoise constant(float v) {
            return new PointNoise((x, z) -> v);
        }
    }

    /** Builds a test EndClimate with the given temperature and moisture channels. */
    private static EndClimate climate(Noise temp, Noise moist) {
        return new EndClimate(temp, moist, PointNoise.constant(0.0F), 1.0F, 0.0F);
    }

    // ----- temperatureInRange -----

    @Test
    void temperatureNullClimateReturnsFalse() {
        assertFalse(ClimatePredicate.temperatureInRange(null, 0, 0, 0.0F, 1.0F));
    }

    @Test
    void temperatureInRange() {
        EndClimate c = climate(PointNoise.constant(0.5F), PointNoise.constant(0.5F));
        assertTrue(ClimatePredicate.temperatureInRange(c, 0, 0, 0.0F, 1.0F));
        assertTrue(ClimatePredicate.temperatureInRange(c, 0, 0, 0.3F, 0.7F));
    }

    @Test
    void temperatureOutOfRange() {
        EndClimate c = climate(PointNoise.constant(0.2F), PointNoise.constant(0.5F));
        assertFalse(ClimatePredicate.temperatureInRange(c, 0, 0, 0.3F, 0.7F));
    }

    @Test
    void temperatureBoundaryInclusive() {
        EndClimate c = climate(PointNoise.constant(0.3F), PointNoise.constant(0.5F));
        assertTrue(ClimatePredicate.temperatureInRange(c, 0, 0, 0.3F, 0.7F),
                "min boundary should be inclusive");
        EndClimate c2 = climate(PointNoise.constant(0.7F), PointNoise.constant(0.5F));
        assertTrue(ClimatePredicate.temperatureInRange(c2, 0, 0, 0.3F, 0.7F),
                "max boundary should be inclusive");
    }

    @Test
    void temperatureSpatialSampling() {
        // temperature = x / 100
        EndClimate c = climate(
                new PointNoise((x, z) -> x / 100.0F),
                PointNoise.constant(0.5F));
        assertTrue(ClimatePredicate.temperatureInRange(c, 50, 0, 0.4F, 0.6F));
        assertFalse(ClimatePredicate.temperatureInRange(c, 10, 0, 0.4F, 0.6F));
        assertFalse(ClimatePredicate.temperatureInRange(c, 90, 0, 0.4F, 0.6F));
    }

    // ----- moistureInRange -----

    @Test
    void moistureNullClimateReturnsFalse() {
        assertFalse(ClimatePredicate.moistureInRange(null, 0, 0, 0.0F, 1.0F));
    }

    @Test
    void moistureInRange() {
        EndClimate c = climate(PointNoise.constant(0.5F), PointNoise.constant(0.8F));
        assertTrue(ClimatePredicate.moistureInRange(c, 0, 0, 0.0F, 1.0F));
        assertTrue(ClimatePredicate.moistureInRange(c, 0, 0, 0.7F, 0.9F));
    }

    @Test
    void moistureOutOfRange() {
        EndClimate c = climate(PointNoise.constant(0.5F), PointNoise.constant(0.2F));
        assertFalse(ClimatePredicate.moistureInRange(c, 0, 0, 0.5F, 1.0F));
    }

    @Test
    void moistureBoundaryInclusive() {
        EndClimate c = climate(PointNoise.constant(0.5F), PointNoise.constant(0.4F));
        assertTrue(ClimatePredicate.moistureInRange(c, 0, 0, 0.4F, 0.6F),
                "min boundary should be inclusive");
        EndClimate c2 = climate(PointNoise.constant(0.5F), PointNoise.constant(0.6F));
        assertTrue(ClimatePredicate.moistureInRange(c2, 0, 0, 0.4F, 0.6F),
                "max boundary should be inclusive");
    }

    // ----- bothInRange -----

    @Test
    void bothNullClimateReturnsFalse() {
        assertFalse(ClimatePredicate.bothInRange(null, 0, 0, 0.0F, 1.0F, 0.0F, 1.0F));
    }

    @Test
    void bothInRange() {
        EndClimate c = climate(PointNoise.constant(0.5F), PointNoise.constant(0.5F));
        assertTrue(ClimatePredicate.bothInRange(c, 0, 0, 0.3F, 0.7F, 0.3F, 0.7F));
    }

    @Test
    void bothTempOutOfRange() {
        EndClimate c = climate(PointNoise.constant(0.1F), PointNoise.constant(0.5F));
        assertFalse(ClimatePredicate.bothInRange(c, 0, 0, 0.3F, 0.7F, 0.3F, 0.7F));
    }

    @Test
    void bothMoistOutOfRange() {
        EndClimate c = climate(PointNoise.constant(0.5F), PointNoise.constant(0.9F));
        assertFalse(ClimatePredicate.bothInRange(c, 0, 0, 0.3F, 0.7F, 0.3F, 0.7F));
    }

    @Test
    void bothBoundaryInclusive() {
        EndClimate c = climate(PointNoise.constant(0.3F), PointNoise.constant(0.7F));
        assertTrue(ClimatePredicate.bothInRange(c, 0, 0, 0.3F, 0.7F, 0.7F, 0.7F),
                "both boundaries should be inclusive");
    }

    @Test
    void bothSpatialSampling() {
        // temp = x/100, moist = z/100
        EndClimate c = climate(
                new PointNoise((x, z) -> x / 100.0F),
                new PointNoise((x, z) -> z / 100.0F));
        assertTrue(ClimatePredicate.bothInRange(c, 50, 50, 0.4F, 0.6F, 0.4F, 0.6F));
        assertFalse(ClimatePredicate.bothInRange(c, 10, 50, 0.4F, 0.6F, 0.4F, 0.6F));
        assertFalse(ClimatePredicate.bothInRange(c, 50, 90, 0.4F, 0.6F, 0.4F, 0.6F));
    }
}
