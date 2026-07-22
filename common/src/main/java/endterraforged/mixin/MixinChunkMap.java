package endterraforged.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;

import endterraforged.world.level.levelgen.EndRandomStateProviderCapture;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Supplies the direct {@code RandomState.create} overload with the dynamic registry context that
 * {@link ChunkMap} already owns through its {@link ServerLevel}.
 *
 * <p>Vanilla 1.21.1 constructs its {@code RandomState} from settings plus the noise registry. That
 * overload cannot reconstruct vanilla End final density by itself, but the enclosing {@code ChunkMap}
 * has access to the complete dynamic registry and exposes its getter-provider view. The capture is
 * immediately consumed by {@code MixinRandomState}; no provider is retained after the constructor call.</p>
 */
@Mixin(ChunkMap.class)
public class MixinChunkMap {

    @Shadow
    @Final
    private ServerLevel level;

    @Inject(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/RandomState;create("
                            + "Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;"
                            + "Lnet/minecraft/core/HolderGetter;J)"
                            + "Lnet/minecraft/world/level/levelgen/RandomState;"
            ),
            require = 2
    )
    private void endTerraForged$captureDirectRandomStateProvider(CallbackInfo ci) {
        EndRandomStateProviderCapture.capture(this.level.registryAccess().asGetterLookup());
    }
}
