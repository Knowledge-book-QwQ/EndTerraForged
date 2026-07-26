package endterraforged.world.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

class EndBoundedFlowErosionRuntimeTest {

    private static final long WORLD_SEED = 123456789L;
    private static final long OWNER = 0x13579BDF2468ACE0L;
    private static final int[] DIRECTION_X = {0, 0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] DIRECTION_Z = {0, -1, -1, 0, 1, 1, 1, 0, -1};

    @Test
    void freezesBoundedFlowConstantsAndPrimitiveLayout() {
        EndBoundedFlowErosionRuntime runtime = new EndBoundedFlowErosionRuntime();
        EndErosionTileKey key = key(128, 0, 0);
        EndBoundedFlowErosionScratch scratch = new EndBoundedFlowErosionScratch(
                key.cellCount(), runtime.floodCells());

        assertEquals(3, EndBoundedFlowErosionRuntime.PRIORITY_RADIUS_SAMPLES);
        assertEquals(12, EndBoundedFlowErosionRuntime.FLOW_STEPS);
        assertEquals(16, EndBoundedFlowErosionRuntime.REQUIRED_HALO_SAMPLES);
        assertEquals(49, runtime.floodCells());
        assertEquals(108L, runtime.primitiveBytes());
        assertEquals(27L * key.cellCount() + 1_176L, scratch.primitiveBytes());
    }

    @Test
    void flatTerrainIsBitUnchangedWithoutArtificialDrainage() {
        Built built = build(ErosionWorldFixtureBuilder.Kind.FLAT,
                128, 0, 0, true, 64.0F, 64.0F);
        forEachCore(built.output().key(), index -> {
            assertEquals(Float.floatToIntBits(built.input().sourceTopBlocks(index)),
                    Float.floatToIntBits(built.output().finalTopBlocks(index)));
            assertEquals(0, Float.floatToIntBits(built.output().deltaBlocks(index)));
            assertEquals(0, Float.floatToIntBits(built.output().erosionStrength(index)));
            assertEquals(0, Float.floatToIntBits(built.output().drainagePotential(index)));
            assertEquals(0, built.output().dominantDirection(index));
        });
        assertEquals(0.0F, built.output().stats().totalCutBlocks(), 0.0F);
    }

    @Test
    void planeAndWatershedProduceDirectedDrainageAndIncision() {
        for (ErosionWorldFixtureBuilder.Kind kind : EnumSet.of(
                ErosionWorldFixtureBuilder.Kind.PLANE,
                ErosionWorldFixtureBuilder.Kind.LONG_PLANE,
                ErosionWorldFixtureBuilder.Kind.WATERSHED)) {
            Built built = build(kind, 128, 0, 0, true, 64.0F, 64.0F);
            assertTrue(coreDrainage(built.output()) > 0.0F, kind.name());
            assertTrue(coreCut(built.output()) > 0.0F, kind.name());
            assertTrue(built.output().stats().routedCells() > 0, kind.name());
            assertTrue(built.output().stats().flowTransfers() > 0, kind.name());
        }
    }

    @Test
    void closedBasinReportsLocalSpillWithoutPublishingWaterAuthority() {
        Built basin = build(ErosionWorldFixtureBuilder.Kind.CLOSED_BASIN,
                128, 0, 0, true, 64.0F, 64.0F);
        Built flat = build(ErosionWorldFixtureBuilder.Kind.FLAT,
                128, 0, 0, true, 64.0F, 64.0F);

        assertTrue(basin.output().stats().maximumSpillDepthBlocks() > 0.0F);
        assertEquals(0.0F, flat.output().stats().maximumSpillDepthBlocks(), 0.0F);
        assertNotEquals(basin.output().checksum(), flat.output().checksum());
    }

    @Test
    void protectedCoastAndArchipelagoAreStrictlyZeroInfluence() {
        for (ErosionWorldFixtureBuilder.Kind kind : EnumSet.of(
                ErosionWorldFixtureBuilder.Kind.COAST_THIN_SHELF,
                ErosionWorldFixtureBuilder.Kind.ARCHIPELAGO)) {
            Built built = build(kind, 128, 0, 0, true, 64.0F, 64.0F);
            forEachCore(built.output().key(), index -> {
                assertEquals(0, Float.floatToIntBits(built.output().activation(index)));
                assertEquals(0, Float.floatToIntBits(built.output().deltaBlocks(index)));
                assertEquals(Float.floatToIntBits(built.input().sourceTopBlocks(index)),
                        Float.floatToIntBits(built.output().finalTopBlocks(index)));
                assertEquals(0, built.output().dominantDirection(index));
            });
        }
    }

