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

import endterraforged.EndTerraForged;
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
 *
 * <p><b>Stage 6.3 binding-time fallback.</b> The End visitor's
 * {@code mapAll} call is wrapped in a {@code try (Exception) catch} block.
 * Under normal operation {@link EndDensityVisitor#apply} does not throw
 * (it just returns {@code function} or constructs a {@code Bound}), so
 * the catch is never entered. The defensive guard exists for the rare
 * cases where another mod's Mixin breaks the router tree shape, or a
 * future refactor of {@link EndDensityVisitor} introduces a throw —
 * without the catch, such a failure would propagate up through
 * {@code NoiseChunk.<init>} and crash chunk-gen for every End chunk
 * forever (no recovery path). On catch: log WARN with the seed,
 * leave {@code endTerraForged$bound == false} (so a future
 * {@code mapAll} call could retry — defensive, vanilla 1.21.1 calls
 * it once), and fall through to {@code return router.mapAll(originalVisitor)}
 * with the unmodified router. The placeholder
 * {@link endterraforged.world.level.levelgen.EndDensityFunction#INSTANCE}
 * then flows through the chunk visitor unchanged → its
 * {@code compute} returns {@code 0.0} → the chunk comes out as air.
 * Neighbour chunks are independent and use the End visitor normally
 * (if they don't throw). Note: vanilla's {@code NoiseRouter.mapAll}
 * returns a NEW router (non-mutating tree walk), so a throw inside it
 * leaves the original {@code router} parameter intact and safe to
 * pass to the fall-through {@code mapAll(originalVisitor)}.</p>
 */
@Mixin(NoiseChunk.class)
public class MixinNoiseChunk {

    /** The RandomState passed to {@code <init>}; captured before {@code NoiseRouter.mapAll} runs. */
    @Unique
    private RandomState endTerraForged$capturedRandomState;

    /** Guard so the End visitor runs at most once per chunk instance. */
    @Unique
    private boolean endTerraForged$bound = false;

    /**
     * Captures the {@link RandomState} argument from {@code <init>}'s
     * parameter list so the {@code @Redirect mapAll} below can read it
     * without the noise-tree walk needing a back-reference.
     *
     * <p><b>Why not {@code @At("HEAD")} on {@code <init>}.</b> v0.1.5
     * shipped with {@code @At("HEAD")} on this {@code <init>} injection
     * and a non-static handler, but it never crashed because
     * {@link MixinRandomState}'s same-pattern bug failed first during
     * Mixin apply. After v0.1.6 fixes {@link MixinRandomState} (switching
     * it to {@code @At("INVOKE")} before {@code mapAll}), this Mixin
     * would become the next one to crash with
     * {@code InvalidInjectionException: @At("HEAD") selector @Inject
     * handler before super() invocation must be static}. We pre-emptively
     * fix it here using the same pattern: inject just before the first
     * {@code NoiseRouter.mapAll} call, which runs after {@code super()}
     * has completed.</p>
     *
     * <p><b>Why {@code @At("INVOKE", target="...mapAll...")} is safe
     * alongside the {@code @Redirect} on the same call.</b> Mixin allows
     * an {@code @Inject} and a {@code @Redirect} on the same INVOKE
     * target within the same mixin class — the {@code @Inject} runs
     * first (at the injection point, just before the call), then the
     * {@code @Redirect} wraps the call itself. The {@code @Inject}'s
     * {@link CallbackInfo} is non-cancellable, so it never short-circuits
     * the redirect.</p>
     *
     * <p>Constructor signature (1.21.1):
     * {@code <init>(int cellCount, RandomState, int firstCellX, int firstCellZ,
     *  NoiseSettings, BeardifierOrMarker, NoiseGeneratorSettings,
     *  FluidPicker, Blender)}.</p>
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"
            )
    )
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
     *
     * <p><b>Fallback on binding failure.</b> The End visitor's
     * {@code mapAll} is wrapped in {@code try (Exception) catch}. On
     * failure: log WARN, leave {@code endTerraForged$bound == false},
     * and fall through with the unmodified {@code router}. See the
     * class-level "Stage 6.3 binding-time fallback" Javadoc for the
     * full rationale and recovery semantics.</p>
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
            try {
                // NoiseRouter.mapAll is non-mutating: it walks the tree
                // and builds a NEW router. If apply() throws inside the
                // walk, the original `router` parameter is left intact
                // and is safe to pass to the fall-through mapAll below.
                NoiseRouter rebound = router.mapAll(new EndDensityVisitor(
                        access.endTerraForged$getEndDensity(),
                        access.endTerraForged$getFloatingIslandsField(),
                        (int) access.endTerraForged$getSeed()));
                router = rebound;
                this.endTerraForged$bound = true;
            } catch (Exception e) {
                // Defensive: EndDensityVisitor.apply doesn't throw under
                // normal operation (just returns `function` or constructs
                // a Bound). This catch guards against another mod's Mixin
                // breaking the router tree, or a future refactor introducing
                // a throw. Without it, such a failure would crash chunk-gen
                // for every End chunk forever — no recovery path.
                //
                // Recovery: fall through with the unmodified router. The
                // placeholder INSTANCE flows through the chunk visitor
                // unchanged → compute() returns 0.0 → air for this chunk.
                // Neighbour chunks are independent.
                //
                // Don't set endTerraForged$bound = true: let a future
                // mapAll call retry (defensive — vanilla 1.21.1 calls
                // mapAll once per <init>, but this protects against
                // other mods' mixins adding extra calls).
                EndTerraForged.LOGGER.warn(
                        "EndTerraForged EndDensityVisitor binding failed for "
                                + "this chunk; falling back to placeholder (air) "
                                + "density for this chunk only. seed={}",
                        access.endTerraForged$getSeed(), e);
            }
        }
        return router.mapAll(originalVisitor);
    }
}
