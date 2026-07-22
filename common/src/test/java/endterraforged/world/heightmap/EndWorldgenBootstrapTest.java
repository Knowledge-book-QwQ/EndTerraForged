package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import endterraforged.world.climate.EndClimate;
import endterraforged.world.climate.EndClimateAccess;
import endterraforged.world.config.AbyssPitConfig;
import endterraforged.world.config.ClimateConfig;
import endterraforged.world.config.ContinentConfig;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.SeaMode;
import endterraforged.world.config.SubsurfaceConfig;
import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TopologyMode;
import endterraforged.world.floatingislands.FloatingIslandsField;
import endterraforged.world.filter.ErosionConfig;
import endterraforged.world.level.biome.EndBiomeLayout;
import endterraforged.world.level.biome.EndBiomeLayoutAccess;

/**
 * Contract tests for {@link EndWorldgenBootstrap}: the stage-6.3 fallback
 * boundary that wraps the End worldgen field-stack construction
 * (climate → heightmap → density → floating islands) in a single
 * try/catch so any failure degrades gracefully to "vanilla End generation"
 * rather than crashing world creation.
 *
 * <p><b>Why these tests exist.</b> The MixinRandomState {@code <init>}
 * inject runs on the bootstrap thread inside vanilla's
 * {@code RandomState.create} call — a single uncaught exception there
 * prevents world creation / load entirely. The previous inline
 * construction sequence had no try/catch, so a malformed preset (slipped
 * past codec validators), a noise-tree {@link StackOverflowError} on a
 * degenerate seed, or an unexpected {@link NullPointerException} from
 * a future refactor would all crash worldgen. {@link EndWorldgenBootstrap}
 * encapsulates the fix; these tests pin its contract.</p>
 *
 * <p><b>Test strategy.</b> Two paths:</p>
 * <ul>
 *   <li><b>Success path.</b> Default profile → non-degraded {@link EndWorldgenBootstrap.Result},
 *       non-null {@link EndDensity}, climate published to
 *       {@link EndClimateAccess}. Both floating-islands-disabled and
 *       floating-islands-enabled profiles are exercised (the latter
 *       produces a non-null {@link FloatingIslandsField}).</li>
 *   <li><b>Failure path.</b> The package-private overload accepts factory
 *       lambdas, so tests inject a factory that throws to exercise the
 *       catch block without depending on the (unlikely) event of a real
 *       factory failing. Both "heightmap factory throws" and
 *       "floating-islands factory throws" cases are covered — the catch
 *       must wrap the entire sequence, not just the heightmap step.</li>
 * </ul>
 *
 * <p><b>Process-wide state.</b> Each test calls {@link EndClimateAccess#clear()}
 * in {@link AfterEach} so the volatile holder doesn't leak a stale climate
 * into the next test (success-path tests publish a climate; failure-path
 * tests roll it back to null, but the cleanup is defensive against future
 * test additions).</p>
 */
class EndWorldgenBootstrapTest {

    /**
     * Default seed for tests — any int works; 42 is conventional. The seed
     * drives noise-tree construction in {@link EndClimate#defaults(int)} and
     * {@link EndHeightmap}'s continent/mountain layers.
     */
    private static final int SEED = 42;

    /**
     * Defensive teardown: even success-path tests publish a climate to
     * {@link EndClimateAccess}, which would leak across tests if not
     * cleared. {@link EndClimateAccess} is a process-wide volatile holder
     * shared with {@code EndBiomeSource#getNoiseBiome} and other tests
     * (e.g. {@code EndClimateAccessTest}, {@code EndBiomeSourceTest}).
     */
    @AfterEach
    void clearClimateHolder() {
        EndClimateAccess.clear();
        EndBiomeLayoutAccess.clear();
    }

    // ------------------------------------------------------------------
    //  Success path: defaults() profile, floating islands disabled
    // ------------------------------------------------------------------

