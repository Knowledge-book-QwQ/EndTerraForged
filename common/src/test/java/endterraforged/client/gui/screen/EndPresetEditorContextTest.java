package endterraforged.client.gui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import endterraforged.world.config.WorldVerticalBounds;

class EndPresetEditorContextTest {

    @TempDir
    Path tempDir;

    @Test
    void createWorldHasNoPersistenceDirectory() {
        EndPresetEditorContext context = EndPresetEditorContext.createWorld();

        assertFalse(context.hasWorldDir());
        assertTrue(context.canEditWorldBounds());
        assertTrue(context.worldDir().isEmpty());
        assertEquals(-256, context.worldBounds().orElseThrow().minY());
        assertEquals(255, context.worldBounds().orElseThrow().maxYInclusive());
    }

    @Test
    void existingWorldNormalizesPersistenceDirectory() {
        EndPresetEditorContext context =
                EndPresetEditorContext.forWorld(tempDir.resolve("world").resolve(".."));

        assertTrue(context.hasWorldDir());
        assertFalse(context.canEditWorldBounds());
        assertEquals(tempDir, context.worldDir().orElseThrow());
    }

    @Test
    void existingWorldCarriesActualVerticalBoundsWhenAvailable() {
        WorldVerticalBounds bounds = new WorldVerticalBounds(-256, 512);
        EndPresetEditorContext context = EndPresetEditorContext.forWorld(tempDir, bounds);

        assertEquals(bounds, context.worldBounds().orElseThrow());
        assertEquals(255, context.worldBounds().orElseThrow().maxYInclusive());
    }
}
