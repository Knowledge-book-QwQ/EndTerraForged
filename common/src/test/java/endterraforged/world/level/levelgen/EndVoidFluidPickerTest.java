package endterraforged.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Aquifer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EndVoidFluidPickerTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void resolvesEveryCoordinateToSharedAirStatus() {
        Aquifer.FluidPicker picker = EndVoidFluidPicker.picker();
        Aquifer.FluidStatus expected = picker.computeFluid(0, 0, 0);

        int[][] coordinates = {
                {0, -256, 0},
                {4096, -64, 4096},
                {-4096, -55, 8192},
                {Integer.MAX_VALUE, -54, Integer.MIN_VALUE},
                {Integer.MIN_VALUE, 0, Integer.MAX_VALUE},
                {17, 255, -31},
                {-1, Integer.MIN_VALUE, 1},
                {1, Integer.MAX_VALUE, -1}
        };
        for (int[] coordinate : coordinates) {
            int x = coordinate[0];
            int y = coordinate[1];
            int z = coordinate[2];
            Aquifer.FluidStatus status = picker.computeFluid(x, y, z);

            assertSame(expected, status,
                    "ETF void fluid picker must reuse its immutable status");
            assertTrue(status.at(y).is(Blocks.AIR),
                    "ETF void fluid picker must return air at " + x + "," + y + "," + z);
            assertFalse(status.at(y).is(Blocks.WATER));
            assertFalse(status.at(y).is(Blocks.LAVA));
            assertTrue(status.at(y).getFluidState().isEmpty());
        }
    }

    @Test
    void exposesOneSharedPickerInstance() {
        assertSame(EndVoidFluidPicker.picker(), EndVoidFluidPicker.picker());
    }
}
