package endterraforged.world.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Result-preserving operations for a world's named preset library and exchange
 * directory.
 *
 * <p>The active preset remains separate from named files: loading or importing
 * a snapshot only returns it to the editor. The editor's Done flow is the
 * single point that applies a snapshot to the active world preset.</p>
 */
public final class EndPresetLibrary {

    private EndPresetLibrary() {
    }

    public enum SaveResult {
        SAVED,
        INVALID_NAME,
        WRITE_FAILED
    }

    public enum LoadStatus {
        LOADED,
        INVALID_NAME,
        SOURCE_MISSING,
        DECODE_FAILED
    }

    public record LoadResult(LoadStatus status, Optional<EndPreset> preset) {

        public LoadResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(preset, "preset");
            if (status == LoadStatus.LOADED && preset.isEmpty()) {
                throw new IllegalArgumentException("loaded result must contain a preset");
            }
            if (status != LoadStatus.LOADED && preset.isPresent()) {
                throw new IllegalArgumentException("failed load result must not contain a preset");
            }
        }
    }

    public enum DeleteResult {
        DELETED,
        INVALID_NAME,
        SOURCE_MISSING,
        DELETE_FAILED
    }

    public enum ExportResult {
        EXPORTED,
        INVALID_NAME,
        WRITE_FAILED
    }

    public enum ImportStatus {
        IMPORTED,
        INVALID_NAME,
        SOURCE_MISSING,
        DECODE_FAILED
    }

    public record ImportResult(ImportStatus status, Optional<EndPreset> preset) {

        public ImportResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(preset, "preset");
            if (status == ImportStatus.IMPORTED && preset.isEmpty()) {
                throw new IllegalArgumentException("imported result must contain a preset");
            }
            if (status != ImportStatus.IMPORTED && preset.isPresent()) {
                throw new IllegalArgumentException("failed import result must not contain a preset");
            }
        }
    }

    public static SaveResult saveSnapshot(Path worldDir, String name, EndPreset preset) {
        Objects.requireNonNull(worldDir, "worldDir");
        Objects.requireNonNull(preset, "preset");
        Optional<String> normalizedName = EndPresetStorage.normalizePresetName(name);
        if (normalizedName.isEmpty()) {
            return SaveResult.INVALID_NAME;
        }
        return EndPresetStorage.saveNamed(worldDir, normalizedName.orElseThrow(), preset)
                ? SaveResult.SAVED
                : SaveResult.WRITE_FAILED;
    }

    public static LoadResult loadSnapshot(Path worldDir, String name, Consumer<String> errorHandler) {
        Objects.requireNonNull(worldDir, "worldDir");
        Objects.requireNonNull(errorHandler, "errorHandler");
        Optional<String> normalizedName = EndPresetStorage.normalizePresetName(name);
        if (normalizedName.isEmpty()) {
            return new LoadResult(LoadStatus.INVALID_NAME, Optional.empty());
        }
        String logicalName = normalizedName.orElseThrow();
        if (!Files.isRegularFile(EndPresetStorage.namedPresetFile(worldDir, logicalName))) {
            return new LoadResult(LoadStatus.SOURCE_MISSING, Optional.empty());
        }
        Optional<EndPreset> preset = EndPresetStorage.loadNamed(worldDir, logicalName, errorHandler);
        return preset
                .<LoadResult>map(value -> new LoadResult(LoadStatus.LOADED, Optional.of(value)))
                .orElseGet(() -> new LoadResult(LoadStatus.DECODE_FAILED, Optional.empty()));
    }

    public static DeleteResult deleteSnapshot(Path worldDir, String name) {
        Objects.requireNonNull(worldDir, "worldDir");
        Optional<String> normalizedName = EndPresetStorage.normalizePresetName(name);
        if (normalizedName.isEmpty()) {
            return DeleteResult.INVALID_NAME;
        }
        String logicalName = normalizedName.orElseThrow();
        if (!Files.isRegularFile(EndPresetStorage.namedPresetFile(worldDir, logicalName))) {
            return DeleteResult.SOURCE_MISSING;
        }
        return EndPresetStorage.deleteNamed(worldDir, logicalName)
                ? DeleteResult.DELETED
                : DeleteResult.DELETE_FAILED;
    }

    public static ExportResult exportSnapshot(Path worldDir, String name, EndPreset preset) {
        Objects.requireNonNull(worldDir, "worldDir");
        Objects.requireNonNull(preset, "preset");
        Optional<String> normalizedName = EndPresetStorage.normalizePresetName(name);
        if (normalizedName.isEmpty()) {
            return ExportResult.INVALID_NAME;
        }
        return EndPresetStorage.exportTo(
                EndPresetStorage.exchangePresetFile(worldDir, normalizedName.orElseThrow()), preset)
                ? ExportResult.EXPORTED
                : ExportResult.WRITE_FAILED;
    }

    public static ImportResult importSnapshot(Path worldDir, String name, Consumer<String> errorHandler) {
        Objects.requireNonNull(worldDir, "worldDir");
        Objects.requireNonNull(errorHandler, "errorHandler");
        Optional<String> normalizedName = EndPresetStorage.normalizePresetName(name);
        if (normalizedName.isEmpty()) {
            return new ImportResult(ImportStatus.INVALID_NAME, Optional.empty());
        }
        Path file = EndPresetStorage.exchangePresetFile(worldDir, normalizedName.orElseThrow());
        if (!Files.isRegularFile(file)) {
            return new ImportResult(ImportStatus.SOURCE_MISSING, Optional.empty());
        }
        Optional<EndPreset> preset = EndPresetStorage.importFrom(file, errorHandler);
        return preset
                .<ImportResult>map(value -> new ImportResult(ImportStatus.IMPORTED, Optional.of(value)))
                .orElseGet(() -> new ImportResult(ImportStatus.DECODE_FAILED, Optional.empty()));
    }
}
