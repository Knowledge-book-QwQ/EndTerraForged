/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Mixin on vanilla's
 * RandomState to expose the world seed + End-dimension flag that
 * RandomState otherwise discards — informed by RTF's interface-injection
 * pattern (MIT).
 */
package endterraforged.mixin;

import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetAccess;
import endterraforged.world.floatingislands.FloatingIslandsField;
import endterraforged.world.heightmap.EndDensity;
import endterraforged.world.heightmap.EndWorldgenBootstrap;
import endterraforged.world.level.levelgen.EndRandomStateAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Interface-injects {@link EndRandomStateAccess} onto vanilla's
 * {@link RandomState}.
 *
 * <p><b>The problem.</b> {@code RandomState}'s constructor consumes the
 * world seed and the {@code ResourceKey<NoiseGeneratorSettings>} to build
 * its router / sampler / surface system, then exposes neither. But the
 * End's late-binding {@link endterraforged.world.level.levelgen.EndDensityVisitor}
 * needs both the seed and the {@link EndDensity} (built from that seed +
 * the dimension profile) to rebind the {@code EndDensityFunction}
 * placeholders. This Mixin bridges that gap.</p>
 *
 * <p><b>The capture flow (ThreadLocal).</b> {@code RandomState.create}
 * has two overloads; only the {@code (Provider, ResourceKey, long)} form
 * carries the {@code ResourceKey}. We {@code @Inject} at its HEAD to stash
 * the seed + isEnd flag into a {@link ThreadLocal}, then {@code @Inject}
 * at {@code <init>} HEAD to read that stash (same thread, synchronous call
 * — {@code create} calls {@code new RandomState(...)} inline) and populate
 * the {@code @Unique} fields before the constructor body runs. The
 * constructor body's {@code router.mapAll(...)} therefore sees a
 * fully-initialised {@code EndRandomStateAccess}.</p>
 *
 * <p><b>Why ThreadLocal and not a side-map.</b> A
 * {@code WeakHashMap<RandomState, ...>} would work but leaks on frequent
 * world reload; interface-injected {@code @Unique} fields GC with the
 * instance. ThreadLocal is the cleanest way to pass data from a static
 * factory ({@code create}) into the instance constructor ({@code <init>})
 * without modifying vanilla's call signature.</p>
 *
 * <p><b>Non-End dimensions.</b> When {@code key != NoiseGeneratorSettings.END},
 * {@code isEnd} stays {@code false} and {@code endDensity} stays {@code null}.
 * The downstream {@code MixinNoiseChunk} checks {@code isEnd} before
 * applying the visitor, so overworld/nether are untouched.</p>
 */
@Mixin(RandomState.class)
public class MixinRandomState implements EndRandomStateAccess {

    /**
     * Thread-local capture buffer: {@code [0] = seed}, {@code [1] = isEnd flag}.
     * Cleared after {@code <init>} consumes it.
     */
    @Unique
    private static final ThreadLocal<long[]> END_TERRAFORGED_CAPTURE =
            ThreadLocal.withInitial(() -> new long[]{-1L, 0L});

    @Unique
    private long endTerraForged$seed = 0L;

    @Unique
    private boolean endTerraForged$isEnd = false;

    @Unique
    private EndDensity endTerraForged$endDensity = null;

    /**
     * The floating-island overlay field. Built only when {@code isEnd} and
     * the dimension profile has {@code floatingIslandsEnabled == true}.
     * Stays {@code null} otherwise — {@link EndDensityVisitor} treats null
     * as "layer disabled" and leaves {@link endterraforged.world.level.levelgen.FloatingIslandsFunction}
     * placeholders as the stateless INSTANCE (compute returns 0.0).
     */
    @Unique
    private FloatingIslandsField endTerraForged$floatingIslandsField = null;

