package endterraforged.world.continent;

/**
 * Reusable mutable destination for continent sampling.
 *
 * <p>Instances are caller-owned and intentionally not thread-safe. Runtime
 * density owns one per worker cache; previews may materialise an immutable
 * {@link ContinentSignals} snapshot after sampling.</p>
 */
public final class ContinentSignalBuffer {

    private float edge;
    private float landness;
    private float inlandness;
    private boolean identified;
    private long continentId;
    private int centerX;
    private int centerZ;

    /** Replaces all values and clears any discrete continent identity. */
    public void set(float edge, float landness, float inlandness) {
        setValues(edge, landness, inlandness);
        clearIdentity();
    }

    /**
     * Replaces all values and assigns a stable discrete continent identity.
     *
     * <p>The id must describe ownership, not a blended edge value. Wrappers may
     * subsequently reshape the continuous values through {@link #setValues}
     * without changing this identity.</p>
     */
    public void setIdentified(float edge, float landness, float inlandness,
                              long continentId, int centerX, int centerZ) {
        setValues(edge, landness, inlandness);
        this.identified = true;
        this.continentId = continentId;
        this.centerX = centerX;
        this.centerZ = centerZ;
    }

    /** Replaces continuous values while preserving the current identity. */
    public void setValues(float edge, float landness, float inlandness) {
        this.edge = Math.clamp(edge, 0.0F, 1.0F);
        this.landness = Math.clamp(landness, 0.0F, 1.0F);
        this.inlandness = Math.clamp(inlandness, 0.0F, 1.0F);
    }

    /** Clears identity metadata without changing the continuous values. */
    public void clearIdentity() {
        this.identified = false;
        this.continentId = 0L;
        this.centerX = 0;
        this.centerZ = 0;
    }

    /** Scales continuous activation while preserving non-zero ownership. */
    public void scale(float factor) {
        float clamped = Math.clamp(factor, 0.0F, 1.0F);
        if (clamped <= 0.0F) {
            set(0.0F, 0.0F, 0.0F);
            return;
        }
        setValues(this.edge * clamped, this.landness * clamped, this.inlandness * clamped);
    }

    public float edge() {
        return this.edge;
    }

    public float landness() {
        return this.landness;
    }

    public float inlandness() {
        return this.inlandness;
    }

    /** Returns whether this sample belongs to a discrete macro-continent cell. */
    public boolean identified() {
        return this.identified;
    }

    /** Returns the stable packed cell identity, or {@code 0} when unidentified. */
    public long continentId() {
        return this.continentId;
    }

    /** Returns the owning continent's representative world X coordinate. */
    public int centerX() {
        return this.centerX;
    }

    /** Returns the owning continent's representative world Z coordinate. */
    public int centerZ() {
        return this.centerZ;
    }

    /** Materialises an immutable diagnostic value outside the density hot path. */
    public ContinentSignals snapshot() {
        return new ContinentSignals(
                this.edge,
                this.landness,
                this.inlandness,
                this.identified,
                this.continentId,
                this.centerX,
                this.centerZ);
    }
}
