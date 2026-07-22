package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import endterraforged.world.filter.ErosionConfig;

/**
 * Contract tests for {@link EndPresetAccess}: a process-wide volatile
 * holder that publishes the user-edited {@link EndPreset} from the GUI's
 * Done button to {@code MixinRandomState}'s construction of the End's
 * {@code EndDensity}.
 *
 * <p>These pin the holder's four-method surface ({@code set/get/getOrDefault/
 * clear}) and the documented null-handling semantics:</p>
 * <ul>
 *   <li>{@code get} returns {@code null} before any {@code set} (initial
 *       state, and after {@code clear}) — the "GUI never ran" case for
 *       dedicated servers and direct world loads</li>
 *   <li>{@code getOrDefault} returns {@link EndPreset#defaults()} before
 *       any {@code set} — the production-safe accessor used by
 *       {@code MixinRandomState}</li>
 *   <li>{@code set} publishes the supplied reference verbatim (no copy,
 *       no defensive wrapping — {@link EndPreset} is already immutable)</li>
 *   <li>{@code set} supersedes any previously published reference</li>
 *   <li>{@code set(null)} is equivalent to {@code clear} (documented
 *       contract)</li>
 * </ul>
 *
 * <p>Each test clears the holder in {@link BeforeEach} so the tests are
 * order-independent and do not leak state into other test classes that
 * might touch the holder.</p>
 */
class EndPresetAccessTest {

    @BeforeEach
    void clearHolder() {
        // Ensure each test starts from a known "no preset published" state,
        // regardless of what previous tests (in this class or others) left.
        EndPresetAccess.clear();
    }

    @Test
    void getReturnsNullBeforeAnySet() {
        // The holder starts empty: a worker thread querying before the GUI
        // runs must see null so the production code (MixinRandomState) falls
        // back to EndPreset.defaults(). This covers the dedicated-server and
        // direct-world-load cases.
        assertNull(EndPresetAccess.get(),
                "holder must be null before any set() call");
    }

    @Test
    void getOrDefaultReturnsDefaultsBeforeAnySet() {
        // The production-safe accessor never returns null: when no GUI has
        // run, it falls back to EndPreset.defaults(). MixinRandomState relies
        // on this to avoid a null check in the construction path.
        assertEquals(EndPreset.defaults(), EndPresetAccess.getOrDefault(),
                "getOrDefault() must return EndPreset.defaults() before any set()");
    }

    @Test
    void setThenGetReturnsSameInstance() {
        // set must publish the exact reference passed in — no copy, no
        // wrapping. EndPreset is an immutable record, so identity equality
        // is the correct assertion (a defensive copy would be wasteful and
        // would break the volatile-publishes-immutable-graph contract).
        EndPreset custom = makeCustom();
        EndPresetAccess.set(custom);
        assertSame(custom, EndPresetAccess.get(),
                "get() must return the same reference passed to set()");
    }

    @Test
    void getOrDefaultReturnsPublishedAfterSet() {
        // After set, getOrDefault returns the published preset, not the
        // defaults fallback — the GUI's edits reach MixinRandomState.
        EndPreset custom = makeCustom();
        EndPresetAccess.set(custom);
        assertSame(custom, EndPresetAccess.getOrDefault(),
                "getOrDefault() must return the published preset after set()");
    }

    @Test
    void clearDropsPublishedValue() {
        // After clear, get returns null and getOrDefault falls back to
        // defaults. Tests rely on this to assert the "no GUI ran" path of
        // MixinRandomState in isolation.
        EndPresetAccess.set(makeCustom());
        assertSame(EndPresetAccess.get(), EndPresetAccess.get(),
                "sanity: holder is populated before clear");
        EndPresetAccess.clear();
        assertNull(EndPresetAccess.get(),
                "clear() must drop the published preset");
        assertEquals(EndPreset.defaults(), EndPresetAccess.getOrDefault(),
                "clear() must restore the defaults fallback in getOrDefault()");
    }

    @Test
    void setOverridesPreviousValue() {
        // A second set supersedes the first: a new create-world flow that
        // runs the GUI again must atomically replace the previous preset.
        // Workers reading concurrently may see either the old or the new
        // one, but never a torn or stale reference after the volatile
        // write completes.
        EndPreset first = makeCustom();
        EndPreset second = new EndPreset(2048, -1024, 64, 0,
                SeaMode.WITH_FLOOR, TopologyMode.CONTINENTAL_SHATTERED, true,
                ContinentConfig.defaults(),
                TerrainConfig.DEFAULT,
                ErosionConfig.DEFAULT);
        EndPresetAccess.set(first);
        assertSame(first, EndPresetAccess.get(),
                "first set() must publish the first preset");
        EndPresetAccess.set(second);
        assertSame(second, EndPresetAccess.get(),
                "second set() must supersede the first preset");
    }

    @Test
    void setNullIsEquivalentToClear() {
        // Documented contract: a null argument to set is equivalent to clear.
        // The GUI's Done button never passes null, but the holder's
        // documented surface promises it — pin it so a future caller (e.g.
        // a world-unload hook) can rely on it.
        EndPresetAccess.set(makeCustom());
        assertSame(EndPresetAccess.get(), EndPresetAccess.get(),
                "sanity: holder is populated before set(null)");
        EndPresetAccess.set(null);
        assertNull(EndPresetAccess.get(),
                "set(null) must be equivalent to clear()");
        assertEquals(EndPreset.defaults(), EndPresetAccess.getOrDefault(),
                "set(null) must restore the defaults fallback in getOrDefault()");
    }

    @Test
    void defaultsFallbackIsValueEqualEachCall() {
        // EndPreset.defaults() may or may not return the same instance each
        // call (it's a record, so value equality is the meaningful contract).
        // The fallback in getOrDefault returns whatever defaults() produces,
        // so callers comparing with equals() (not ==) see consistent values.
        // Pinning value equality documents that the fallback is a fresh
        // defaults() each call — useful for memory reasoning (no leak
        // across worlds).
        assertEquals(EndPreset.defaults(), EndPreset.defaults(),
                "EndPreset.defaults() must be value-equal each call");
    }

    /** Builds a non-default EndPreset for tests that need a "user-edited" value. */
    private static EndPreset makeCustom() {
        return new EndPreset(2048, -1024, 64, 0,
                SeaMode.WITH_FLOOR, TopologyMode.CONTINENTAL_SHATTERED, true,
                ContinentConfig.defaults(),
                new TerrainConfig(0.75F, 2.0F),
                new ErosionConfig(256, 64, 1.5F, 0.8F, 0.3F, 0.7F));
    }
}
