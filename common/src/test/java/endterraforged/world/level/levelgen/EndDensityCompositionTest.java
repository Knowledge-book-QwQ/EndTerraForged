package endterraforged.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

import endterraforged.world.config.SeaMode;
import endterraforged.world.config.TestProfile;
import endterraforged.world.config.TopologyMode;
import endterraforged.world.continent.EndCentralRegionPolicy;
import endterraforged.world.floatingislands.FloatingIslandsField;
import endterraforged.world.heightmap.EndDensity;
import endterraforged.world.heightmap.EndHeightmap;

class EndDensityCompositionTest {
    private static final int SEED = 42;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void maxUnionMatchesLegacyClampedAdditionForEtfDensityContract() {
        EndHeightmap heightmap = new EndHeightmap(
                new TestProfile(512, -256, 0, 0,
                        SeaMode.WITH_FLOOR, TopologyMode.CONTINENTAL_SHATTERED, true),
                SEED);
        DensityFunction terrain = new EndDensityFunction.Bound(new EndDensity(heightmap), SEED);
        DensityFunction floating = new FloatingIslandsFunction.Bound(
                FloatingIslandsField.defaults(), SEED);
        DensityFunction optimized = DensityFunctions.max(terrain, floating);

        for (int i = 0; i < 256; i++) {
            int x = i * 37 - 4096;
            int z = i * 53 - 4096;
            for (int y = -256; y < 256; y += 17) {
                DensityFunction.FunctionContext context =
                        new DensityFunction.SinglePointContext(x, y, z);
                double legacy = Math.clamp(
                        terrain.compute(context) + floating.compute(context), 0.0, 1.0);
                assertEquals(legacy, optimized.compute(context), 0.0,
                        "max union must preserve density at " + x + "," + y + "," + z);
            }
        }
    }

    @Test
    void centralRegionDelegatesToVanillaFallbackAndSuppressesFloatingOverlay() {
        EndHeightmap heightmap = new EndHeightmap(
                new TestProfile(512, -256, 0, 0,
                        SeaMode.WITH_FLOOR, TopologyMode.CONTINENTAL, true),
                SEED);
        DensityFunction fallback = DensityFunctions.constant(-0.25D);
        DensityFunction terrain = new EndDensityFunction.Bound(new EndDensity(heightmap), SEED, fallback);
        DensityFunction floating = new FloatingIslandsFunction.Bound(
                FloatingIslandsField.defaults(), SEED, fallback.minValue());
        DensityFunction combined = DensityFunctions.max(terrain, floating);

        DensityFunction.FunctionContext central = new DensityFunction.SinglePointContext(0, 0, 0);
        DensityFunction.FunctionContext outer = new DensityFunction.SinglePointContext(
                EndCentralRegionPolicy.OUTER_TERRAIN_RADIUS_BLOCKS + 1, 0, 0);

        assertEquals(-0.25D, terrain.compute(central), 0.0,
                "central terrain must preserve vanilla final density");
        assertEquals(-0.25D, combined.compute(central), 0.0,
                "ETF floating islands must not alter protected vanilla density");
        assertEquals(1.0D, terrain.compute(outer), 0.0,
                "outer terrain must still use the ETF density field");
    }
}
