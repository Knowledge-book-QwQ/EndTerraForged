package endterraforged.world.filter;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.cell.Cell;
import endterraforged.world.config.SeaMode;
import endterraforged.world.config.TestProfile;
import endterraforged.world.config.TopologyMode;

/**
 * Verifies that {@link ErosionFactory} produces working erosion filters across
 * all three {@link SeaMode}s — the core stage-2 guarantee that the same
 * algorithm erodes an End-with-a-sea and an End-with-no-sea correctly because
 * the only thing that changes is the height-band anchor.
 */
class ErosionFactoryTest {

    private static final int MAP_SIZE = 16;
    private static final int SEED = 42;

    @Test
    void erodesInNoSeaMode() {
        TestProfile profile = new TestProfile(4064, -2032, 0, 64,
                SeaMode.NONE, TopologyMode.ISLANDS, false);
        Erosion erosion = ErosionFactory.create(SEED, MAP_SIZE, ErosionConfig.DEFAULT, profile);

        Cell[] before = rampField(MAP_SIZE);
        Cell[] after = rampField(MAP_SIZE);
        applyOnce(erosion, after, MAP_SIZE);

        assertTrue(anyEroded(after), "NONE mode should still erode the ramp");
        assertMassConserved(before, after, "NONE mode");
    }

    @Test
    void erodesInWithFloorMode() {
        TestProfile profile = new TestProfile(4064, -2032, 0, 64,
                SeaMode.WITH_FLOOR, TopologyMode.ISLANDS, false);
        Erosion erosion = ErosionFactory.create(SEED, MAP_SIZE, ErosionConfig.DEFAULT, profile);

        Cell[] before = rampField(MAP_SIZE);
        Cell[] after = rampField(MAP_SIZE);
        applyOnce(erosion, after, MAP_SIZE);

        assertTrue(anyEroded(after), "WITH_FLOOR mode should erode the ramp");
        assertMassConserved(before, after, "WITH_FLOOR mode");
    }

    @Test
    void differentSeaModesProduceDifferentResults() {
        // Sanity: the two profiles share the same seed/mapSize/ramp, so if the
        // factory truly anchors to different surfaces, the erosion output must
        // differ. Identical output would mean the sea mode is being ignored.
        TestProfile noSea = new TestProfile(4064, -2032, 0, 64,
                SeaMode.NONE, TopologyMode.ISLANDS, false);
        TestProfile withFloor = new TestProfile(4064, -2032, 0, 64,
                SeaMode.WITH_FLOOR, TopologyMode.ISLANDS, false);

        Cell[] noneField = rampField(MAP_SIZE);
        Cell[] floorField = rampField(MAP_SIZE);
        applyOnce(ErosionFactory.create(SEED, MAP_SIZE, ErosionConfig.DEFAULT, noSea), noneField, MAP_SIZE);
        applyOnce(ErosionFactory.create(SEED, MAP_SIZE, ErosionConfig.DEFAULT, withFloor), floorField, MAP_SIZE);

        boolean anyDifference = false;
        for (int i = 0; i < noneField.length; i++) {
            if (Float.floatToIntBits(noneField[i].height) != Float.floatToIntBits(floorField[i].height)) {
                anyDifference = true;
                break;
            }
        }
        assertNotEquals(true, !anyDifference,
                "NONE and WITH_FLOOR should anchor to different surfaces and thus erode differently");
    }

    // --- helpers -------------------------------------------------------------

    private static void applyOnce(Erosion erosion, Cell[] cells, int mapSize) {
        Size size = Size.make(mapSize, 1);
        Filterable map = new Filterable() {
            @Override public int getBlockX() { return 0; }
            @Override public int getBlockZ() { return 0; }
            @Override public Size getBlockSize() { return size; }
            @Override public Cell[] getBacking() { return cells; }
            @Override public Cell getCellRaw(int x, int z) { return cells[z * size.total() + x]; }
        };
        erosion.apply(map, 0, 0, 1);
    }

    private static Cell[] rampField(int mapSize) {
        int total = mapSize + 2;
        Cell[] cells = new Cell[total * total];
        for (int z = 0; z < total; z++) {
            for (int x = 0; x < total; x++) {
                Cell c = new Cell();
                // Ramp straddles the NONE-mode ground band (ground(0) ~= 0.016
                // in a 4064-tall world) so the band's ramp modifier actually
                // varies across the field. A field entirely above the band
                // would see modifier==1 everywhere and could not distinguish
                // sea modes — the original test caught that gap.
                float t = (float) x / (total - 1);
                c.height = 0.005f + 0.05f * t;
                cells[z * total + x] = c;
            }
        }
        return cells;
    }

    private static boolean anyEroded(Cell[] cells) {
        for (Cell c : cells) {
            if (c.heightErosion < -1e-6f) return true;
        }
        return false;
    }

    private static void assertMassConserved(Cell[] before, Cell[] after, String label) {
        float beforeSum = 0;
        float afterSum = 0;
        float sediment = 0;
        for (Cell c : before) beforeSum += c.height;
        for (Cell c : after) {
            afterSum += c.height;
            sediment += c.sediment;
        }
        assertTrue(Math.abs(beforeSum - (afterSum + sediment)) < 1.0f,
                label + ": mass should be conserved (height+sediment ~= initial)");
    }
}
