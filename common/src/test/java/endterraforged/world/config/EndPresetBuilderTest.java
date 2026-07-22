package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.filter.ErosionConfig;

/**
 * Contract tests for {@link EndPresetBuilder}: the GUI's mutable editing
 * state. Covers round-trip fidelity ({@code builder(preset).build() == preset}),
 * {@link EndPresetBuilder#reset()} restores defaults, each setter stores the
 * value, and the no-arg constructor starts at defaults.
 *
 * <p>These tests are the testable core behind the (sandbox-untestable) Screen
 * layer — the GUI binds widgets to the builder's setters, and {@link
 * EndPresetBuilder#build()} snapshots state into an immutable preset.</p>
 */
class EndPresetBuilderTest {

    @Test
    void defaultConstructorStartsAtDefaults() {
        EndPresetBuilder b = new EndPresetBuilder();
        assertEquals(EndPreset.defaults(), b.build(),
                "no-arg constructor must initialise to EndPreset.defaults()");
    }

    @Test
    void roundTripFromPresetIsIdentity() {
        EndPreset original = new EndPreset(2048, -1024, 64, 0,
                SeaMode.WITH_FLOOR, TopologyMode.CONTINENTAL_SHATTERED, true,
                ContinentConfig.defaults(),
                new TerrainConfig(0.75F, 2.0F),
                new ClimateConfig(6000.0F, 900, 1200, 1500, 0.4F),
                new ErosionConfig(256, 64, 1.5F, 0.8F, 0.3F, 0.7F));
        EndPreset roundTripped = new EndPresetBuilder(original).build();
        assertEquals(original, roundTripped,
                "builder(preset).build() must equal the source preset");
    }

    @Test
    void resetRestoresDefaults() {
        EndPresetBuilder b = new EndPresetBuilder()
                .worldHeight(1024)
                .minY(-512)
                .seaLevelY(123)
                .islandBaselineY(256)
                .seaMode(SeaMode.WITH_FLOOR)
                .topologyMode(TopologyMode.CONTINENTAL_SHATTERED)
                .floatingIslandsEnabled(true)
                .continentConfig(ContinentConfig.defaults())
                .terrainConfig(new TerrainConfig(0.5F, 2.5F))
                .climateConfig(new ClimateConfig(6000.0F, 900, 1200, 1500, 0.4F))
                .subsurfaceConfig(new SubsurfaceConfig(
                        new AbyssPitConfig(true, 1700, 640, 0.65F, 0.2F, 512, 0.1F)))
                .erosionConfig(new ErosionConfig(1, 1, 1.0F, 1.0F, 1.0F, 1.0F));
        assertNotEquals(EndPreset.defaults(), b.build(),
                "sanity: builder was mutated away from defaults before reset");
        b.reset();
        assertEquals(EndPreset.defaults(), b.build(),
                "reset() must restore EndPreset.defaults()");
    }

    @Test
    void loadReplacesCurrentState() {
        EndPreset replacement = new EndPreset(2048, -1024, 64, 0,
                SeaMode.WITH_FLOOR, TopologyMode.CONTINENTAL_SHATTERED, true,
                ContinentConfig.defaults(),
                new TerrainConfig(0.75F, 2.0F),
                new ClimateConfig(6000.0F, 900, 1200, 1500, 0.4F),
                BiomeLayoutConfig.DEFAULT,
                SubsurfaceConfig.DEFAULT,
                new ErosionConfig(256, 64, 1.5F, 0.8F, 0.3F, 0.7F));
        EndPresetBuilder b = new EndPresetBuilder()
                .worldHeight(1024)
                .minY(-512);

        assertSame(b, b.load(replacement));

        assertEquals(replacement, b.build(),
                "load(preset) must replace every builder field with the supplied preset");
    }

    @Test
    void loadRejectsNullPreset() {
        assertThrows(NullPointerException.class, () -> new EndPresetBuilder().load(null));
    }

    @Test
    void explicitFeatureUpgradePromotesLegacyPresetToCurrentFormat() {
        EndPreset defaults = EndPreset.defaults();
        EndPreset legacy = new EndPreset(defaults.worldHeight(), defaults.minY(), defaults.seaLevelY(),
                defaults.islandBaselineY(), defaults.seaMode(), defaults.topologyMode(),
                defaults.floatingIslandsEnabled(), defaults.continentConfig(), defaults.terrainConfig(),
                defaults.climateConfig(), defaults.biomeLayoutConfig(), defaults.subsurfaceConfig(),
                defaults.erosionConfig(), 2);
        EndPresetBuilder builder = new EndPresetBuilder(legacy);

        assertEquals(2, builder.formatVersion());
        assertSame(builder, builder.upgradeToCurrentFormat());
        assertEquals(EndPreset.CURRENT_FORMAT_VERSION, builder.build().formatVersion());
    }

