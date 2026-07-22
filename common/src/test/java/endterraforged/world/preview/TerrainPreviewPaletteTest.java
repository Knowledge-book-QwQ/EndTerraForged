package endterraforged.world.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.world.heightmap.EndTerrainBlend;
import endterraforged.world.heightmap.EndTerrainLayer;
import endterraforged.world.level.biome.EndBiomeLayout;

class TerrainPreviewPaletteTest {

    @Test
    void visibleLandThresholdClassifiesTinyValuesAsVoid() {
        assertFalse(TerrainPreviewPalette.isVisibleLand(0.0F));
        assertFalse(TerrainPreviewPalette.isVisibleLand(TerrainPreviewPalette.VISIBLE_LAND_THRESHOLD));
        assertTrue(TerrainPreviewPalette.isVisibleLand(TerrainPreviewPalette.VISIBLE_LAND_THRESHOLD + 0.001F));
    }

    @Test
    void layerColorsAreStableAndDistinct() {
        assertEquals(0, TerrainPreviewPalette.layerColor(EndTerrainLayer.NONE));
        assertNotEquals(
                TerrainPreviewPalette.layerColor(EndTerrainLayer.PLAINS),
                TerrainPreviewPalette.layerColor(EndTerrainLayer.HILLS));
        assertNotEquals(
                TerrainPreviewPalette.layerColor(EndTerrainLayer.PLATEAU),
                TerrainPreviewPalette.layerColor(EndTerrainLayer.VOLCANO));
        assertNotEquals(
                TerrainPreviewPalette.layerColor(EndTerrainLayer.MOUNTAINS),
                TerrainPreviewPalette.layerColor(EndTerrainLayer.VOLCANO));
    }

    @Test
    void modeColorsHaveSeparateResponsibilities() {
        int combined = TerrainPreviewPalette.color(1.0F, 0.5F,
                EndTerrainLayer.VOLCANO, TerrainPreviewMode.COMBINED);
        int height = TerrainPreviewPalette.color(1.0F, 0.5F,
                EndTerrainLayer.VOLCANO, TerrainPreviewMode.HEIGHT);
        int landness = TerrainPreviewPalette.color(1.0F, 0.5F,
                EndTerrainLayer.VOLCANO, TerrainPreviewMode.LANDNESS);
        int layers = TerrainPreviewPalette.color(1.0F, 0.5F,
                EndTerrainLayer.VOLCANO, TerrainPreviewMode.LAYERS);
        int biomes = TerrainPreviewPalette.color(1.0F, 0.5F,
                EndTerrainLayer.VOLCANO, TerrainPreviewMode.BIOMES);
        int biomeClimate = TerrainPreviewPalette.color(1.0F, 0.5F,
                EndTerrainBlend.single(EndTerrainLayer.VOLCANO),
                TerrainPreviewMode.BIOME_CLIMATE, 0.9F, 0.9F, 0.0F);
        int caves = TerrainPreviewPalette.color(1.0F, 0.5F,
                EndTerrainLayer.VOLCANO, TerrainPreviewMode.CAVES);
        int caveChambers = TerrainPreviewPalette.color(1.0F, 0.5F,
                EndTerrainLayer.VOLCANO, TerrainPreviewMode.CAVE_CHAMBERS);
        int caveNetwork = TerrainPreviewPalette.color(1.0F, 0.5F,
                EndTerrainLayer.VOLCANO, TerrainPreviewMode.CAVE_NETWORK);
        int caveRifts = TerrainPreviewPalette.color(1.0F, 0.5F,
                EndTerrainLayer.VOLCANO, TerrainPreviewMode.CAVE_RIFTS);
        int caveFlows = TerrainPreviewPalette.color(1.0F, 0.5F,
                EndTerrainLayer.VOLCANO, TerrainPreviewMode.CAVE_FLOWS);
        int caveWater = TerrainPreviewPalette.color(1.0F, 0.5F,
                EndTerrainLayer.VOLCANO, TerrainPreviewMode.CAVE_WATER);
        int caveLava = TerrainPreviewPalette.color(1.0F, 0.5F,
                EndTerrainLayer.VOLCANO, TerrainPreviewMode.CAVE_LAVA);
        int caveDepth = TerrainPreviewPalette.color(1.0F, 0.5F,
                EndTerrainLayer.VOLCANO, TerrainPreviewMode.CAVE_DEPTH);
        int abyss = TerrainPreviewPalette.color(1.0F, 0.5F,
                EndTerrainLayer.VOLCANO, TerrainPreviewMode.ABYSS);

        assertNotEquals(combined, height);
        assertNotEquals(height, landness);
        assertEquals(TerrainPreviewPalette.layerColor(EndTerrainLayer.VOLCANO), layers);
        assertEquals(TerrainPreviewPalette.biomeColor(1.0F, 0), biomes);
        assertNotEquals(layers, biomes);
        assertNotEquals(biomes, biomeClimate);
        assertEquals(TerrainPreviewPalette.biomeClimateColor(1.0F,
                EndBiomeLayout.Ring.END, 0.9F, 0.9F), biomeClimate);
        assertEquals(TerrainPreviewPalette.caveColor(1.0F, 0.0F), caves);
        assertEquals(caves, caveChambers);
        assertEquals(caves, caveNetwork);
        assertEquals(caves, caveRifts);
        assertEquals(caves, caveFlows);
        assertEquals(TerrainPreviewPalette.caveWaterColor(1.0F, 0.0F), caveWater);
        assertEquals(TerrainPreviewPalette.caveLavaColor(1.0F, 0.0F), caveLava);
        assertEquals(TerrainPreviewPalette.caveDepthColor(1.0F, 0.0F, 0.0F), caveDepth);
        assertNotEquals(caves, abyss);
        assertEquals(TerrainPreviewPalette.abyssColor(1.0F, 0.0F), abyss);
    }

