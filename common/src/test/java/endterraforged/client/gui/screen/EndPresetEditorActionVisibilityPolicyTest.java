package endterraforged.client.gui.screen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EndPresetEditorActionVisibilityPolicyTest {

    @TempDir
    Path worldDir;

    @Test
    void libraryIsOnlyVisibleForExistingWorldEditors() {
        assertFalse(EndPresetEditorActionVisibilityPolicy.showsPresetLibrary(
                EndPresetEditorContext.createWorld()));
        assertTrue(EndPresetEditorActionVisibilityPolicy.showsPresetLibrary(
                EndPresetEditorContext.forWorld(worldDir)));
    }

    @Test
    void reloadNoticeIsOnlyVisibleForExistingWorldEditors() {
        assertFalse(EndPresetEditorActionVisibilityPolicy.showsReloadRequiredNotice(
                EndPresetEditorContext.createWorld()));
        assertTrue(EndPresetEditorActionVisibilityPolicy.showsReloadRequiredNotice(
                EndPresetEditorContext.forWorld(worldDir)));
    }
}
