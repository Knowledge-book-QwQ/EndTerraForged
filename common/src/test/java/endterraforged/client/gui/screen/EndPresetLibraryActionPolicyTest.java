package endterraforged.client.gui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetLibrary;

class EndPresetLibraryActionPolicyTest {

    @Test
    void saveActionCoversEveryResult() {
        for (EndPresetLibrary.SaveResult result : EndPresetLibrary.SaveResult.values()) {
            EndPresetLibraryActionPolicy.SaveAction action =
                    EndPresetLibraryActionPolicy.afterSave(result);
            assertEquals("endterraforged.gui.preset_library.save."
                    + result.name().toLowerCase(java.util.Locale.ROOT), action.statusKey());
            assertEquals(result == EndPresetLibrary.SaveResult.SAVED, action.refreshEntries());
        }
    }

    @Test
    void loadActionCoversEveryResult() {
        for (EndPresetLibrary.LoadStatus status : EndPresetLibrary.LoadStatus.values()) {
            Optional<EndPreset> preset = status == EndPresetLibrary.LoadStatus.LOADED
                    ? Optional.of(EndPreset.defaults())
                    : Optional.empty();
            EndPresetLibraryActionPolicy.LoadAction action =
                    EndPresetLibraryActionPolicy.afterLoad(
                            new EndPresetLibrary.LoadResult(status, preset));
            assertEquals("endterraforged.gui.preset_library.load."
                    + status.name().toLowerCase(java.util.Locale.ROOT), action.statusKey());
            assertEquals(status == EndPresetLibrary.LoadStatus.LOADED, action.loadPreset());
            assertEquals(status == EndPresetLibrary.LoadStatus.LOADED, action.closeScreen());
        }
    }

    @Test
    void deleteActionCoversEveryResult() {
        for (EndPresetLibrary.DeleteResult result : EndPresetLibrary.DeleteResult.values()) {
            EndPresetLibraryActionPolicy.DeleteAction action =
                    EndPresetLibraryActionPolicy.afterDelete(result);
            assertEquals("endterraforged.gui.preset_library.delete."
                    + result.name().toLowerCase(java.util.Locale.ROOT), action.statusKey());
            assertEquals(result == EndPresetLibrary.DeleteResult.DELETED, action.clearName());
            assertEquals(result == EndPresetLibrary.DeleteResult.DELETED, action.refreshEntries());
        }
    }

    @Test
    void exchangeActionsPreserveStatusAndApplyOnlySuccessfulImports() {
        for (EndPresetLibrary.ExportResult result : EndPresetLibrary.ExportResult.values()) {
            assertEquals("endterraforged.gui.preset_library.export."
                    + result.name().toLowerCase(java.util.Locale.ROOT),
                    EndPresetLibraryActionPolicy.afterExport(result).statusKey());
        }
        for (EndPresetLibrary.ImportStatus status : EndPresetLibrary.ImportStatus.values()) {
            Optional<EndPreset> preset = status == EndPresetLibrary.ImportStatus.IMPORTED
                    ? Optional.of(EndPreset.defaults())
                    : Optional.empty();
            EndPresetLibraryActionPolicy.ImportAction action =
                    EndPresetLibraryActionPolicy.afterImport(
                            new EndPresetLibrary.ImportResult(status, preset));
            assertEquals(status == EndPresetLibrary.ImportStatus.IMPORTED, action.loadPreset());
            assertEquals(status == EndPresetLibrary.ImportStatus.IMPORTED, action.closeScreen());
        }
    }

    @Test
    void successfulActionsExposeExpectedBehavior() {
        assertTrue(EndPresetLibraryActionPolicy.afterSave(
                EndPresetLibrary.SaveResult.SAVED).refreshEntries());
        assertFalse(EndPresetLibraryActionPolicy.afterExport(
                EndPresetLibrary.ExportResult.EXPORTED).statusKey().isEmpty());
    }
}
