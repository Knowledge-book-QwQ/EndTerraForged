package endterraforged.world.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.cell.Cell;

/**
 * Verifies the hydraulic droplet erosion invariants on a small synthetic
 * height-field. Tests use a deterministic ramp so that "downhill" and
 * "uphill" are unambiguous, and a {@link #noopModifier()} so the filter's
 * raw effect on {@code cell.height} is observable without height-band scaling.
 */
class ErosionTest {

    private static final int MAP_SIZE = 16;
    private static final int SEED = 42;

    @Test
    void erosionLowersHeightsAndRaisesSediment() {
        Cell[] cells = rampField(MAP_SIZE, 0.2f, 0.8f);
        Erosion erosion = new Erosion(SEED, MAP_SIZE, ErosionConfig.DEFAULT, noopModifier());

        applyOnce(erosion, cells, MAP_SIZE);

        float totalHeightBefore = sumHeight(rampField(MAP_SIZE, 0.2f, 0.8f));
        float totalHeightAfter = sumHeight(cells);
        // mass conservation: height lost to erosion shows up as sediment + height
        // gained from deposition; net height must drop somewhere (erosion dominant
        // on a ramp) but total (height+sediment) is preserved by the algorithm
        float totalSediment = sumSediment(cells);
        float totalAfter = totalHeightAfter + totalSediment;
        // droplets move mass around but cannot create it; allow tiny float slack
        assertEquals(totalHeightBefore, totalAfter, 1.0f,
                "mass should be conserved (height+sediment ~= initial height)");
        // at least one cell should have been eroded (heightErosion < 0)
        assertTrue(anyEroded(cells), "expected at least one eroded cell on a ramp");
    }

    @Test
    void sameSeedProducesSameResult() {
        Cell[] a = rampField(MAP_SIZE, 0.2f, 0.8f);
        Cell[] b = rampField(MAP_SIZE, 0.2f, 0.8f);
        Erosion erosion = new Erosion(SEED, MAP_SIZE, ErosionConfig.DEFAULT, noopModifier());

        applyOnce(erosion, a, MAP_SIZE);
        applyOnce(erosion, b, MAP_SIZE);

        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i].height, b[i].height, 1e-5f, "height diverged at " + i);
            assertEquals(a[i].sediment, b[i].sediment, 1e-5f, "sediment diverged at " + i);
        }
    }

    @Test
    void erosionMaskProtectsCell() {
        Cell[] cells = rampField(MAP_SIZE, 0.2f, 0.8f);
        // mark the whole field protected: nothing should change
        for (Cell c : cells) {
            c.erosionMask = true;
        }
        float[] before = snapshotHeight(cells);
        Erosion erosion = new Erosion(SEED, MAP_SIZE, ErosionConfig.DEFAULT, noopModifier());

        applyOnce(erosion, cells, MAP_SIZE);

        for (int i = 0; i < cells.length; i++) {
            assertEquals(before[i], cells[i].height, 0.0f,
                    "masked cell " + i + " was modified");
        }
    }

    // --- helpers -------------------------------------------------------------

    private static Modifier noopModifier() {
        return v -> 1.0F;
    }

    /** Builds a Filterable over the given cell array with a 1-block border. */
    private static Filterable filterable(Cell[] cells, int mapSize) {
        Size size = Size.make(mapSize, 1);
        return new Filterable() {
            @Override
            public int getBlockX() {
                return 0;
            }

            @Override
            public int getBlockZ() {
                return 0;
            }

            @Override
            public Size getBlockSize() {
                return size;
            }

            @Override
            public Cell[] getBacking() {
                return cells;
            }

            @Override
            public Cell getCellRaw(int x, int z) {
                return cells[z * size.total() + x];
            }
        };
    }

    private static void applyOnce(Erosion erosion, Cell[] cells, int mapSize) {
        erosion.apply(filterable(cells, mapSize), 0, 0, 1);
    }

    /** A smooth ramp rising along X so droplets flow downhill toward x=0. */
    private static Cell[] rampField(int mapSize, float low, float high) {
        // include a 1-block border to match Size.make(mapSize, 1)
        int total = mapSize + 2;
        Cell[] cells = new Cell[total * total];
        for (int z = 0; z < total; z++) {
            for (int x = 0; x < total; x++) {
                Cell c = new Cell();
                float t = (float) x / (total - 1);
                c.height = low + (high - low) * t;
                cells[z * total + x] = c;
            }
        }
        return cells;
    }

    private static float sumHeight(Cell[] cells) {
        float s = 0;
        for (Cell c : cells) {
            s += c.height;
        }
        return s;
    }

    private static float sumSediment(Cell[] cells) {
        float s = 0;
        for (Cell c : cells) {
            s += c.sediment;
        }
        return s;
    }

    private static boolean anyEroded(Cell[] cells) {
        for (Cell c : cells) {
            if (c.heightErosion < -1e-6f) {
                return true;
            }
        }
        return false;
    }

    private static float[] snapshotHeight(Cell[] cells) {
        float[] out = new float[cells.length];
        for (int i = 0; i < cells.length; i++) {
            out[i] = cells[i].height;
        }
        return out;
    }
}
