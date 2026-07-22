package endterraforged.world.continent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContinentSignalBufferTest {

    @Test
    void valueTransformsPreserveIdentityUntilExplicitlyCleared() {
        ContinentSignalBuffer buffer = new ContinentSignalBuffer();
        buffer.setIdentified(0.8F, 0.7F, 0.6F, 42L, 1200, -3400);

        buffer.setValues(0.5F, 0.4F, 0.3F);
        assertTrue(buffer.identified());
        assertEquals(42L, buffer.continentId());
        assertEquals(1200, buffer.centerX());
        assertEquals(-3400, buffer.centerZ());

        buffer.scale(0.5F);
        assertTrue(buffer.identified());
        assertEquals(0.25F, buffer.edge(), 0.0F);
        assertEquals(0.2F, buffer.landness(), 0.0F);
        assertEquals(0.15F, buffer.inlandness(), 0.0F);
        assertEquals(42L, buffer.continentId());
    }

    @Test
    void zeroActivationAndLegacySetClearStaleIdentity() {
        ContinentSignalBuffer buffer = new ContinentSignalBuffer();
        buffer.setIdentified(1.0F, 1.0F, 1.0F, 99L, 10, 20);
        buffer.scale(0.0F);
        assertFalse(buffer.identified());
        assertEquals(0L, buffer.continentId());

        buffer.setIdentified(1.0F, 1.0F, 1.0F, 100L, 30, 40);
        buffer.set(0.2F, 0.1F, 1.0F);
        assertFalse(buffer.identified());
        assertEquals(0L, buffer.continentId());
        assertEquals(0, buffer.centerX());
        assertEquals(0, buffer.centerZ());
    }

    @Test
    void snapshotRetainsPrimitiveIdentityWithoutSharingMutableState() {
        ContinentSignalBuffer buffer = new ContinentSignalBuffer();
        buffer.setIdentified(0.9F, 0.8F, 0.7F, 123L, -500, 750);
        ContinentSignals snapshot = buffer.snapshot();
        buffer.set(0.0F, 0.0F, 0.0F);

        assertTrue(snapshot.identified());
        assertEquals(123L, snapshot.continentId());
        assertEquals(-500, snapshot.centerX());
        assertEquals(750, snapshot.centerZ());
        assertEquals(0.8F, snapshot.landness(), 0.0F);
    }
}
