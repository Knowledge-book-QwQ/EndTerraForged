package endterraforged.world.cave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.AbyssPitConfig;
import endterraforged.world.config.CaveChamberConfig;
import endterraforged.world.config.CaveNetworkConfig;
import endterraforged.world.config.CaveSystemConfig;
import endterraforged.world.config.CaveTunnelConfig;
import endterraforged.world.config.SubsurfaceConfig;

class EndCavePreviewMaskTest {

    private static final int SEED = 91;

    @Test
    void disabledConfigHasNoCaveFootprint() {
        EndCavePreviewMask mask = SubsurfaceConfig.DISABLED.buildCavePreviewMask(SEED);

        assertEquals(0.0F, mask.strength(0.0F, 0.0F, 1.0F), 0.0F);
        assertEquals(0L, signature(mask, 1.0F));
    }

    @Test
    void disabledSystemIgnoresAggressiveNetworkAndChambers() {
        SubsurfaceConfig config = config(false, 2400,
                1.0F, 1.0F, 1.0F, 0.0F);
        EndCavePreviewMask mask = config.buildCavePreviewMask(SEED);

        assertEquals(0.0F, mask.strength(128.0F, 512.0F, 1.0F), 0.0F);
        assertEquals(0L, signature(mask, 1.0F));
    }

    @Test
    void minLandnessGatesCaveFootprint() {
        EndCavePreviewMask mask = config(true, 2400,
                0.9F, 0.9F, 0.9F, 0.55F).buildCavePreviewMask(SEED);

        assertEquals(0.0F, mask.strength(128.0F, 512.0F, 0.54F), 0.0F);
        assertEquals(0L, signature(mask, 0.2F));
    }

    @Test
    void caveFootprintIsDeterministic() {
        EndCavePreviewMask mask = config(true, 2400,
                0.8F, 0.75F, 0.9F, 0.0F).buildCavePreviewMask(SEED);

        assertEquals(signature(mask, 1.0F), signature(mask, 1.0F));
    }

    @Test
    void seedOffsetChangesCaveFootprint() {
        EndCavePreviewMask base = config(true, 2400,
                0.8F, 0.75F, 0.9F, 0.0F).buildCavePreviewMask(SEED);
        EndCavePreviewMask shifted = config(true, 2600,
                0.8F, 0.75F, 0.9F, 0.0F).buildCavePreviewMask(SEED);

        assertNotEquals(signature(base, 1.0F), signature(shifted, 1.0F));
    }

    @Test
    void caveParametersChangeFootprint() {
        EndCavePreviewMask sparse = config(true, 2400,
                0.15F, 0.15F, 0.1F, 0.0F).buildCavePreviewMask(SEED);
        EndCavePreviewMask spectacular = config(true, 2400,
                0.9F, 0.9F, 0.95F, 0.0F).buildCavePreviewMask(SEED);

        assertNotEquals(signature(sparse, 1.0F), signature(spectacular, 1.0F));
    }

    @Test
    void caveFootprintExposesChamberAndNetworkChannels() {
        EndCavePreviewMask mask = config(true, 2400,
                0.9F, 0.9F, 0.95F, 0.0F).buildCavePreviewMask(SEED);

        int chamberSamples = 0;
        int networkSamples = 0;
        for (int z = 0; z < 24; z++) {
            for (int x = 0; x < 24; x++) {
                float worldX = (x - 12) * 73.0F;
                float worldZ = (z - 12) * 91.0F;
                float chamber = mask.chamberStrength(worldX, worldZ, 1.0F);
                float network = mask.networkStrength(worldX, worldZ, 1.0F);

                if (chamber > 0.0F) {
                    chamberSamples++;
                }
                if (network > 0.0F) {
                    networkSamples++;
                }
                assertEquals(Math.max(chamber, network),
                        mask.strength(worldX, worldZ, 1.0F), 0.0F);
            }
        }

        assertTrue(chamberSamples > 0, "chamber channel must produce visible samples");
        assertTrue(networkSamples > 0, "network channel must produce visible samples");
    }

    private static SubsurfaceConfig config(boolean enabled, int seedOffset,
                                           float chamberProbability,
                                           float networkDensity,
                                           float spectacleBias,
                                           float minLandness) {
        return new SubsurfaceConfig(
                AbyssPitConfig.DISABLED,
                CaveTunnelConfig.DISABLED,
                new CaveSystemConfig(enabled, seedOffset, 128, 1536,
                        spectacleBias, 0.8F, 0.03F),
                new CaveNetworkConfig(512, networkDensity, 160,
                        3.0F, 0.5F, 0.45F, minLandness),
                new CaveChamberConfig(chamberProbability, 64, 256,
                        1.6F, 0.35F, 0.6F));
    }

    private static long signature(EndCavePreviewMask mask, float landness) {
        long hash = 0L;
        for (int z = 0; z < 24; z++) {
            for (int x = 0; x < 24; x++) {
                float worldX = (x - 12) * 73.0F;
                float worldZ = (z - 12) * 91.0F;
                hash = hash * 31 + Float.floatToIntBits(mask.strength(worldX, worldZ, landness));
            }
        }
        return hash;
    }
}
