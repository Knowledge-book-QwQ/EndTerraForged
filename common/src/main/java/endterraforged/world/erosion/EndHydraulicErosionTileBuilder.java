package endterraforged.world.erosion;

import java.util.Objects;

/** Worker-owned builder for cached hydraulic candidate artifacts. */
final class EndHydraulicErosionTileBuilder
        implements EndErosionTileCache.Builder<EndHydraulicErosionTile> {

    private final EndErosionTileCache.Builder<EndErosionTile> inputBuilder;
    private final EndHydraulicErosionRuntime runtime;
    private final EndHydraulicErosionScratch scratch;

    EndHydraulicErosionTileBuilder(EndErosionTileCache.Builder<EndErosionTile> inputBuilder,
                                   EndHydraulicErosionRuntime runtime,
                                   int cells) {
        this.inputBuilder = Objects.requireNonNull(inputBuilder, "inputBuilder");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.scratch = new EndHydraulicErosionScratch(cells);
    }

    @Override
    public EndErosionTileCache.BuildResult<EndHydraulicErosionTile> build(
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
        EndHydraulicErosionTile tile = this.runtime.apply(
                inputResult.tile(), this.scratch, finalTopBlocks, deltaBlocks,
                erosionStrength, drainagePotential, activation);
        long peak = Math.addExact(inputResult.peakPrimitiveBytes(),
                Math.addExact(this.scratch.primitiveBytes(), tile.primitiveBytes()));
        return new EndErosionTileCache.BuildResult<>(tile, peak);
    }

    long scratchPrimitiveBytes() {
        return this.scratch.primitiveBytes();
    }
}
