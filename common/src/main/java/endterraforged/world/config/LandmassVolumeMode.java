package endterraforged.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * Vertical interpretation of a macro landmass after its two-dimensional top
 * surface has been sampled.
 */
public enum LandmassVolumeMode {
    /** Preserves the historical sea-mode-controlled column filling behaviour. */
    LEGACY_COLUMN,

    /** Creates a finite landmass between its terrain top and a configured underside. */
    FLOATING_SHELF;

    public static final Codec<LandmassVolumeMode> CODEC = Codec.STRING.flatXmap(
            name -> {
                try {
                    return DataResult.success(LandmassVolumeMode.valueOf(name));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown LandmassVolumeMode: " + name);
                }
            },
            mode -> DataResult.success(mode.name()));
}
