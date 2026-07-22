package endterraforged.world.terrain;

/**
 * Describes how a terrain family is placed in the macro terrain pipeline.
 *
 * <p>Only AREA participates in complete macro ownership. RIDGE and COMPACT
 * are finite feature modes sampled by independent bounded layouts.</p>
 */
public enum TerrainPlacementMode {
    AREA,
    RIDGE,
    COMPACT
}
