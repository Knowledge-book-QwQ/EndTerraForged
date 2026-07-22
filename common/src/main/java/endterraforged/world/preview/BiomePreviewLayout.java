package endterraforged.world.preview;

import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;

import endterraforged.world.config.BiomeLayoutConfig;
import endterraforged.world.level.biome.EndBiomeLayout;

/**
 * Preview-only adapter over the runtime biome layout classifier.
 */
final class BiomePreviewLayout {

    private final EndBiomeLayout layout;
    private final boolean showVariantBlend;

    private BiomePreviewLayout(EndBiomeLayout layout, boolean showVariantBlend) {
        this.layout = layout;
        this.showVariantBlend = showVariantBlend;
    }

    static BiomePreviewLayout create(BiomeLayoutConfig config) {
        return new BiomePreviewLayout(config.buildRuntime(),
                !config.variantBlendConfig().equals(BiomeLayoutConfig.DEFAULT.variantBlendConfig()));
    }

    int color(float worldX, float worldZ) {
        int biomeX = biomeCoordinate(worldX);
        int biomeZ = biomeCoordinate(worldZ);
        EndBiomeLayout.Ring ring = layout.ringAt(biomeX, biomeZ);
        if (!showVariantBlend) {
            return TerrainPreviewPalette.biomeRingColor(ring);
        }
        return TerrainPreviewPalette.biomeRingBlendColor(ring,
                layout.fracX(biomeX, biomeZ), layout.fracZ(biomeX, biomeZ));
    }

    int climateColor(float worldX, float worldZ, float landness,
                     float temperature, float moisture) {
        int biomeX = biomeCoordinate(worldX);
        int biomeZ = biomeCoordinate(worldZ);
        return TerrainPreviewPalette.biomeClimateColor(landness, layout.ringAt(biomeX, biomeZ),
                temperature, moisture);
    }

    static int biomeCoordinate(float world) {
        return QuartPos.fromBlock(Mth.floor(world));
    }
}
