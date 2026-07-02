/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Mixin on vanilla's
 * NoiseChunk to rebind EndDensityFunction placeholders to Bound instances
 * before the chunk-level wrap/interpolation visitor runs — informed by
 * RTF's chunk-time mapAll pattern (MIT).
 */
package endterraforged.mixin;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;

import endterraforged.world.level.levelgen.EndDensityVisitor;
import endterraforged.world.level.levelgen.EndRandomStateAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects the End's {@link EndDensityVisitor} into vanilla's
 * {@link NoiseChunk} construction, so every {@link endterraforged.world.level.levelgen.EndDensityFunction}
 * placeholder in the loaded {@code NoiseRouter} is swapped for a
 * {@link endterraforged.world.level.levelgen.EndDensityFunction.Bound}
 * instance before chunk-level wrapping/interpolation runs.
 *
 * <p><b>Injection point.</b> {@code NoiseChunk.<init>} calls
 * {@code randomState.router().mapAll(chunkVisitor)} to bind the router to
 * this chunk (the chunkVisitor wraps each density function with
 * interpolation/caching). We {@code @Redirect} that {@code NoiseRouter.mapAll}
 * call: if the backing {@code RandomState} is for the End dimension, we first
 * run {@link EndDensityVisitor} on the router (rebinding placeholders → Bound),
 * then let the original chunkVisitor run on the rebound router. The Bound
 * instances flow unchanged through the chunkVisitor's wrapping, ending up in
 * the {@code finalDensity}/{@code initialDensityNoJaggedness} fields that
 * vanilla samples during column generation.</p>
 *
 * <p><b>Why redirect mapAll and not router().</b> {@code router()} may be
 * called multiple times during {@code <init>}; redirecting it would re-run
 * the visitor per call. {@code mapAll} is the single top-level binding call,
 * so one redirect covers the whole router tree in one pass.</p>
 *
 * <p><b>Idempotency.</b> The {@code endTerraForged$bound} guard ensures the
 * End visitor runs at most once per chunk even if {@code mapAll} is invoked
 * multiple times (defensive — vanilla 1.21.1 calls it once, but this protects
 * against other mods' mixins adding extra calls).</p>
 *
 * <p><b>Non-End dimensions.</b> When {@code isEnd() == false} the redirect
 * is a pure passthrough — no visitor, no allocation, no behaviour change.</p>
 */
@Mixin(NoiseChunk.class)
public class MixinNoiseChunk {

    /** The RandomState passed to {@code <init>}; captured at HEAD for use in the redirect. */
    @Unique
    private RandomState endTerraForged$capturedRandomState;

    /** Guard so the End visitor runs at most once per chunk instance. */
    @Unique
    private boolean endTerraForged$bound = false;

    /**
     * Captures the {@code RandomState} (2nd parameter of {@code <init>})
     * before the constructor body runs. Mixin allows {@code @Inject} to
     * declare only the leading parameters of the target method; we need
     * just {@code cellCount} (skipped via position) and {@code randomState}.
     *
     * <p>Constructor signature (1.21.1):
     * {@code <init>(int cellCount, RandomState, int firstCellX, int firstCellZ,
     *  NoiseSettings, BeardifierOrMarker, NoiseGeneratorSettings,
     *  FluidPicker, Blender)}.</p>
     */
    @Inject(method = "<init>", at = @At("HEAD"))
    private void endTerraForged$captureRandomState(
            int cellCount,
            RandomState randomState,
            CallbackInfo ci) {
        this.endTerraForged$capturedRandomState = randomState;
    }

    /**
     * Redirects {@code NoiseRouter.mapAll(Visitor)} inside {@code <init>}:
     * for the End dimension, pre-binds the End placeholders before the
     * chunk-level visitor runs.
     */
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"
            )
    )
    private NoiseRouter endTerraForged$wrapMapAll(
            NoiseRouter router,
            DensityFunction.Visitor originalVisitor) {
        if (!this.endTerraForged$bound
                && (Object) this.endTerraForged$capturedRandomState instanceof EndRandomStateAccess access
                && access.endTerraForged$isEnd()) {
            router = router.mapAll(new EndDensityVisitor(
                    access.endTerraForged$getEndDensity(),
                    access.endTerraForged$getFloatingIslandsField(),
                    (int) access.endTerraForged$getSeed()));
            this.endTerraForged$bound = true;
        }
        return router.mapAll(originalVisitor);
    }
}
