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

import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.concurrent.atomic.AtomicBoolean;

import endterraforged.EndTerraForged;
import endterraforged.world.level.levelgen.EndDensityVisitor;
import endterraforged.world.level.levelgen.EndRandomStateAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
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
 * interpolation/caching). We replace that call's visitor argument: if the
 * backing {@code RandomState} is for the End dimension, the wrapper binds ETF
 * placeholders first and then delegates to the original chunk visitor. The
 * Bound instances therefore flow through vanilla's wrapping and end up in the
 * {@code finalDensity}/{@code initialDensityNoJaggedness} fields that vanilla
 * samples during column generation.</p>
 *
 * <p><b>Why modify the visitor and not redirect mapAll.</b> Other worldgen
 * mods can redirect the outer {@code mapAll} invocation to preserve their own
 * chunk setup. A redirect conflict lets only one mod win. Replacing the
 * visitor argument composes with that wrapper: any redirect that invokes its
 * original visitor also applies ETF binding, while ETF remains inert outside
 * its End router.</p>
 *
 * <p><b>Mixin ordering.</b> The explicit priority {@code 1100} is above the
 * default {@code 1000} used by redirect-based worldgen mixins. ETF therefore
 * inserts its argument transformer first while leaving the original
 * {@code mapAll} invocation available for the later redirect.</p>
 *
 * <p><b>Non-End dimensions.</b> When {@code isEnd() == false} the wrapper
 * is a pure passthrough — no visitor, no allocation, no behaviour change.</p>
 *
 * <p><b>Binding failure policy.</b> The End visitor's {@code mapAll} call is
 * wrapped only to attach a diagnostic to failures caused by incompatible
 * router rewrites or missing vanilla fallback density. It then rejects chunk
 * generation. Falling through with ETF's unresolved placeholder would return
 * {@code 0.0} and silently write an empty End chunk, which is worse than a
 * visible compatibility failure and could corrupt a save.</p>
 */
@Mixin(value = NoiseChunk.class, priority = 1100)
public class MixinNoiseChunk {
    @Unique
    private static final ThreadLocal<EndRandomStateAccess> END_TERRAFORGED_RANDOM_STATE =
            new ThreadLocal<>();

    @Unique
    private static final AtomicBoolean END_TERRAFORGED_FIRST_END_BINDING_LOGGED = new AtomicBoolean();

    /**
     * Captures the {@link RandomState} argument from {@code <init>}'s
     * parameter list so the {@code @ModifyArg mapAll} below can read it
     * without the noise-tree walk needing a back-reference.
     *
     * <p><b>Why use {@code @At("HEAD")}.</b> The handler runs before
     * {@code super()} has completed, so it is static and passes no instance
     * state. A thread-local is safe here because vanilla creates and consumes
     * the router synchronously in the same constructor call;
     * {@code endTerraForged$wrapChunkVisitor} removes the value.</p>
     *
     * <p>Constructor signature (1.21.1):
     * {@code <init>(int cellCount, RandomState, int firstCellX, int firstCellZ,
     *  NoiseSettings, BeardifierOrMarker, NoiseGeneratorSettings,
     *  FluidPicker, Blender)}.</p>
     */
    @Inject(
            method = "<init>",
            at = @At("HEAD")
    )
    private static void endTerraForged$captureRandomState(
            int cellCount,
            RandomState randomState,
            int firstCellX,
            int firstCellZ,
            NoiseSettings noiseSettings,
            DensityFunctions.BeardifierOrMarker beardifier,
            NoiseGeneratorSettings noiseGeneratorSettings,
            Aquifer.FluidPicker fluidPicker,
            Blender blender,
            CallbackInfo ci) {
        if ((Object) randomState instanceof EndRandomStateAccess access
                && access.endTerraForged$isEnd()) {
            END_TERRAFORGED_RANDOM_STATE.set(access);
        } else {
            END_TERRAFORGED_RANDOM_STATE.remove();
        }
    }

    /**
     * Wraps {@code NoiseRouter.mapAll(Visitor)} inside {@code <init>}: for
     * the End dimension, binds ETF placeholders before the chunk-level visitor
     * runs.
     *
     * <p><b>Binding failure policy.</b> The End visitor's {@code mapAll} is
     * wrapped in {@code try (Exception) catch} only to emit a diagnostic
     * before rejecting generation. It must never fall through with ETF's
     * unresolved density placeholder because that would silently produce air.</p>
     */
    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"
            ),
            index = 0
    )
    private DensityFunction.Visitor endTerraForged$wrapChunkVisitor(
            DensityFunction.Visitor originalVisitor) {
        EndRandomStateAccess access = END_TERRAFORGED_RANDOM_STATE.get();
        END_TERRAFORGED_RANDOM_STATE.remove();
        if (access != null) {
            try {
                DensityFunction fallbackEndDensity = access.endTerraForged$getFallbackEndDensity();
                if (fallbackEndDensity == null) {
                    throw new IllegalStateException(
                            "EndTerraForged cannot bind End density without vanilla final density");
                }
                if (END_TERRAFORGED_FIRST_END_BINDING_LOGGED.compareAndSet(false, true)) {
                    EndTerraForged.LOGGER.info(
                            "EndTerraForged binding End density: seed={}, densityPresent={}, "
                                    + "fallbackPresent={}, floatingIslandsPresent={}",
                            access.endTerraForged$getSeed(),
                            access.endTerraForged$getEndDensity() != null,
                            true,
                            access.endTerraForged$getFloatingIslandsField() != null);
                }
                return EndDensityVisitor.withChunkVisitor(
                        originalVisitor,
                        access.endTerraForged$getEndDensity(),
                        access.endTerraForged$getFloatingIslandsField(),
                        (int) access.endTerraForged$getSeed(),
                        fallbackEndDensity);
            } catch (Exception e) {
                // Defensive: EndDensityVisitor.apply doesn't throw under
                // normal operation (just returns `function` or constructs
                // a Bound). This catch guards against another mod's Mixin
                // breaking the router tree, or a future refactor introducing
                // a throw. Without it, such a failure would crash chunk-gen
                // for every End chunk forever — no recovery path.
                //
                EndTerraForged.LOGGER.error(
                        "EndTerraForged refused End chunk generation because safe density binding "
                                + "failed. Continuing would leave ETF placeholders as air. seed={}",
                        access.endTerraForged$getSeed(), e);
                throw new IllegalStateException(
                        "EndTerraForged could not safely bind End density for this chunk", e);
            }
        }
        return originalVisitor;
    }
}
