package endterraforged.world.level.levelgen;

import java.util.Objects;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Aquifer;

import endterraforged.world.continent.EndCentralRegionPolicy;
import endterraforged.world.heightmap.EndDensity;

/**
 * Selects water only for ETF void samples connected to the exterior sea.
 *
 * <p>The picker is bound to one immutable {@link EndDensity} and seed during
 * {@code RandomState} construction. It therefore has no process-wide mutable
 * world state and is safe for parallel chunk generation. Continental caves
 * remain air because finite shelves expose their cached underside: void below
 * it is open sea, while carved void inside the shelf body is underground.</p>
 */
public final class EndOceanFluidPicker implements Aquifer.FluidPicker {

    private static final Aquifer.FluidStatus AIR =
            new Aquifer.FluidStatus(Integer.MAX_VALUE, Blocks.AIR.defaultBlockState());

    private final EndDensity density;
    private final int seed;
    private final Aquifer.FluidStatus water;

    /**
     * Creates a picker bound to one dimension runtime.
     *
     * @param density immutable terrain runtime used for exterior-ocean membership
     * @param seed world seed truncated consistently with ETF's noise runtime
     */
    public EndOceanFluidPicker(EndDensity density, int seed) {
        this.density = Objects.requireNonNull(density, "density");
        this.seed = seed;
        this.water = new Aquifer.FluidStatus(
                density.heightmap().levels().surfaceY,
                Blocks.WATER.defaultBlockState());
    }

    @Override
    public Aquifer.FluidStatus computeFluid(int x, int y, int z) {
        if (EndCentralRegionPolicy.usesVanillaDensity(x, z)
                || !this.density.hasOceanAt(x, y, z, this.seed)) {
            return AIR;
        }
        return this.water;
    }
}
