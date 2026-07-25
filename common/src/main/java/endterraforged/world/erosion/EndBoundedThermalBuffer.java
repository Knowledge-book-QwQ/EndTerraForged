package endterraforged.world.erosion;

/**
 * Caller-owned primitive scratch and output for the bounded thermal candidate.
 *
 * <p>This buffer is mutable and not thread-safe. It owns no world or runtime
 * references and performs no allocation after construction.</p>
 */
final class EndBoundedThermalBuffer {

    private static final int ARRAY_COUNT = 4;

    private final float[] first;
    private final float[] second;
    private final float[] transferDelta;
    private final float[] remainingExportBudget;

    private float[] current;
    private float[] next;
    private float[] resultTop;
    private float[] resultDelta;
    private float movedMaterialBlocks;
    private float netMaterialDeltaBlocks;
    private int transferCount;

    EndBoundedThermalBuffer(int cells) {
        if (cells <= 0) {
            throw new IllegalArgumentException("cells must be > 0, got " + cells);
        }
        this.first = new float[cells];
        this.second = new float[cells];
        this.transferDelta = new float[cells];
        this.remainingExportBudget = new float[cells];
        this.current = this.first;
        this.next = this.second;
        this.resultTop = this.first;
        this.resultDelta = this.second;
    }

    int capacity() {
        return this.first.length;
    }

    long primitiveBytes() {
        return (long) this.first.length * ARRAY_COUNT * Float.BYTES;
    }

    float top(int index) {
        return this.resultTop[index];
    }

    float erosionDelta(int index) {
        return this.resultDelta[index];
    }

    float movedMaterialBlocks() {
        return this.movedMaterialBlocks;
    }

    float netMaterialDeltaBlocks() {
        return this.netMaterialDeltaBlocks;
    }

    int transferCount() {
        return this.transferCount;
    }

    float[] current() {
        return this.current;
    }

    float[] next() {
        return this.next;
    }

    float[] transferDelta() {
        return this.transferDelta;
    }

    float[] remainingExportBudget() {
        return this.remainingExportBudget;
    }

    void begin() {
        this.current = this.first;
        this.next = this.second;
        this.movedMaterialBlocks = 0.0F;
        this.netMaterialDeltaBlocks = 0.0F;
        this.transferCount = 0;
    }

    void swap() {
        float[] previous = this.current;
        this.current = this.next;
        this.next = previous;
    }

    void recordTransfer(float amount) {
        this.movedMaterialBlocks += amount;
        this.transferCount++;
    }

    void finish(float[] sourceTop, float worldHeightBlocks, int cells,
                double sourceMaterialBlocks, double finalMaterialBlocks) {
        this.resultTop = this.current;
        this.resultDelta = this.next;
        for (int index = 0; index < cells; index++) {
            float finalTop = Math.clamp(this.current[index] / worldHeightBlocks, 0.0F, 1.0F);
            this.resultTop[index] = finalTop;
            this.resultDelta[index] = finalTop - sourceTop[index];
        }
        this.netMaterialDeltaBlocks = (float) (finalMaterialBlocks - sourceMaterialBlocks);
    }
}
