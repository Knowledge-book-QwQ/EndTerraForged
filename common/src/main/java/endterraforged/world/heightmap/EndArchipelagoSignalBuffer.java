package endterraforged.world.heightmap;

import endterraforged.world.continent.Continent;

/**
 * Caller-owned primitive output for the attached archipelago layer.
 * Instances are mutable and not thread-safe; each density worker owns its
 * buffer and callers must not share one between concurrent samples.
 */
public final class EndArchipelagoSignalBuffer {

    private float mask;
    private float landness;
    private float inlandness;
    private float reliefWeight;
    private boolean identified;
    private long chainId;

    /** Clears the feature without changing any external continent identity. */
    public void clear() {
        this.mask = 0.0F;
        this.landness = 0.0F;
        this.inlandness = 0.0F;
        this.reliefWeight = 0.0F;
        this.identified = false;
        this.chainId = 0L;
    }

    /** Stores a sampled feature and its diagnostic chain identity. */
    public void set(float mask, float chainLandness, float chainInlandness,
                    float chainReliefWeight, int chainX, int chainZ) {
        this.mask = Math.clamp(mask, 0.0F, 1.0F);
        this.landness = Math.clamp(chainLandness, 0.0F, 1.0F);
        this.inlandness = Math.clamp(chainInlandness, 0.0F, 1.0F);
        this.reliefWeight = Math.clamp(chainReliefWeight, 0.0F, 1.0F);
        this.identified = this.mask > 0.001F;
        this.chainId = this.identified ? Continent.packId(chainX, chainZ) : 0L;
    }

    public float mask() {
        return this.mask;
    }

    public float landness() {
        return this.landness;
    }

    public float inlandness() {
        return this.inlandness;
    }

    public float reliefWeight() {
        return this.reliefWeight;
    }

    public boolean identified() {
        return this.identified;
    }

    public long chainId() {
        return this.chainId;
    }
}
