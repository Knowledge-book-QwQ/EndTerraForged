package endterraforged.client.gui.screen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import endterraforged.client.gui.widget.ActionButtonLayout;
import endterraforged.client.gui.widget.PresetLibraryEntryLayout;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetLibrary;
import endterraforged.world.config.EndPresetStorage;

/**
 * Named-preset library for an existing world.
 *
 * <p>This screen never writes the active world preset. Successful loads and
 * imports return an immutable snapshot to the parent editor, whose Done flow
 * remains the only place allowed to persist and publish it.</p>
 *
 * <p>Client-thread confined and not thread-safe.</p>
 */
public final class EndPresetLibraryScreen extends Screen {

    private static final int CONTENT_MAX_WIDTH = 320;
    private static final int CONTENT_MARGIN = 20;
    private static final int FIELD_Y = 44;
    private static final int ACTION_HEIGHT = 20;
    private static final int ACTION_STEP = 24;
    private static final int LIST_TOP = 144;
    private static final int LIST_BOTTOM_MARGIN = 28;
    private static final int LIST_ROW_STEP = 22;

    private final Path worldDir;
    private final Screen parent;
    private final Supplier<EndPreset> snapshotSupplier;
    private final BiConsumer<EndPreset, String> onPresetLoaded;
    private final List<String> entries = new ArrayList<>();
    private EditBox nameField;
    private Component statusMessage;
    private int scrollRows;
    private int maxScrollRows;

    public EndPresetLibraryScreen(
            Path worldDir,
            Screen parent,
            Supplier<EndPreset> snapshotSupplier,
            BiConsumer<EndPreset, String> onPresetLoaded) {
        super(Component.translatable("endterraforged.gui.preset_library.title"));
        this.worldDir = Objects.requireNonNull(worldDir, "worldDir").normalize();
        this.parent = parent;
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.onPresetLoaded = Objects.requireNonNull(onPresetLoaded, "onPresetLoaded");
        refreshEntries();
    }

