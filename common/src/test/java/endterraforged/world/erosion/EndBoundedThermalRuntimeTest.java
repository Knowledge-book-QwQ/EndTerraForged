package endterraforged.world.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

class EndBoundedThermalRuntimeTest {

    private static final int CENTRE = ErosionFixture.SIZE / 2;

    @Test
    void fixedPassesUseTheCanonicalHalo() {
        assertEquals(2, EndBoundedThermalRuntime.PASSES);
        assertEquals(ErosionFixture.HALO, EndBoundedThermalRuntime.REQUIRED_HALO);
    }

    @Test
    void flatAndStablePlaneRemainUnchanged() {
        assertFixtureUnchanged(ErosionFixture.create(ErosionFixture.Kind.FLAT));
        assertFixtureUnchanged(ErosionFixture.create(ErosionFixture.Kind.PLANE));
    }

    @Test
    void isolatedSpikeRelaxesWithConservedMaterial() {
        ErosionFixture fixture = ErosionFixture.create(ErosionFixture.Kind.ISOLATED_SPIKE);
        EndBoundedThermalBuffer output = apply(fixture);
        int centre = fixture.index(CENTRE, CENTRE);

        assertTrue(output.top(centre) < fixture.rawTop(CENTRE, CENTRE));
        boolean sawRaisedCell = false;
        for (int index = 0; index < ErosionFixture.SIZE * ErosionFixture.SIZE; index++) {
            sawRaisedCell |= output.erosionDelta(index) > 0.0F;
        }
        assertTrue(sawRaisedCell);
        assertTrue(output.movedMaterialBlocks() > 0.0F);
        assertTrue(output.transferCount() > 0);
        assertEquals(0.0F, output.netMaterialDeltaBlocks(), 1.0E-3F);
    }

    @Test
    void ridgeCrestAndPlateauEdgeUseResistanceProtection() {
        ErosionFixture ridge = ErosionFixture.create(ErosionFixture.Kind.RIDGE);
        ErosionFixture plateau = ErosionFixture.create(ErosionFixture.Kind.PLATEAU_EDGE);
        EndBoundedThermalBuffer ridgeOutput = apply(ridge);
        EndBoundedThermalBuffer plateauOutput = apply(plateau);
        int ridgeCrest = ridge.index(CENTRE, CENTRE);
        int plateauEdge = plateau.index(CENTRE + 11, CENTRE);

        assertEquals(ridge.rawTop(CENTRE, CENTRE), ridgeOutput.top(ridgeCrest), 0.0F);
        assertEquals(plateau.rawTop(CENTRE + 11, CENTRE), plateauOutput.top(plateauEdge), 0.0F);
    }

    @Test
    void coastThinShelfAndArchipelagoDominantCellsHaveZeroImpact() {
        ErosionFixture coast = ErosionFixture.create(ErosionFixture.Kind.COAST_THIN_SHELF);
        assertFixtureUnchanged(coast);

        ErosionFixture archipelago = ErosionFixture.create(ErosionFixture.Kind.ARCHIPELAGO_WINDOW);
        EndBoundedThermalBuffer output = apply(archipelago);
        for (int index = 0; index < ErosionFixture.SIZE * ErosionFixture.SIZE; index++) {
            if (archipelago.archipelagoDominantValues()[index]) {
                assertEquals(archipelago.rawTopValues()[index], output.top(index), 0.0F);
            }
        }
    }

    @Test
    void everyFixtureStaysFiniteAndWithinTheFixedExportBudget() {
        float maximumCut = EndBoundedThermalRuntime.MAX_EXPORT_BLOCKS
                / ErosionFixture.WORLD_HEIGHT_BLOCKS;
        for (ErosionFixture fixture : ErosionFixture.standardSet()) {
            EndBoundedThermalBuffer output = apply(fixture);
            for (int index = 0; index < ErosionFixture.SIZE * ErosionFixture.SIZE; index++) {
                assertTrue(Float.isFinite(output.top(index)));
                assertTrue(Float.isFinite(output.erosionDelta(index)));
                assertTrue(output.top(index) >= 0.0F && output.top(index) <= 1.0F);
                assertTrue(output.erosionDelta(index) >= -maximumCut - 1.0E-6F);
            }
        }
    }

    @Test
    void protectedAndVoidGridsRemainUnchanged() {
        ErosionFixture fixture = ErosionFixture.create(ErosionFixture.Kind.ISOLATED_SPIKE);
        float[] protectedActivation = Arrays.copyOf(
                fixture.outerActivationValues(), fixture.outerActivationValues().length);
        Arrays.fill(protectedActivation, 0.0F);
        assertUnchanged(fixture, protectedActivation, fixture.landnessValues());

        float[] voidLandness = Arrays.copyOf(
                fixture.landnessValues(), fixture.landnessValues().length);
        Arrays.fill(voidLandness, 0.0F);
        assertUnchanged(fixture, fixture.outerActivationValues(), voidLandness,
                fixture.availableThicknessValues());

        float[] thinThickness = Arrays.copyOf(
                fixture.availableThicknessValues(), fixture.availableThicknessValues().length);
        Arrays.fill(thinThickness, 4.0F);
        assertUnchanged(fixture, fixture.outerActivationValues(), fixture.landnessValues(),
                thinThickness);
    }

