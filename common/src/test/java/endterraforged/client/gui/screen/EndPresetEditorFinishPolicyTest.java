package endterraforged.client.gui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetStorage;

class EndPresetEditorFinishPolicyTest {

    @TempDir
    Path tempDir;

    @Test
    void createWorldPublishesWithoutWritingToDisk() {
        AtomicReference<EndPreset> published = new AtomicReference<>();
        AtomicInteger doneCalls = new AtomicInteger();

        EndPresetEditorFinishPolicy.FinishResult result = EndPresetEditorFinishPolicy.finish(
                EndPreset::defaults,
                EndPresetEditorContext.createWorld(),
                published::set,
                doneCalls::incrementAndGet);

        assertEquals(EndPresetEditorFinishPolicy.Status.FINISHED, result.status());
        assertEquals(EndPreset.defaults(), published.get());
        assertEquals(1, doneCalls.get());
        assertFalse(Files.exists(EndPresetStorage.presetFile(tempDir)));
    }

    @Test
    void existingWorldPublishesOnlyAfterSuccessfulSave() {
        AtomicReference<EndPreset> published = new AtomicReference<>();
        AtomicInteger doneCalls = new AtomicInteger();

        EndPresetEditorFinishPolicy.FinishResult result = EndPresetEditorFinishPolicy.finish(
                EndPreset::defaults,
                EndPresetEditorContext.forWorld(tempDir),
                published::set,
                doneCalls::incrementAndGet);

        assertEquals(EndPresetEditorFinishPolicy.Status.FINISHED, result.status());
        assertEquals(EndPreset.defaults(), published.get());
        assertEquals(1, doneCalls.get());
        assertEquals(EndPreset.defaults(), EndPresetStorage.load(tempDir).orElseThrow());
    }

    @Test
    void failedSaveDoesNotPublishOrClose() throws IOException {
        Path nonDirectory = tempDir.resolve("not-a-world-directory");
        Files.writeString(nonDirectory, "file");
        AtomicReference<EndPreset> published = new AtomicReference<>();
        AtomicInteger doneCalls = new AtomicInteger();

        EndPresetEditorFinishPolicy.FinishResult result = EndPresetEditorFinishPolicy.finish(
                EndPreset::defaults,
                EndPresetEditorContext.forWorld(nonDirectory),
                published::set,
                doneCalls::incrementAndGet);

        assertEquals(EndPresetEditorFinishPolicy.Status.SAVE_FAILED, result.status());
        assertEquals(EndPresetEditorFinishPolicy.SAVE_FAILED_KEY, result.message());
        assertEquals(null, published.get());
        assertEquals(0, doneCalls.get());
    }

    @Test
    void validationFailureDoesNotPublishOrClose() {
        AtomicReference<EndPreset> published = new AtomicReference<>();
        AtomicInteger doneCalls = new AtomicInteger();

        EndPresetEditorFinishPolicy.FinishResult result = EndPresetEditorFinishPolicy.finish(
                () -> {
                    throw new IllegalStateException("invalid preset");
                },
                EndPresetEditorContext.createWorld(),
                published::set,
                doneCalls::incrementAndGet);

        assertEquals(EndPresetEditorFinishPolicy.Status.VALIDATION_FAILED, result.status());
        assertEquals("invalid preset", result.message());
        assertEquals(null, published.get());
        assertEquals(0, doneCalls.get());
    }
}