    @Test
    void fixturesStayFiniteAndWithinIncisionBudgets() {
        for (ErosionWorldFixtureBuilder.Kind kind : ErosionWorldFixtureBuilder.Kind.values()) {
            Built built = build(kind, 128, 0, 0, true, 64.0F, 64.0F);
            for (int index = 0; index < built.output().key().cellCount(); index++) {
                float factor = built.output().activation(index)
                        * (1.0F - Math.clamp(
                        built.input().erosionResistance(index), 0.0F, 1.0F));
                float budget = Math.min(EndBoundedFlowErosionRuntime.MAX_INCISION_BLOCKS,
                        built.input().availableThicknessBlocks(index)
                                * EndBoundedFlowErosionRuntime.MAX_THICKNESS_FRACTION) * factor;
                assertTrue(Float.isFinite(built.output().finalTopBlocks(index)), kind.name());
                assertTrue(Float.isFinite(built.output().deltaBlocks(index)), kind.name());
                assertTrue(built.output().deltaBlocks(index) <= 0.0F, kind.name());
                assertTrue(built.output().deltaBlocks(index) >= -budget - 1.0E-4F, kind.name());
                assertTrue(built.output().drainagePotential(index) >= 0.0F
                        && built.output().drainagePotential(index) <= 1.0F, kind.name());
                assertTrue(built.output().dominantDirection(index) >= 0
                        && built.output().dominantDirection(index) <= 8, kind.name());
            }
        }
    }

    @Test
    void ridgeAndPlateauResistanceReduceCutsWithoutChangingSources() {
        for (ErosionWorldFixtureBuilder.Kind kind : EnumSet.of(
                ErosionWorldFixtureBuilder.Kind.RIDGE,
                ErosionWorldFixtureBuilder.Kind.PLATEAU_EDGE)) {
            Built protectedTile = build(kind, 128, 0, 0, true, 64.0F, 64.0F);
            Built openTile = build(kind, 128, 0, 0, false, 64.0F, 64.0F);
            float protectedCut = coreCut(protectedTile.output());
            float openCut = coreCut(openTile.output());
            assertTrue(openCut > 0.0F, kind + " must exercise stream-power cutting");
            assertTrue(protectedCut < openCut, kind + " resistance must reduce cutting");
        }
    }

    @Test
    void dominantDirectionsDoNotFormCycles() {
        for (ErosionWorldFixtureBuilder.Kind kind : EnumSet.of(
                ErosionWorldFixtureBuilder.Kind.PLANE,
                ErosionWorldFixtureBuilder.Kind.CLOSED_BASIN,
                ErosionWorldFixtureBuilder.Kind.WATERSHED)) {
            EndBoundedFlowErosionTile tile = build(
                    kind, 128, 0, 0, true, 64.0F, 64.0F).output();
            assertNoCycles(tile, kind.name());
        }
    }

    @Test
    void fixedWatershedTileMatchesTheFrozenPrimitiveGolden() {
        Built watershed = build(ErosionWorldFixtureBuilder.Kind.WATERSHED,
                128, 0, 0, true, 64.0F, 64.0F);
        assertEquals(5601421594159001151L, watershed.output().checksum());
    }

    @Test
    void one256CoreMatchesFour128CoresAtPositiveAndNegativeCoordinates() {
        compareCorePartition(0, 0, 128.0F, 128.0F);
        compareCorePartition(-1, -1, -128.0F, -128.0F);
    }

    private static void assertNoCycles(EndBoundedFlowErosionTile tile, String fixture) {
        int cells = tile.key().cellCount();
        int[] visitStamp = new int[cells];
        int stamp = 1;
        int halo = tile.key().haloSamples();
        for (int z = halo; z < tile.key().sampleHeight() - halo; z++) {
            for (int x = halo; x < tile.key().sampleWidth() - halo; x++) {
                int current = tile.index(x, z);
                while (tile.dominantDirection(current) != 0) {
                    assertTrue(visitStamp[current] != stamp,
                            fixture + " cycle at " + current);
                    visitStamp[current] = stamp;
                    byte direction = tile.dominantDirection(current);
                    int nextX = current % tile.key().sampleWidth() + DIRECTION_X[direction];
                    int nextZ = current / tile.key().sampleWidth() + DIRECTION_Z[direction];
                    if (nextX < 0 || nextX >= tile.key().sampleWidth()
                            || nextZ < 0 || nextZ >= tile.key().sampleHeight()) {
                        break;
                    }
                    current = tile.index(nextX, nextZ);
                }
                stamp++;
            }
        }
    }

    private static void compareCorePartition(int largeTileX,
                                             int largeTileZ,
                                             float centerX,
                                             float centerZ) {
        Built large = build(ErosionWorldFixtureBuilder.Kind.WATERSHED,
                256, largeTileX, largeTileZ, true, centerX, centerZ);
        for (int quadrantZ = 0; quadrantZ < 2; quadrantZ++) {
            for (int quadrantX = 0; quadrantX < 2; quadrantX++) {
                Built small = build(ErosionWorldFixtureBuilder.Kind.WATERSHED,
                        128, largeTileX * 2 + quadrantX,
                        largeTileZ * 2 + quadrantZ, true, centerX, centerZ);
                compareQuadrant(large.output(), small.output(), quadrantX, quadrantZ);
            }
        }
    }