    /**
     * The most common production path: a freshly-loaded world with the
     * default preset. {@link EndPreset#defaults()} has
     * {@code floatingIslandsEnabled == false}, so {@link FloatingIslandsField}
     * is {@code null} and {@link EndWorldgenBootstrap.Result#floatingIslandsField()}
     * is {@code null} — but {@link EndWorldgenBootstrap.Result#degraded()}
     * is {@code false} (null here means "layer disabled", not "bootstrap
     * failed"). The distinction matters: {@code MixinRandomState} only drops
     * the End flag when {@code degraded == true}.
     */
    @Test
    void bootstrapSucceedsWithDefaultsProfile() {
        EndPreset profile = EndPreset.defaults();
        EndWorldgenBootstrap.Result result = EndWorldgenBootstrap.bootstrap(SEED, profile);

        assertTrue(result.succeeded(),
                "bootstrap with a default preset must succeed (no exception expected)");
        assertFalse(result.degraded(),
                "degraded flag must be false on success");
        assertNotNull(result.endDensity(),
                "endDensity must be constructed on success");
        // EndPreset.defaults() has floatingIslandsEnabled == false, so the
        // field is legitimately null — but succeeded() is true.
        assertNull(result.floatingIslandsField(),
                "floatingIslandsField must be null when profile.floatingIslandsEnabled() == false");
    }

    /**
     * On success, {@link EndClimateAccess} must hold the climate constructed
     * during bootstrap — worker-thread biome lookups depend on this
     * publication. This pins the publication contract: if a future refactor
     * forgets the {@code EndClimateAccess.set(climate)} call, biome
     * selection silently falls back to fast-path 1 (vanilla) and the climate
     * variant feature breaks.
     */
    @Test
    void bootstrapPublishesClimateOnSuccess() {
        EndWorldgenBootstrap.Result result =
                EndWorldgenBootstrap.bootstrap(SEED, EndPreset.defaults());

        assertTrue(result.succeeded());
        EndClimate published = EndClimateAccess.get();
        assertNotNull(published,
                "bootstrap must publish the constructed EndClimate to EndClimateAccess on success");
    }

    @Test
    void bootstrapUsesActualNoiseSettingsBoundsForLegacyPresetEnvelope() {
        EndPreset legacyEditorPreset = new EndPreset(2128, -192, 0, 0,
                SeaMode.NONE, TopologyMode.ISLANDS, false,
                ContinentConfig.defaults(), TerrainConfig.DEFAULT, ErosionConfig.DEFAULT);

        EndWorldgenBootstrap.Result result = EndWorldgenBootstrap.bootstrap(
                SEED, legacyEditorPreset, -256, 512);

        assertTrue(result.succeeded());
        EndLevels levels = result.endDensity().heightmap().levels();
        assertEquals(-256, levels.minY);
        assertEquals(512, levels.worldHeight);
    }

    @Test
    void bootstrapPreservesExplicitFloorAndContinuousMainlandAcrossBoundsAdjustment() {
        EndPreset legacyFloorPreset = new EndPreset(2128, -192, 0, 0,
                SeaMode.WITH_FLOOR, TopologyMode.CONTINENTAL, true,
                ContinentConfig.legacyDefaults(), TerrainConfig.DEFAULT, ErosionConfig.DEFAULT);

        EndWorldgenBootstrap.Result result = EndWorldgenBootstrap.bootstrap(
                SEED, legacyFloorPreset, -256, 512);

        assertTrue(result.succeeded());
        EndDensity density = result.endDensity();
        assertEquals(1.0F, density.heightmap().getLandness(0.0F, 0.0F, SEED), 0.0F,
                "CONTINENTAL must remain a continuous mainland after bounds adjustment");
        assertEquals(1.0F, density.density(0.0F, -256, 0.0F, SEED), 0.0F,
                "WITH_FLOOR must keep solid terrain at the loaded world bottom");
    }

