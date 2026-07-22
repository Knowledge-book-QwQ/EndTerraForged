package endterraforged.world.terrain;

/**
 * Caller-owned mutable destination for {@link TerrainRegionLayout} sampling.
 *
 * <p>The buffer is intentionally not thread-safe. It stores a bounded set of
 * candidates whose compact-support weights form a partition of unity, so
 * multi-region junctions do not depend on a hard-coded nearest-neighbour count.</p>
 */
public final class TerrainRegionBuffer {

    final TerrainRegionLayout.CandidateScratch ownership = new TerrainRegionLayout.CandidateScratch();

    private final long[] candidateRegionIds = new long[TerrainRegionLayout.MAX_SEARCH_CANDIDATES];
    private final int[] candidateEntryIds = new int[TerrainRegionLayout.MAX_SEARCH_CANDIDATES];
    private final TerrainRegionFamily[] candidateFamilies =
            new TerrainRegionFamily[TerrainRegionLayout.MAX_SEARCH_CANDIDATES];
    private final TerrainPlacementMode[] candidatePlacements =
            new TerrainPlacementMode[TerrainRegionLayout.MAX_SEARCH_CANDIDATES];
    private final float[] candidateWeights = new float[TerrainRegionLayout.MAX_SEARCH_CANDIDATES];
    private final float[] candidateCenterX = new float[TerrainRegionLayout.MAX_SEARCH_CANDIDATES];
    private final float[] candidateCenterZ = new float[TerrainRegionLayout.MAX_SEARCH_CANDIDATES];
    private final float[] candidateOrientations = new float[TerrainRegionLayout.MAX_SEARCH_CANDIDATES];

    private int candidateCount;
    private long underlayRegionId;
    private int underlayEntryId;
    private TerrainRegionFamily underlayFamily;
    private TerrainRegionFamily visibleFamily;
    private float edge;
    private float blend;
    private float physicalInfluence;
    private long featureAnchorKey;
    private float sampleX;
    private float sampleZ;

    void setBase(long underlayRegionId,
                 int underlayEntryId,
                 TerrainRegionFamily underlayFamily,
                 TerrainRegionFamily visibleFamily,
                 float edge,
                 float blend,
                 float physicalInfluence,
                 float sampleX,
                 float sampleZ) {
        this.underlayRegionId = underlayRegionId;
        this.underlayEntryId = underlayEntryId;
        this.underlayFamily = underlayFamily;
        this.visibleFamily = visibleFamily;
        this.edge = edge;
        this.blend = blend;
        this.physicalInfluence = physicalInfluence;
        this.featureAnchorKey = 0L;
        this.sampleX = sampleX;
        this.sampleZ = sampleZ;
    }

    void clearCandidates() {
        this.candidateCount = 0;
    }

    void addCandidate(long regionId,
                      int entryId,
                      TerrainRegionFamily family,
                      TerrainPlacementMode placement,
                      float blend,
                      float centerX,
                      float centerZ,
                      float orientation) {
        if (this.candidateCount >= this.candidateRegionIds.length) {
            throw new IllegalStateException("terrain region blend candidate capacity exceeded");
        }
        int index = this.candidateCount++;
        this.candidateRegionIds[index] = regionId;
        this.candidateEntryIds[index] = entryId;
        this.candidateFamilies[index] = family;
        this.candidatePlacements[index] = placement;
        this.candidateWeights[index] = blendRatio(blend);
        this.candidateCenterX[index] = centerX;
        this.candidateCenterZ[index] = centerZ;
        this.candidateOrientations[index] = orientation;
    }

    void normalizeCandidateWeights() {
        float total = 0.0F;
        for (int index = 0; index < this.candidateCount; index++) {
            total += this.candidateWeights[index];
        }
        if (!(total > 0.0F) || !Float.isFinite(total)) {
            throw new IllegalStateException("terrain region blend weights must have a finite positive sum");
        }
        float inverse = 1.0F / total;
        for (int index = 0; index < this.candidateCount; index++) {
            this.candidateWeights[index] *= inverse;
        }
    }

    public int candidateCount() {
        return this.candidateCount;
    }

    public long candidateRegionId(int index) {
        requireCandidate(index);
        return this.candidateRegionIds[index];
    }

    public int candidateEntryId(int index) {
        requireCandidate(index);
        return this.candidateEntryIds[index];
    }

    public TerrainRegionFamily candidateFamily(int index) {
        requireCandidate(index);
        return this.candidateFamilies[index];
    }

    public TerrainPlacementMode candidatePlacement(int index) {
        requireCandidate(index);
        return this.candidatePlacements[index];
    }

    public float candidateWeight(int index) {
        requireCandidate(index);
        return this.candidateWeights[index];
    }

    public float candidateCenterX(int index) {
        requireCandidate(index);
        return this.candidateCenterX[index];
    }

    public float candidateCenterZ(int index) {
        requireCandidate(index);
        return this.candidateCenterZ[index];
    }

    public float candidateOrientation(int index) {
        requireCandidate(index);
        return this.candidateOrientations[index];
    }

    public long regionId() {
        return candidateRegionId(0);
    }

    public int ownershipEntryId() {
        return candidateEntryId(0);
    }

    public TerrainRegionFamily ownershipFamily() {
        return candidateFamily(0);
    }

    public TerrainPlacementMode placement() {
        return candidatePlacement(0);
    }

