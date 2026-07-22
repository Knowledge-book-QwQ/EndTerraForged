package endterraforged.world.level.biome;

/**
 * Process-wide holder for the active End biome layout.
 *
 * <p>The holder mirrors {@code EndClimateAccess}: bootstrap publishes the
 * runtime layout once the End preset is decoded, biome source lookups read it
 * from worker threads, and the server halt hook clears it between world loads
 * in the same JVM session.</p>
 */
public final class EndBiomeLayoutAccess {

    private static volatile EndBiomeLayout current;

    private EndBiomeLayoutAccess() {
    }

    public static void set(EndBiomeLayout layout) {
        current = layout;
    }

    public static EndBiomeLayout get() {
        return current;
    }

    public static EndBiomeLayout getOrDefault() {
        EndBiomeLayout layout = current;
        return layout == null ? EndBiomeLayout.DEFAULT : layout;
    }

    public static void clear() {
        current = null;
    }
}
