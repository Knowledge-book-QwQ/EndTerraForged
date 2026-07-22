package endterraforged.world.config;

import java.util.Objects;

/**
 * Resolves a persisted preset against the vertical envelope of the loaded
 * Minecraft dimension.
 *
 * <p>Preset JSON may outlive a data-pack update or may have been written by
 * an older editor that exposed non-functional height controls. The actual
 * {@code NoiseSettings} remain authoritative, so this resolver preserves all
 * terrain controls while replacing only the profile envelope used by runtime
 * height math. Reference Y values must already fit the actual dimension; a
 * bad value is rejected rather than silently clamped into a different world.</p>
 *
 * <p><b>Thread safety:</b> stateless utility.</p>
 */
public final class EndPresetRuntimeResolver {

    private EndPresetRuntimeResolver() {
    }

    /**
     * Result of aligning a preset to an actual dimension envelope.
     *
     * @param preset preset safe for runtime construction
     * @param boundsAdjusted whether the persisted height envelope differed
     *                       from the actual dimension
     */
    public record Resolution(EndPreset preset, boolean boundsAdjusted) {

        public Resolution {
            Objects.requireNonNull(preset, "preset");
        }
    }

    /**
     * Aligns a valid persisted preset with the actual Minecraft world bounds.
     *
     * @throws IllegalArgumentException if a stored sea level or island
     *                                  baseline is outside the loaded world
     */
    public static Resolution resolve(EndPreset configured, WorldVerticalBounds actualBounds) {
        Objects.requireNonNull(configured, "configured");
        Objects.requireNonNull(actualBounds, "actualBounds");

        if (!actualBounds.contains(configured.seaLevelY())) {
            throw new IllegalArgumentException("stored sea_level_y=" + configured.seaLevelY()
                    + " is outside actual world bounds [" + actualBounds.minY() + ", "
                    + (actualBounds.maxYExclusive() - 1) + "]");
        }
        if (!actualBounds.contains(configured.islandBaselineY())) {
            throw new IllegalArgumentException("stored island_baseline_y="
                    + configured.islandBaselineY() + " is outside actual world bounds ["
                    + actualBounds.minY() + ", " + (actualBounds.maxYExclusive() - 1) + "]");
        }

        boolean adjusted = configured.worldBounds().equals(actualBounds) == false;
        return new Resolution(adjusted ? configured.withWorldBounds(actualBounds) : configured, adjusted);
    }
}
