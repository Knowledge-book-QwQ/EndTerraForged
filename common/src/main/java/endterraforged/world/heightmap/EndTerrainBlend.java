package endterraforged.world.heightmap;

import java.util.Objects;

/**
 * Diagnostic terrain-layer blend sampled at a world coordinate.
 *
 * <p>World generation consumes the scalar height from {@link EndHeightmap};
 * this value object exists so previews can show smooth layer boundaries without
 * duplicating selector math outside the heightmap package.</p>
 */
public record EndTerrainBlend(EndTerrainLayer from, EndTerrainLayer to, float alpha) {
    public static final EndTerrainBlend NONE = single(EndTerrainLayer.NONE);

    public EndTerrainBlend {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        alpha = Math.clamp(alpha, 0.0F, 1.0F);
    }

    public static EndTerrainBlend single(EndTerrainLayer layer) {
        Objects.requireNonNull(layer, "layer");
        return new EndTerrainBlend(layer, layer, 0.0F);
    }

    public boolean isBlended() {
        return this.from != this.to && this.alpha > 0.0F;
    }

    public EndTerrainLayer dominantLayer() {
        return this.alpha < 0.5F ? this.from : this.to;
    }
}
