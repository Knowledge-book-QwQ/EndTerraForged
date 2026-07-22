package endterraforged.world.continent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EndCentralRegionPolicyTest {

    @Test
    void protectedRadiusIncludesItsBoundary() {
        assertTrue(EndCentralRegionPolicy.usesVanillaDensity(0, 0));
        assertTrue(EndCentralRegionPolicy.usesVanillaDensity(
                EndCentralRegionPolicy.VANILLA_RADIUS_BLOCKS, 0));
        assertFalse(EndCentralRegionPolicy.usesVanillaDensity(
                EndCentralRegionPolicy.VANILLA_RADIUS_BLOCKS + 1, 0));
    }

    @Test
    void outerActivationIsDeterministicAndBounded() {
        assertEquals(0.0F, EndCentralRegionPolicy.outerActivation(0.0F, 0.0F));
        assertEquals(0.0F, EndCentralRegionPolicy.outerActivation(
                EndCentralRegionPolicy.VANILLA_RADIUS_BLOCKS, 0.0F));
        assertEquals(1.0F, EndCentralRegionPolicy.outerActivation(
                EndCentralRegionPolicy.OUTER_TERRAIN_RADIUS_BLOCKS, 0.0F));
        float transition = EndCentralRegionPolicy.outerActivation(
                EndCentralRegionPolicy.VANILLA_RADIUS_BLOCKS + 256.0F, 0.0F);
        assertTrue(transition > 0.0F && transition < 1.0F);
        assertEquals(transition, EndCentralRegionPolicy.outerActivation(
                EndCentralRegionPolicy.VANILLA_RADIUS_BLOCKS + 256.0F, 0.0F));
    }
}
