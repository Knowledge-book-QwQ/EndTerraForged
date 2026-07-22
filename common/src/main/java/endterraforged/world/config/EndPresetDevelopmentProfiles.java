package endterraforged.world.config;

/**
 * Non-persisted development profiles used for real-client smoke tests.
 *
 * <p>These profiles are opt-in through a JVM system property and are never
 * returned by the codec, editor, or normal default path. They exist because
 * the current v3 validator deliberately rejects {@link TerrainLayoutMode#REGION_PLANNED}
 * until the v4 terrain catalog is ready.</p>
 */
public final class EndPresetDevelopmentProfiles {

    public static final String P46_ARCHIPELAGO_SMOKE_TEST_PROPERTY =
            "endterraforged.p46_archipelago_smoke_test";

    private static final EndPreset P46_ARCHIPELAGO_SMOKE_TEST = createArchipelagoSmokeTest();

    private EndPresetDevelopmentProfiles() {
    }

    /** Returns whether the P4.6 real-client smoke profile was explicitly requested. */
    public static boolean archipelagoSmokeTestEnabled() {
        return Boolean.parseBoolean(System.getProperty(P46_ARCHIPELAGO_SMOKE_TEST_PROPERTY, "false"));
    }

    /**
     * Returns the opt-in P4.6 profile, or the normal player default when the
     * development property is absent.
     */
    public static EndPreset defaultFallback() {
        return archipelagoSmokeTestEnabled() ? P46_ARCHIPELAGO_SMOKE_TEST : EndPreset.defaults();
    }

    /** Returns the immutable profile used by direct development tests. */
    static EndPreset archipelagoSmokeTest() {
        return P46_ARCHIPELAGO_SMOKE_TEST;
    }

    private static EndPreset createArchipelagoSmokeTest() {
        TerrainConfig base = TerrainConfig.DEFAULT;
        TerrainLayerConfig smokeMountains = new TerrainLayerConfig(
                1.0F, 1.0F, 1.25F, 0.85F);
        TerrainConfig regionPlanned = new TerrainConfig(
                base.terrainSeedOffset(), base.terrainRegionSize(),
                base.globalVerticalScale(), base.globalHorizontalScale(),
                base.terrainBlendRange(), TerrainLayoutMode.REGION_PLANNED,
                base.terrainShape(), TerrainLayerConfig.DEFAULT, TerrainLayerConfig.DEFAULT,
                TerrainLayerConfig.DEFAULT, smokeMountains, base.volcano());
        EndPreset defaults = EndPreset.defaults();
        return new EndPreset(
                defaults.worldHeight(), defaults.minY(), defaults.seaLevelY(),
                defaults.islandBaselineY(), SeaMode.WITH_FLOOR, defaults.topologyMode(),
                defaults.floatingIslandsEnabled(), ContinentConfig.rtfMultiDefaults(),
                regionPlanned, defaults.climateConfig(), defaults.biomeLayoutConfig(),
                defaults.subsurfaceConfig(), defaults.erosionConfig(), defaults.formatVersion());
    }
}
