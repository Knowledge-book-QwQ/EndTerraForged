package endterraforged.world.terrain;

import java.util.Objects;

/**
 * Immutable diagnostic snapshot of one macro terrain ownership decision.
 *
 * <p>Runtime sampling writes into {@link TerrainRegionBuffer}; this record is
 * only materialised for tests, preview and diagnostics. {@code edge} is one in
 * a stable region interior and approaches zero at an ownership boundary.</p>
 *
 * @param regionId packed owner-grid cell id; paired with {@code ownershipEntryId}
 * @param ownershipEntryId stable catalog entry id
 * @param ownershipFamily macro owner independent of physical feature influence
 * @param placement owner placement semantic
 * @param underlayRegionId packed AREA underlay-grid cell id
 * @param underlayEntryId stable AREA underlay catalog entry id
 * @param underlayFamily AREA family used below a shaped owner
 * @param visibleFamily family currently visible to downstream consumers
 * @param boundaryRegionId packed identity of the nearest competing region
 * @param boundaryEntryId nearest competing catalog entry id
 * @param boundaryFamily nearest competing family
 * @param boundaryPlacement nearest competing placement semantic
 * @param edge region-interior signal in {@code [0, 1]}
 * @param blend boundary blend signal in {@code [0, 1]}
 * @param physicalInfluence shaped feature influence in {@code [0, 1]}
 * @param centerX owner world-space centre X
 * @param centerZ owner world-space centre Z
 * @param orientation stable owner orientation in radians
 * @param featureAnchorKey stable shaped-feature anchor identity; meaningful when physical influence is positive
 */
public record TerrainRegionPlan(long regionId,
                                int ownershipEntryId,
                                TerrainRegionFamily ownershipFamily,
                                TerrainPlacementMode placement,
                                long underlayRegionId,
                                int underlayEntryId,
                                TerrainRegionFamily underlayFamily,
                                TerrainRegionFamily visibleFamily,
                                long boundaryRegionId,
                                int boundaryEntryId,
                                TerrainRegionFamily boundaryFamily,
                                TerrainPlacementMode boundaryPlacement,
                                float edge,
                                float blend,
                                float physicalInfluence,
                                float centerX,
                                float centerZ,
                                float orientation,
                                float boundaryCenterX,
                                float boundaryCenterZ,
                                float boundaryOrientation,
                                long featureAnchorKey) {

    public TerrainRegionPlan {
        Objects.requireNonNull(ownershipFamily, "ownershipFamily");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(underlayFamily, "underlayFamily");
        Objects.requireNonNull(visibleFamily, "visibleFamily");
        Objects.requireNonNull(boundaryFamily, "boundaryFamily");
        Objects.requireNonNull(boundaryPlacement, "boundaryPlacement");
        requireUnit("edge", edge);
        requireUnit("blend", blend);
        requireUnit("physicalInfluence", physicalInfluence);
        if (!Float.isFinite(centerX) || !Float.isFinite(centerZ) || !Float.isFinite(orientation)
                || !Float.isFinite(boundaryCenterX) || !Float.isFinite(boundaryCenterZ)
                || !Float.isFinite(boundaryOrientation)) {
            throw new IllegalArgumentException("terrain region coordinates and orientation must be finite");
        }
    }

    private static void requireUnit(String name, float value) {
        if (!Float.isFinite(value) || value < 0.0F || value > 1.0F) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1], got " + value);
        }
    }
}
