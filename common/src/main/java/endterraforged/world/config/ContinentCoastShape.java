package endterraforged.world.config;

/**
 * Controls how a cell-owned continent transitions into End void.
 *
 * <p>{@link #RADIAL_LEGACY} preserves the original feature-point radius
 * contour for existing presets. {@link #ORGANIC} blends that contour with the
 * local cell boundary and a deterministic coast field, producing bays and
 * headlands without changing the continent centre layout.</p>
 */
public enum ContinentCoastShape {
    RADIAL_LEGACY,
    ORGANIC
}
