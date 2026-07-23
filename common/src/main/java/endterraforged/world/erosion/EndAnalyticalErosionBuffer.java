package endterraforged.world.erosion;

/**
 * Caller-owned output for one local analytical erosion sample.
 *
 * <p>This buffer is mutable and not thread-safe. Keep one instance per
 * sampling thread and do not retain it across runtime swaps.</p>
 */
public final class EndAnalyticalErosionBuffer {

    private float top;
    private float erosionDelta;
    private float erosionStrength;
    private float drainagePotential;
    private float activation;

    void set(float top, float erosionDelta, float erosionStrength,
             float drainagePotential, float activation) {
        this.top = top;
        this.erosionDelta = erosionDelta;
        this.erosionStrength = erosionStrength;
        this.drainagePotential = drainagePotential;
        this.activation = activation;
    }

    /** Returns the final normalized top for this candidate sample. */
    public float top() {
        return top;
    }

    /** Returns {@code top - rawTop}; the baseline never returns a positive value. */
    public float erosionDelta() {
        return erosionDelta;
    }

    /** Returns the bounded local erosion strength in {@code [0,1]}. */
    public float erosionStrength() {
        return erosionStrength;
    }

    /** Returns the diagnostic valley/drainage potential in {@code [0,1]}. */
    public float drainagePotential() {
        return drainagePotential;
    }

    /** Returns the final gate activation in {@code [0,1]}. */
    public float activation() {
        return activation;
    }
}
