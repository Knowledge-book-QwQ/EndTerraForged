package endterraforged.world.continent;

/**
 * Caller-owned diagnostics produced by {@link RtfAdvancedContinent}.
 *
 * <p>The buffer is intentionally mutable and not thread-safe. Chunk-generation
 * callers must keep one buffer per worker or invocation. The continent runtime
 * itself remains immutable and safe to share.</p>
 */
public final class AdvancedContinentSignalBuffer {

    private float edge;
    private float continentId;
    private float distance;
    private int centerX;
    private int centerZ;
    private boolean skipped;

    void set(float edge, float continentId, float distance,
             int centerX, int centerZ, boolean skipped) {
        this.edge = Math.clamp(edge, 0.0F, 1.0F);
        this.continentId = continentId;
        this.distance = Math.max(0.0F, distance);
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.skipped = skipped;
    }

    public float edge() {
        return this.edge;
    }

    public float continentId() {
        return this.continentId;
    }

    public float distance() {
        return this.distance;
    }

    public int centerX() {
        return this.centerX;
    }

    public int centerZ() {
        return this.centerZ;
    }

    public boolean skipped() {
        return this.skipped;
    }
}