    private static void compareQuadrant(EndBoundedFlowErosionTile large,
                                        EndBoundedFlowErosionTile small,
                                        int quadrantX,
                                        int quadrantZ) {
        int halo = EndBoundedFlowErosionRuntime.REQUIRED_HALO_SAMPLES;
        int smallCoreSamples = 128 / small.key().sampleDistanceBlocks();
        for (int z = 0; z < smallCoreSamples; z++) {
            for (int x = 0; x < smallCoreSamples; x++) {
                int largeIndex = large.index(
                        halo + quadrantX * smallCoreSamples + x,
                        halo + quadrantZ * smallCoreSamples + z);
                int smallIndex = small.index(halo + x, halo + z);
                assertBitsEqual(large, largeIndex, small, smallIndex,
                        "quadrant=" + quadrantX + ',' + quadrantZ
                                + " local=" + x + ',' + z);
            }
        }
    }

    private static void assertBitsEqual(EndBoundedFlowErosionTile left,
                                        int leftIndex,
                                        EndBoundedFlowErosionTile right,
                                        int rightIndex,
                                        String position) {
        assertEquals(Float.floatToIntBits(left.finalTopBlocks(leftIndex)),
                Float.floatToIntBits(right.finalTopBlocks(rightIndex)), position + " top");
        assertEquals(Float.floatToIntBits(left.deltaBlocks(leftIndex)),
                Float.floatToIntBits(right.deltaBlocks(rightIndex)), position + " delta");
        assertEquals(Float.floatToIntBits(left.erosionStrength(leftIndex)),
                Float.floatToIntBits(right.erosionStrength(rightIndex)), position + " strength");
        assertEquals(Float.floatToIntBits(left.drainagePotential(leftIndex)),
                Float.floatToIntBits(right.drainagePotential(rightIndex)), position + " drainage");
        assertEquals(Float.floatToIntBits(left.activation(leftIndex)),
                Float.floatToIntBits(right.activation(rightIndex)), position + " activation");
        assertEquals(left.dominantDirection(leftIndex),
                right.dominantDirection(rightIndex), position + " direction");
    }

    private static Built build(ErosionWorldFixtureBuilder.Kind kind,
                               int coreBlocks,
                               int tileX,
                               int tileZ,
                               boolean protectionEnabled,
                               float centerX,
                               float centerZ) {
        EndErosionTileKey key = key(coreBlocks, tileX, tileZ);
        ErosionWorldFixtureBuilder inputBuilder = new ErosionWorldFixtureBuilder(
                kind, centerX, centerZ, protectionEnabled, null);
        EndErosionTile input = inputBuilder.build(key).tile();
        EndBoundedFlowErosionRuntime runtime = new EndBoundedFlowErosionRuntime();
        EndBoundedFlowErosionTileBuilder builder = new EndBoundedFlowErosionTileBuilder(
                inputBuilder, runtime, key.cellCount());
        return new Built(input, builder.build(key).tile());
    }

    static EndErosionTileKey key(int coreBlocks, int tileX, int tileZ) {
        int sampleDistance = (int) ErosionFixture.SAMPLE_DISTANCE_BLOCKS;
        int coreSamples = coreBlocks / sampleDistance;
        int sampleSize = coreSamples
                + EndBoundedFlowErosionRuntime.REQUIRED_HALO_SAMPLES * 2;
        return new EndErosionTileKey(
                EndBoundedFlowErosionRuntime.ALGORITHM_ID,
                EndBoundedFlowErosionRuntime.ALGORITHM_VERSION,
                WORLD_SEED, OWNER, -256,
                (int) ErosionFixture.WORLD_HEIGHT_BLOCKS,
                4, tileX, tileZ, sampleSize, sampleSize,
                EndBoundedFlowErosionRuntime.REQUIRED_HALO_SAMPLES,
                sampleDistance);
    }

    private static float coreCut(EndBoundedFlowErosionTile tile) {
        float cut = 0.0F;
        int halo = tile.key().haloSamples();
        for (int z = halo; z < tile.key().sampleHeight() - halo; z++) {
            for (int x = halo; x < tile.key().sampleWidth() - halo; x++) {
                cut += Math.max(0.0F, -tile.deltaBlocks(tile.index(x, z)));
            }
        }
        return cut;
    }

    private static float coreDrainage(EndBoundedFlowErosionTile tile) {
        float drainage = 0.0F;
        int halo = tile.key().haloSamples();
        for (int z = halo; z < tile.key().sampleHeight() - halo; z++) {
            for (int x = halo; x < tile.key().sampleWidth() - halo; x++) {
                drainage += tile.drainagePotential(tile.index(x, z));
            }
        }
        return drainage;
    }

    private static void forEachCore(EndErosionTileKey key, IndexConsumer consumer) {
        int halo = key.haloSamples();
        for (int z = halo; z < key.sampleHeight() - halo; z++) {
            for (int x = halo; x < key.sampleWidth() - halo; x++) {
                consumer.accept(z * key.sampleWidth() + x);
            }
        }
    }

    private record Built(EndErosionTile input, EndBoundedFlowErosionTile output) {
    }

    @FunctionalInterface
    private interface IndexConsumer {
        void accept(int index);
    }
}