    /**
     * Captures the seed + isEnd flag from the static {@code create} factory
     * before the {@code <init>} constructor runs. The constructor reads this
     * stash in {@link #endTerraForged$initCapture}.
     *
     * <p>Why a ThreadLocal: {@code create} has two overloads and only the
     * 3-arg {@code (Provider, ResourceKey, long)} form carries the
     * {@code ResourceKey}. We can't change vanilla's call signature, so the
     * data has to flow from the static factory into the instance constructor
     * via a side channel. ThreadLocal is the cleanest side channel because
     * {@code create} calls {@code new RandomState(...)} synchronously on the
     * same thread — no race.</p>
     *
     * <p>Must be static because the {@code create} injection runs in a static
     * context (no {@code this}).</p>
     */
    @Inject(
            method = "create(Lnet/minecraft/core/HolderGetter$Provider;Lnet/minecraft/resources/ResourceKey;J)Lnet/minecraft/world/level/levelgen/RandomState;",
            at = @At("HEAD")
    )
    private static void endTerraForged$captureCreate(
            HolderGetter.Provider provider,
            ResourceKey<?> key,
            long seed,
            CallbackInfoReturnable<RandomState> cir) {
        long[] cap = END_TERRAFORGED_CAPTURE.get();
        cap[0] = seed;
        // Use .equals rather than == for the ResourceKey comparison.
        // Vanilla interns ResourceKey instances via ResourceKey.intern()
        // (a process-wide ConcurrentHashMap cache), so == works in practice
        // for the vanilla-provided NoiseGeneratorSettings.END key. But the
        // ResourceKey contract is .equals — a non-interned key constructed
        // by another mod (or by a test) would fail == but match .equals.
        // The cost is one method call at RandomState.create (one dimension
        // load per world), negligible vs the noise-tree construction that
        // follows.
        cap[1] = (NoiseGeneratorSettings.END.equals(key)) ? 1L : 0L;
    }

