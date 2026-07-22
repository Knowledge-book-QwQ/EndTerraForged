package endterraforged.client.gui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetStorage;

class EndPresetEditorBootstrapTest {

    @TempDir
    Path worldDir;

    @Test
    void missingActivePresetUsesFallback() {
        AtomicInteger errors = new AtomicInteger();

        EndPresetEditorBootstrap.InitialPresetResult result =
                EndPresetEditorBootstrap.loadInitialPreset(
                        worldDir, EndPreset::defaults, ignored -> errors.incrementAndGet());

        assertEquals(EndPresetEditorBootstrap.InitialPresetStatus.FALLBACK_PRESET, result.status());
        assertEquals(EndPreset.defaults(), result.preset().orElseThrow());
        assertEquals(0, errors.get());
    }

    @Test
    void validActivePresetOverridesFallback() {
        assertTrue(EndPresetStorage.save(worldDir, EndPreset.defaults()));

        EndPresetEditorBootstrap.InitialPresetResult result =
                EndPresetEditorBootstrap.loadInitialPreset(
                        worldDir, () -> null, ignored -> {});

        assertEquals(EndPresetEditorBootstrap.InitialPresetStatus.STORED_PRESET, result.status());
        assertEquals(EndPreset.defaults(), result.preset().orElseThrow());
    }

    @Test
    void malformedActivePresetRefusesToOpenEditor() throws IOException {
        Files.writeString(EndPresetStorage.presetFile(worldDir), "{not-json");
        AtomicInteger errors = new AtomicInteger();

        EndPresetEditorBootstrap.InitialPresetResult result =
                EndPresetEditorBootstrap.loadInitialPreset(
                        worldDir, EndPreset::defaults, ignored -> errors.incrementAndGet());

        assertEquals(EndPresetEditorBootstrap.InitialPresetStatus.INVALID_STORED_FILE, result.status());
        assertFalse(result.canOpenEditor());
        assertTrue(result.preset().isEmpty());
        assertEquals(1, errors.get());
    }

    @Test
    void directoryAtActivePresetPathUsesFallback() throws IOException {
        Files.createDirectory(EndPresetStorage.presetFile(worldDir));

        EndPresetEditorBootstrap.InitialPresetResult result =
                EndPresetEditorBootstrap.loadInitialPreset(worldDir, EndPreset::defaults, ignored -> {});

        assertEquals(EndPresetEditorBootstrap.InitialPresetStatus.FALLBACK_PRESET, result.status());
        assertTrue(result.canOpenEditor());
    }
}
