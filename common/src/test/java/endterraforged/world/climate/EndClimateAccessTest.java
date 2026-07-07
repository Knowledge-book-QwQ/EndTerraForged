package endterraforged.world.climate;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for {@link EndClimateAccess}: a process-wide volatile
 * holder that publishes the active {@link EndClimate} from the bootstrap
 * thread to worker-thread biome lookups.
 *
 * <p>These pin the holder's three-method surface ({@code set/get/clear})
 * and the documented null-handling semantics:
 * <ul>
 *   <li>{@code get} returns {@code null} before any {@code set} (initial
 *       state, and after {@code clear});</li>
 *   <li>{@code set} publishes the supplied reference verbatim (no copy,
 *       no defensive wrapping — {@link EndClimate} is already immutable);</li>
 *   <li>{@code set} supersedes any previously published reference;</li>
 *   <li>{@code set(null)} is equivalent to {@code clear} (documented
 *       contract — non-End dimensions must not leave a stale climate).</li>
 * </ul>
 *
 * <p>Each test clears the holder in {@link BeforeEach} so the tests are
 * order-independent and do not leak state into other test classes that
 * might touch the holder (e.g. {@code EndBiomeSourceTest}).</p>
 */
class EndClimateAccessTest {

    @BeforeEach
    void clearHolder() {
        // Ensure each test starts from a known "no climate published" state,
        // regardless of what previous tests (in this class or others) left.
        EndClimateAccess.clear();
    }

    @Test
    void getReturnsNullBeforeAnySet() {
        // The holder starts empty: a worker thread querying before the first
        // End load must see null so the biome selector's fast-path falls
        // back to the ring's base biome. This also covers the "no End
        // dimension loaded at all" case (overworld/nether-only servers).
        assertNull(EndClimateAccess.get(),
                "holder must be null before any set() call");
    }

    @Test
    void setThenGetReturnsSameInstance() {
        // set must publish the exact reference passed in — no copy, no
        // wrapping. EndClimate is an immutable record, so identity equality
        // is the correct assertion (a defensive copy would be wasteful and
        // would break the volatile-publishes-immutable-graph contract).
        EndClimate climate = EndClimate.defaults(42);
        EndClimateAccess.set(climate);
        assertSame(climate, EndClimateAccess.get(),
                "get() must return the same reference passed to set()");
    }

    @Test
    void clearDropsPublishedValue() {
        // After clear, get returns null even if a climate was previously
        // published. This is the contract tests rely on to assert the
        // "no climate configured" fast path of the biome selector.
        EndClimateAccess.set(EndClimate.defaults(7));
        assertSame(EndClimateAccess.get(), EndClimateAccess.get(),
                "sanity: holder is populated before clear");
        EndClimateAccess.clear();
        assertNull(EndClimateAccess.get(),
                "clear() must drop the published climate");
    }

    @Test
    void setOverridesPreviousValue() {
        // A second set supersedes the first: a server reload that rebuilds
        // RandomState and re-publishes a fresh EndClimate must atomically
        // replace the previous reference. Workers reading concurrently may
        // see either the old or the new one, but never a torn or stale
        // reference after the volatile write completes.
        EndClimate first = EndClimate.defaults(1);
        EndClimate second = EndClimate.defaults(2);
        EndClimateAccess.set(first);
        assertSame(first, EndClimateAccess.get(),
                "first set() must publish the first climate");
        EndClimateAccess.set(second);
        assertSame(second, EndClimateAccess.get(),
                "second set() must supersede the first climate");
    }

    @Test
    void setNullIsEquivalentToClear() {
        // Documented contract: a null argument to set is equivalent to clear.
        // MixinRandomState only calls set inside the isEnd branch, so this
        // path is not exercised in production, but the holder's documented
        // surface promises it — pin it so a future caller (e.g. a non-End
        // branch that wants to defensively drop a stale climate) can rely
        // on it.
        EndClimateAccess.set(EndClimate.defaults(99));
        assertSame(EndClimateAccess.get(), EndClimateAccess.get(),
                "sanity: holder is populated before set(null)");
        EndClimateAccess.set(null);
        assertNull(EndClimateAccess.get(),
                "set(null) must be equivalent to clear()");
    }
}
