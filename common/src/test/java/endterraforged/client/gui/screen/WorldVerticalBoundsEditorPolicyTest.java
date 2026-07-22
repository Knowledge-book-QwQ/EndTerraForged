package endterraforged.client.gui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.WorldVerticalBounds;

class WorldVerticalBoundsEditorPolicyTest {

    @Test
    void standardLimitsProduceTheExactDisplayedMaximum() {
        WorldVerticalBounds bounds =
                WorldVerticalBoundsEditorPolicy.fromInclusiveLimits(-256, 255);

        assertEquals(-256, bounds.minY());
        assertEquals(512, bounds.height());
        assertEquals(255, bounds.maxYInclusive());
    }

    @Test
    void epicLimitsUseTheFullVanillaEnvelope() {
        WorldVerticalBounds bounds =
                WorldVerticalBoundsEditorPolicy.fromInclusiveLimits(-2032, 2031);

        assertEquals(4064, bounds.height());
        assertEquals(2031, bounds.maxYInclusive());
    }

    @Test
    void changingOneLimitPreservesTheOtherExactly() {
        WorldVerticalBounds standard = new WorldVerticalBounds(-256, 512);

        WorldVerticalBounds raisedTop =
                WorldVerticalBoundsEditorPolicy.withMaximumY(standard, 1023);
        WorldVerticalBounds loweredBottom =
                WorldVerticalBoundsEditorPolicy.withMinimumY(raisedTop, -512);

        assertEquals(-256, raisedTop.minY());
        assertEquals(1023, raisedTop.maxYInclusive());
        assertEquals(-512, loweredBottom.minY());
        assertEquals(1023, loweredBottom.maxYInclusive());
    }

    @Test
    void rejectsValuesMinecraftWouldRoundOrReject() {
        assertThrows(IllegalArgumentException.class,
                () -> WorldVerticalBoundsEditorPolicy.fromInclusiveLimits(-255, 1023));
        assertThrows(IllegalArgumentException.class,
                () -> WorldVerticalBoundsEditorPolicy.fromInclusiveLimits(-256, 2000));
        assertThrows(IllegalArgumentException.class,
                () -> WorldVerticalBoundsEditorPolicy.fromInclusiveLimits(-2048, 2031));
        assertThrows(IllegalArgumentException.class,
                () -> WorldVerticalBoundsEditorPolicy.fromInclusiveLimits(-256, 2047));
    }
}
