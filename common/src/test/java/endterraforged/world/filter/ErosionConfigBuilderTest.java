package endterraforged.world.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Contract tests for {@link ErosionConfigBuilder}: the GUI erosion sub-editor's
 * mutable editing state. Mirrors {@code EndPresetBuilderTest}'s coverage:
 * round-trip fidelity ({@code builder(config).build() == config}),
 * {@link ErosionConfigBuilder#reset()} restores defaults, each setter stores
 * the value, and the no-arg constructor starts at defaults.
 *
 * <p>These tests are the testable core behind the (sandbox-untestable)
 * ErosionConfigEditorScreen — the screen binds sliders to the builder's
 * setters, and {@link ErosionConfigBuilder#build()} snapshots state into an
 * immutable {@link ErosionConfig} that the parent EndPresetEditorScreen then
 * embeds into the {@link endterraforged.world.config.EndPreset}.</p>
 */
class ErosionConfigBuilderTest {

    @Test
    void defaultConstructorStartsAtDefaults() {
        ErosionConfigBuilder b = new ErosionConfigBuilder();
        assertEquals(ErosionConfig.DEFAULT, b.build(),
                "no-arg constructor must initialise to ErosionConfig.DEFAULT");
    }

    @Test
    void roundTripFromConfigIsIdentity() {
        ErosionConfig original = new ErosionConfig(256, 64, 1.5F, 0.8F, 0.3F, 0.7F);
        ErosionConfig roundTripped = new ErosionConfigBuilder(original).build();
        assertEquals(original, roundTripped,
                "builder(config).build() must equal the source config");
    }

    @Test
    void resetRestoresDefaults() {
        ErosionConfigBuilder b = new ErosionConfigBuilder()
                .dropletsPerChunk(999)
                .dropletLifetime(99)
                .dropletVolume(0.1F)
                .dropletVelocity(0.2F)
                .erosionRate(0.9F)
                .depositRate(0.95F);
        assertNotEquals(ErosionConfig.DEFAULT, b.build(),
                "sanity: builder was mutated away from defaults before reset");
        b.reset();
        assertEquals(ErosionConfig.DEFAULT, b.build(),
                "reset() must restore ErosionConfig.DEFAULT");
    }

    @Test
    void eachSetterStoresValue() {
        ErosionConfigBuilder b = new ErosionConfigBuilder();
        b.dropletsPerChunk(512)
         .dropletLifetime(128)
         .dropletVolume(2.0F)
         .dropletVelocity(1.5F)
         .erosionRate(0.6F)
         .depositRate(0.4F);

        ErosionConfig built = b.build();
        assertEquals(512, built.dropletsPerChunk);
        assertEquals(128, built.dropletLifetime);
        assertEquals(2.0F, built.dropletVolume, 1e-6F);
        assertEquals(1.5F, built.dropletVelocity, 1e-6F);
        assertEquals(0.6F, built.erosionRate, 1e-6F);
        assertEquals(0.4F, built.depositRate, 1e-6F);
    }

    @Test
    void gettersReflectCurrentState() {
        ErosionConfigBuilder b = new ErosionConfigBuilder()
                .dropletsPerChunk(64)
                .erosionRate(0.25F);
        assertEquals(64, b.dropletsPerChunk());
        assertEquals(0.25F, b.erosionRate(), 1e-6F);
        // Unchanged fields still at defaults.
        ErosionConfig d = ErosionConfig.DEFAULT;
        assertEquals(d.dropletLifetime, b.dropletLifetime());
        assertEquals(d.depositRate, b.depositRate(), 1e-6F);
    }

    @Test
    void buildProducesConsistentConfigEachCall() {
        ErosionConfigBuilder b = new ErosionConfigBuilder()
                .dropletsPerChunk(200)
                .erosionRate(0.5F);
        ErosionConfig first = b.build();
        ErosionConfig second = b.build();
        assertEquals(first, second,
                "build() called twice without mutation must produce equal configs");
    }

    @Test
    void buildRejectsInvalidState() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new ErosionConfigBuilder()
                        .erosionRate(1.5F)
                        .build());
        assertTrue(e.getMessage().contains("erosion_rate"));
    }

    @Test
    void settersAreFluentAndChainable() {
        // Verifies the builder pattern: each setter returns `this` so the
        // GUI can chain them in a fluent style. Also catches accidental
        // return-type mistakes (e.g. returning ErosionConfig instead of
        // ErosionConfigBuilder from a setter).
        ErosionConfigBuilder b = new ErosionConfigBuilder();
        ErosionConfigBuilder same = b
                .dropletsPerChunk(100)
                .dropletLifetime(50)
                .dropletVolume(1.0F)
                .dropletVelocity(1.0F)
                .erosionRate(0.5F)
                .depositRate(0.5F);
        assertSame(b, same, "fluent setters must return the same builder instance");
    }
}