    @Test
    void legacyContinentalFloorPresetStaysSolidAtTheP1OuterBaseline() {
        long p1Seed = 7286241398135878839L;
        int noiseSeed = (int) p1Seed;
        EndPreset legacyFloorPreset = new EndPreset(2128, -192, 0, 0,
                SeaMode.WITH_FLOOR, TopologyMode.CONTINENTAL, true,
                ContinentConfig.legacyDefaults(), TerrainConfig.DEFAULT, ErosionConfig.DEFAULT);

        EndWorldgenBootstrap.Result result = EndWorldgenBootstrap.bootstrap(
                noiseSeed, legacyFloorPreset, -256, 512);

        assertTrue(result.succeeded());
        assertEquals(1.0F, result.endDensity().heightmap().getLandness(4096.0F, 4096.0F, noiseSeed),
                0.0F, "CONTINENTAL must cover the fixed outer baseline coordinate");
        assertEquals(1.0F, result.endDensity().density(4096.0F, -224, 4096.0F, noiseSeed),
                0.0F, "WITH_FLOOR must be solid at the valid Standard-world bottom baseline");
    }

    @Test
    void bootstrapDegradesWhenLegacyReferenceHeightDoesNotFitActualBounds() {
        EndPreset incompatibleLegacyPreset = new EndPreset(2048, -1024, 768, 0,
                SeaMode.WITH_FLOOR, TopologyMode.ISLANDS, false,
                ContinentConfig.defaults(), TerrainConfig.DEFAULT, ErosionConfig.DEFAULT);

        EndWorldgenBootstrap.Result result = EndWorldgenBootstrap.bootstrap(
                SEED, incompatibleLegacyPreset, -256, 512);

        assertTrue(result.degraded());
        assertNull(result.endDensity());
        assertNull(EndClimateAccess.get());
        assertNull(EndBiomeLayoutAccess.get());
    }

    @Test
    void bootstrapPublishesBiomeLayoutOnSuccess() {
        EndWorldgenBootstrap.Result result =
                EndWorldgenBootstrap.bootstrap(SEED, EndPreset.defaults());

        assertTrue(result.succeeded());
        EndBiomeLayout published = EndBiomeLayoutAccess.get();
        assertNotNull(published,
                "bootstrap must publish the constructed EndBiomeLayout on success");
    }

    @Test
    void bootstrapBuildsClimateFromPresetConfig() {
        ClimateConfig climateConfig = new ClimateConfig(6000.0F, 900, 1200, 1500, 0.4F);
        EndPreset profile = new EndPreset(4064, -2032, 0, 0,
                SeaMode.NONE, TopologyMode.ISLANDS, false,
                ContinentConfig.defaults(),
                TerrainConfig.DEFAULT,
                climateConfig,
                ErosionConfig.DEFAULT);

        EndWorldgenBootstrap.Result result = EndWorldgenBootstrap.bootstrap(SEED, profile);

        assertTrue(result.succeeded());
        EndClimate published = EndClimateAccess.get();
        assertNotNull(published);
        assertEquals(climateConfig.climateRadius(), published.climateRadius());
        assertEquals(climateConfig.perturbation(), published.perturbation());
    }

    @Test
    void bootstrapBuildsSubsurfaceFromPresetConfig() {
        SubsurfaceConfig subsurfaceConfig = new SubsurfaceConfig(
                new AbyssPitConfig(true, 1700, 512, 0.7F, 0.2F, 256, 0.5F));
        EndPreset profile = new EndPreset(4064, -2032, 0, 0,
                SeaMode.NONE, TopologyMode.ISLANDS, false,
                ContinentConfig.defaults(),
                TerrainConfig.DEFAULT,
                ClimateConfig.DEFAULT,
                EndPreset.defaults().biomeLayoutConfig(),
                subsurfaceConfig,
                ErosionConfig.DEFAULT);

        EndWorldgenBootstrap.Result result = EndWorldgenBootstrap.bootstrap(SEED, profile);

        assertTrue(result.succeeded());
        assertNotNull(result.endDensity());
        assertTrue(result.endDensity().subsurface().enabled());
    }

    // ------------------------------------------------------------------
    //  Success path: floating islands enabled
    // ------------------------------------------------------------------

