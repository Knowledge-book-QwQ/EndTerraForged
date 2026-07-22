package endterraforged.world.level.levelgen;

import java.util.Objects;

import net.minecraft.core.HolderGetter;

/**
 * Transfers a dynamic-registry provider across the synchronous {@code ChunkMap} to
 * {@code RandomState.create} construction boundary.
 *
 * <p>The settings-only {@code RandomState.create} overload does not expose the provider required to
 * rebuild vanilla End final density. {@code ChunkMap} still owns a {@code ServerLevel}, whose
 * registry access is that provider. The value is strictly thread-local and one-shot: it is captured
 * immediately before the direct call and removed by {@link #take}. It is never a world cache and must
 * not be used outside that construction window.</p>
 *
 * <p>This class is thread-safe through thread confinement. Callers on a given thread must consume a
 * captured value before beginning another direct {@code RandomState} construction.</p>
 */
public final class EndRandomStateProviderCapture {

    private static final ThreadLocal<HolderGetter.Provider> CAPTURED_PROVIDER = new ThreadLocal<>();

    private EndRandomStateProviderCapture() {
    }

    /** Captures the provider for the next direct {@code RandomState.create} call on this thread. */
    public static void capture(HolderGetter.Provider provider) {
        CAPTURED_PROVIDER.set(Objects.requireNonNull(provider, "provider"));
    }

    /**
     * Returns and removes the provider captured on this thread, or {@code null} when this is not a
     * {@code ChunkMap}-backed direct construction.
     */
    public static HolderGetter.Provider take() {
        HolderGetter.Provider provider = CAPTURED_PROVIDER.get();
        CAPTURED_PROVIDER.remove();
        return provider;
    }

    /** Clears an unconsumed value after an exceptional construction path. */
    public static void clear() {
        CAPTURED_PROVIDER.remove();
    }
}
