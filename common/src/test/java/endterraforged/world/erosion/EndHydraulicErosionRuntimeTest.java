package endterraforged.world.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import endterraforged.util.FastRandom;
import endterraforged.util.NoiseUtil;

class EndHydraulicErosionRuntimeTest {

    private static final long WORLD_SEED = 123456789L;
    private static final long OWNER = 0x13579BDF2468ACE0L;
    private static final float MASS_TOLERANCE_BLOCKS = 0.05F;

    @Test
    void freezesRtfReferenceConstantsBrushAndSourceRandomOrder() {
        EndHydraulicErosionRuntime runtime = new EndHydraulicErosionRuntime();

        assertEquals(135, EndHydraulicErosionRuntime.DROPLETS_PER_SOURCE_CHUNK);
        assertEquals(12, EndHydraulicErosionRuntime.DROPLET_LIFETIME);
        assertEquals(4, EndHydraulicErosionRuntime.BRUSH_RADIUS_SAMPLES);
        assertEquals(16, EndHydraulicErosionRuntime.REQUIRED_HALO_SAMPLES);
        assertEquals(0.7F, EndHydraulicErosionRuntime.INITIAL_WATER, 0.0F);
        assertEquals(0.7F, EndHydraulicErosionRuntime.INITIAL_SPEED, 0.0F);
        assertEquals(0.5F, EndHydraulicErosionRuntime.EROSION_RATE, 0.0F);
        assertEquals(0.5F, EndHydraulicErosionRuntime.DEPOSITION_RATE, 0.0F);
        assertEquals(45, runtime.brushEntries());
        assertEquals(540L, runtime.primitiveBytes());

        int seed = Long.hashCode(WORLD_SEED) + EndHydraulicErosionRuntime.SEED_OFFSET;
        FastRandom random = new FastRandom();
        random.seed(NoiseUtil.seed(-1, -1), NoiseUtil.seed(seed, 0));
        assertEquals(8, random.nextInt(16));
        assertEquals(7, random.nextInt(16));
    }