    /**
     * When {@link EndPreset#floatingIslandsEnabled()} is {@code true}, the
     * success path must produce a non-null {@link FloatingIslandsField}.
     * This exercises the {@code floatingIslandsFactory} branch in the
     * default {@link EndWorldgenBootstrap#bootstrap(int, EndPreset)} entry
     * point — a regression here would silently disable the floating-island
     * overlay even when the user enabled it.
     */
    @Test
    void bootstrapSucceedsWithFloatingIslandsEnabled() {
        // Construct a non-default profile identical to defaults() except
        // floatingIslandsEnabled is true. Uses the public record ctor so
        // the test doesn't depend on a future builder API.
        EndPreset profile = new EndPreset(
                4064, -2032, 0, 0,
                SeaMode.NONE, TopologyMode.ISLANDS, true,
                ContinentConfig.defaults(),
                TerrainConfig.DEFAULT,
                ErosionConfig.DEFAULT);

        EndWorldgenBootstrap.Result result = EndWorldgenBootstrap.bootstrap(SEED, profile);

        assertTrue(result.succeeded());
        assertNotNull(result.endDensity(),
                "endDensity must be constructed on success regardless of floating-islands flag");
        assertNotNull(result.floatingIslandsField(),
                "floatingIslandsField must be non-null when profile opts in");
    }

    // ------------------------------------------------------------------
    //  Success path: package-private overload direct call
    // ------------------------------------------------------------------

    /**
     * Pins the contract that the package-private overload accepts a
     * {@code p -> null} floating-islands factory as a <em>success</em>
     * path (not a degradation). This mirrors the public overload's
     * default behavior when {@link EndPreset#floatingIslandsEnabled()}
     * is {@code false} — the field is legitimately {@code null} but
     * {@link EndWorldgenBootstrap.Result#degraded()} is {@code false}.
     *
     * <p><b>Why this matters.</b> The package-private overload is the
     * only entry point tests can call to inject factories. A future
     * refactor that mistakenly treats a {@code null}
     * {@link FloatingIslandsField} return as a degradation signal would
     * break the "floating islands disabled" production path (the public
     * overload's default factory returns {@code null} when the profile
     * opts out) — every fresh EndTerraForged world would silently fall
     * back to vanilla generation. This test would catch that regression.</p>
     *
     * <p><b>Coverage gap this fills.</b> The public-overload tests
     * ({@link #bootstrapSucceedsWithDefaultsProfile},
     * {@link #bootstrapSucceedsWithFloatingIslandsEnabled}) exercise the
     * public entry point only; the failure-path tests
     * ({@link #bootstrapDegradesWhenFloatingIslandsFactoryThrows},
     * {@link #bootstrapDegradesOnNullPointerException}, etc.) cover the
     * package-private overload but only on the throw path. This test
     * pins the package-private overload's success path with a
     * {@code null} floating-islands return — the exact contract the
     * public overload relies on internally.</p>
     */
    @Test
    void bootstrapSucceedsWhenFloatingIslandsFactoryReturnsNullDirectly() {
        EndPreset profile = EndPreset.defaults();
        BiFunction<EndPreset, Integer, EndHeightmap> realHeightmapFactory =
                (p, s) -> new EndHeightmap(p, s);
        Function<EndPreset, FloatingIslandsField> nullFloatingIslandsFactory = p -> null;

        EndWorldgenBootstrap.Result result = EndWorldgenBootstrap.bootstrap(
                SEED, profile, realHeightmapFactory, nullFloatingIslandsFactory);

        assertTrue(result.succeeded(),
                "a null floating-islands return is a valid success (layer disabled), "
                        + "not a degradation");
        assertFalse(result.degraded(),
                "degraded must be false when only the floating-islands factory returned null");
        assertNotNull(result.endDensity(),
                "endDensity must still be constructed when floating islands are disabled");
        assertNull(result.floatingIslandsField(),
                "floatingIslandsField must be null when the factory returned null");
    }

    // ------------------------------------------------------------------
    //  Failure path: heightmap factory throws
    // ------------------------------------------------------------------

