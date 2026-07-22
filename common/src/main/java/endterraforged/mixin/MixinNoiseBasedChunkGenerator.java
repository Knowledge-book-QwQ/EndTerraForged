package endterraforged.mixin;

import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

import endterraforged.world.level.levelgen.EndRandomStateAccess;
import endterraforged.world.level.levelgen.EndVoidFluidPicker;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Replaces vanilla's global lava fallback for each ETF End RandomState.
 *
 * <p>Vanilla's disabled aquifer still delegates every empty density cell to a
 * global fluid picker, which returns lava below Y=-54. ETF's finite
 * landmasses intentionally produce empty cells below their underside, so the
 * generic picker would fill the entire lower void with lava. Binding at
 * {@code createNoiseChunk} provides the dimension seed and immutable ETF
 * runtime, while leaving other dimensions and generators untouched.</p>
 */
@Mixin(NoiseBasedChunkGenerator.class)
public class MixinNoiseBasedChunkGenerator {

    @ModifyArgs(
            method = "createNoiseChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/NoiseChunk;forChunk("
                            + "Lnet/minecraft/world/level/chunk/ChunkAccess;"
                            + "Lnet/minecraft/world/level/levelgen/RandomState;"
                            + "Lnet/minecraft/world/level/levelgen/DensityFunctions$BeardifierOrMarker;"
                            + "Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;"
                            + "Lnet/minecraft/world/level/levelgen/Aquifer$FluidPicker;"
                            + "Lnet/minecraft/world/level/levelgen/blending/Blender;)"
                            + "Lnet/minecraft/world/level/levelgen/NoiseChunk;"
            )
    )
    private void endTerraForged$bindFluidPicker(Args args) {
        RandomState randomState = args.get(1);
        EndRandomStateAccess access = (EndRandomStateAccess) (Object) randomState;
        if (!access.endTerraForged$isEnd()) {
            return;
        }
        Aquifer.FluidPicker picker = access.endTerraForged$getFluidPicker();
        args.set(4, picker != null ? picker : EndVoidFluidPicker.picker());
    }
}
