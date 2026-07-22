package endterraforged.world.heightmap;

/**
 * Runtime terrain layer selected at a sampled coordinate.
 *
 * <p>This is a worldgen/preview classification, not a serialized preset value.
 * Presets keep using {@link endterraforged.world.config.TerrainLayerConfig}
 * for user-tunable parameters.</p>
 */
public enum EndTerrainLayer {
    NONE,
    PLAINS,
    HILLS,
    PLATEAU,
    MOUNTAINS,
    VOLCANO
}
