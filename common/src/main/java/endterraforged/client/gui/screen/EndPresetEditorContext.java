package endterraforged.client.gui.screen;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import endterraforged.world.config.EndPreset;
import endterraforged.world.config.WorldVerticalBounds;

/**
 * Persistence context for a preset-editor session.
 *
 * <p>Create-world editing has no stable save directory yet, so it publishes an
 * in-memory preset only. Existing-world editing carries the resolved world
 * directory and may persist the active preset before publishing it.</p>
 */
public record EndPresetEditorContext(
        Optional<Path> worldDir,
        Optional<WorldVerticalBounds> worldBounds) {

    public EndPresetEditorContext {
        Objects.requireNonNull(worldDir, "worldDir");
        Objects.requireNonNull(worldBounds, "worldBounds");
        worldDir = worldDir.map(path -> Objects.requireNonNull(path, "worldDir path").normalize());
    }

    public EndPresetEditorContext(Optional<Path> worldDir) {
        this(worldDir, Optional.empty());
    }

    public static EndPresetEditorContext createWorld() {
        return createWorld(EndPreset.defaults().worldBounds());
    }

    public static EndPresetEditorContext createWorld(WorldVerticalBounds worldBounds) {
        return new EndPresetEditorContext(
                Optional.empty(), Optional.of(Objects.requireNonNull(worldBounds, "worldBounds")));
    }

    public static EndPresetEditorContext forWorld(Path worldDir) {
        return new EndPresetEditorContext(
                Optional.of(Objects.requireNonNull(worldDir, "worldDir")), Optional.empty());
    }

    public static EndPresetEditorContext forWorld(
            Path worldDir, WorldVerticalBounds worldBounds) {
        return new EndPresetEditorContext(
                Optional.of(Objects.requireNonNull(worldDir, "worldDir")),
                Optional.of(Objects.requireNonNull(worldBounds, "worldBounds")));
    }

    public boolean hasWorldDir() {
        return worldDir.isPresent();
    }

    /** Creation sessions may resize the End; existing worlds cannot be resized in place. */
    public boolean canEditWorldBounds() {
        return worldDir.isEmpty();
    }
}
