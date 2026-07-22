package endterraforged.client.gui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetStorage;

class EndPresetEditorPersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    void createWorldDoesNotWriteAnActivePreset() {
        EndPresetEditorPersistence.SaveResult result =
                EndPresetEditorPersistence.saveActiveIfWorldDirectoryAvailable(
                        EndPresetEditorContext.createWorld(), EndPreset.defaults());

        assertEquals(EndPresetEditorPersistence.SaveResult.NOT_REQUIRED, result);
        assertFalse(Files.exists(EndPresetStorage.presetFile(tempDir)));
    }

    @Test
    void existingWorldWritesTheActivePreset() {
        EndPresetEditorPersistence.SaveResult result =
                EndPresetEditorPersistence.saveActiveIfWorldDirectoryAvailable(
                        EndPresetEditorContext.forWorld(tempDir), EndPreset.defaults());

        assertEquals(EndPresetEditorPersistence.SaveResult.SAVED, result);
        assertEquals(EndPreset.defaults(), EndPresetStorage.load(tempDir).orElseThrow());
    }

    @Test
    void writeFailureIsReportedWithoutThrowing() throws IOException {
        Path nonDirectory = tempDir.resolve("not-a-world-directory");
        Files.writeString(nonDirectory, "file");

        EndPresetEditorPersistence.SaveResult result =
                EndPresetEditorPersistence.saveActiveIfWorldDirectoryAvailable(
                        EndPresetEditorContext.forWorld(nonDirectory), EndPreset.defaults());

        assertEquals(EndPresetEditorPersistence.SaveResult.WRITE_FAILED, result);
        assertTrue(Files.isRegularFile(nonDirectory));
    }
}