    /**
     * The core fallback contract: if the heightmap factory throws, the
     * exception must be caught and {@link EndWorldgenBootstrap.Result#degraded()}
     * must be {@code true}. The world must NOT crash — this is the whole
     * point of stage 6.3's fallback layer.
     *
     * <p>Uses the package-private factory overload to inject a heightmap
     * factory that always throws. This is more reliable than constructing
     * a profile that breaks {@link EndHeightmap}'s constructor (which is
     * difficult by design — the constructor is defensive).</p>
     */
    @Test
    void bootstrapDegradesWhenHeightmapFactoryThrows() {
        EndPreset profile = EndPreset.defaults();
        BiFunction<EndPreset, Integer, EndHeightmap> throwingFactory =
                (p, s) -> { throw new IllegalStateException("test: simulated heightmap failure"); };

        EndWorldgenBootstrap.Result result = EndWorldgenBootstrap.bootstrap(
                SEED, profile, throwingFactory, p -> null);

        assertTrue(result.degraded(),
                "degraded flag must be true when the heightmap factory throws");
        assertFalse(result.succeeded(),
                "succeeded flag must be false when the heightmap factory throws");
        assertNull(result.endDensity(),
                "endDensity must be null on degradation (MixinRandomState drops End flag)");
        assertNull(result.floatingIslandsField(),
                "floatingIslandsField must be null on degradation");
    }

    /**
     * On degradation, the climate published to {@link EndClimateAccess}
     * <em>before</em> the heightmap failure must be rolled back to
     * {@code null}. Otherwise a stale climate from a partially-constructed
     * stack would leak into the next End load (via the process-wide
     * volatile holder), causing {@code EndBiomeSource#getNoiseBiome} to
     * run the climate variant selector against stale data — silently
     * producing wrong biome variants on the next world load.
     */
    @Test
    void bootstrapRollsBackClimateOnFailure() {
        EndPreset profile = EndPreset.defaults();
        BiFunction<EndPreset, Integer, EndHeightmap> throwingFactory =
                (p, s) -> { throw new IllegalStateException("test: simulated failure"); };

        // Sanity: the holder may have a climate from a prior test (defensive —
        // AfterEach clears it, but this guards against future test additions
        // that forget the teardown). Use a known non-null climate so the
        // assertion below isn't vacuously true.
        EndClimateAccess.set(EndClimate.defaults(SEED + 1));
        EndBiomeLayoutAccess.set(EndBiomeLayout.DEFAULT);
        EndClimate preFailure = EndClimateAccess.get();
        assertNotNull(preFailure, "sanity: holder must be populated before bootstrap");

        EndWorldgenBootstrap.bootstrap(SEED, profile, throwingFactory, p -> null);

        EndClimate postFailure = EndClimateAccess.get();
        assertNull(postFailure,
                "EndClimateAccess must be rolled back to null when bootstrap degrades "
                        + "(otherwise stale climate leaks into the next End load)");
        assertNotEquals(preFailure, postFailure,
                "EndClimateAccess.get() must change after a degraded bootstrap (rollback occurred)");
        assertNull(EndBiomeLayoutAccess.get(),
                "EndBiomeLayoutAccess must be rolled back to null when bootstrap degrades");
    }

    // ------------------------------------------------------------------
    //  Failure path: floating-islands factory throws (catch covers whole try)
    // ------------------------------------------------------------------

    /**
     * The catch block must wrap the entire bootstrap sequence, not just
     * the heightmap step — if the floating-islands factory throws (after
     * the heightmap + density have already been built), the exception
     * must still be caught and produce a degraded {@link EndWorldgenBootstrap.Result}.
     *
     * <p>This test injects a heightmap factory that succeeds and a
     * floating-islands factory that throws, exercising the tail of the
     * try block. A regression that narrows the catch to just the heightmap
     * step would let this exception escape and crash worldgen.</p>
     */
    @Test
    void bootstrapDegradesWhenFloatingIslandsFactoryThrows() {
        EndPreset profile = EndPreset.defaults();
        BiFunction<EndPreset, Integer, EndHeightmap> realFactory =
                (p, s) -> new EndHeightmap(p, s);
        Function<EndPreset, FloatingIslandsField> throwingFloatingIslands =
                p -> { throw new IllegalStateException("test: simulated floating-islands failure"); };

        EndWorldgenBootstrap.Result result = EndWorldgenBootstrap.bootstrap(
                SEED, profile, realFactory, throwingFloatingIslands);

        assertTrue(result.degraded(),
                "degraded flag must be true when the floating-islands factory throws "
                        + "(catch must wrap the entire try block, not just the heightmap step)");
        assertNull(result.endDensity(),
                "endDensity must be null on degradation even though it was constructed "
                        + "before the floating-islands factory threw");
        assertNull(result.floatingIslandsField(),
                "floatingIslandsField must be null on degradation");
    }

