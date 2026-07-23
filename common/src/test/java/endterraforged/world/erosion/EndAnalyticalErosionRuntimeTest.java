package endterraforged.world.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.TestProfile;
import endterraforged.world.heightmap.EndHeightmap;
import endterraforged.world.heightmap.EndTerrainProfileBuffer;

class EndAnalyticalErosionRuntimeTest {

    private static final float OUTER_ACTIVATION = 1.0F;
    private static final float LANDNESS = 0.75F;
    private static final float INLANDNESS = 0.80F;
    private static final float THICKNESS = 128.0F;

    @Test
    void protectedInputsHaveZeroImpact() {
        EndAnalyticalErosionRuntime runtime = new EndAnalyticalErosionRuntime();
        EndAnalyticalErosionBuffer output = new EndAnalyticalErosionBuffer();

        runtime.apply(0.70F, 512.0F, 0.40F, 0.10F, 1.0F, 0.0F,
                LANDNESS, INLANDNESS, 0.0F, THICKNESS, false, output);
        assertZeroImpact(output, 0.70F);

        runtime.apply(0.70F, 512.0F, 0.40F, 0.10F, 1.0F, 0.0F,
                LANDNESS, INLANDNESS, OUTER_ACTIVATION, THICKNESS, true, output);
        assertZeroImpact(output, 0.70F);

        runtime.apply(0.70F, 512.0F, 0.40F, 0.10F, 1.0F, 0.0F,
                0.05F, INLANDNESS, OUTER_ACTIVATION, THICKNESS, false, output);
        assertZeroImpact(output, 0.70F);

        runtime.apply(0.70F, 512.0F, 0.40F, 0.10F, 1.0F, 0.0F,
                LANDNESS, INLANDNESS, OUTER_ACTIVATION, 4.0F, false, output);
        assertZeroImpact(output, 0.70F);
    }

    @Test
    void baselineOnlyCutsFiniteTerrainAndNeverRaisesTop() {
        EndAnalyticalErosionRuntime runtime = new EndAnalyticalErosionRuntime();
        EndAnalyticalErosionBuffer output = new EndAnalyticalErosionBuffer();
        runtime.apply(0.72F, 512.0F, 0.55F, 0.05F, 1.0F, 0.0F,
                LANDNESS, INLANDNESS, OUTER_ACTIVATION, THICKNESS, false, output);

        assertTrue(Float.isFinite(output.top()));
        assertTrue(Float.isFinite(output.erosionDelta()));
        assertTrue(output.erosionDelta() <= 0.0F);
        assertEquals(output.top() - 0.72F, output.erosionDelta(), 0.0F);
        assertTrue(output.erosionStrength() > 0.0F);
        assertTrue(output.top() >= 0.0F && output.top() <= 1.0F);
    }

    @Test
    void cutUsesTheSameBlockScaleAcrossWorldHeights() {
        EndAnalyticalErosionRuntime runtime = new EndAnalyticalErosionRuntime();
        EndAnalyticalErosionBuffer standard = new EndAnalyticalErosionBuffer();
        EndAnalyticalErosionBuffer tall = new EndAnalyticalErosionBuffer();

        runtime.apply(0.72F, 512.0F, 0.55F, 0.05F, 1.0F, 0.0F,
                LANDNESS, INLANDNESS, OUTER_ACTIVATION, THICKNESS, false, standard);
        runtime.apply(0.72F, 1024.0F, 0.55F, 0.05F, 1.0F, 0.0F,
                LANDNESS, INLANDNESS, OUTER_ACTIVATION, THICKNESS, false, tall);

        assertEquals(standard.erosionDelta() * 512.0F,
                tall.erosionDelta() * 1024.0F, 1.0E-3F);
        assertEquals(standard.erosionStrength(), tall.erosionStrength(), 0.0F);
    }

