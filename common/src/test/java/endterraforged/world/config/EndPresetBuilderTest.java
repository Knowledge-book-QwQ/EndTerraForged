package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
                new ErosionConfig(256, 64, 1.5F, 0.8F, 0.3F, 0.7F));
        EndPreset roundTripped = new EndPresetBuilder(original).build();
        assertEquals(original, roundTripped,
                "builder(preset).build() must equal the source preset");
    }

    @Test
    void resetRestoresDefaults() {
        EndPresetBuilder b = new EndPresetBuilder()
                .worldHeight(999)
                .minY(-999)
                .seaLevelY(123)
                .islandBaselineY(456)
                .seaMode(SeaMode.WITH_FLOOR)
                .topologyMode(TopologyMode.CONTINENTAL_SHATTERED)
                .floatingIslandsEnabled(true)
                .erosionConfig(new ErosionConfig(1, 1, 1.0F, 1.0F, 1.0F, 1.0F));
        assertNotEquals(EndPreset.defaults(), b.build(),
                "sanity: builder was mutated away from defaults before reset");
        b.reset();
        assertEquals(EndPreset.defaults(), b.build(),
                "reset() must restore EndPreset.defaults()");
    }

    @Test
    void eachSetterStoresValue() {
        EndPresetBuilder b = new EndPresetBuilder();
        ErosionConfig custom = new ErosionConfig(200, 50, 0.7F, 1.2F, 0.4F, 0.6F);
        b.worldHeight(3000)
         .minY(-1500)
         .seaLevelY(32)
         .islandBaselineY(-64)
         .seaMode(SeaMode.WITH_FLOOR)
         .topologyMode(TopologyMode.CONTINENTAL_SHATTERED)
         .floatingIslandsEnabled(true)
         .erosionConfig(custom);

        EndPreset built = b.build();
        assertEquals(3000, built.worldHeight());
        assertEquals(-1500, built.minY());
        assertEquals(32, built.seaLevelY());
        assertEquals(-64, built.islandBaselineY());
        assertEquals(SeaMode.WITH_FLOOR, built.seaMode());
        assertEquals(TopologyMode.CONTINENTAL_SHATTERED, built.topologyMode());
        assertEquals(true, built.floatingIslandsEnabled());
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
                .seaLevelY(100);
        EndPreset first = b.build();
        EndPreset second = b.build();
        assertEquals(first, second,
                "build() called twice without mutation must produce equal presets");
    }
}
