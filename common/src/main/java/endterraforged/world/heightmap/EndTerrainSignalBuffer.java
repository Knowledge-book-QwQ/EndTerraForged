package endterraforged.world.heightmap;

/**
 * Caller-owned scalar terrain channels for one world position.
 *
 * <p>{@code height} is the auxiliary contribution before continent volume
 * scaling. {@code uplift} is an independent macro relief signal and is not
 * folded into {@code height}; the heightmap owns the final envelope step.
 * Roughness and erosion resistance are family-level signals at this stage;
 * structured feature refinements are added by their own runtimes.</p>
 *
 * <p>This buffer is mutable and not thread-safe. A caller must keep one
 * instance per sampling thread and must not retain it across runtime swaps.</p>
 */
public final class EndTerrainSignalBuffer {
    private float height;
    private float uplift;
    private float roughness;
    private float erosionResistance;
    private int terrainTags;

    public void clear() {
        this.height = 0.0F;
        this.uplift = 0.0F;
        this.roughness = 0.0F;
        this.erosionResistance = 0.0F;
        this.terrainTags = 0;
    }

    void set(float height, float roughness, float erosionResistance, int terrainTags) {
        this.height = height;
        this.uplift = 0.0F;
        this.roughness = roughness;
        this.erosionResistance = erosionResistance;
        this.terrainTags = terrainTags;
    }

    void addWeighted(EndTerrainSignalBuffer sample, float weight) {
        this.height += sample.height * weight;
        this.uplift += sample.uplift * weight;
        this.roughness += sample.roughness * weight;
        this.erosionResistance += sample.erosionResistance * weight;
        if (weight > 0.0F) {
            this.terrainTags |= sample.terrainTags;
        }
    }

    public float height() {
        return height;
    }

    /** Returns the independent macro relief signal in {@code [0,1]}. */
    public float uplift() {
        return uplift;
    }

    void setUplift(float uplift) {
        this.uplift = Math.clamp(uplift, 0.0F, 1.0F);
    }

    public float roughness() {
        return roughness;
    }

    public float erosionResistance() {
        return erosionResistance;
    }

    public int terrainTags() {
        return terrainTags;
    }
}