    // ------------------------------------------------------------------
    //  Failure path: exception type coverage
    // ------------------------------------------------------------------

    /**
     * The catch must catch any {@link Exception}, not just a specific
     * subtype. {@link NullPointerException} is the most common runtime
     * failure mode (a future refactor dereferences a null field) and
     * must be caught — pinning the {@code catch (Exception)} contract
     * against a future narrowing to {@code catch (RuntimeException)} or
     * similar.
     */
    @Test
    void bootstrapDegradesOnNullPointerException() {
        EndPreset profile = EndPreset.defaults();
        BiFunction<EndPreset, Integer, EndHeightmap> npeFactory =
                (p, s) -> { throw new NullPointerException("test: simulated NPE"); };

        EndWorldgenBootstrap.Result result = EndWorldgenBootstrap.bootstrap(
                SEED, profile, npeFactory, p -> null);

        assertTrue(result.degraded(),
                "NullPointerException must be caught and produce a degraded Result");
    }

    /**
     * {@link IllegalArgumentException} is the typical exception thrown by
     * noise-tree validators (e.g. negative frequency, out-of-range octave
     * count). Must be caught — pinning the contract against a future
     * refactor that lets these propagate.
     */
    @Test
    void bootstrapDegradesOnIllegalArgumentException() {
        EndPreset profile = EndPreset.defaults();
        BiFunction<EndPreset, Integer, EndHeightmap> argFactory =
                (p, s) -> { throw new IllegalArgumentException("test: simulated arg failure"); };

        EndWorldgenBootstrap.Result result = EndWorldgenBootstrap.bootstrap(
                SEED, profile, argFactory, p -> null);

        assertTrue(result.degraded(),
                "IllegalArgumentException must be caught and produce a degraded Result");
    }

    /**
     * {@link RuntimeException} is the supertype of most unchecked failures.
     * Pinning it explicitly guards against a refactor that narrows the
     * catch to specific subtypes.
     */
    @Test
    void bootstrapDegradesOnRuntimeException() {
        EndPreset profile = EndPreset.defaults();
        BiFunction<EndPreset, Integer, EndHeightmap> rteFactory =
                (p, s) -> { throw new RuntimeException("test: simulated RTE"); };

        EndWorldgenBootstrap.Result result = EndWorldgenBootstrap.bootstrap(
                SEED, profile, rteFactory, p -> null);

        assertTrue(result.degraded(),
                "RuntimeException must be caught and produce a degraded Result");
    }

    // ------------------------------------------------------------------
    //  Success path: idempotent re-bootstrap
    // ------------------------------------------------------------------

    /**
     * Two consecutive bootstraps with the same inputs must both succeed
     * and produce distinct (non-{@code ==}) {@link EndDensity} instances.
     * This pins the contract that {@link EndWorldgenBootstrap} is
     * stateless — calling it twice doesn't share state or fail on the
     * second call (the production {@code MixinRandomState} path runs once
     * per dimension load, but the bootstrap itself must not accumulate
     * state).
     */
    @Test
    void bootstrapIsStatelessAcrossCalls() {
        EndPreset profile = EndPreset.defaults();

        EndWorldgenBootstrap.Result first = EndWorldgenBootstrap.bootstrap(SEED, profile);
        EndWorldgenBootstrap.Result second = EndWorldgenBootstrap.bootstrap(SEED, profile);

        assertTrue(first.succeeded());
        assertTrue(second.succeeded());
        // Distinct instances — bootstrap must not cache or share state.
        assertNotEquals(System.identityHashCode(first.endDensity()),
                System.identityHashCode(second.endDensity()),
                "two bootstrap calls must produce distinct EndDensity instances (stateless)");
    }
}
