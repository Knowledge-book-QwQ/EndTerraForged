package endterraforged.world.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

class TerrainRegionLayoutTest {

    private static final int SEED = 123456789;

    @Test
    void rejectsInvalidCatalogsBeforeSampling() {
        assertThrows(IllegalArgumentException.class, () ->
                new TerrainRegionLayout(SEED, 900, 80.0F, List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new TerrainRegionLayout(SEED, 900, 80.0F, List.of(
                        TerrainRegionEntry.area(0, TerrainRegionFamily.PLAINS, 1.0F, 1000),
                        TerrainRegionEntry.area(0, TerrainRegionFamily.HILLS, 1.0F, 1000))));
        assertThrows(IllegalArgumentException.class, () ->
                new TerrainRegionLayout(SEED, 900, 80.0F, List.of(
                        new TerrainRegionEntry(0, TerrainRegionFamily.MOUNTAINS,
                                TerrainPlacementMode.RIDGE, 1.0F, 1000, 2.0F))));
    }

    @Test
    void samplingIsStableAcrossRepeatReorderAndThreads() throws Exception {
        TerrainRegionLayout layout = layout(entries());
        List<TerrainRegionEntry> reversed = new ArrayList<>(entries());
        java.util.Collections.reverse(reversed);
        TerrainRegionLayout reordered = layout(reversed);

        TerrainRegionPlan[] expected = samplePlans(layout);
        assertPlansEqual(expected, samplePlans(layout));
        assertPlansEqual(expected, samplePlans(reordered));

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<TerrainRegionPlan[]>> tasks = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                tasks.add(() -> samplePlans(layout));
            }
            for (Future<TerrainRegionPlan[]> future : executor.invokeAll(tasks)) {
                assertPlansEqual(expected, future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void everyCoordinateHasOneAreaOwnerAndPartitionOfUnity() {
        TerrainRegionLayout layout = layout(entries());
        TerrainRegionBuffer buffer = new TerrainRegionBuffer();
        boolean sawPlains = false;
        boolean sawHills = false;
        boolean sawPlateau = false;
        for (int z = -24000; z <= 24000; z += 173) {
            for (int x = -24000; x <= 24000; x += 173) {
                layout.sample(x, z, buffer);
                assertTrue(buffer.ownershipEntryId() >= 0);
                assertTrue(buffer.underlayEntryId() >= 0);
                assertTrue(buffer.boundaryEntryId() >= 0);
                assertTrue(buffer.regionId() != buffer.boundaryRegionId()
                                || buffer.ownershipEntryId() != buffer.boundaryEntryId(),
                        "nearest competing candidate must differ by packed cell or catalog entry");
                assertTrue(buffer.edge() >= 0.0F && buffer.edge() <= 1.0F);
                assertTrue(buffer.blend() >= 0.0F && buffer.blend() <= 1.0F);
                assertEquals(1.0F, buffer.edge() + buffer.blend(), 0.000001F);
                assertTrue(buffer.tertiaryEntryId() >= 0);
                assertTrue(buffer.tertiaryBlend() >= 0.0F && buffer.tertiaryBlend() <= 1.0F);
                float weightSum = 0.0F;
                for (int candidate = 0; candidate < buffer.candidateCount(); candidate++) {
                    weightSum += buffer.candidateWeight(candidate);
                }
                assertEquals(1.0F, weightSum, 0.000001F);
                assertTrue(buffer.ownershipWeight() >= buffer.boundaryWeight());
                assertTrue(buffer.boundaryWeight() >= buffer.tertiaryWeight());
                assertEquals(TerrainPlacementMode.AREA, buffer.placement());
                assertEquals(buffer.ownershipFamily(), buffer.underlayFamily());
                assertEquals(buffer.ownershipFamily(), buffer.visibleFamily());
                assertEquals(0.0F, buffer.physicalInfluence(), 0.0F);
                sawPlains |= buffer.ownershipFamily() == TerrainRegionFamily.PLAINS;
                sawHills |= buffer.ownershipFamily() == TerrainRegionFamily.HILLS;
                sawPlateau |= buffer.ownershipFamily() == TerrainRegionFamily.PLATEAU;
                assertFalse(Float.isNaN(buffer.sampleX()));
                assertFalse(Float.isNaN(buffer.sampleZ()));
                assertFalse(Float.isNaN(buffer.boundaryCenterX()));
                assertFalse(Float.isNaN(buffer.boundaryCenterZ()));
                assertFalse(Float.isNaN(buffer.boundaryOrientation()));
            }
        }
        assertTrue(sawPlains);
        assertTrue(sawHills);
        assertTrue(sawPlateau);
    }

    @Test
    void regionCentersAndOwnershipAreContinuousAcrossChunkBoundaries() {
        TerrainRegionLayout layout = layout(entries());
        TerrainRegionBuffer left = new TerrainRegionBuffer();
        TerrainRegionBuffer right = new TerrainRegionBuffer();
        for (int z = -16000; z <= 16000; z += 511) {
            for (int x = -16000; x <= 16000; x += 16) {
                layout.sample(x + 15.999F, z, left);
                layout.sample(x + 16.001F, z, right);
                assertTrue(Math.abs(left.centerX() - right.centerX()) < 6000.0F);
                assertTrue(Math.abs(left.centerZ() - right.centerZ()) < 6000.0F);
                assertTrue(Math.abs(left.edge() - right.edge()) < 0.1F);
            }
        }
    }

    @Test
    void regionScaleChangesRepetitionWithoutDominatingAreaShare() {
        TerrainRegionLayout smallPlains = layout(List.of(
                TerrainRegionEntry.area(0, TerrainRegionFamily.PLAINS, 1.0F, 600),
                TerrainRegionEntry.area(1, TerrainRegionFamily.HILLS, 1.0F, 1400)));
        TerrainRegionLayout largePlains = layout(List.of(
                TerrainRegionEntry.area(0, TerrainRegionFamily.PLAINS, 1.0F, 2200),
                TerrainRegionEntry.area(1, TerrainRegionFamily.HILLS, 1.0F, 1400)));

        float smallShare = areaShare(smallPlains, TerrainRegionFamily.PLAINS);
        float largeShare = areaShare(largePlains, TerrainRegionFamily.PLAINS);

        assertTrue(Math.abs(smallShare - largeShare) < 0.12F,
                () -> "region size must not silently redefine the target area share: small="
                        + smallShare + ", large=" + largeShare);
        assertTrue(Math.abs(smallShare - 0.5F) < 0.18F);
        assertTrue(Math.abs(largeShare - 0.5F) < 0.18F);
    }

    @Test
    void plansRemainFiniteAtNegativeAndRemoteCoordinates() {
        TerrainRegionLayout layout = layout(entries());
        TerrainRegionBuffer buffer = new TerrainRegionBuffer();
        int[][] coordinates = {
                {0, 0},
                {-4096, 8192},
                {31777, -25001},
                {-240000, -160000},
                {750000, 1250000}
        };
        for (int[] coordinate : coordinates) {
            layout.sample(coordinate[0], coordinate[1], buffer);
            TerrainRegionPlan plan = buffer.snapshot();
            assertFalse(Float.isNaN(plan.centerX()));
            assertFalse(Float.isNaN(plan.centerZ()));
            assertFalse(Float.isNaN(plan.orientation()));
        }
    }

    private static TerrainRegionLayout layout(List<TerrainRegionEntry> entries) {
        return new TerrainRegionLayout(SEED, 900, 80.0F, entries);
    }

    private static List<TerrainRegionEntry> entries() {
        return List.of(
                TerrainRegionEntry.area(0, TerrainRegionFamily.PLAINS, 1.2F, 1600),
                TerrainRegionEntry.area(1, TerrainRegionFamily.HILLS, 0.8F, 1100),
                TerrainRegionEntry.area(2, TerrainRegionFamily.PLATEAU, 0.5F, 1800));
    }

    private static TerrainRegionPlan[] samplePlans(TerrainRegionLayout layout) {
        TerrainRegionPlan[] result = new TerrainRegionPlan[49];
        TerrainRegionBuffer buffer = new TerrainRegionBuffer();
        int index = 0;
        for (int z = -9000; z <= 9000; z += 3000) {
            for (int x = -9000; x <= 9000; x += 3000) {
                layout.sample(x, z, buffer);
                result[index++] = buffer.snapshot();
            }
        }
        return result;
    }

    private static void assertPlansEqual(TerrainRegionPlan[] expected, TerrainRegionPlan[] actual) {
        assertEquals(expected.length, actual.length);
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], actual[index], "mismatch at index " + index);
        }
    }

    private static float areaShare(TerrainRegionLayout layout, TerrainRegionFamily family) {
        TerrainRegionBuffer buffer = new TerrainRegionBuffer();
        int matches = 0;
        int total = 0;
        for (int z = -24000; z <= 24000; z += 128) {
            for (int x = -24000; x <= 24000; x += 128) {
                layout.sample(x, z, buffer);
                matches += buffer.ownershipFamily() == family ? 1 : 0;
                total++;
            }
        }
        return matches / (float) total;
    }
}
