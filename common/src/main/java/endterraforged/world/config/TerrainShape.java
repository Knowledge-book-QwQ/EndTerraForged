package endterraforged.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * Selects the mountain noise recipe used by the End heightmap.
 */
public enum TerrainShape {
    /** Smooth, rolling ridge fields based on RTF's first mountain recipe. */
    ROLLING_RIDGES,
    /** Broken, sharp ridge fields based on RTF's second mountain recipe. */
    SHATTERED_RIDGES;

    public static final Codec<TerrainShape> CODEC = Codec.STRING.flatXmap(
            name -> {
                try {
                    return DataResult.success(TerrainShape.valueOf(name));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown TerrainShape: " + name);
                }
            },
            shape -> DataResult.success(shape.name()));
}
