package endterraforged.world.preview;

/**
 * CPU preview render modes.
 *
 * <p>The GUI may switch modes without changing the sampled preset; all modes
 * are derived from the same heightmap query path.</p>
 */
public enum TerrainPreviewMode {
    COMBINED,
    HEIGHT,
    LANDNESS,
    ARCHIPELAGO,
    VOLUME,
    LAYERS,
    BIOMES,
    BIOME_CLIMATE,
    CAVES,
    CAVE_CHAMBERS,
    CAVE_NETWORK,
    CAVE_RIFTS,
    CAVE_FLOWS,
    CAVE_WATER,
    CAVE_LAVA,
    CAVE_DEPTH,
    ABYSS,
    TEMPERATURE,
    MOISTURE,
    WIND
}
