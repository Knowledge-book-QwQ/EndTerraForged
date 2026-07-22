package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.EndPreset;

class EndArchipelagoReliefTest {

    @Test
    void disabledFeatureReturnsTheReferenceSurface() {
        EndLevels levels = new EndLevels(EndPreset.defaults());
        EndArchipelagoSignalBuffer signals = new EndArchipelagoSignalBuffer();

        assertEquals(levels.surface, EndArchipelagoRelief.DISABLED.top(
                1024.0F, -512.0F, levels, signals), 0.0F);
    }

    @Test
    void reliefIsFiniteAndGrowsOnlyInsideTheInlandEnvelope() {
        EndLevels levels = new EndLevels(EndPreset.defaults());
        EndArchipelagoRelief relief = new EndArchipelagoRelief(123456789, 3000);
        EndArchipelagoSignalBuffer coast = new EndArchipelagoSignalBuffer();
        EndArchipelagoSignalBuffer inland = new EndArchipelagoSignalBuffer();
        coast.set(0.20F, 0.30F, EndCoastBands.inlandness(0.20F),
                EndCoastBands.reliefWeight(0.20F), 1, 1);
        inland.set(0.90F, 0.90F, EndCoastBands.inlandness(0.90F),
                EndCoastBands.reliefWeight(0.90F), 1, 1);

        float coastTop = relief.top(2048.0F, -4096.0F, levels, coast);
        float inlandTop = relief.top(2048.0F, -4096.0F, levels, inland);

        assertTrue(coastTop >= levels.surface);
        assertTrue(inlandTop >= coastTop);
        assertTrue(inlandTop <= levels.surface + levels.elevationRange * 0.34F);
    }
}
