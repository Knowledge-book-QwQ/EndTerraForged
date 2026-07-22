package endterraforged.world.heightmap;

/**
 * Caller-owned surface inputs sampled from the raw terrain top.
 *
 * <p>{@code rawTop} is the normalised terrain height in the backing
 * {@link EndLevels} coordinate space, before climate, river and lake
 * post-processing. Convert it with {@link EndLevels#scale(float)} when a
 * world-space Y coordinate is required. {@code slope} is the dimensionless gradient
 * {@code g / (1 + g)}, where {@code g} is blocks of height per block of
 * horizontal distance, so it is always in {@code [0, 1)}. {@code curvature}
 * is the signed five-point Laplacian in blocks per block squared, normalised
 * as {@code k / (1 + abs(k))} into {@code [-1, 1]}. Positive curvature means
 * the centre is lower than its four neighbours.
 *
 * <p>The remaining channels are copied from the same centre sample used by
 * {@link EndHeightmap#sampleTerrainProfile}; they are family-level inputs,
 * not a claim that analytical erosion or sediment simulation is complete.
 * This buffer is mutable and not thread-safe. Keep one instance per sampling
 * thread and do not retain it across runtime swaps.</p>
 */
public final class EndTerrainProfileBuffer {
    private float rawTop;
    private float slope;
    private float curvature;
    private float roughness;
    private float erosionResistance;
    private int terrainTags;

    void set(float rawTop, float slope, float curvature,
             EndTerrainSignalBuffer signals) {
        this.rawTop = rawTop;
        this.slope = slope;
        this.curvature = curvature;
        this.roughness = signals.roughness();
        this.erosionResistance = signals.erosionResistance();
        this.terrainTags = signals.terrainTags();
    }

    public float rawTop() {
        return this.rawTop;
    }

    public float slope() {
        return this.slope;
    }

    public float curvature() {
        return this.curvature;
    }

    public float roughness() {
        return this.roughness;
    }

    public float erosionResistance() {
        return this.erosionResistance;
    }

    public int terrainTags() {
        return this.terrainTags;
    }
}
