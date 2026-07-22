package endterraforged.world.cave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.AbyssPitConfig;
import endterraforged.world.config.CaveChamberConfig;
import endterraforged.world.config.CaveNetworkConfig;
import endterraforged.world.config.CaveSystemConfig;
import endterraforged.world.config.CaveTunnelConfig;
import endterraforged.world.config.SubsurfaceConfig;

class EndCaveGraphPreviewMaskTest {

    private static final int SEED = 123;

    @Test
    void disabledConfigHasNoGraphPreviewStrength() {
        EndCaveGraphPreviewMask mask = EndCaveGraphPreviewMask.fromConfig(
                SubsurfaceConfig.DISABLED, SEED);

        assertFalse(mask.enabled());
        assertEquals(0.0F, mask.strength(0.0F, 0.0F, 1.0F), 0.0F);
        assertEquals(0.0F, mask.riftStrength(0.0F, 0.0F, 1.0F), 0.0F);
        assertEquals(0.0F, mask.flowStrength(0.0F, 0.0F, 1.0F), 0.0F);
        assertEquals(0.0F, mask.waterCandidateStrength(0.0F, 0.0F, 1.0F), 0.0F);
        assertEquals(0.0F, mask.lavaCandidateStrength(0.0F, 0.0F, 1.0F), 0.0F);
    }

    @Test
    void graphPreviewExposesRiftAndFlowChannels() {
        EndCaveGraphPreviewMask mask = EndCaveGraphPreviewMask.fromConfig(config(), SEED);
        int riftSamples = 0;
        int flowSamples = 0;
        int waterSamples = 0;
        int lavaSamples = 0;

        for (int z = -24; z <= 24; z++) {
            for (int x = -24; x <= 24; x++) {
                float worldX = x * 53.0F;
                float worldZ = z * 59.0F;
                float rift = mask.riftStrength(worldX, worldZ, 1.0F);
                float flow = mask.flowStrength(worldX, worldZ, 1.0F);
                float water = mask.waterCandidateStrength(worldX, worldZ, 1.0F);
                float lava = mask.lavaCandidateStrength(worldX, worldZ, 1.0F);

                if (rift > 0.0F) {
                    riftSamples++;
                }
                if (flow > 0.0F) {
                    flowSamples++;
                }
                if (water > 0.0F) {
                    waterSamples++;
                }
                if (lava > 0.0F) {
                    lavaSamples++;
                }
                assertEquals(Math.max(rift, flow), mask.strength(worldX, worldZ, 1.0F),
                        0.0F);
            }
        }

        assertTrue(riftSamples > 0, "rift preview channel must produce visible samples");
        assertTrue(flowSamples > 0, "flow preview channel must produce visible samples");
        assertTrue(waterSamples > 0, "water candidate preview channel must produce visible samples");
        assertTrue(lavaSamples > 0, "lava candidate preview channel must produce visible samples");
    }

    @Test
    void minLandnessGatesGraphPreview() {
        EndCaveGraphPreviewMask mask = EndCaveGraphPreviewMask.fromConfig(config(), SEED);

        assertEquals(0.0F, mask.strength(128.0F, 512.0F, 0.59F), 0.0F);
        assertEquals(0.0F, mask.riftStrength(128.0F, 512.0F, 0.59F), 0.0F);
        assertEquals(0.0F, mask.flowStrength(128.0F, 512.0F, 0.59F), 0.0F);
        assertEquals(0.0F, mask.waterCandidateStrength(128.0F, 512.0F, 0.59F), 0.0F);
        assertEquals(0.0F, mask.lavaCandidateStrength(128.0F, 512.0F, 0.59F), 0.0F);
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyLiquidStrengthNamesForwardToCandidateMasks() {
        EndCaveGraphPreviewMask mask = EndCaveGraphPreviewMask.fromConfig(config(), SEED);
        float x = 384.0F;
        float z = -192.0F;
        float landness = 1.0F;

        assertEquals(mask.waterCandidateStrength(x, z, landness),
                mask.waterStrength(x, z, landness), 0.0F);
        assertEquals(mask.lavaCandidateStrength(x, z, landness),
                mask.lavaStrength(x, z, landness), 0.0F);
    }

    private static SubsurfaceConfig config() {
        return new SubsurfaceConfig(
                AbyssPitConfig.DISABLED,
                CaveTunnelConfig.DISABLED,
                new CaveSystemConfig(true, 2400, 96, 768,
                        1.0F, 1.0F, 0.0F),
                new CaveNetworkConfig(384, 0.95F, 128,
                        4.0F, 1.0F, 0.45F, 0.6F),
                new CaveChamberConfig(0.95F, 96, 384,
                        2.2F, 0.35F, 0.7F));
    }
}