    /**
     * Reads the captured seed + isEnd flag and populates the {@code @Unique}
     * fields BEFORE {@code NoiseRouter.mapAll} runs in the constructor body.
     *
     * <p><b>Why not {@code @At("HEAD")} on {@code <init>}.</b> v0.1.5 shipped
     * with {@code @At("HEAD")} here and crashed at server-tick start with
     * {@code InvalidInjectionException: @At("HEAD") selector @Inject handler
     * before super() invocation must be static}. The Mixin processor injects
     * {@code <init>} HEAD bytecode BEFORE the {@code aload_0; invokespecial
     * Object.<init>} sequence — i.e. before {@code super()} has been called.
     * At that point {@code this} is uninitialised, so the handler must be
     * {@code static}. A static handler can't write to the {@code @Unique}
     * instance fields ({@code this.endTerraForged$seed = ...}), which is what
     * we need here.</p>
     *
     * <p><b>Why {@code @At("INVOKE", target="...mapAll...")} instead.</b>
     * Vanilla {@code RandomState.<init>} does (verified via {@code javap -c}):
     * <ol>
     *   <li>{@code aload_0; invokespecial Object.<init>} — super() call</li>
     *   <li>set up {@code random}, {@code noises}, {@code aquiferRandom},
     *       {@code oreRandom}, {@code noiseIntances}, {@code positionalRandoms},
     *       {@code surfaceSystem}</li>
     *   <li>{@code NoiseGeneratorSettings.noiseRouter()} — fetch the router</li>
     *   <li>{@code new RandomState$1NoiseWiringHelper(...)} — build visitor</li>
     *   <li>{@code NoiseRouter.mapAll(Visitor)} ← we inject just before this</li>
     *   <li>...temperature/vegetation/continents/erosion/depth/ridges.mapAll...</li>
     *   <li>{@code return}</li>
     * </ol>
     * Injecting just before the first {@code mapAll} call means:
     * <ul>
     *   <li>super() has run → instance handler valid</li>
     *   <li>{@code this} fields can be assigned normally</li>
     *   <li>{@code router.mapAll} still sees the {@code @Unique} fields
     *       populated, which is the whole point — the {@link endterraforged.world.level.levelgen.EndDensityVisitor}
     *       installed by {@code MixinNoiseChunk.@Redirect mapAll} reads them</li>
     * </ul>
     *
     * <p>The Mixin default {@code ordinal=-1} matches the first occurrence of
     * the target INVOKE, which is the {@code NoiseRouter.mapAll} call (the
     * subsequent {@code DensityFunction.mapAll} calls have a different owner
     * class so the target string doesn't match them).</p>
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"
            )
    )
    private void endTerraForged$initCapture(CallbackInfo ci) {
        long[] cap = END_TERRAFORGED_CAPTURE.get();
        try {
            this.endTerraForged$seed = cap[0];
            this.endTerraForged$isEnd = (cap[1] == 1L);
            if (this.endTerraForged$isEnd) {
                // Build the End's density field eagerly: EndHeightmap + EndDensity
                // are immutable and dimension-scoped, so one instance serves all
                // chunks of this dimension. Cast long→int to match the noise
                // system's int seed convention (high bits discarded, but the cast
                // is applied consistently everywhere the seed is used).
                int noiseSeed = (int) this.endTerraForged$seed;
                // Stage 5.1: load the dimension profile from EndPreset (the
                // serialisable single source of truth) rather than the stage-3.2
                // EndDefaults placeholder. defaults() matches EndDefaults' values,
                // so production terrain is unchanged; a future GUI/data-pack can
                // supply a non-default EndPreset here.
                //
                // Stage 5.3: read from EndPresetAccess.getOrDefault() instead of
                // EndPreset.defaults() directly, so the GUI's user-edited preset
                // (published via EndPresetAccess.set on the GUI Done button)
                // reaches the worldgen pipeline. When no GUI has run (dedicated
                // server, direct world load), getOrDefault() falls back to
                // EndPreset.defaults() — preserving the pre-GUI behaviour.
                EndPreset profile = EndPresetAccess.getOrDefault();
                // Stage 4.x production wiring: attach the climate → rivers → lakes
                // post-processors so the pure-logic layers (stage 2.5 / 4.x)
                // actually reach the generated dimension. EndHeightmap.getHeight
                // runs them in this order, so climate modulates the surface rivers
                // then carve, then lakes carve on top. Without this the End would
                // ship with continent×mountains only — no rivers, no lakes, no
                // climate modulation — despite those modules being complete.
                //
                // Stage 6.3: the bootstrap sequence (climate → heightmap → density
                // → floating islands) is wrapped in EndWorldgenBootstrap with an
                // end-to-end try/catch. Any exception in the chain degrades
                // gracefully: EndWorldgenBootstrap publishes a non-degraded
                // Result on success, or a degraded Result (with null fields +
                // rolled-back EndClimateAccess) on failure. In the degraded case
                // we drop the End flag below so MixinNoiseChunk skips the visitor
                // pass — a null endDensity would otherwise NPE inside
                // EndDensityFunction.Bound.compute on the first chunk and
                // re-crash worldgen. The End dimension then loads with vanilla
                // generation (placeholder EndDensityFunction.INSTANCE returns 0,
                // so chunks come out as air; biome_source falls back to fast-path 1
                // because EndClimateAccess is null). The world keeps loading
                // rather than crashing — the user can fix the bad preset file
                // and re-open.
                EndWorldgenBootstrap.Result result =
                        EndWorldgenBootstrap.bootstrap(noiseSeed, profile);
                if (result.degraded()) {
                    // Drop End-ness so MixinNoiseChunk's redirect skips the
                    // visitor pass (it checks isEnd() before calling
                    // EndDensityVisitor). endDensity / floatingIslandsField stay
                    // null (EndWorldgenBootstrap returned null fields and
                    // EndClimateAccess was rolled back inside the bootstrap).
                    this.endTerraForged$isEnd = false;
                } else {
                    this.endTerraForged$endDensity = result.endDensity();
                    this.endTerraForged$floatingIslandsField = result.floatingIslandsField();
                }
            }
        } finally {
            // Always clear the capture — even if EndWorldgenBootstrap (or
            // a future addition to this try block) throws an Error
            // (OutOfMemoryError, StackOverflowError from a degenerate
            // noise tree). EndWorldgenBootstrap itself catches Exception
            // and returns a degraded Result, but Error propagates. Without
            // this finally, the ThreadLocal would retain stale [seed, isEnd]
            // values and leak across subsequent RandomState.create calls
            // on the same thread.
            //
            // In practice endTerraForged$captureCreate always overwrites
            // the ThreadLocal before endTerraForged$initCapture reads it
            // (captureCreate is at HEAD of RandomState.create, which is
            // the only entry point that triggers <init>), so stale values
            // wouldn't cause a functional bug. But the try/finally makes
            // the ThreadLocal management correct by construction rather
            // than by coincidence — defensive against future code paths
            // that might construct RandomState without going through create.
            cap[0] = -1L;
            cap[1] = 0L;
        }
    }

    @Override
    public long endTerraForged$getSeed() {
        return this.endTerraForged$seed;
    }

    @Override
    public boolean endTerraForged$isEnd() {
        return this.endTerraForged$isEnd;
    }

    @Override
    public EndDensity endTerraForged$getEndDensity() {
        return this.endTerraForged$endDensity;
    }

    @Override
    public FloatingIslandsField endTerraForged$getFloatingIslandsField() {
        return this.endTerraForged$floatingIslandsField;
    }
}