    @Test
    void flatTerrainIsBitUnchangedAndExportsNoMaterial() {
        Built built = build(ErosionWorldFixtureBuilder.Kind.FLAT, 128, 0, 0, true, 64.0F, 64.0F);
        EndHydraulicErosionTile tile = built.output();

        forEachCore(tile.key(), index -> {
            assertEquals(0, Float.floatToIntBits(tile.deltaBlocks(index)));
            assertEquals(Float.floatToIntBits(built.input().sourceTopBlocks(index)),
                    Float.floatToIntBits(tile.finalTopBlocks(index)));
            assertEquals(0, Float.floatToIntBits(tile.erosionStrength(index)));
            assertEquals(0, Float.floatToIntBits(tile.drainagePotential(index)));
        });
        assertEquals(0.0F, tile.stats().erodedBlocks(), 0.0F);
        assertEquals(0.0F, tile.stats().depositedBlocks(), 0.0F);
        assertEquals(0.0F, tile.stats().exportedSedimentBlocks(), 0.0F);
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
            });
        }
    }

    @Test
    void fixturesStayFiniteWithinTransferBudgetsAndAccountForSediment() {
        for (ErosionWorldFixtureBuilder.Kind kind : ErosionWorldFixtureBuilder.Kind.values()) {
            Built built = build(kind, 128, 0, 0, true, 64.0F, 64.0F);
            EndHydraulicErosionTile output = built.output();
            for (int index = 0; index < output.key().cellCount(); index++) {
                float factor = output.activation(index)
                        * (1.0F - Math.clamp(built.input().erosionResistance(index), 0.0F, 1.0F));
                float cutBudget = Math.min(EndHydraulicErosionRuntime.MAX_TRANSFER_BLOCKS,
                        built.input().availableThicknessBlocks(index) * 0.25F) * factor;
                float fillBudget = EndHydraulicErosionRuntime.MAX_TRANSFER_BLOCKS * factor;
                assertTrue(Float.isFinite(output.finalTopBlocks(index)));
                assertTrue(Float.isFinite(output.deltaBlocks(index)));
                assertTrue(output.deltaBlocks(index) >= -cutBudget - 1.0E-4F);
                assertTrue(output.deltaBlocks(index) <= fillBudget + 1.0E-4F);
                assertTrue(output.erosionStrength(index) >= 0.0F
                        && output.erosionStrength(index) <= 1.0F);
                assertTrue(output.drainagePotential(index) >= 0.0F
                        && output.drainagePotential(index) <= 1.0F);
            }
            EndHydraulicErosionTile.Stats stats = output.stats();
            assertEquals(stats.erodedBlocks(),
                    stats.depositedBlocks() + stats.exportedSedimentBlocks(),
                    MASS_TOLERANCE_BLOCKS, kind.name());
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
            assertTrue(openCut > 0.0F, kind + " must exercise hydraulic cutting");
            assertTrue(protectedCut < openCut, kind + " resistance must reduce cutting");
        }
    }

    @Test
    void basinAndWatershedProduceDrainageSignal() {
        Built flat = build(ErosionWorldFixtureBuilder.Kind.FLAT,
                128, 0, 0, true, 64.0F, 64.0F);
        Built basin = build(ErosionWorldFixtureBuilder.Kind.CLOSED_BASIN,
                128, 0, 0, true, 64.0F, 64.0F);
        Built watershed = build(ErosionWorldFixtureBuilder.Kind.WATERSHED,
                128, 0, 0, true, 64.0F, 64.0F);

        assertEquals(0.0F, coreDrainage(flat.output()), 0.0F);
        assertTrue(coreDrainage(basin.output()) > 0.0F);
        assertTrue(coreDrainage(watershed.output()) > 0.0F);
        assertNotEquals(basin.output().checksum(), watershed.output().checksum());
    }

    @Test
    void fixedWatershedTileMatchesTheFrozenPrimitiveGolden() {
        Built watershed = build(ErosionWorldFixtureBuilder.Kind.WATERSHED,
                128, 0, 0, true, 64.0F, 64.0F);
        assertEquals(4281940154564766763L, watershed.output().checksum());
    }

    @Test
    void one256CoreMatchesFour128CoresAtPositiveAndNegativeCoordinates() {
        compareCorePartition(0, 0, 128.0F, 128.0F);
        compareCorePartition(-1, -1, -128.0F, -128.0F);
    }

    private static void compareCorePartition(int largeTileX,
                                             int largeTileZ,
                                             float centerX,
                                             float centerZ) {
        Built large = build(ErosionWorldFixtureBuilder.Kind.WATERSHED,
                256, largeTileX, largeTileZ, true, centerX, centerZ);
        for (int quadrantZ = 0; quadrantZ < 2; quadrantZ++) {
            for (int quadrantX = 0; quadrantX < 2; quadrantX++) {
                int smallTileX = largeTileX * 2 + quadrantX;
                int smallTileZ = largeTileZ * 2 + quadrantZ;
                Built small = build(ErosionWorldFixtureBuilder.Kind.WATERSHED,
                        128, smallTileX, smallTileZ, true, centerX, centerZ);
                compareQuadrant(large.output(), small.output(), quadrantX, quadrantZ);
            }
        }
    }

    private static void compareQuadrant(EndHydraulicErosionTile large,
                                        EndHydraulicErosionTile small,
                                        int quadrantX,
                                        int quadrantZ) {
        int halo = EndHydraulicErosionRuntime.REQUIRED_HALO_SAMPLES;
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

    private static void assertBitsEqual(EndHydraulicErosionTile left,
                                        int leftIndex,
                                        EndHydraulicErosionTile right,
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
        EndHydraulicErosionRuntime runtime = new EndHydraulicErosionRuntime();
        EndHydraulicErosionTileBuilder builder = new EndHydraulicErosionTileBuilder(
                inputBuilder, runtime, key.cellCount());
        EndHydraulicErosionTile output = builder.build(key).tile();
        return new Built(input, output);
    }

    static EndErosionTileKey key(int coreBlocks, int tileX, int tileZ) {
        int sampleDistance = (int) ErosionFixture.SAMPLE_DISTANCE_BLOCKS;
        int coreSamples = coreBlocks / sampleDistance;
        int sampleSize = coreSamples
                + EndHydraulicErosionRuntime.REQUIRED_HALO_SAMPLES * 2;
        return new EndErosionTileKey(
                EndHydraulicErosionRuntime.ALGORITHM_ID,
                EndHydraulicErosionRuntime.ALGORITHM_VERSION,
                WORLD_SEED,
                OWNER,
                -256,
                (int) ErosionFixture.WORLD_HEIGHT_BLOCKS,
                4,
                tileX,
                tileZ,
                sampleSize,
                sampleSize,
                EndHydraulicErosionRuntime.REQUIRED_HALO_SAMPLES,
                sampleDistance);
    }

    private static float coreCut(EndHydraulicErosionTile tile) {
        float cut = 0.0F;
        int halo = tile.key().haloSamples();
        for (int z = halo; z < tile.key().sampleHeight() - halo; z++) {
            for (int x = halo; x < tile.key().sampleWidth() - halo; x++) {
                cut += Math.max(0.0F, -tile.deltaBlocks(tile.index(x, z)));
            }
        }
        return cut;
    }

    private static float coreDrainage(EndHydraulicErosionTile tile) {
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

    @FunctionalInterface
    private interface IndexConsumer {
        void accept(int index);
    }

    private record Built(EndErosionTile input, EndHydraulicErosionTile output) {
    }
}
