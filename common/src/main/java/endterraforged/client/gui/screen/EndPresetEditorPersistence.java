package endterraforged.client.gui.screen;

import java.util.Objects;

import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetStorage;

/**
 * Persistence adapter shared by create-world and existing-world editor flows.
 */
final class EndPresetEditorPersistence {

    private EndPresetEditorPersistence() {
    }

    enum SaveResult {
        NOT_REQUIRED,
        SAVED,
        WRITE_FAILED
    }

    static SaveResult saveActiveIfWorldDirectoryAvailable(
            EndPresetEditorContext context, EndPreset preset) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(preset, "preset");
        if (context.worldDir().isEmpty()) {
            return SaveResult.NOT_REQUIRED;
        }
        return EndPresetStorage.save(context.worldDir().orElseThrow(), preset)
                ? SaveResult.SAVED
                : SaveResult.WRITE_FAILED;
    }
}
