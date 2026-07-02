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

import endterraforged.world.config.EndDefaults;
import endterraforged.world.floatingislands.FloatingIslandsField;
import endterraforged.world.heightmap.EndDensity;
import endterraforged.world.heightmap.EndHeightmap;
import endterraforged.world.level.levelgen.EndRandomStateAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Inject(
            method = "create(Lnet/minecraft/core/HolderGetter$Provider;Lnet/minecraft/resources/ResourceKey;J)Lnet/minecraft/world/level/levelgen/RandomState;",
            at = @At("HEAD")
    )
    private static void endTerraForged$captureCreate(
            HolderGetter.Provider provider,
            ResourceKey<?> key,
            long seed,
            CallbackInfo ci) {
        long[] cap = END_TERRAFORGED_CAPTURE.get();
        cap[0] = seed;
        cap[1] = (key == NoiseGeneratorSettings.END) ? 1L : 0L;
    }

    @Inject(method = "<init>", at = @At("HEAD"))
    private void endTerraForged$initCapture(CallbackInfo ci) {
        long[] cap = END_TERRAFORGED_CAPTURE.get();
        this.endTerraForged$seed = cap[0];
        this.endTerraForged$isEnd = (cap[1] == 1L);
        if (this.endTerraForged$isEnd) {
            // Build the End's density field eagerly: EndHeightmap + EndDensity
            // are immutable and dimension-scoped, so one instance serves all
            // chunks of this dimension. Cast long→int to match the noise
            // system's int seed convention (high bits discarded, but the cast
            // is applied consistently everywhere the seed is used).
            int noiseSeed = (int) this.endTerraForged$seed;
            EndDefaults profile = EndDefaults.endDefaults();
            this.endTerraForged$endDensity = new EndDensity(
                    new EndHeightmap(profile, noiseSeed));
            // Stage 3.6: build the floating-island overlay only when the
            // profile opts in. When disabled, the field stays null and the
            // visitor leaves floating_islands placeholders as INSTANCE (0.0),
            // so the noise_settings add+clamp composition passes the main
            // terrain density through unchanged.
            if (profile.floatingIslandsEnabled()) {
                this.endTerraForged$floatingIslandsField = FloatingIslandsField.defaults();
            }
        }
        // Clear the capture so a nested/stray create can't poison a later <init>.
        cap[0] = -1L;
        cap[1] = 0L;
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
