package endterraforged.client.gui.screen;

import java.util.Locale;
import java.util.Objects;

import endterraforged.world.config.EndPresetLibrary;

/**
 * Pure UI mapping for named-library and exchange-directory operation results.
 */
final class EndPresetLibraryActionPolicy {

    private EndPresetLibraryActionPolicy() {
    }

    static SaveAction afterSave(EndPresetLibrary.SaveResult result) {
        Objects.requireNonNull(result, "result");
        return new SaveAction(statusKey("save", result.name()), result == EndPresetLibrary.SaveResult.SAVED);
    }

    static LoadAction afterLoad(EndPresetLibrary.LoadResult result) {
        Objects.requireNonNull(result, "result");
        boolean loaded = result.status() == EndPresetLibrary.LoadStatus.LOADED;
        return new LoadAction(statusKey("load", result.status().name()), loaded, loaded);
    }

    static DeleteAction afterDelete(EndPresetLibrary.DeleteResult result) {
        Objects.requireNonNull(result, "result");
        boolean deleted = result == EndPresetLibrary.DeleteResult.DELETED;
        return new DeleteAction(statusKey("delete", result.name()), deleted, deleted);
    }

    static ExportAction afterExport(EndPresetLibrary.ExportResult result) {
        Objects.requireNonNull(result, "result");
        return new ExportAction(statusKey("export", result.name()));
    }

    static ImportAction afterImport(EndPresetLibrary.ImportResult result) {
        Objects.requireNonNull(result, "result");
        boolean imported = result.status() == EndPresetLibrary.ImportStatus.IMPORTED;
        return new ImportAction(statusKey("import", result.status().name()), imported, imported);
    }

    private static String statusKey(String action, String status) {
        return "endterraforged.gui.preset_library."
                + action + "." + status.toLowerCase(Locale.ROOT);
    }

    record SaveAction(String statusKey, boolean refreshEntries) {
    }

    record LoadAction(String statusKey, boolean loadPreset, boolean closeScreen) {
    }

    record DeleteAction(String statusKey, boolean clearName, boolean refreshEntries) {
    }

    record ExportAction(String statusKey) {
    }

    record ImportAction(String statusKey, boolean loadPreset, boolean closeScreen) {
    }
}
