package endterraforged.world.level.levelgen;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Aquifer;

/**
 * Supplies air for void cells in EndTerraForged's End generator.
 *
 * <p>Vanilla's generic global fluid picker hard-codes lava below Y=-54, even
 * when aquifers are disabled. ETF creates intentional void below finite
 * shelves and inside unfilled cave candidates, so using that picker would
 * turn those void cells into a solid lava layer. Formal rivers and lava
 * features must use their own placement path rather than this fallback.</p>
 *
 * <p>Thread safety: immutable singleton state.</p>
 */
public final class EndVoidFluidPicker {

    private static final Aquifer.FluidStatus VOID =
            new Aquifer.FluidStatus(Integer.MAX_VALUE, Blocks.AIR.defaultBlockState());
    private static final Aquifer.FluidPicker PICKER = (x, y, z) -> VOID;

    private EndVoidFluidPicker() {
    }

    /** Returns the shared fluid picker that resolves every queried cell to air. */
    public static Aquifer.FluidPicker picker() {
        return PICKER;
    }
}
