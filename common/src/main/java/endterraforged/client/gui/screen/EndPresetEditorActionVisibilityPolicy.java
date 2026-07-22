package endterraforged.client.gui.screen;

import java.util.Objects;

/**
 * Keeps optional editor actions tied to capabilities that are safe in the
 * current editing context.
 */
final class EndPresetEditorActionVisibilityPolicy {

    private EndPresetEditorActionVisibilityPolicy() {
    }

    static boolean showsPresetLibrary(EndPresetEditorContext context) {
        return Objects.requireNonNull(context, "context").hasWorldDir();
    }

    static boolean showsReloadRequiredNotice(EndPresetEditorContext context) {
        return Objects.requireNonNull(context, "context").hasWorldDir();
    }
}
