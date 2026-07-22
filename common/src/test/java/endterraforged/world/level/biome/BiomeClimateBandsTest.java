package endterraforged.world.level.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BiomeClimateBandsTest {

    @Test
    void temperatureBandsCoverUnitIntervalInOrder() {
        assertBandsCoverUnitInterval(BiomeClimateBands.Temperature.values());
    }

    @Test
    void moistureBandsCoverUnitIntervalInOrder() {
        assertBandsCoverUnitInterval(BiomeClimateBands.Moisture.values());
    }

    @Test
    void temperatureBandsUseClosedRangeSemantics() {
        assertTrue(BiomeClimateBands.Temperature.FROZEN.contains(0.0F));
        assertTrue(BiomeClimateBands.Temperature.FROZEN.contains(0.2F));
        assertTrue(BiomeClimateBands.Temperature.COLD.contains(0.2F));
        assertFalse(BiomeClimateBands.Temperature.FROZEN.contains(0.2001F));
        assertTrue(BiomeClimateBands.Temperature.HOT.contains(1.0F));
    }

    @Test
    void moistureBandsUseClosedRangeSemantics() {
        assertTrue(BiomeClimateBands.Moisture.ARID.contains(0.0F));
        assertTrue(BiomeClimateBands.Moisture.ARID.contains(0.2F));
        assertTrue(BiomeClimateBands.Moisture.DRY.contains(0.2F));
        assertFalse(BiomeClimateBands.Moisture.ARID.contains(0.2001F));
        assertTrue(BiomeClimateBands.Moisture.SATURATED.contains(1.0F));
    }

    private static void assertBandsCoverUnitInterval(BiomeClimateBands.Temperature[] bands) {
        assertEquals(0.0F, bands[0].min());
        for (int i = 0; i < bands.length - 1; i++) {
            assertEquals(bands[i].max(), bands[i + 1].min());
        }
        assertEquals(1.0F, bands[bands.length - 1].max());
    }

    private static void assertBandsCoverUnitInterval(BiomeClimateBands.Moisture[] bands) {
        assertEquals(0.0F, bands[0].min());
        for (int i = 0; i < bands.length - 1; i++) {
            assertEquals(bands[i].max(), bands[i + 1].min());
        }
        assertEquals(1.0F, bands[bands.length - 1].max());
    }
}
