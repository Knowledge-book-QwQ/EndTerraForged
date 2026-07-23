package endterraforged.world.heightmap;

/**
 * Caller-owned scalar terrain channels for one world position.
 *
 * <p>{@code height} is the auxiliary contribution before continent volume
 * scaling. Roughness and erosion resistance are family-level signals at this stage;
 * structured feature refinements are added by their own runtimes.</p>
 *
 * <p>This buffer is mutable and not thread-safe. A caller must keep one
 * instance per sampling thread and must not retain it across runtime swaps.</p>
 */
public final class EndTerrainSignalBuffer {
    private float height;
    private float roughness;
    private float erosionResistance;
    private int terrainTags;

    public void clear() {
        this.height = 0.0F;
        this.roughness = 0.0F;
        this.erosionResistance = 0.0F;
        this.terrainTags = 0;
    }

    void set(float height, float roughness, float erosionResistance, int terrainTags) {
        this.height = height;
        this.roughness = roughness;
        this.erosionResistance = erosionResistance;
        this.terrainTags = terrainTags;
    }

    void addWeighted(EndTerrainSignalBuffer sample, float weight) {
        this.height += sample.height * weight;
        this.roughness += sample.roughness * weight;
        this.erosionResistance += sample.erosionResistance * weight;
        if (weight > 0.0F) {
            this.terrainTags |= sample.terrainTags;
        }
    }

    public float height() {
        return height;
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
