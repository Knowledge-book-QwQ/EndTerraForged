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
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;

import endterraforged.EndTerraForged;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetAccess;
import endterraforged.world.floatingislands.FloatingIslandsField;
import endterraforged.world.heightmap.EndDensity;
import endterraforged.world.heightmap.EndWorldgenBootstrap;
import endterraforged.world.level.levelgen.EndDensityBindingPolicy;
import endterraforged.world.level.levelgen.EndDensitySettingsClassifier;
import endterraforged.world.level.levelgen.EndRandomStateProviderCapture;
import endterraforged.world.level.levelgen.EndRandomStateAccess;
import endterraforged.world.level.levelgen.EndOceanFluidPicker;

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
 * has a ResourceKey overload and a direct settings overload. Both converge on
 * the constructor, but chunk generators may call the direct settings overload
 * without a key. We retain the optional provider from the key overload and
 * identify ETF settings in the direct overload from the final-density tree,
 * then pass the seed + target flag to the constructor through a
 * {@link ThreadLocal}. For the direct {@code ChunkMap} route,
 * {@code MixinChunkMap} supplies its dynamic registry provider through a
 * separate one-shot thread-local. The constructor body's {@code router.mapAll(...)}
 * therefore sees a fully-initialised {@code EndRandomStateAccess}. ETF still
 * rejects any direct End settings call without that provider because proceeding
 * would overwrite the frozen central region with ETF terrain or placeholder air.</p>
 *
 * <p><b>Why ThreadLocal and not a side-map.</b> A
 * {@code WeakHashMap<RandomState, ...>} would work but leaks on frequent
 * world reload; interface-injected {@code @Unique} fields GC with the
 * instance. ThreadLocal is the cleanest way to pass data from a static
 * factory ({@code create}) into the instance constructor ({@code <init>})
 * without modifying vanilla's call signature.</p>
 *
 * <p><b>Non-target dimensions.</b> When the final-density tree lacks the
 * ETF placeholder, {@code isEnd} stays {@code false} and
 * {@code endDensity} stays {@code null}. The downstream
 * {@code MixinNoiseChunk} checks that flag before applying the visitor, so
 * overworld, nether and other noise settings are untouched.</p>
 */
@Mixin(RandomState.class)
public class MixinRandomState implements EndRandomStateAccess {

    /**
     * Thread-local capture buffer: {@code [0] = seed}, {@code [1] = isEnd flag},
     * {@code [2] = actual min Y}, {@code [3] = actual world height}.
     * Cleared after {@code <init>} consumes it.
     */
    @Unique
    private static final ThreadLocal<long[]> END_TERRAFORGED_CAPTURE =
            ThreadLocal.withInitial(() -> new long[]{-1L, 0L, 0L, 0L});

    @Unique
    private static final ThreadLocal<HolderGetter.Provider> END_TERRAFORGED_PROVIDER =
            new ThreadLocal<>();

    @Unique
    private long endTerraForged$seed = 0L;

    @Unique
    private boolean endTerraForged$isEnd = false;

    @Unique
    private EndDensity endTerraForged$endDensity = null;

    @Unique
    private Aquifer.FluidPicker endTerraForged$fluidPicker = null;

