package endterraforged.mixin;

import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseRouterData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accesses vanilla's End noise-router factory for the degraded worldgen path.
 */
@Mixin(NoiseRouterData.class)
public interface NoiseRouterDataAccessor {

    @Invoker("end")
    static NoiseRouter endterraforged$createEndRouter(
            HolderGetter<DensityFunction> densityFunctions) {
        throw new AssertionError();
    }
}
