package endterraforged.world.heightmap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetBuilder;
import endterraforged.world.config.SeaMode;
import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainConfigBuilder;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.continent.ContinentSignals;
import endterraforged.world.continent.RtfMultiContinent;

class EndTerrainContinuityTest {

    private static final int SEED = 123;
    private static final int CENTER_X = 11069;
    private static final int CENTER_Z = -2573;
    private static final int RADIUS = 192;
    private static final float MAX_ADJACENT_HEIGHT_DELTA = 16.0F;

    @Test
    void shatteredTerrainDoesNotCreateOneColumnMegawallsNearReportedCoordinate() {
        EndPreset preset = reportedPreset();
        EndHeightmap heightmap = new EndHeightmap(preset, SEED);
        float largestDelta = 0.0F;
        int largestX = 0;
        int largestZ = 0;
        int neighborX = 0;
        int neighborZ = 0;

        for (int z = CENTER_Z - RADIUS; z <= CENTER_Z + RADIUS; z++) {
            float previous = heightmap.getTerrainHeight(CENTER_X - RADIUS, z, SEED);
            for (int x = CENTER_X - RADIUS + 1; x <= CENTER_X + RADIUS; x++) {
                float current = heightmap.getTerrainHeight(x, z, SEED);
                float delta = Math.abs(current - previous);
                if (delta > largestDelta) {
                    largestDelta = delta;
                    largestX = x;
                    largestZ = z;
                    neighborX = x - 1;
                    neighborZ = z;
                }
                previous = current;
            }
        }
        for (int x = CENTER_X - RADIUS; x <= CENTER_X + RADIUS; x++) {
            float previous = heightmap.getTerrainHeight(x, CENTER_Z - RADIUS, SEED);
            for (int z = CENTER_Z - RADIUS + 1; z <= CENTER_Z + RADIUS; z++) {
                float current = heightmap.getTerrainHeight(x, z, SEED);
                float delta = Math.abs(current - previous);
                if (delta > largestDelta) {
                    largestDelta = delta;
                    largestX = x;
                    largestZ = z;
                    neighborX = x;
                    neighborZ = z - 1;
                }
                previous = current;
            }
        }

        float blockDelta = largestDelta * preset.worldHeight();
        RtfMultiContinent rawContinent = new RtfMultiContinent(SEED, preset.continentConfig());
        ContinentSignals rawNeighbor = rawContinent.signalsAt(neighborX, neighborZ, SEED);
        ContinentSignals rawLargest = rawContinent.signalsAt(largestX, largestZ, SEED);
        assertTrue(blockDelta <= MAX_ADJACENT_HEIGHT_DELTA,
                "adjacent terrain columns differ by " + blockDelta
                        + " blocks between " + neighborX + "," + neighborZ
                        + " and " + largestX + "," + largestZ
                        + "; landness=" + heightmap.getLandness(neighborX, neighborZ, SEED)
                        + " -> " + heightmap.getLandness(largestX, largestZ, SEED)
                        + "; inlandness="
                        + heightmap.getContinentSignals(neighborX, neighborZ, SEED).inlandness()
                        + " -> "
                        + heightmap.getContinentSignals(largestX, largestZ, SEED).inlandness()
                        + "; terrain=" + heightmap.terrain().compute(neighborX, neighborZ, SEED)
                        + " -> " + heightmap.terrain().compute(largestX, largestZ, SEED)
                        + "; height=" + heightmap.getTerrainHeight(neighborX, neighborZ, SEED)
                        + " -> " + heightmap.getTerrainHeight(largestX, largestZ, SEED)
                        + "; raw edge=" + rawNeighbor.edge() + " -> " + rawLargest.edge()
                        + "; raw landness=" + rawNeighbor.landness() + " -> " + rawLargest.landness());
    }

    private static EndPreset reportedPreset() {
        TerrainConfig defaults = TerrainConfig.DEFAULT;
        TerrainLayerConfig mountains = new TerrainLayerConfig(
                1.61F,
                defaults.mountains().baseScale(),
                defaults.mountains().verticalScale(),
                defaults.mountains().horizontalScale());
        TerrainConfig terrain = new TerrainConfigBuilder(defaults)
                .mountains(mountains)
                .build();
        return new EndPresetBuilder(EndPreset.defaults())
                .seaMode(SeaMode.WITH_FLOOR)
                .terrainConfig(terrain)
                .build();
    }
}
