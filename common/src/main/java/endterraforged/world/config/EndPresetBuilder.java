package endterraforged.world.config;

import java.util.Objects;

import endterraforged.world.filter.ErosionConfig;

/**
 * Mutable builder for {@link EndPreset}, serving as the GUI's editable state.
 *
 * <p><b>Why this exists.</b> {@link EndPreset} is a record (immutable), so a
 * GUI editor cannot mutate it in place while the user drags sliders. This
 * builder mirrors the eight record components as mutable fields; the GUI
 * binds widgets to the setters, and {@link #build()} snapshots the current
 * state into an immutable {@link EndPreset} for the worldgen pipeline.</p>
 *
 * <p><b>Pure logic.</b> This class has zero Minecraft / DFU dependencies — it
 * is the testable core behind the (untestable-in-sandbox) Screen layer. Unit
 * tests verify round-trip ({@code builder(preset).build() == preset}),
 * {@link #reset()} restores defaults, and each setter stores the value.</p>
 *
 * <p><b>Thread safety.</b> Not thread-safe — GUI editing happens on the
 * render thread only. The resulting {@link EndPreset} is then handed to the
 * worldgen pipeline, which is the thread-safety boundary.</p>
 */
public final class EndPresetBuilder {

    private int worldHeight;
    private int minY;
    private int seaLevelY;
    private int islandBaselineY;
    private SeaMode seaMode;
    private TopologyMode topologyMode;
    private boolean floatingIslandsEnabled;
    private ErosionConfig erosionConfig;

    /** Creates a builder initialised to {@link EndPreset#defaults()}. */
    public EndPresetBuilder() {
        this(EndPreset.defaults());
    }

    /**
     * Creates a builder initialised to the given preset's values — the GUI
     * loads an existing preset for editing via this constructor.
     */
    public EndPresetBuilder(EndPreset source) {
        this.worldHeight = source.worldHeight();
        this.minY = source.minY();
        this.seaLevelY = source.seaLevelY();
        this.islandBaselineY = source.islandBaselineY();
        this.seaMode = source.seaMode();
        this.topologyMode = source.topologyMode();
        this.floatingIslandsEnabled = source.floatingIslandsEnabled();
        this.erosionConfig = source.erosionConfig();
    }

    /** Snapshots the current state into an immutable {@link EndPreset}. */
    public EndPreset build() {
        return new EndPreset(worldHeight, minY, seaLevelY, islandBaselineY,
                seaMode, topologyMode, floatingIslandsEnabled, erosionConfig);
    }

    /** Resets every field to {@link EndPreset#defaults()}. */
    public EndPresetBuilder reset() {
        EndPreset d = EndPreset.defaults();
        this.worldHeight = d.worldHeight();
        this.minY = d.minY();
        this.seaLevelY = d.seaLevelY();
        this.islandBaselineY = d.islandBaselineY();
        this.seaMode = d.seaMode();
        this.topologyMode = d.topologyMode();
        this.floatingIslandsEnabled = d.floatingIslandsEnabled();
        this.erosionConfig = d.erosionConfig();
        return this;
    }

    // ----- getters (for widget initial values) ------------------------------

    public int worldHeight() { return worldHeight; }
    public int minY() { return minY; }
    public int seaLevelY() { return seaLevelY; }
    public int islandBaselineY() { return islandBaselineY; }
    public SeaMode seaMode() { return seaMode; }
    public TopologyMode topologyMode() { return topologyMode; }
    public boolean floatingIslandsEnabled() { return floatingIslandsEnabled; }
    public ErosionConfig erosionConfig() { return erosionConfig; }

    // ----- setters (bound to GUI widgets) -----------------------------------

    public EndPresetBuilder worldHeight(int v) { this.worldHeight = v; return this; }
    public EndPresetBuilder minY(int v) { this.minY = v; return this; }
    public EndPresetBuilder seaLevelY(int v) { this.seaLevelY = v; return this; }
    public EndPresetBuilder islandBaselineY(int v) { this.islandBaselineY = v; return this; }
    public EndPresetBuilder seaMode(SeaMode v) { this.seaMode = Objects.requireNonNull(v); return this; }
    public EndPresetBuilder topologyMode(TopologyMode v) { this.topologyMode = Objects.requireNonNull(v); return this; }
    public EndPresetBuilder floatingIslandsEnabled(boolean v) { this.floatingIslandsEnabled = v; return this; }
    public EndPresetBuilder erosionConfig(ErosionConfig v) { this.erosionConfig = Objects.requireNonNull(v); return this; }
}
