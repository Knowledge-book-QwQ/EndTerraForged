package endterraforged.world.terrain;

/**
 * Caller-owned Top-3 destination for bounded ridge anchor sampling.
 *
 * <p>The buffer is mutable and not thread-safe. Candidate order is strongest
 * physical influence first, followed by distance and unsigned anchor key.</p>
 */
public final class TerrainRidgeBuffer {
    public static final int MAX_CANDIDATES = 3;

    private final long[] anchorKeys = new long[MAX_CANDIDATES];
    private final int[] anchorSeeds = new int[MAX_CANDIDATES];
    private final float[] centerX = new float[MAX_CANDIDATES];
    private final float[] centerZ = new float[MAX_CANDIDATES];
    private final float[] rotationCos = new float[MAX_CANDIDATES];
    private final float[] rotationSin = new float[MAX_CANDIDATES];
    private final float[] distanceSquared = new float[MAX_CANDIDATES];
    private final float[] influence = new float[MAX_CANDIDATES];
    private int candidateCount;

    void clear() {
        this.candidateCount = 0;
    }

    void insert(long anchorKey, int anchorSeed, float centerX, float centerZ,
                float rotationCos, float rotationSin, float distanceSquared, float influence) {
        int insertion = this.candidateCount;
        for (int index = 0; index < this.candidateCount; index++) {
            if (better(influence, distanceSquared, anchorKey,
                    this.influence[index], this.distanceSquared[index], this.anchorKeys[index])) {
                insertion = index;
                break;
            }
        }
        if (insertion >= MAX_CANDIDATES) {
            return;
        }
        int newCount = Math.min(MAX_CANDIDATES, this.candidateCount + 1);
        for (int index = newCount - 1; index > insertion; index--) {
            copy(index - 1, index);
        }
        this.anchorKeys[insertion] = anchorKey;
        this.anchorSeeds[insertion] = anchorSeed;
        this.centerX[insertion] = centerX;
        this.centerZ[insertion] = centerZ;
        this.rotationCos[insertion] = rotationCos;
        this.rotationSin[insertion] = rotationSin;
        this.distanceSquared[insertion] = distanceSquared;
        this.influence[insertion] = influence;
        this.candidateCount = newCount;
    }

    public int candidateCount() {
        return this.candidateCount;
    }

    public long anchorKey(int index) {
        requireCandidate(index);
        return this.anchorKeys[index];
    }

    public int anchorSeed(int index) {
        requireCandidate(index);
        return this.anchorSeeds[index];
    }

    public float centerX(int index) {
        requireCandidate(index);
        return this.centerX[index];
    }

    public float centerZ(int index) {
        requireCandidate(index);
        return this.centerZ[index];
    }

    public float rotationCos(int index) {
        requireCandidate(index);
        return this.rotationCos[index];
    }

    public float rotationSin(int index) {
        requireCandidate(index);
        return this.rotationSin[index];
    }

    public float distanceSquared(int index) {
        requireCandidate(index);
        return this.distanceSquared[index];
    }

    public float influence(int index) {
        requireCandidate(index);
        return this.influence[index];
    }

    private void copy(int source, int target) {
        this.anchorKeys[target] = this.anchorKeys[source];
        this.anchorSeeds[target] = this.anchorSeeds[source];
        this.centerX[target] = this.centerX[source];
        this.centerZ[target] = this.centerZ[source];
        this.rotationCos[target] = this.rotationCos[source];
        this.rotationSin[target] = this.rotationSin[source];
        this.distanceSquared[target] = this.distanceSquared[source];
        this.influence[target] = this.influence[source];
    }

    private static boolean better(float influence, float distanceSquared, long anchorKey,
                                  float currentInfluence, float currentDistanceSquared,
                                  long currentAnchorKey) {
        int influenceOrder = Float.compare(influence, currentInfluence);
        if (influenceOrder != 0) {
            return influenceOrder > 0;
        }
        int distanceOrder = Float.compare(distanceSquared, currentDistanceSquared);
        if (distanceOrder != 0) {
            return distanceOrder < 0;
        }
        return Long.compareUnsigned(anchorKey, currentAnchorKey) < 0;
    }

    private void requireCandidate(int index) {
        if (index < 0 || index >= this.candidateCount) {
            throw new IndexOutOfBoundsException("ridge candidate index: " + index);
        }
    }
}
