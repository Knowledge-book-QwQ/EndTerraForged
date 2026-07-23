package endterraforged.world.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ErosionFixtureTest {

    private static final int CENTRE = ErosionFixture.SIZE / 2;

    @Test
    void standardSetContainsEveryP47Primitive() {
        Map<ErosionFixture.Kind, ErosionFixture> fixtures = new EnumMap<>(ErosionFixture.Kind.class);
        for (ErosionFixture fixture : ErosionFixture.standardSet()) {
            fixtures.put(fixture.kind(), fixture);
        }
        assertEquals(ErosionFixture.Kind.values().length, fixtures.size());
        assertTrue(fixtures.get(ErosionFixture.Kind.COAST_THIN_SHELF).landness(0, CENTRE)
                < fixtures.get(ErosionFixture.Kind.COAST_THIN_SHELF).landness(ErosionFixture.SIZE - 1, CENTRE));
        assertTrue(fixtures.get(ErosionFixture.Kind.ARCHIPELAGO_WINDOW)
                .archipelagoDominant(CENTRE - 9, CENTRE - 3));
    }

    @Test
    void fixturesExposeDistinctVisualSignals() {
        ErosionFixture flat = ErosionFixture.create(ErosionFixture.Kind.FLAT);
        ErosionFixture plane = ErosionFixture.create(ErosionFixture.Kind.PLANE);
        ErosionFixture ridge = ErosionFixture.create(ErosionFixture.Kind.RIDGE);
        ErosionFixture plateau = ErosionFixture.create(ErosionFixture.Kind.PLATEAU_EDGE);
        ErosionFixture basin = ErosionFixture.create(ErosionFixture.Kind.CLOSED_BASIN);
        ErosionFixture watershed = ErosionFixture.create(ErosionFixture.Kind.WATERSHED);

        assertEquals(0.0F, flat.slope(CENTRE, CENTRE), 0.0F);
        assertEquals(0.0F, flat.curvature(CENTRE, CENTRE), 0.0F);
        assertTrue(plane.slope(CENTRE, CENTRE) > 0.0F);
        assertTrue(Math.abs(plane.curvature(CENTRE, CENTRE)) < 1.0E-5F);
        assertTrue(ridge.rawTop(CENTRE, CENTRE) > ridge.rawTop(CENTRE, CENTRE - 6));
        assertTrue(plateau.slope(CENTRE, CENTRE) < plateau.slope(CENTRE, CENTRE - 10));
        assertTrue(basin.rawTop(CENTRE, CENTRE) < basin.rawTop(0, 0));
        assertTrue(watershed.curvature(CENTRE, CENTRE - 4) * watershed.curvature(CENTRE - 4, CENTRE)
                < 0.0F);
        assertNotEquals(flat.checksum(false), plane.checksum(false));
    }

    @Test
    void checksumIsIndependentOfTraversalOrder() {
        for (ErosionFixture fixture : ErosionFixture.standardSet()) {
            assertEquals(fixture.checksum(false), fixture.checksum(true), fixture.kind().name());
        }
    }

    @Test
    void derivativesStayFiniteInsideTheFixedHalo() {
        for (ErosionFixture fixture : ErosionFixture.standardSet()) {
            for (int z = ErosionFixture.HALO; z < ErosionFixture.SIZE - ErosionFixture.HALO; z++) {
                for (int x = ErosionFixture.HALO; x < ErosionFixture.SIZE - ErosionFixture.HALO; x++) {
                    assertTrue(Float.isFinite(fixture.slope(x, z)));
                    assertTrue(Float.isFinite(fixture.curvature(x, z)));
                    assertTrue(fixture.slope(x, z) >= 0.0F && fixture.slope(x, z) < 1.0F);
                    assertTrue(fixture.curvature(x, z) >= -1.0F && fixture.curvature(x, z) <= 1.0F);
                }
            }
        }
    }
}
