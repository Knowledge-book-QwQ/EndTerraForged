/*
 * EndTerraForged original design (LGPL-3.0-or-later). Pure-logic climate
 * condition checks — the testable core behind the MC-integrated
 * ClimateTemperatureCondition / ClimateMoistureCondition surface-rule
 * conditions and ClimatePlacementFilter feature gate.
 */
package endterraforged.world.climate;

/**
 * Pure-logic climate condition predicates: tests whether an
 * {@link EndClimate} field falls within a closed range at a given world
 * coordinate.
 *
 * <p>This class is the testable core behind the MC-integrated surface-rule
 * conditions ({@link endterraforged.world.level.levelgen.ClimateTemperatureCondition},
 * {@link endterraforged.world.level.levelgen.ClimateMoistureCondition}) and
 * the feature-placement filter
 * ({@link endterraforged.world.level.levelgen.ClimatePlacementFilter}). It
 * depends only on {@link EndClimate} — no MC classes — so it can be unit
 * tested with a test-only {@code EndClimate} (e.g. a lambda-backed
 * {@link endterraforged.world.noise.Noise} as used in stage 5.2.1 tests),
 * following the §六.2 "algorithm/MC decoupling" principle.</p>
 *
 * <p><b>Thread safety.</b> All methods are static and stateless. The
 * {@link EndClimate} argument is an immutable record; safe to call from
 * parallel chunk-gen threads.</p>
 */
public final class ClimatePredicate {

    private ClimatePredicate() {
    }

    /**
     * Tests whether the temperature at {@code (x, z)} falls in
     * {@code [min, max]}.
     *
     * @param climate the End's climate field, or {@code null} if no End
     *                dimension is loaded (returns {@code false})
     * @param x       world X
     * @param z       world Z
     * @param min     inclusive lower bound (typically in {@code [0,1]})
     * @param max     inclusive upper bound (typically in {@code [0,1]})
     * @return {@code true} iff {@code climate != null} and the sampled
     *         temperature is in {@code [min, max]}
     */
    public static boolean temperatureInRange(EndClimate climate, float x, float z,
                                             float min, float max) {
        if (climate == null) {
            return false;
        }
        float temp = climate.getTemperature(x, z, 0);
        return temp >= min && temp <= max;
    }

    /**
     * Tests whether the moisture at {@code (x, z)} falls in
     * {@code [min, max]}.
     *
     * @param climate the End's climate field, or {@code null} if no End
     *                dimension is loaded (returns {@code false})
     * @param x       world X
     * @param z       world Z
     * @param min     inclusive lower bound (typically in {@code [0,1]})
     * @param max     inclusive upper bound (typically in {@code [0,1]})
     * @return {@code true} iff {@code climate != null} and the sampled
     *         moisture is in {@code [min, max]}
     */
    public static boolean moistureInRange(EndClimate climate, float x, float z,
                                          float min, float max) {
        if (climate == null) {
            return false;
        }
        float moist = climate.getMoisture(x, z, 0);
        return moist >= min && moist <= max;
    }

    /**
     * Tests whether both temperature and moisture at {@code (x, z)} fall in
     * their respective closed ranges. This is the dual-axis gate used by
     * {@link endterraforged.world.level.levelgen.ClimatePlacementFilter} to
     * decide whether a feature should be placed.
     *
     * @param climate  the End's climate field, or {@code null}
     * @param x        world X
     * @param z        world Z
     * @param minTemp  inclusive temperature lower bound
     * @param maxTemp  inclusive temperature upper bound
     * @param minMoist inclusive moisture lower bound
     * @param maxMoist inclusive moisture upper bound
     * @return {@code true} iff {@code climate != null} and both channels
     *         match their ranges
     */
    public static boolean bothInRange(EndClimate climate, float x, float z,
                                      float minTemp, float maxTemp,
                                      float minMoist, float maxMoist) {
        if (climate == null) {
            return false;
        }
        float temp = climate.getTemperature(x, z, 0);
        float moist = climate.getMoisture(x, z, 0);
        return temp >= minTemp && temp <= maxTemp
                && moist >= minMoist && moist <= maxMoist;
    }
}
