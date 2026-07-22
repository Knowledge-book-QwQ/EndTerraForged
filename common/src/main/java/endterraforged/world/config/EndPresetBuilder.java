package endterraforged.world.config;

import java.util.Objects;

import com.mojang.serialization.DataResult;

import endterraforged.world.filter.ErosionConfig;

/**
 * Mutable builder for {@link EndPreset}, serving as the GUI's editable state.
 *
 * <p><b>Why this exists.</b> {@link EndPreset} is a record (immutable), so a
 * GUI editor cannot mutate it in place while the user drags sliders. This
 * builder mirrors the record components as mutable fields; the GUI
 * binds widgets to the setters, and {@link #build()} snapshots and validates
 * the current state before it reaches the worldgen pipeline.</p>
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
    private ContinentConfig continentConfig;
    private TerrainConfig terrainConfig;
    private ClimateConfig climateConfig;
    private BiomeLayoutConfig biomeLayoutConfig;
    private SubsurfaceConfig subsurfaceConfig;
    private ErosionConfig erosionConfig;
    private int formatVersion;

    /** Creates a builder initialised to {@link EndPreset#defaults()}. */
    public EndPresetBuilder() {
        this(EndPreset.defaults());
    }

    /**
     * Creates a builder initialised to the given preset's values — the GUI
     * loads an existing preset for editing via this constructor.
     */
    public EndPresetBuilder(EndPreset source) {
        load(source);
    }

    /** Replaces every builder field with the supplied preset's values. */
    public EndPresetBuilder load(EndPreset source) {
        Objects.requireNonNull(source, "source");
        this.worldHeight = source.worldHeight();
        this.minY = source.minY();
        this.seaLevelY = source.seaLevelY();
        this.islandBaselineY = source.islandBaselineY();
        this.seaMode = source.seaMode();
        this.topologyMode = source.topologyMode();
        this.floatingIslandsEnabled = source.floatingIslandsEnabled();
        this.continentConfig = source.continentConfig();
        this.terrainConfig = source.terrainConfig();
        this.climateConfig = source.climateConfig();
        this.biomeLayoutConfig = source.biomeLayoutConfig();
        this.subsurfaceConfig = source.subsurfaceConfig();
        this.erosionConfig = source.erosionConfig();
        this.formatVersion = source.formatVersion();
        return this;
    }

    /** Snapshots and validates the current state into an immutable {@link EndPreset}. */
    public EndPreset build() {
        EndPreset preset = new EndPreset(worldHeight, minY, seaLevelY, islandBaselineY,
                seaMode, topologyMode, floatingIslandsEnabled, continentConfig,
                terrainConfig, climateConfig, biomeLayoutConfig, subsurfaceConfig, erosionConfig,
                formatVersion);
        DataResult<EndPreset> result = EndPresetValidator.validate(preset);
        return result.result().orElseThrow(() -> invalidState(result));
    }

    private static IllegalStateException invalidState(DataResult<?> result) {
        String message = result.error().map(error -> error.message()).orElse("unknown validation error");
        return new IllegalStateException("invalid end preset builder state: " + message);
    }

    /** Resets every field to {@link EndPreset#defaults()}. */
    public EndPresetBuilder reset() {
        return load(EndPreset.defaults());
    }

    // ----- getters (for widget initial values) ------------------------------

    public int worldHeight() {
        return worldHeight;
    }

    public int minY() {
        return minY;
    }

    /** Returns the currently requested vertical envelope. */
    public WorldVerticalBounds worldBounds() {
        return new WorldVerticalBounds(minY, worldHeight);
    }

    public int seaLevelY() {
        return seaLevelY;
    }

    public int islandBaselineY() {
        return islandBaselineY;
    }

    public SeaMode seaMode() {
        return seaMode;
    }

    public TopologyMode topologyMode() {
        return topologyMode;
    }

    public boolean floatingIslandsEnabled() {
        return floatingIslandsEnabled;
    }

    public ContinentConfig continentConfig() {
        return continentConfig;
    }

    public TerrainConfig terrainConfig() {
        return terrainConfig;
    }

    public ClimateConfig climateConfig() {
        return climateConfig;
    }

    public BiomeLayoutConfig biomeLayoutConfig() {
        return biomeLayoutConfig;
    }

    public SubsurfaceConfig subsurfaceConfig() {
        return subsurfaceConfig;
    }

    /** Returns the persistence format carried by the source preset. */
    public int formatVersion() {
        return formatVersion;
    }

    public ErosionConfig erosionConfig() {
        return erosionConfig;
    }

    // ----- setters (bound to GUI widgets) -----------------------------------

    public EndPresetBuilder worldHeight(int v) {
        this.worldHeight = v;
        return this;
    }

    public EndPresetBuilder minY(int v) {
        this.minY = v;
        return this;
    }

    /** Replaces the two coupled world-envelope fields atomically. */
    public EndPresetBuilder worldBounds(WorldVerticalBounds bounds) {
        Objects.requireNonNull(bounds, "bounds");
        this.minY = bounds.minY();
        this.worldHeight = bounds.height();
        return this;
    }

    public EndPresetBuilder seaLevelY(int v) {
        this.seaLevelY = v;
        return this;
    }

    public EndPresetBuilder islandBaselineY(int v) {
        this.islandBaselineY = v;
        return this;
    }

    public EndPresetBuilder seaMode(SeaMode v) {
        this.seaMode = Objects.requireNonNull(v);
        return this;
    }

    public EndPresetBuilder topologyMode(TopologyMode v) {
        this.topologyMode = Objects.requireNonNull(v);
        return this;
    }

    public EndPresetBuilder floatingIslandsEnabled(boolean v) {
        this.floatingIslandsEnabled = v;
        return this;
    }

    public EndPresetBuilder continentConfig(ContinentConfig v) {
        this.continentConfig = Objects.requireNonNull(v);
        return this;
    }

    /**
     * Promotes an explicitly edited preset to the current persistence format.
     *
     * <p>Callers must only invoke this after opting into a feature introduced by
     * the current format; merely opening an old preset must not rewrite its
     * compatibility semantics.</p>
     */
    public EndPresetBuilder upgradeToCurrentFormat() {
        this.formatVersion = EndPreset.CURRENT_FORMAT_VERSION;
        return this;
    }

    public EndPresetBuilder terrainConfig(TerrainConfig v) {
        this.terrainConfig = Objects.requireNonNull(v);
        return this;
    }

    public EndPresetBuilder climateConfig(ClimateConfig v) {
        this.climateConfig = Objects.requireNonNull(v);
        return this;
    }

    public EndPresetBuilder biomeLayoutConfig(BiomeLayoutConfig v) {
        this.biomeLayoutConfig = Objects.requireNonNull(v);
        return this;
    }

    public EndPresetBuilder subsurfaceConfig(SubsurfaceConfig v) {
        this.subsurfaceConfig = Objects.requireNonNull(v);
        return this;
    }

    public EndPresetBuilder erosionConfig(ErosionConfig v) {
        this.erosionConfig = Objects.requireNonNull(v);
        return this;
    }
}