    @Test
    void ridgeIsProtectedWhileBasinProducesDrainageDiagnostic() {
        ErosionFixture ridge = ErosionFixture.create(ErosionFixture.Kind.RIDGE);
        ErosionFixture basin = ErosionFixture.create(ErosionFixture.Kind.CLOSED_BASIN);
        EndAnalyticalErosionRuntime runtime = new EndAnalyticalErosionRuntime();
        EndAnalyticalErosionBuffer ridgeOutput = new EndAnalyticalErosionBuffer();
        EndAnalyticalErosionBuffer flankOutput = new EndAnalyticalErosionBuffer();
        EndAnalyticalErosionBuffer basinOutput = new EndAnalyticalErosionBuffer();

        runtime.apply(ridge.rawTop(16, 16), ErosionFixture.WORLD_HEIGHT_BLOCKS,
                ridge.slope(16, 16), ridge.curvature(16, 16), 1.0F, 0.0F,
                ridge.landness(16, 16), ridge.inlandness(16, 16), OUTER_ACTIVATION,
                ridge.availableThicknessBlocks(16, 16), false, ridgeOutput);
        runtime.apply(ridge.rawTop(16, 12), ErosionFixture.WORLD_HEIGHT_BLOCKS,
                ridge.slope(16, 12), ridge.curvature(16, 12), 1.0F, 0.0F,
                ridge.landness(16, 12), ridge.inlandness(16, 12), OUTER_ACTIVATION,
                ridge.availableThicknessBlocks(16, 12), false, flankOutput);
        runtime.apply(basin.rawTop(16, 16), ErosionFixture.WORLD_HEIGHT_BLOCKS,
                basin.slope(16, 16), basin.curvature(16, 16), 1.0F, 0.0F,
                basin.landness(16, 16), basin.inlandness(16, 16), OUTER_ACTIVATION,
                basin.availableThicknessBlocks(16, 16), false, basinOutput);

        assertTrue(ridgeOutput.erosionStrength() <= flankOutput.erosionStrength());
        assertTrue(basinOutput.drainagePotential() > 0.0F);
        assertEquals(0.0F, basinOutput.erosionDelta(), 0.0F,
                "the first slice diagnoses basin drainage without carving it");
    }

    @Test
    void fixtureTraversalProducesOrderIndependentOutput() {
        EndAnalyticalErosionRuntime runtime = new EndAnalyticalErosionRuntime();
        for (ErosionFixture fixture : ErosionFixture.standardSet()) {
            long forward = outputChecksum(runtime, fixture, false);
            long reverse = outputChecksum(runtime, fixture, true);
            assertEquals(forward, reverse, fixture.kind().name());
        }
    }

    @Test
    void profileApiUsesTheCallerOwnedProfileWithoutChangingTheRawSample() {
        EndHeightmap heightmap = new EndHeightmap(TestProfile.defaultEnd(), 9412);
        EndTerrainProfileBuffer profile = new EndTerrainProfileBuffer();
        EndAnalyticalErosionBuffer output = new EndAnalyticalErosionBuffer();

        heightmap.sampleTerrainProfile(8192.0F, 8192.0F, 9412, profile);
        new EndAnalyticalErosionRuntime().apply(profile, LANDNESS, INLANDNESS,
                0.0F, THICKNESS, false, output);

        assertZeroImpact(output, profile.rawTop());
    }

    private static long outputChecksum(EndAnalyticalErosionRuntime runtime,
                                       ErosionFixture fixture,
                                       boolean reverse) {
        EndAnalyticalErosionBuffer output = new EndAnalyticalErosionBuffer();
        long checksum = 0L;
        int start = reverse ? fixture.size() - ErosionFixture.HALO - 1 : ErosionFixture.HALO;
        int end = reverse ? ErosionFixture.HALO - 1 : fixture.size() - ErosionFixture.HALO;
        int step = reverse ? -1 : 1;
        for (int z = start; z != end; z += step) {
            for (int x = start; x != end; x += step) {
                runtime.apply(fixture.rawTop(x, z), ErosionFixture.WORLD_HEIGHT_BLOCKS,
                        fixture.slope(x, z), fixture.curvature(x, z), 1.0F, 0.0F,
                        fixture.landness(x, z), fixture.inlandness(x, z), OUTER_ACTIVATION,
                        fixture.availableThicknessBlocks(x, z), fixture.archipelagoDominant(x, z), output);
                checksum += Float.floatToIntBits(output.top());
                checksum += Float.floatToIntBits(output.erosionDelta());
                checksum += Float.floatToIntBits(output.erosionStrength());
                checksum += Float.floatToIntBits(output.drainagePotential());
                checksum += Float.floatToIntBits(output.activation());
            }
        }
        return checksum;
    }

    private static void assertZeroImpact(EndAnalyticalErosionBuffer output, float rawTop) {
        assertEquals(rawTop, output.top(), 0.0F);
        assertEquals(0.0F, output.erosionDelta(), 0.0F);
        assertEquals(0.0F, output.erosionStrength(), 0.0F);
        assertEquals(0.0F, output.activation(), 0.0F);
    }
}