    @Unique
    private DensityFunction endTerraForged$fallbackEndDensity = null;

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
     * <p>The key overload retains the provider needed by the vanilla central
     * fallback and the degraded fallback.
     * The settings overload classifies the router, which is necessary because
     * chunk generators may call it directly. ThreadLocal passes that combined
     * state into the synchronous constructor invocation without changing
     * vanilla's method signatures.</p>
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
        END_TERRAFORGED_PROVIDER.set(provider);
        // This overload delegates to the settings overload below. Keep its
        // provider for the degraded vanilla fallback; the settings overload
        // classifies the router that is actually constructed.
    }

    @Inject(
            method = "create(Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;"
                    + "Lnet/minecraft/core/HolderGetter;J)"
                    + "Lnet/minecraft/world/level/levelgen/RandomState;",
            at = @At("HEAD")
    )
    private static void endTerraForged$captureSettingsCreate(
            NoiseGeneratorSettings settings,
            HolderGetter<net.minecraft.world.level.levelgen.synth.NormalNoise.NoiseParameters> noises,
            long seed,
            CallbackInfoReturnable<RandomState> cir) {
        long[] cap = END_TERRAFORGED_CAPTURE.get();
        cap[0] = seed;
        HolderGetter.Provider chunkMapProvider = EndRandomStateProviderCapture.take();
        HolderGetter.Provider provider = END_TERRAFORGED_PROVIDER.get();
        if (provider == null) {
            provider = chunkMapProvider;
        }
        boolean isEnd = EndDensitySettingsClassifier.containsEndDensity(settings.noiseRouter());
        EndDensityBindingPolicy.Decision decision = EndDensityBindingPolicy.decide(
                isEnd, provider != null);
        if (decision == EndDensityBindingPolicy.Decision.REJECT_MISSING_VANILLA_FALLBACK) {
            endTerraForged$clearCapture(cap);
            EndTerraForged.LOGGER.error(
                    "EndTerraForged refused direct End RandomState creation because no density "
                            + "registry provider is available to preserve vanilla central density. "
                            + "Use RandomState.create(HolderGetter.Provider, ResourceKey, long)."
            );
            throw new IllegalStateException(
                    "EndTerraForged requires a density registry provider to preserve vanilla End "
                            + "central generation");
        }
        if (decision == EndDensityBindingPolicy.Decision.BIND) {
            END_TERRAFORGED_PROVIDER.set(provider);
        } else {
            END_TERRAFORGED_PROVIDER.remove();
        }
        cap[1] = decision == EndDensityBindingPolicy.Decision.BIND ? 1L : 0L;
        NoiseSettings noiseSettings = settings.noiseSettings();
        cap[2] = noiseSettings.minY();
        cap[3] = noiseSettings.height();
        if (isEnd) {
            // P1 diagnostics: read the loaded settings, not the bundled JSON.
            // Data packs or other mods can replace defaults before RandomState is built.
            EndTerraForged.LOGGER.info(
                    "EndTerraForged captured loaded End noise settings: minY={}, height={}, seaLevel={}, "
                            + "aquifersEnabled={}, defaultBlock={}, defaultFluid={}",
                    noiseSettings.minY(), noiseSettings.height(), settings.seaLevel(),
                    settings.aquifersEnabled(), settings.defaultBlock(), settings.defaultFluid());
        }
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
     *       composed by {@code MixinNoiseChunk}'s {@code @ModifyArg} reads them</li>
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
        HolderGetter.Provider provider = END_TERRAFORGED_PROVIDER.get();
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
                // EndDefaults placeholder. The bundled default is the standard
                // player envelope; the bootstrap below aligns it with the actual
                // NoiseSettings selected by Minecraft.
                //
                // Stage 5.3: read from EndPresetAccess.getOrDefault() instead of
                // EndPreset.defaults() directly, so the GUI's user-edited preset
                // (published via EndPresetAccess.set on the GUI Done button)
                // reaches the worldgen pipeline. When no GUI has run (dedicated
                // server, direct world load), getOrDefault() falls back to
                // EndPreset.defaults() — preserving the pre-GUI behaviour.
                EndPreset profile = EndPresetAccess.getOrDefault();
                try {
                    this.endTerraForged$fallbackEndDensity =
                            NoiseRouterDataAccessor.endterraforged$createEndRouter(
                                    provider.lookupOrThrow(Registries.DENSITY_FUNCTION))
                                    .finalDensity();
                } catch (Exception e) {
                    EndTerraForged.LOGGER.error(
                            "EndTerraForged refused End RandomState creation because vanilla End "
                                    + "final density could not be constructed. Generating without this "
                                    + "fallback could alter the protected central region.",
                            e);
                    throw new IllegalStateException(
                            "EndTerraForged could not construct vanilla End final density", e);
                }
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
                // rolled-back EndClimateAccess) on failure. The visitor remains
                // enabled in that case and replaces the ETF placeholder with the
                // vanilla density retained above, so the dimension does not turn
                // into air. The world keeps loading rather than crashing — the
                // user can fix the bad preset file and re-open.
                EndWorldgenBootstrap.Result result =
                        EndWorldgenBootstrap.bootstrap(noiseSeed, profile, (int) cap[2], (int) cap[3]);
                if (!result.degraded()) {
                    this.endTerraForged$endDensity = result.endDensity();
                    this.endTerraForged$fluidPicker =
                            new EndOceanFluidPicker(result.endDensity(), noiseSeed);
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
            endTerraForged$clearCapture(cap);
        }
    }

    @Unique
    private static void endTerraForged$clearCapture(long[] capture) {
        capture[0] = -1L;
        capture[1] = 0L;
        capture[2] = 0L;
        capture[3] = 0L;
        END_TERRAFORGED_PROVIDER.remove();
        EndRandomStateProviderCapture.clear();
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
    public Aquifer.FluidPicker endTerraForged$getFluidPicker() {
        return this.endTerraForged$fluidPicker;
    }

    @Override
    public DensityFunction endTerraForged$getFallbackEndDensity() {
        return this.endTerraForged$fallbackEndDensity;
    }

    @Override
    public FloatingIslandsField endTerraForged$getFloatingIslandsField() {
        return this.endTerraForged$floatingIslandsField;
    }
}
