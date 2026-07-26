package endterraforged.world.erosion;

import java.util.Objects;

/** Worker-owned builder for cached bounded flow erosion artifacts. */
final class EndBoundedFlowErosionTileBuilder
        implements EndErosionTileCache.Builder<EndBoundedFlowErosionTile> {

    private final EndErosionTileCache.Builder<EndErosionTile> inputBuilder;
    private final EndBoundedFlowErosionRuntime runtime;
    private final EndBoundedFlowErosionScratch scratch;

    EndBoundedFlowErosionTileBuilder(
            EndErosionTileCache.Builder<EndErosionTile> inputBuilder,
            EndBoundedFlowErosionRuntime runtime,
            int cells) {
        this.inputBuilder = Objects.requireNonNull(inputBuilder, "inputBuilder");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.scratch = new EndBoundedFlowErosionScratch(cells, runtime.floodCells());
    }

    @Override
    public EndErosionTileCache.BuildResult<EndBoundedFlowErosionTile> build(
            EndErosionTileKey key) {
        EndErosionTileCache.BuildResult<EndErosionTile> inputResult = this.inputBuilder.build(key);
        int cells = key.cellCount();
        if (this.scratch.capacity() != cells) {
            throw new IllegalArgumentException("builder scratch does not match tile geometry");
        }
        float[] finalTopBlocks = new float[cells];
        float[] deltaBlocks = new float[cells];
        float[] erosionStrength = new float[cells];
        float[] drainagePotential = new float[cells];
        float[] activation = new float[cells];
        byte[] dominantDirection = new byte[cells];
        EndBoundedFlowErosionTile tile = this.runtime.apply(
                inputResult.tile(), this.scratch, finalTopBlocks, deltaBlocks,
                erosionStrength, drainagePotential, activation, dominantDirection);
        long peak = Math.addExact(inputResult.peakPrimitiveBytes(),
                Math.addExact(this.scratch.primitiveBytes(), tile.primitiveBytes()));
        return new EndErosionTileCache.BuildResult<>(tile, peak);
    }

    long scratchPrimitiveBytes() {
        return this.scratch.primitiveBytes();
    }
}
