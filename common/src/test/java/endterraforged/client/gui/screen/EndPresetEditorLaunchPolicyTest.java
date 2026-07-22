package endterraforged.client.gui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.EndPreset;

class EndPresetEditorLaunchPolicyTest {

    @Test
    void storedPresetOpensWithoutToast() {
        EndPresetEditorLaunchPolicy.LaunchAction action =
                EndPresetEditorLaunchPolicy.afterInitialPreset(
                        new EndPresetEditorBootstrap.InitialPresetResult(
                                EndPresetEditorBootstrap.InitialPresetStatus.STORED_PRESET,
                                Optional.of(EndPreset.defaults())));

        assertTrue(action.openEditor());
        assertTrue(action.toast().isEmpty());
    }

    @Test
    void fallbackPresetOpensWithoutToast() {
        EndPresetEditorLaunchPolicy.LaunchAction action =
                EndPresetEditorLaunchPolicy.afterInitialPreset(
                        new EndPresetEditorBootstrap.InitialPresetResult(
                                EndPresetEditorBootstrap.InitialPresetStatus.FALLBACK_PRESET,
                                Optional.of(EndPreset.defaults())));

        assertTrue(action.openEditor());
        assertTrue(action.toast().isEmpty());
    }

    @Test
    void invalidStoredPresetBlocksOpenWithVisibleError() {
        EndPresetEditorLaunchPolicy.LaunchAction action =
                EndPresetEditorLaunchPolicy.afterInitialPreset(
                        new EndPresetEditorBootstrap.InitialPresetResult(
                                EndPresetEditorBootstrap.InitialPresetStatus.INVALID_STORED_FILE,
                                Optional.empty()));

        assertFalse(action.openEditor());
        assertEquals("endterraforged.gui.preset_editor.open_failed.title",
                action.toast().orElseThrow().titleKey());
        assertEquals("endterraforged.gui.preset_editor.open_failed.description",
                action.toast().orElseThrow().descriptionKey());
    }

    @Test
    void savedToastExplainsReloadRequirement() {
        EndPresetEditorLaunchPolicy.ToastMessage toast = EndPresetEditorLaunchPolicy.savedToast();

        assertEquals("endterraforged.gui.preset_editor.saved.title", toast.titleKey());
        assertEquals("endterraforged.gui.preset_editor.saved.description", toast.descriptionKey());
    }
}
