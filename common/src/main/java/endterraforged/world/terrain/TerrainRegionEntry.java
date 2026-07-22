package endterraforged.world.terrain;

import java.util.Objects;

/**
 * Immutable construction input for one macro terrain family.
 *
 * @param entryId stable runtime catalog id
 * @param family built-in terrain family
 * @param placement macro placement semantic; ownership layouts only accept {@link TerrainPlacementMode#AREA}
 * @param weight target relative AREA share before region-size compensation
 * @param regionSize average world-space repetition size
 * @param aspectRatio major/minor axis ratio for shaped ownership grids
 */
public record TerrainRegionEntry(int entryId,
                                 TerrainRegionFamily family,
                                 TerrainPlacementMode placement,
                                 float weight,
                                 int regionSize,
                                 float aspectRatio) {

    public TerrainRegionEntry {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(placement, "placement");
    }

    public static TerrainRegionEntry area(int entryId, TerrainRegionFamily family,
                                          float weight, int regionSize) {
        return new TerrainRegionEntry(entryId, family, TerrainPlacementMode.AREA,
                weight, regionSize, 1.0F);
    }
}