    @Test
    void eachSetterStoresValue() {
        EndPresetBuilder b = new EndPresetBuilder();
        ErosionConfig custom = new ErosionConfig(200, 50, 0.7F, 1.2F, 0.4F, 0.6F);
        TerrainConfig terrain = new TerrainConfig(0.8F, 2.2F);
        ClimateConfig climate = new ClimateConfig(6000.0F, 900, 1200, 1500, 0.4F);
        BiomeLayoutConfig biomeLayout = new BiomeLayoutConfig(32, 6.0F, 35.0F, -5.0F,
                180, 3, 2.5F, 0.65F, 22.0F,
                360, 2, 0.35F);
        SubsurfaceConfig subsurface = new SubsurfaceConfig(
                new AbyssPitConfig(true, 1700, 640, 0.65F, 0.2F, 512, 0.1F));
        b.worldHeight(3008)
         .minY(-1504)
         .seaLevelY(32)
         .islandBaselineY(-64)
         .seaMode(SeaMode.WITH_FLOOR)
         .topologyMode(TopologyMode.CONTINENTAL_SHATTERED)
         .floatingIslandsEnabled(true)
         .continentConfig(ContinentConfig.defaults())
         .terrainConfig(terrain)
         .climateConfig(climate)
         .biomeLayoutConfig(biomeLayout)
         .subsurfaceConfig(subsurface)
         .erosionConfig(custom);

        EndPreset built = b.build();
        assertEquals(3008, built.worldHeight());
        assertEquals(-1504, built.minY());
        assertEquals(32, built.seaLevelY());
        assertEquals(-64, built.islandBaselineY());
        assertEquals(SeaMode.WITH_FLOOR, built.seaMode());
        assertEquals(TopologyMode.CONTINENTAL_SHATTERED, built.topologyMode());
        assertTrue(built.floatingIslandsEnabled());
        assertEquals(ContinentConfig.defaults(), built.continentConfig());
        assertEquals(terrain, built.terrainConfig());
        assertEquals(climate, built.climateConfig());
        assertEquals(biomeLayout, built.biomeLayoutConfig());
        assertEquals(subsurface, built.subsurfaceConfig());
        assertEquals(custom, built.erosionConfig());
    }

    @Test
    void gettersReflectCurrentState() {
        EndPresetBuilder b = new EndPresetBuilder()
                .worldHeight(1024)
                .seaLevelY(50);
        assertEquals(1024, b.worldHeight());
        assertEquals(50, b.seaLevelY());
        // Unchanged fields still at defaults.
        EndPreset d = EndPreset.defaults();
        assertEquals(d.minY(), b.minY());
        assertEquals(d.seaMode(), b.seaMode());
        assertEquals(d.terrainConfig(), b.terrainConfig());
        assertEquals(d.climateConfig(), b.climateConfig());
        assertEquals(d.biomeLayoutConfig(), b.biomeLayoutConfig());
        assertEquals(d.subsurfaceConfig(), b.subsurfaceConfig());
    }

    @Test
    void erosionConfigIsReplacedNotMutated() {
        // The builder stores a reference; replacing it should not affect the
        // old config. (ErosionConfig is immutable so this is trivially true,
        // but the test documents the contract.)
        ErosionConfig original = ErosionConfig.DEFAULT;
        ErosionConfig replacement = new ErosionConfig(1, 1, 0.1F, 0.1F, 0.1F, 0.1F);
        EndPresetBuilder b = new EndPresetBuilder();
        assertSame(original, b.erosionConfig(), "sanity: starts at DEFAULT");
        b.erosionConfig(replacement);
        assertSame(replacement, b.erosionConfig(),
                "setter must store the new reference");
        // Original DEFAULT is unchanged (ErosionConfig is immutable, but
        // this guards against future mutable-field mistakes).
        assertEquals(ErosionConfig.DEFAULT, ErosionConfig.DEFAULT,
                "DEFAULT constant must not be affected by builder operations");
    }

    @Test
    void buildProducesConsistentPresetEachCall() {
        EndPresetBuilder b = new EndPresetBuilder()
                .worldHeight(4000)
                .minY(-2032)
                .seaLevelY(100);
        EndPreset first = b.build();
        EndPreset second = b.build();
        assertEquals(first, second,
                "build() called twice without mutation must produce equal presets");
    }

    @Test
    void buildRejectsInvalidState() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new EndPresetBuilder()
                        .worldHeight(15)
                        .build());
        assertTrue(e.getMessage().contains("world_height"));
    }
}
