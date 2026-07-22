package endterraforged.client.gui.screen;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import endterraforged.world.config.EndPreset;

/**
 * Pure Done-button policy that prevents a failed disk write from publishing a
 * partially committed existing-world preset.
 */
final class EndPresetEditorFinishPolicy {

    static final String SAVE_FAILED_KEY = "endterraforged.gui.preset_editor.save_failed";

    private EndPresetEditorFinishPolicy() {
    }

    enum Status {
        FINISHED,
        VALIDATION_FAILED,
        SAVE_FAILED
    }

    record FinishResult(Status status, String message) {

        FinishResult {
            Objects.requireNonNull(status, "status");
            if (status == Status.FINISHED && message != null) {
                throw new IllegalArgumentException("finished result must not carry a message");
            }
            if (status != Status.FINISHED && message == null) {
                throw new IllegalArgumentException("failed result must carry a message");
            }
        }
    }

    static FinishResult finish(
            Supplier<EndPreset> presetSupplier,
            EndPresetEditorContext context,
            Consumer<EndPreset> publisher,
            Runnable onDone) {
        Objects.requireNonNull(presetSupplier, "presetSupplier");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(publisher, "publisher");

        try {
            EndPreset preset = presetSupplier.get();
            EndPresetEditorPersistence.SaveResult saveResult =
                    EndPresetEditorPersistence.saveActiveIfWorldDirectoryAvailable(context, preset);
            if (saveResult == EndPresetEditorPersistence.SaveResult.WRITE_FAILED) {
                return new FinishResult(Status.SAVE_FAILED, SAVE_FAILED_KEY);
            }
            publisher.accept(preset);
            if (onDone != null) {
                onDone.run();
            }
            return new FinishResult(Status.FINISHED, null);
        } catch (IllegalStateException e) {
            return new FinishResult(Status.VALIDATION_FAILED, e.getMessage());
        }
    }
}