    @Test
    void layerBlendColorsInterpolateAdjacentLayerTints() {
        int plains = TerrainPreviewPalette.layerColor(EndTerrainLayer.PLAINS);
        int hills = TerrainPreviewPalette.layerColor(EndTerrainLayer.HILLS);
        int blended = TerrainPreviewPalette.layerColor(
                new EndTerrainBlend(EndTerrainLayer.PLAINS, EndTerrainLayer.HILLS, 0.5F));

        assertEquals(plains, TerrainPreviewPalette.layerColor(
                EndTerrainBlend.single(EndTerrainLayer.PLAINS)));
        assertNotEquals(plains, blended);
        assertNotEquals(hills, blended);
        assertEquals(blended, TerrainPreviewPalette.color(1.0F, 0.5F,
                new EndTerrainBlend(EndTerrainLayer.PLAINS, EndTerrainLayer.HILLS, 0.5F),
                TerrainPreviewMode.LAYERS));
    }

    @Test
    void abyssColorsTrackConfiguredStrength() {
        int land = TerrainPreviewPalette.abyssColor(1.0F, 0.0F);
        int rim = TerrainPreviewPalette.abyssColor(1.0F, 0.45F);
        int core = TerrainPreviewPalette.abyssColor(1.0F, 1.0F);
        int voidColor = TerrainPreviewPalette.color(0.0F, 0.5F,
                EndTerrainLayer.NONE, TerrainPreviewMode.ABYSS);

        assertNotEquals(land, rim);
        assertNotEquals(rim, core);
        assertEquals(TerrainPreviewPalette.abyssColor(0.0F, 1.0F), voidColor);
    }

    @Test
    void caveColorsTrackConfiguredStrength() {
        int land = TerrainPreviewPalette.caveColor(1.0F, 0.0F);
        int passage = TerrainPreviewPalette.caveColor(1.0F, 0.62F);
        int chamber = TerrainPreviewPalette.caveColor(1.0F, 1.0F);
        int voidColor = TerrainPreviewPalette.color(0.0F, 0.5F,
                EndTerrainLayer.NONE, TerrainPreviewMode.CAVES);

        assertNotEquals(land, passage);
        assertNotEquals(passage, chamber);
        assertEquals(TerrainPreviewPalette.caveColor(0.0F, 1.0F), voidColor);
    }

    @Test
    void caveDepthColorsTrackStrengthAndDepth() {
        int land = TerrainPreviewPalette.caveDepthColor(1.0F, 0.0F, 0.0F);
        int shallow = TerrainPreviewPalette.caveDepthColor(1.0F, 1.0F, 0.1F);
        int deep = TerrainPreviewPalette.caveDepthColor(1.0F, 1.0F, 0.9F);
        int weakDeep = TerrainPreviewPalette.caveDepthColor(1.0F, 0.25F, 0.9F);
        int voidColor = TerrainPreviewPalette.caveDepthColor(0.0F, 1.0F, 0.9F);

        assertNotEquals(land, shallow);
        assertNotEquals(shallow, deep);
        assertNotEquals(deep, weakDeep);
        assertEquals(TerrainPreviewPalette.caveColor(0.0F, 1.0F), voidColor);
    }

    @Test
    void caveLiquidColorsTrackStrengthAndLiquidType() {
        int land = TerrainPreviewPalette.caveWaterColor(1.0F, 0.0F);
        int water = TerrainPreviewPalette.caveWaterColor(1.0F, 1.0F);
        int lava = TerrainPreviewPalette.caveLavaColor(1.0F, 1.0F);
        int weakLava = TerrainPreviewPalette.caveLavaColor(1.0F, 0.25F);
        int voidColor = TerrainPreviewPalette.caveWaterColor(0.0F, 1.0F);

        assertNotEquals(land, water);
        assertNotEquals(water, lava);
        assertNotEquals(lava, weakLava);
        assertEquals(TerrainPreviewPalette.caveColor(0.0F, 1.0F), voidColor);
    }

    @Test
    void upliftColorsTrackTheMacroReliefSignal() {
        int low = TerrainPreviewPalette.upliftColor(1.0F, 0.0F);
        int high = TerrainPreviewPalette.upliftColor(1.0F, 1.0F);
        int voidColor = TerrainPreviewPalette.upliftColor(0.0F, 1.0F);

        assertTrue(low != high);
        assertEquals(TerrainPreviewPalette.upliftColor(0.0F, 0.0F), voidColor);
    }

    @Test
    void biomeClimateColorsTrackRingTemperatureAndMoistureBands() {
        int coldDryHighlands = TerrainPreviewPalette.biomeClimateColor(1.0F,
                EndBiomeLayout.Ring.HIGHLANDS, 0.1F, 0.1F);
        int hotWetHighlands = TerrainPreviewPalette.biomeClimateColor(1.0F,
                EndBiomeLayout.Ring.HIGHLANDS, 0.9F, 0.9F);
        int coldDryBarrens = TerrainPreviewPalette.biomeClimateColor(1.0F,
                EndBiomeLayout.Ring.BARRENS, 0.1F, 0.1F);
        int voidColor = TerrainPreviewPalette.biomeClimateColor(0.0F,
                EndBiomeLayout.Ring.HIGHLANDS, 0.9F, 0.9F);

        assertNotEquals(coldDryHighlands, hotWetHighlands);
        assertNotEquals(coldDryHighlands, coldDryBarrens);
        assertEquals(TerrainPreviewPalette.caveColor(0.0F, 1.0F), voidColor);
    }
}
