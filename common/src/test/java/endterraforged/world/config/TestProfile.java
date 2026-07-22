package endterraforged.world.config;

/**
 * Minimal in-test {@link DimensionProfile} implementation so unit tests can
 * construct profiles without pulling in the full preset/serialisation layer.
 * Lives in the test sourceset on purpose; production code uses the real
 * {@code EndPreset}-backed profile built in stage 3.
 */
public final class TestProfile implements DimensionProfile {
    private final int worldHeight;
    private final int minY;
    private final int seaLevelY;
    private final int islandBaselineY;
    private final SeaMode seaMode;
    private final TopologyMode topologyMode;
    private final boolean floatingIslandsEnabled;
    private final TerrainConfig terrainConfig;

    public TestProfile(int worldHeight, int minY, int seaLevelY, int islandBaselineY,
                       SeaMode seaMode, TopologyMode topologyMode, boolean floatingIslandsEnabled) {
        this(worldHeight, minY, seaLevelY, islandBaselineY, seaMode, topologyMode,
                floatingIslandsEnabled, TerrainConfig.DEFAULT);
    }

    public TestProfile(int worldHeight, int minY, int seaLevelY, int islandBaselineY,
                       SeaMode seaMode, TopologyMode topologyMode, boolean floatingIslandsEnabled,
                       TerrainConfig terrainConfig) {
        this.worldHeight = worldHeight;
        this.minY = minY;
        this.seaLevelY = seaLevelY;
        this.islandBaselineY = islandBaselineY;
        this.seaMode = seaMode;
        this.topologyMode = topologyMode;
        this.floatingIslandsEnabled = floatingIslandsEnabled;
        this.terrainConfig = terrainConfig;
    }

    /** Convenience: a no-sea, no-floating-islands End of the standard player envelope. */
    public static TestProfile defaultEnd() {
        return new TestProfile(512, -256, 0, 0, SeaMode.NONE, TopologyMode.ISLANDS, false);
    }

    @Override
    public int worldHeight() {
        return worldHeight;
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int seaLevelY() {
        return seaLevelY;
    }

    @Override
    public int islandBaselineY() {
        return islandBaselineY;
    }

    @Override
    public SeaMode seaMode() {
        return seaMode;
    }

    @Override
    public TopologyMode topologyMode() {
        return topologyMode;
    }

    @Override
    public boolean floatingIslandsEnabled() {
        return floatingIslandsEnabled;
    }

    @Override
    public TerrainConfig terrainConfig() {
        return terrainConfig;
    }
}