    public long underlayRegionId() {
        return underlayRegionId;
    }

    public int underlayEntryId() {
        return underlayEntryId;
    }

    public TerrainRegionFamily underlayFamily() {
        return underlayFamily;
    }

    public TerrainRegionFamily visibleFamily() {
        return visibleFamily;
    }

    public long boundaryRegionId() {
        return candidateRegionId(1);
    }

    public int boundaryEntryId() {
        return candidateEntryId(1);
    }

    public TerrainRegionFamily boundaryFamily() {
        return candidateFamily(1);
    }

    public TerrainPlacementMode boundaryPlacement() {
        return candidatePlacement(1);
    }

    public long tertiaryRegionId() {
        return candidateRegionId(2);
    }

    public int tertiaryEntryId() {
        return candidateEntryId(2);
    }

    public TerrainRegionFamily tertiaryFamily() {
        return candidateFamily(2);
    }

    public TerrainPlacementMode tertiaryPlacement() {
        return candidatePlacement(2);
    }

    public float edge() {
        return edge;
    }

    public float blend() {
        return blend;
    }

    public float tertiaryBlend() {
        return blendFromWeight(candidateWeight(2), candidateWeight(0));
    }

    public float ownershipWeight() {
        return candidateWeight(0);
    }

    public float boundaryWeight() {
        return candidateWeight(1);
    }

    public float tertiaryWeight() {
        return candidateWeight(2);
    }

    public float physicalInfluence() {
        return physicalInfluence;
    }

    /**
     * Updates the bounded physical footprint of the shaped owner at the sampled point.
     */
    public void setPhysicalInfluence(float physicalInfluence) {
        if (!Float.isFinite(physicalInfluence)) {
            throw new IllegalArgumentException("terrain physical influence must be finite");
        }
        this.physicalInfluence = Math.clamp(physicalInfluence, 0.0F, 1.0F);
        if (placement() != TerrainPlacementMode.AREA) {
            this.visibleFamily = this.physicalInfluence > 0.0F
                    ? ownershipFamily()
                    : this.underlayFamily;
        }
    }

    /**
     * Publishes a shaped overlay without changing AREA ownership.
     *
     * <p>The caller must pass the feature family only for positive influence.
     * Clearing the feature restores the visible AREA owner.</p>
     */
    public void setFeatureInfluence(float physicalInfluence, TerrainRegionFamily featureFamily) {
        setFeatureInfluence(physicalInfluence, featureFamily, 0L);
    }

    /** Publishes the strongest shaped overlay and its stable anchor identity. */
    public void setFeatureInfluence(float physicalInfluence,
                                    TerrainRegionFamily featureFamily,
                                    long featureAnchorKey) {
        if (!Float.isFinite(physicalInfluence)) {
            throw new IllegalArgumentException("terrain physical influence must be finite");
        }
        this.physicalInfluence = Math.clamp(physicalInfluence, 0.0F, 1.0F);
        this.visibleFamily = this.physicalInfluence > 0.0F
                ? java.util.Objects.requireNonNull(featureFamily, "featureFamily")
                : ownershipFamily();
        this.featureAnchorKey = this.physicalInfluence > 0.0F ? featureAnchorKey : 0L;
    }

    public long featureAnchorKey() {
        return this.featureAnchorKey;
    }

    public float sampleX() {
        return sampleX;
    }

    public float sampleZ() {
        return sampleZ;
    }

    public float centerX() {
        return candidateCenterX(0);
    }

    public float centerZ() {
        return candidateCenterZ(0);
    }

    public float orientation() {
        return candidateOrientation(0);
    }

    public float boundaryCenterX() {
        return candidateCenterX(1);
    }

    public float boundaryCenterZ() {
        return candidateCenterZ(1);
    }

    public float boundaryOrientation() {
        return candidateOrientation(1);
    }

    public float tertiaryCenterX() {
        return candidateCenterX(2);
    }

    public float tertiaryCenterZ() {
        return candidateCenterZ(2);
    }

    public float tertiaryOrientation() {
        return candidateOrientation(2);
    }

    /** Materialises a diagnostic value outside the worldgen hot path. */
    public TerrainRegionPlan snapshot() {
        return new TerrainRegionPlan(regionId(), ownershipEntryId(), ownershipFamily(), placement(),
                underlayRegionId, underlayEntryId, underlayFamily, visibleFamily,
                boundaryRegionId(), boundaryEntryId(), boundaryFamily(), boundaryPlacement(), edge, blend,
                physicalInfluence, centerX(), centerZ(), orientation(),
                boundaryCenterX(), boundaryCenterZ(), boundaryOrientation(), featureAnchorKey);
    }

    private void requireCandidate(int index) {
        if (index < 0 || index >= this.candidateCount) {
            throw new IndexOutOfBoundsException("terrain region candidate index: " + index);
        }
    }

    private static float blendRatio(float blend) {
        float clamped = Math.clamp(blend, 0.0F, 1.0F);
        return clamped / (2.0F - clamped);
    }

    private static float blendFromWeight(float candidateWeight, float ownerWeight) {
        if (!(ownerWeight > 0.0F)) {
            return 0.0F;
        }
        float ratio = candidateWeight / ownerWeight;
        return Math.clamp((2.0F * ratio) / (1.0F + ratio), 0.0F, 1.0F);
    }
}
