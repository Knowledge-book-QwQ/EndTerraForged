package endterraforged.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Aquifer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import endterraforged.world.config.SeaMode;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetBuilder;
import endterraforged.world.config.TestProfile;
import endterraforged.world.config.TopologyMode;
import endterraforged.world.heightmap.EndDensity;
import endterraforged.world.heightmap.EndHeightmap;

class EndOceanFluidPickerTest {

    private static final int SEED = 42;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void seaModesFillOnlyExteriorOceanColumns() {
        for (SeaMode mode : new SeaMode[]{SeaMode.WITH_FLOOR, SeaMode.NO_FLOOR}) {
            EndHeightmap map = map(mode);
            EndOceanFluidPicker picker = new EndOceanFluidPicker(new EndDensity(map), SEED);
            Sample ocean = findSample(map, false);
            Sample land = findSample(map, true);
            int waterY = map.levels().surfaceY - 1;

            assertTrue(picker.computeFluid(ocean.x(), waterY, ocean.z()).at(waterY).is(Blocks.WATER),
                    mode + " must place water in exterior ocean columns below sea level");
            assertTrue(picker.computeFluid(ocean.x(), map.levels().surfaceY, ocean.z())
                            .at(map.levels().surfaceY).is(Blocks.AIR),
                    mode + " must leave blocks at and above the fluid level as air");
            assertTrue(picker.computeFluid(land.x(), waterY, land.z()).at(waterY).is(Blocks.AIR),
                    mode + " must not flood continental caves or shelf voids");
            assertFalse(picker.computeFluid(ocean.x(), waterY, ocean.z()).at(waterY).is(Blocks.LAVA));
        }
    }

    @Test
    void noSeaAndCentralProtectionRemainDry() {
        EndHeightmap noSeaMap = map(SeaMode.NONE);
        EndOceanFluidPicker noSea = new EndOceanFluidPicker(new EndDensity(noSeaMap), SEED);
        Sample ocean = findSample(noSeaMap, false);
        int waterY = noSeaMap.levels().surfaceY - 1;

        assertTrue(noSea.computeFluid(ocean.x(), waterY, ocean.z()).at(waterY).is(Blocks.AIR));

        EndHeightmap seaMap = map(SeaMode.WITH_FLOOR);
        EndOceanFluidPicker sea = new EndOceanFluidPicker(new EndDensity(seaMap), SEED);
        Aquifer.FluidStatus central = sea.computeFluid(0, seaMap.levels().surfaceY - 1, 0);
        assertTrue(central.at(seaMap.levels().surfaceY - 1).is(Blocks.AIR),
                "the frozen vanilla central region must not be flooded by ETF sea mode");
    }

    @Test
    void finiteShelvesReceiveWaterBelowTheirUndersideButNotInsideTheirBody() {
        EndPreset preset = new EndPresetBuilder(EndPreset.defaults())
                .seaMode(SeaMode.NO_FLOOR)
                .build();
        EndHeightmap map = new EndHeightmap(preset, SEED);
        EndDensity density = new EndDensity(map);
        EndOceanFluidPicker picker = new EndOceanFluidPicker(density, SEED);
        Sample land = findSample(map, true);
        float landness = map.getLandness(land.x(), land.z(), SEED);
        float terrainTop = map.getHeight(land.x(), land.z(), SEED);
        float underside = map.landmassVolume().underside(
                land.x(), land.z(), landness, terrainTop);
        int belowUndersideY = map.levels().scale(underside) - 1;
        int insideShelfY = Math.min(
                map.levels().surfaceY - 1,
                map.levels().scale(underside) + 1);

        assertTrue(picker.computeFluid(land.x(), belowUndersideY, land.z())
                        .at(belowUndersideY).is(Blocks.WATER),
                "floorless sea must immerse the open space below a finite continental shelf");
        assertTrue(picker.computeFluid(land.x(), insideShelfY, land.z())
                        .at(insideShelfY).is(Blocks.AIR),
                "shelf-interior cave space must not be classified as exterior ocean");
    }

    @Test
    void flooredSeaDoesNotFloodCavesBelowItsContinuousSeabed() {
        EndPreset preset = new EndPresetBuilder(EndPreset.defaults())
                .seaMode(SeaMode.WITH_FLOOR)
                .build();
        EndHeightmap map = new EndHeightmap(preset, SEED);
        EndDensity density = new EndDensity(map);
        EndOceanFluidPicker picker = new EndOceanFluidPicker(density, SEED);
        Sample land = findSample(map, true);
        int deepY = map.levels().minY + 1;

        assertTrue(picker.computeFluid(land.x(), deepY, land.z()).at(deepY).is(Blocks.AIR),
                "deep carved space below a floored sea must retain cave fluid semantics");
    }

    private static EndHeightmap map(SeaMode mode) {
        return new EndHeightmap(new TestProfile(
                512, -256, 0, 0, mode, TopologyMode.ISLANDS, false), SEED);
    }

    private static Sample findSample(EndHeightmap map, boolean land) {
        for (int z = 4096; z <= 16384; z += 128) {
            for (int x = 4096; x <= 16384; x += 128) {
                boolean isLand = map.getLandness(x, z, SEED) > 0.0F;
                if (isLand == land) {
                    return new Sample(x, z);
                }
            }
        }
        throw new AssertionError("no " + (land ? "land" : "ocean") + " sample found");
    }

    private record Sample(int x, int z) {
    }
}