    @Override
    public void onClose() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    protected void init() {
        int contentWidth = Math.max(1, Math.min(CONTENT_MAX_WIDTH, this.width - CONTENT_MARGIN * 2));
        int left = (this.width - contentWidth) / 2;
        this.maxScrollRows = PresetLibraryEntryLayout.maxScrollRows(entries.size(), visibleRows());
        this.scrollRows = PresetLibraryEntryLayout.clampScrollRows(scrollRows, maxScrollRows);

        nameField = new EditBox(this.font, left, FIELD_Y, contentWidth, ACTION_HEIGHT,
                Component.translatable("endterraforged.gui.preset_library.name"));
        nameField.setMaxLength(64);
        nameField.setHint(Component.translatable("endterraforged.gui.preset_library.name"));
        addRenderableWidget(nameField);

        ActionButtonLayout.Bounds[] firstActions =
                ActionButtonLayout.row(left, FIELD_Y + ACTION_STEP, contentWidth, ACTION_HEIGHT, 2);
        addRenderableWidget(button("endterraforged.gui.preset_library.save", firstActions[0], this::saveSnapshot));
        addRenderableWidget(button("endterraforged.gui.preset_library.load", firstActions[1], this::loadSnapshot));

        ActionButtonLayout.Bounds[] secondActions =
                ActionButtonLayout.row(left, FIELD_Y + ACTION_STEP * 2, contentWidth, ACTION_HEIGHT, 2);
        addRenderableWidget(button("endterraforged.gui.preset_library.export", secondActions[0], this::exportSnapshot));
        addRenderableWidget(button("endterraforged.gui.preset_library.import", secondActions[1], this::importSnapshot));

        ActionButtonLayout.Bounds[] thirdActions =
                ActionButtonLayout.row(left, FIELD_Y + ACTION_STEP * 3, contentWidth, ACTION_HEIGHT, 2);
        addRenderableWidget(button("endterraforged.gui.preset_library.delete", thirdActions[0], this::deleteSnapshot));
        addRenderableWidget(button("gui.back", thirdActions[1], this::onClose));

        int displayedRows = visibleRows();
        int lastVisibleEntry = Math.min(entries.size(), scrollRows + displayedRows);
        for (int index = scrollRows; index < lastVisibleEntry; index++) {
            int y = PresetLibraryEntryLayout.rowY(LIST_TOP, LIST_ROW_STEP, index, scrollRows);
            String entry = entries.get(index);
            String label = this.font.plainSubstrByWidth(entry, Math.max(1, contentWidth - 12));
            addRenderableWidget(Button.builder(Component.literal(label), button -> selectEntry(entry))
                    .bounds(left, y, contentWidth, ACTION_HEIGHT)
                    .build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("endterraforged.gui.preset_library.entries"),
                contentLeft(), LIST_TOP - 13, 0xFFFFFF);
        if (entries.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("endterraforged.gui.preset_library.empty"),
                    contentLeft(), LIST_TOP + 4, 0xAAAAAA);
        }
        EditorScreenWidgets.renderStatus(graphics, this.font, statusMessage, this.width, this.height);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScrollRows <= 0 || scrollY == 0.0D) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int nextScroll = PresetLibraryEntryLayout.scrollAfterWheel(scrollRows, maxScrollRows, scrollY);
        if (nextScroll == scrollRows) {
            return true;
        }
        scrollRows = nextScroll;
        rebuildWidgetsPreservingName(name());
        return true;
    }

    private Button button(String key, ActionButtonLayout.Bounds bounds, Runnable action) {
        return Button.builder(Component.translatable(key), button -> action.run())
                .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height())
                .build();
    }

    private void selectEntry(String entry) {
        nameField.setValue(entry);
        nameField.setFocused(true);
    }

    private void saveSnapshot() {
        String selectedName = name();
        try {
            EndPresetLibraryActionPolicy.SaveAction action = EndPresetLibraryActionPolicy.afterSave(
                    EndPresetLibrary.saveSnapshot(worldDir, name(), snapshotSupplier.get()));
            setStatus(action.statusKey());
            if (action.refreshEntries()) {
                refreshEntries();
                rebuildWidgetsPreservingName(selectedName);
            }
        } catch (IllegalStateException exception) {
            statusMessage = Component.literal(exception.getMessage());
        }
    }

    private void loadSnapshot() {
        EndPresetLibrary.LoadResult result =
                EndPresetLibrary.loadSnapshot(worldDir, name(), ignored -> {});
        EndPresetLibraryActionPolicy.LoadAction action = EndPresetLibraryActionPolicy.afterLoad(result);
        if (action.loadPreset()) {
            onPresetLoaded.accept(result.preset().orElseThrow(), action.statusKey());
        }
        setStatus(action.statusKey());
        if (action.closeScreen()) {
            onClose();
        }
    }

    private void deleteSnapshot() {
        EndPresetLibraryActionPolicy.DeleteAction action = EndPresetLibraryActionPolicy.afterDelete(
                EndPresetLibrary.deleteSnapshot(worldDir, name()));
        setStatus(action.statusKey());
        if (action.clearName()) {
            nameField.setValue("");
        }
        if (action.refreshEntries()) {
            refreshEntries();
            rebuildWidgetsPreservingName(action.clearName() ? "" : name());
        }
    }

    private void exportSnapshot() {
        try {
            EndPresetLibraryActionPolicy.ExportAction action = EndPresetLibraryActionPolicy.afterExport(
                    EndPresetLibrary.exportSnapshot(worldDir, name(), snapshotSupplier.get()));
            setStatus(action.statusKey());
        } catch (IllegalStateException exception) {
            statusMessage = Component.literal(exception.getMessage());
        }
    }

    private void importSnapshot() {
        EndPresetLibrary.ImportResult result =
                EndPresetLibrary.importSnapshot(worldDir, name(), ignored -> {});
        EndPresetLibraryActionPolicy.ImportAction action = EndPresetLibraryActionPolicy.afterImport(result);
        if (action.loadPreset()) {
            onPresetLoaded.accept(result.preset().orElseThrow(), action.statusKey());
        }
        setStatus(action.statusKey());
        if (action.closeScreen()) {
            onClose();
        }
    }

    private void refreshEntries() {
        entries.clear();
        entries.addAll(EndPresetStorage.listNamed(worldDir));
    }

    private int visibleRows() {
        return PresetLibraryEntryLayout.visibleRows(
                this.height, LIST_TOP, LIST_BOTTOM_MARGIN, ACTION_HEIGHT, LIST_ROW_STEP);
    }

    private int contentLeft() {
        int contentWidth = Math.max(1, Math.min(CONTENT_MAX_WIDTH, this.width - CONTENT_MARGIN * 2));
        return (this.width - contentWidth) / 2;
    }

    private String name() {
        return nameField.getValue();
    }

    private void setStatus(String key) {
        statusMessage = Component.translatable(key);
    }

    private void rebuildWidgetsPreservingName(String selectedName) {
        rebuildWidgets();
        nameField.setValue(selectedName);
    }
}