    @Test
    void checksumsAreOrderIndependentAndThreadSafe()
            throws ExecutionException, InterruptedException {
        EndBoundedThermalRuntime runtime = new EndBoundedThermalRuntime();
        List<ErosionFixture> fixtures = ErosionFixture.standardSet();
        long expected = fixtureSetChecksum(runtime, fixtures);
        List<ErosionFixture> reversed = new ArrayList<>(fixtures);
        Collections.reverse(reversed);
        assertEquals(expected, fixtureSetChecksum(runtime, reversed));

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (int task = 0; task < 12; task++) {
                futures.add(executor.submit(() -> fixtureSetChecksum(runtime, fixtures)));
            }
            for (Future<Long> future : futures) {
                assertEquals(expected, future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static EndBoundedThermalBuffer apply(ErosionFixture fixture) {
        EndBoundedThermalBuffer output = new EndBoundedThermalBuffer(
                ErosionFixture.SIZE * ErosionFixture.SIZE);
        apply(new EndBoundedThermalRuntime(), fixture, fixture.rawTopValues(),
                fixture.landnessValues(), fixture.outerActivationValues(), output);
        return output;
    }

    private static void assertFixtureUnchanged(ErosionFixture fixture) {
        EndBoundedThermalBuffer output = apply(fixture);
        for (int index = 0; index < ErosionFixture.SIZE * ErosionFixture.SIZE; index++) {
            assertEquals(fixture.rawTopValues()[index], output.top(index), 0.0F);
            assertEquals(0.0F, output.erosionDelta(index), 0.0F);
        }
        assertEquals(0.0F, output.movedMaterialBlocks(), 0.0F);
    }

    private static void assertUnchanged(ErosionFixture fixture,
                                        float[] outerActivation,
                                        float[] landness) {
        assertUnchanged(fixture, outerActivation, landness, fixture.availableThicknessValues());
    }

    private static void assertUnchanged(ErosionFixture fixture,
                                        float[] outerActivation,
                                        float[] landness,
                                        float[] availableThickness) {
        EndBoundedThermalBuffer output = new EndBoundedThermalBuffer(
                ErosionFixture.SIZE * ErosionFixture.SIZE);
        apply(new EndBoundedThermalRuntime(), fixture, fixture.rawTopValues(),
                landness, outerActivation, availableThickness, output);
        for (int index = 0; index < ErosionFixture.SIZE * ErosionFixture.SIZE; index++) {
            assertEquals(fixture.rawTopValues()[index], output.top(index), 0.0F);
        }
    }

    private static long fixtureSetChecksum(EndBoundedThermalRuntime runtime,
                                           List<ErosionFixture> fixtures) {
        long checksum = 0L;
        EndBoundedThermalBuffer output = new EndBoundedThermalBuffer(
                ErosionFixture.SIZE * ErosionFixture.SIZE);
        for (ErosionFixture fixture : fixtures) {
            apply(runtime, fixture, fixture.rawTopValues(), fixture.landnessValues(),
                    fixture.outerActivationValues(), fixture.availableThicknessValues(), output);
            checksum += outputChecksum(output, false);
            assertEquals(outputChecksum(output, false), outputChecksum(output, true));
        }
        return checksum;
    }

    private static long outputChecksum(EndBoundedThermalBuffer output, boolean reverse) {
        long checksum = 0L;
        int start = reverse ? ErosionFixture.SIZE - 1 : 0;
        int end = reverse ? -1 : ErosionFixture.SIZE;
        int step = reverse ? -1 : 1;
        for (int z = start; z != end; z += step) {
            for (int x = start; x != end; x += step) {
                int index = z * ErosionFixture.SIZE + x;
                long cell = 0xCBF29CE484222325L;
                cell = (cell ^ index) * 0x100000001B3L;
                cell = (cell ^ Float.floatToIntBits(output.top(index))) * 0x100000001B3L;
                cell = (cell ^ Float.floatToIntBits(output.erosionDelta(index)))
                        * 0x100000001B3L;
                checksum += cell;
            }
        }
        return checksum;
    }

    private static void apply(EndBoundedThermalRuntime runtime,
                              ErosionFixture fixture,
                              float[] sourceTop,
                              float[] landness,
                              float[] outerActivation,
                              EndBoundedThermalBuffer output) {
        apply(runtime, fixture, sourceTop, landness, outerActivation,
                fixture.availableThicknessValues(), output);
    }

    private static void apply(EndBoundedThermalRuntime runtime,
                              ErosionFixture fixture,
                              float[] sourceTop,
                              float[] landness,
                              float[] outerActivation,
                              float[] availableThickness,
                              EndBoundedThermalBuffer output) {
        runtime.apply(ErosionFixture.SIZE, ErosionFixture.SIZE,
                ErosionFixture.WORLD_HEIGHT_BLOCKS, ErosionFixture.SAMPLE_DISTANCE_BLOCKS,
                sourceTop, landness, fixture.inlandnessValues(), outerActivation,
                fixture.erosionResistanceValues(), availableThickness,
                fixture.erosionMaskedValues(), fixture.archipelagoDominantValues(), output);
    }
}
