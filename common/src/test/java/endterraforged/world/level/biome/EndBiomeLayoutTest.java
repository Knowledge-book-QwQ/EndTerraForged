package endterraforged.world.level.biome;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.BiomeLayoutConfig;
import endterraforged.world.config.BiomeVariantBlendConfig;

class EndBiomeLayoutTest {

    @Test
    void defaultWarpStrengthPreservesLegacyRingLayout() {
        EndBiomeLayout legacy = new BiomeLayoutConfig(18, 8.0F, 40.0F, 0.0F,
                200, 4, 2.0F, 0.5F, 15.0F,
                400, 4, 0.2F).buildRuntime();
        EndBiomeLayout explicitDefaultWarp = new BiomeLayoutConfig(18, 8.0F, 40.0F, 0.0F,
                200, 4, 2.0F, 0.5F, 15.0F,
                300, 0.0F,
                400, 4, 0.2F).buildRuntime();

        for (int z = -96; z <= 96; z += 8) {
            for (int x = -96; x <= 96; x += 8) {
                assertSame(legacy.ringAt(x, z), explicitDefaultWarp.ringAt(x, z));
                assertSame(legacy.ringAt(x, z), BiomeLayoutConfig.DEFAULT.buildRuntime().ringAt(x, z));
            }
        }
    }

    @Test
    void configuredWarpChangesSomeRingSamples() {
        EndBiomeLayout defaultLayout = BiomeLayoutConfig.DEFAULT.buildRuntime();
        EndBiomeLayout warped = new BiomeLayoutConfig(18, 8.0F, 40.0F, 0.0F,
                200, 4, 2.0F, 0.5F, 15.0F,
                80, 80.0F,
                400, 4, 0.2F).buildRuntime();

        boolean changed = false;
        for (int z = -160; z <= 160 && !changed; z += 4) {
            for (int x = -160; x <= 160; x += 4) {
                if (defaultLayout.ringAt(x, z) != warped.ringAt(x, z)) {
                    changed = true;
                    break;
                }
            }
        }

        assertTrue(changed, "configured biome warp should change some ring samples");
    }

    @Test
    void configuredVariantBlendChangesFractionSamples() {
        EndBiomeLayout defaults = BiomeLayoutConfig.DEFAULT.buildRuntime();
        EndBiomeLayout coarseBlend = new BiomeLayoutConfig(18, 8.0F, 40.0F, 0.0F,
                200, 4, 2.0F, 0.5F, 15.0F,
                300, 0.0F,
                400, 4, 0.2F,
                new BiomeVariantBlendConfig(180, 1)).buildRuntime();

        boolean changed = false;
        for (int z = -64; z <= 64 && !changed; z += 8) {
            for (int x = -64; x <= 64; x += 8) {
                if (Float.floatToIntBits(defaults.fracX(x, z))
                        != Float.floatToIntBits(coarseBlend.fracX(x, z))
                        || Float.floatToIntBits(defaults.fracZ(x, z))
                        != Float.floatToIntBits(coarseBlend.fracZ(x, z))) {
                    changed = true;
                    break;
                }
            }
        }

        assertTrue(changed, "variant blend config should change fractional selector samples");
    }
}
