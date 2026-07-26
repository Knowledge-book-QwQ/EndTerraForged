package endterraforged.world.erosion;

import java.util.Arrays;

/** Worker-owned primitive scratch for the bounded flow erosion candidate. */
final class EndBoundedFlowErosionScratch {

    private static final int FLOAT_CELL_ARRAYS = 6;
    private static final int BYTE_CELL_ARRAYS = 3;

    private final float[] routingTopBlocks;
    private final float[] spillDepthBlocks;
    private final float[] dominantWeight;
    private final float[] firstFlux;
    private final float[] secondFlux;
    private final float[] accumulatedFlux;
    private final byte[] escapeDirection;
    private final byte[] dominantDirection;
    private final byte[] secondaryDirection;

    private final float[] floodBest;
    private final int[] floodParent;
    private final int[] floodHeapNodes;
    private final int[] floodHeapPositions;
    private final long[] floodTie;

    private float[] currentFlux;
    private float[] nextFlux;
    private int floodHeapSize;

    EndBoundedFlowErosionScratch(int cells, int floodCells) {
        if (cells <= 0) {
            throw new IllegalArgumentException("cells must be > 0, got " + cells);
        }
        if (floodCells <= 0) {
            throw new IllegalArgumentException("floodCells must be > 0, got " + floodCells);
        }
        this.routingTopBlocks = new float[cells];
        this.spillDepthBlocks = new float[cells];
        this.dominantWeight = new float[cells];
        this.firstFlux = new float[cells];
        this.secondFlux = new float[cells];
        this.accumulatedFlux = new float[cells];
        this.escapeDirection = new byte[cells];
        this.dominantDirection = new byte[cells];
        this.secondaryDirection = new byte[cells];
        this.floodBest = new float[floodCells];
        this.floodParent = new int[floodCells];
        this.floodHeapNodes = new int[floodCells];
        this.floodHeapPositions = new int[floodCells];
        this.floodTie = new long[floodCells];
        this.currentFlux = this.firstFlux;
        this.nextFlux = this.secondFlux;
    }

    int capacity() {
        return this.routingTopBlocks.length;
    }

    int floodCapacity() {
        return this.floodBest.length;
    }

    long primitiveBytes() {
        long cellBytes = (long) capacity()
                * (FLOAT_CELL_ARRAYS * Float.BYTES + BYTE_CELL_ARRAYS);
        long floodBytes = (long) floodCapacity()
                * (Float.BYTES + Integer.BYTES * 3L + Long.BYTES);
        return cellBytes + floodBytes;
    }

    void clear() {
        Arrays.fill(this.routingTopBlocks, 0.0F);
        Arrays.fill(this.spillDepthBlocks, 0.0F);
        Arrays.fill(this.dominantWeight, 0.0F);
        Arrays.fill(this.firstFlux, 0.0F);
        Arrays.fill(this.secondFlux, 0.0F);
        Arrays.fill(this.accumulatedFlux, 0.0F);
        Arrays.fill(this.escapeDirection, (byte) 0);
        Arrays.fill(this.dominantDirection, (byte) 0);
        Arrays.fill(this.secondaryDirection, (byte) 0);
        this.currentFlux = this.firstFlux;
        this.nextFlux = this.secondFlux;
    }

    void beginFlood() {
        Arrays.fill(this.floodBest, Float.POSITIVE_INFINITY);
        Arrays.fill(this.floodParent, -1);
        Arrays.fill(this.floodHeapPositions, -1);
        this.floodHeapSize = 0;
    }

    void clearNextFlux() {
        Arrays.fill(this.nextFlux, 0.0F);
    }

    void swapFlux() {
        float[] previous = this.currentFlux;
        this.currentFlux = this.nextFlux;
        this.nextFlux = previous;
    }

    float[] routingTopBlocks() {
        return this.routingTopBlocks;
    }

    float[] spillDepthBlocks() {
        return this.spillDepthBlocks;
    }

    float[] dominantWeight() {
        return this.dominantWeight;
    }

    float[] currentFlux() {
        return this.currentFlux;
    }

    float[] nextFlux() {
        return this.nextFlux;
    }

    float[] accumulatedFlux() {
        return this.accumulatedFlux;
    }

    byte[] escapeDirection() {
        return this.escapeDirection;
    }

    byte[] dominantDirection() {
        return this.dominantDirection;
    }

    byte[] secondaryDirection() {
        return this.secondaryDirection;
    }

    float[] floodBest() {
        return this.floodBest;
    }

    int[] floodParent() {
        return this.floodParent;
    }

    int[] floodHeapNodes() {
        return this.floodHeapNodes;
    }

    int[] floodHeapPositions() {
        return this.floodHeapPositions;
    }

    long[] floodTie() {
        return this.floodTie;
    }

    int floodHeapSize() {
        return this.floodHeapSize;
    }

    void floodHeapSize(int size) {
        this.floodHeapSize = size;
    }
}
