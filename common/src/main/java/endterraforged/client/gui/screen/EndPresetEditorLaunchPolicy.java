package endterraforged.client.gui.screen;

import java.util.Objects;
import java.util.Optional;

/**
 * Maps existing-world editor bootstrap results to platform UI actions.
 */
public final class EndPresetEditorLaunchPolicy {

    private static final ToastMessage OPEN_FAILED_TOAST = new ToastMessage(
            "endterraforged.gui.preset_editor.open_failed.title",
            "endterraforged.gui.preset_editor.open_failed.description");
    private static final ToastMessage SAVED_TOAST = new ToastMessage(
            "endterraforged.gui.preset_editor.saved.title",
            "endterraforged.gui.preset_editor.saved.description");

    private EndPresetEditorLaunchPolicy() {
    }

    public record ToastMessage(String titleKey, String descriptionKey) {

        public ToastMessage {
            Objects.requireNonNull(titleKey, "titleKey");
            Objects.requireNonNull(descriptionKey, "descriptionKey");
        }
    }

    public record LaunchAction(boolean openEditor, Optional<ToastMessage> toast) {

        public LaunchAction {
            Objects.requireNonNull(toast, "toast");
            if (openEditor && toast.isPresent()) {
                throw new IllegalArgumentException("open editor action must not show a toast");
            }
            if (!openEditor && toast.isEmpty()) {
                throw new IllegalArgumentException("blocked action must explain why");
            }
        }
    }

    public static LaunchAction afterInitialPreset(
            EndPresetEditorBootstrap.InitialPresetResult initialPreset) {
        Objects.requireNonNull(initialPreset, "initialPreset");
        if (initialPreset.canOpenEditor()) {
            return new LaunchAction(true, Optional.empty());
        }
        return new LaunchAction(false, Optional.of(OPEN_FAILED_TOAST));
    }

    public static ToastMessage savedToast() {
        return SAVED_TOAST;
    }
}
