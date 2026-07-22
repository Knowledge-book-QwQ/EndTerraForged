package endterraforged.world.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EndPresetLibraryTest {

    @TempDir
    Path worldDir;

    @Test
    void namedSnapshotRoundTripsWithoutReplacingActivePreset() {
        assertEquals(EndPresetLibrary.SaveResult.SAVED,
                EndPresetLibrary.saveSnapshot(worldDir, "featured", EndPreset.defaults()));

        EndPresetLibrary.LoadResult result =
                EndPresetLibrary.loadSnapshot(worldDir, "featured", ignored -> {});

        assertEquals(EndPresetLibrary.LoadStatus.LOADED, result.status());
        assertEquals(EndPreset.defaults(), result.preset().orElseThrow());
        assertFalse(Files.exists(EndPresetStorage.presetFile(worldDir)));
    }

    @Test
    void invalidNameIsVisibleForEveryNamedOperation() {
        assertEquals(EndPresetLibrary.SaveResult.INVALID_NAME,
                EndPresetLibrary.saveSnapshot(worldDir, "///", EndPreset.defaults()));
        assertEquals(EndPresetLibrary.LoadStatus.INVALID_NAME,
                EndPresetLibrary.loadSnapshot(worldDir, "///", ignored -> {}).status());
        assertEquals(EndPresetLibrary.DeleteResult.INVALID_NAME,
                EndPresetLibrary.deleteSnapshot(worldDir, "///"));
        assertEquals(EndPresetLibrary.ExportResult.INVALID_NAME,
                EndPresetLibrary.exportSnapshot(worldDir, "///", EndPreset.defaults()));
        assertEquals(EndPresetLibrary.ImportStatus.INVALID_NAME,
                EndPresetLibrary.importSnapshot(worldDir, "///", ignored -> {}).status());
    }

    @Test
    void missingNamedAndExchangeFilesAreDistinguished() {
        assertEquals(EndPresetLibrary.LoadStatus.SOURCE_MISSING,
                EndPresetLibrary.loadSnapshot(worldDir, "missing", ignored -> {}).status());
        assertEquals(EndPresetLibrary.DeleteResult.SOURCE_MISSING,
                EndPresetLibrary.deleteSnapshot(worldDir, "missing"));
        assertEquals(EndPresetLibrary.ImportStatus.SOURCE_MISSING,
                EndPresetLibrary.importSnapshot(worldDir, "missing", ignored -> {}).status());
    }

    @Test
    void corruptNamedAndExchangeFilesReportDecodeFailures() throws IOException {
        Path named = EndPresetStorage.namedPresetFile(worldDir, "broken");
        Files.createDirectories(named.getParent());
        Files.writeString(named, "{not-json");
        Path exchange = EndPresetStorage.exchangePresetFile(worldDir, "broken");
        Files.createDirectories(exchange.getParent());
        Files.writeString(exchange, "{not-json");
        AtomicInteger errors = new AtomicInteger();

        EndPresetLibrary.LoadResult namedResult =
                EndPresetLibrary.loadSnapshot(worldDir, "broken", ignored -> errors.incrementAndGet());
        EndPresetLibrary.ImportResult exchangeResult =
                EndPresetLibrary.importSnapshot(worldDir, "broken", ignored -> errors.incrementAndGet());

        assertEquals(EndPresetLibrary.LoadStatus.DECODE_FAILED, namedResult.status());
        assertEquals(EndPresetLibrary.ImportStatus.DECODE_FAILED, exchangeResult.status());
        assertEquals(2, errors.get());
    }

    @Test
    void exchangeExportAndImportRoundTripWithoutChangingActivePreset() {
        assertEquals(EndPresetLibrary.ExportResult.EXPORTED,
                EndPresetLibrary.exportSnapshot(worldDir, "portable", EndPreset.defaults()));

        EndPresetLibrary.ImportResult result =
                EndPresetLibrary.importSnapshot(worldDir, "portable", ignored -> {});

        assertEquals(EndPresetLibrary.ImportStatus.IMPORTED, result.status());
        assertEquals(Optional.of(EndPreset.defaults()), result.preset());
        assertFalse(Files.exists(EndPresetStorage.presetFile(worldDir)));
    }
}
