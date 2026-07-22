package endterraforged.client.gui.screen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import endterraforged.client.gui.screen.SubsurfacePreviewModePolicy.ModeMemory;
import endterraforged.client.gui.screen.SubsurfacePreviewModePolicy.Section;
import endterraforged.world.preview.TerrainPreviewMode;

class SubsurfacePreviewModePolicyTest {

    @Test
    void abyssOnlyAllowsAbyssPreview() {
        assertArrayEquals(new TerrainPreviewMode[] {TerrainPreviewMode.ABYSS},
                SubsurfacePreviewModePolicy.modes(Section.ABYSS));
        assertTrue(SubsurfacePreviewModePolicy.allows(
                Section.ABYSS, TerrainPreviewMode.ABYSS));
        assertFalse(SubsurfacePreviewModePolicy.allows(
                Section.ABYSS, TerrainPreviewMode.CAVES));
    }

    @Test
    void cavesOnlyAllowCavePreviewModes() {
        assertArrayEquals(new TerrainPreviewMode[] {
                TerrainPreviewMode.CAVES,
                TerrainPreviewMode.CAVE_CHAMBERS,
                TerrainPreviewMode.CAVE_NETWORK,
                TerrainPreviewMode.CAVE_RIFTS,
                TerrainPreviewMode.CAVE_FLOWS,
                TerrainPreviewMode.CAVE_DEPTH,
                TerrainPreviewMode.CAVE_WATER,
                TerrainPreviewMode.CAVE_LAVA
        }, SubsurfacePreviewModePolicy.modes(Section.CAVES));
        assertTrue(SubsurfacePreviewModePolicy.allows(
                Section.CAVES, TerrainPreviewMode.CAVE_LAVA));
        assertFalse(SubsurfacePreviewModePolicy.allows(
                Section.CAVES, TerrainPreviewMode.BIOMES));
    }

    @Test
    void modesReturnsDefensiveCopy() {
        TerrainPreviewMode[] modes = SubsurfacePreviewModePolicy.modes(Section.CAVES);
        modes[0] = TerrainPreviewMode.HEIGHT;

        assertEquals(TerrainPreviewMode.CAVES,
                SubsurfacePreviewModePolicy.modes(Section.CAVES)[0]);
    }

    @Test
    void normalizeFallsBackToSectionDefault() {
        assertEquals(TerrainPreviewMode.ABYSS,
                SubsurfacePreviewModePolicy.normalize(Section.ABYSS, TerrainPreviewMode.CAVE_DEPTH));
        assertEquals(TerrainPreviewMode.CAVES,
                SubsurfacePreviewModePolicy.normalize(Section.CAVES, TerrainPreviewMode.ABYSS));
    }

    @Test
    void modeMemoryRemembersOnlyAllowedModes() {
        ModeMemory memory = ModeMemory.DEFAULT
                .remember(Section.CAVES, TerrainPreviewMode.CAVE_WATER)
                .remember(Section.ABYSS, TerrainPreviewMode.CAVES);

        assertEquals(TerrainPreviewMode.ABYSS, memory.modeFor(Section.ABYSS));
        assertEquals(TerrainPreviewMode.CAVE_WATER, memory.modeFor(Section.CAVES));
    }

    @Test
    void modeMemoryNormalizesInvalidConstructorInput() {
        ModeMemory memory = new ModeMemory(TerrainPreviewMode.WIND, TerrainPreviewMode.ABYSS);

        assertEquals(TerrainPreviewMode.ABYSS, memory.modeFor(Section.ABYSS));
        assertEquals(TerrainPreviewMode.CAVES, memory.modeFor(Section.CAVES));
    }

    @Test
    void slicePreviewOnlyBelongsToCavesSection() {
        assertFalse(SubsurfacePreviewModePolicy.showsSlicePreview(Section.ABYSS));
        assertTrue(SubsurfacePreviewModePolicy.showsSlicePreview(Section.CAVES));
    }
}
