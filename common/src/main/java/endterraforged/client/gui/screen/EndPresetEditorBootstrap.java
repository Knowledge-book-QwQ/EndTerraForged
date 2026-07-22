package endterraforged.client.gui.screen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetStorage;

/**
 * Loads the initial editor snapshot for an entry point with a known world
 * directory without silently replacing a corrupt active preset with defaults.
 */
public final class EndPresetEditorBootstrap {

    private EndPresetEditorBootstrap() {
    }

    public enum InitialPresetStatus {
        STORED_PRESET,
        FALLBACK_PRESET,
        INVALID_STORED_FILE
    }

    public record InitialPresetResult(InitialPresetStatus status, Optional<EndPreset> preset) {

        public InitialPresetResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(preset, "preset");
            if (status == InitialPresetStatus.INVALID_STORED_FILE && preset.isPresent()) {
                throw new IllegalArgumentException("invalid stored preset result must not contain a preset");
            }
            if (status != InitialPresetStatus.INVALID_STORED_FILE && preset.isEmpty()) {
                throw new IllegalArgumentException("openable preset result must contain a preset");
            }
        }

        public boolean canOpenEditor() {
            return preset.isPresent();
        }
    }

    public static InitialPresetResult loadInitialPreset(
            Path worldDir, Supplier<EndPreset> fallbackPreset, Consumer<String> errorHandler) {
        Objects.requireNonNull(worldDir, "worldDir");
        Objects.requireNonNull(fallbackPreset, "fallbackPreset");
        Objects.requireNonNull(errorHandler, "errorHandler");

        Optional<EndPreset> stored = EndPresetStorage.load(worldDir, errorHandler);
        if (stored.isPresent()) {
            return new InitialPresetResult(InitialPresetStatus.STORED_PRESET, stored);
        }
        if (Files.isRegularFile(EndPresetStorage.presetFile(worldDir))) {
            return new InitialPresetResult(InitialPresetStatus.INVALID_STORED_FILE, Optional.empty());
        }
        return new InitialPresetResult(
                InitialPresetStatus.FALLBACK_PRESET,
                Optional.of(Objects.requireNonNull(fallbackPreset.get(), "fallback preset")));
    }
}
