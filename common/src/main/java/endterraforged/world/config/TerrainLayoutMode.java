package endterraforged.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * Selects how auxiliary terrain families are organised across a landmass.
 *
 * <p>{@link #REGION_PLANNED} is an internal P4 runtime path until the complete
 * v4 terrain catalog, morphology families, migration and editor closure exist.
 * Persisted v3 presets must continue to use {@link #LEGACY_SELECTOR}.</p>
 */
public enum TerrainLayoutMode {
    LEGACY_SELECTOR,
    REGION_PLANNED;

    public static final Codec<TerrainLayoutMode> CODEC = Codec.STRING.flatXmap(
            name -> {
                try {
                    return DataResult.success(TerrainLayoutMode.valueOf(name));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown TerrainLayoutMode: " + name);
                }
            },
            mode -> DataResult.success(mode.name()));
}
