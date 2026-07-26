package endterraforged.world.erosion;

import java.util.Arrays;

/** Worker-owned primitive scratch for the P4.7 hydraulic tile candidate. */
final class EndHydraulicErosionScratch {

    private static final int ARRAY_COUNT = 4;

    private final float[] deltaUnits;
    private final float[] cutBudgetUnits;
    private final float[] transferFactor;
    private final float[] flux;
    private final Sample currentSample = new Sample();
    private final Sample nextSample = new Sample();

    EndHydraulicErosionScratch(int cells) {
        if (cells <= 0) {
            throw new IllegalArgumentException("cells must be > 0, got " + cells);
        }
        this.deltaUnits = new float[cells];
        this.cutBudgetUnits = new float[cells];
        this.transferFactor = new float[cells];
        this.flux = new float[cells];
    }

    int capacity() {
        return this.deltaUnits.length;
    }

    long primitiveBytes() {
        return (long) capacity() * ARRAY_COUNT * Float.BYTES;
    }

    float[] deltaUnits() {
        return this.deltaUnits;
    }

    float[] cutBudgetUnits() {
        return this.cutBudgetUnits;
    }

    float[] transferFactor() {
        return this.transferFactor;
    }

    float[] flux() {
        return this.flux;
    }

    Sample currentSample() {
        return this.currentSample;
    }

    Sample nextSample() {
        return this.nextSample;
    }

    void clear() {
        Arrays.fill(this.deltaUnits, 0.0F);
        Arrays.fill(this.cutBudgetUnits, 0.0F);
        Arrays.fill(this.transferFactor, 0.0F);
        Arrays.fill(this.flux, 0.0F);
    }

    static final class Sample {
        int nodeX;
        int nodeZ;
        float offsetX;
        float offsetZ;
        float height;
        float gradientX;
        float gradientZ;
    }
}
