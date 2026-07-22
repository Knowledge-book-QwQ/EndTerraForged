/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). The End dimension's
 * preset editor screen uses vanilla components and ETF's own paged layout.
 */
package endterraforged.client.gui.screen;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import endterraforged.client.gui.widget.ActionButtonLayout;
import endterraforged.client.gui.widget.EndSlider;
import endterraforged.client.gui.widget.EditorColumnLayout;
import endterraforged.client.gui.widget.EditorPageLayout;
import endterraforged.client.gui.widget.FloatSliderScale;
import endterraforged.client.gui.widget.IntSliderScale;
import endterraforged.world.config.ContinentConfig;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetAccess;
import endterraforged.world.config.EndPresetBuilder;
import endterraforged.world.config.SeaMode;
import endterraforged.world.config.TerrainConfig;
import endterraforged.world.config.TerrainConfigBuilder;
import endterraforged.world.config.TerrainLayerConfig;
import endterraforged.world.config.TerrainShape;
import endterraforged.world.config.TopologyMode;
import endterraforged.world.config.WorldVerticalBounds;
import endterraforged.world.preview.TerrainPreviewSettings;

/**
 * The End dimension's preset editor screen.
 *
 * <p><b>Current layout.</b> Controls are grouped into short pages so the
 * option surface stays usable on small game windows while the live
 * preview remains visible. All pages mutate the same {@link EndPresetBuilder}
 * instance; switching pages only rebuilds widgets, not editing state.</p>
 *
 * <p><b>Layout.</b> A vertical stack of widgets on the screen's left side,
 * each bound to one of {@link EndPresetBuilder}'s setters:</p>
 * <ul>
 *   <li>2 world-envelope rows plus 2 {@link EndSlider}s for reference heights
 *       ({@code seaLevelY}, {@code islandBaselineY})</li>
 *   <li>2 {@link CycleButton}s for the enum switches ({@link SeaMode},
 *       {@link TopologyMode})</li>
 *   <li>1 boolean {@link CycleButton} for {@code floatingIslandsEnabled}</li>
 *   <li>1 "Erosion..." {@link Button} that opens
 *       {@link ErosionConfigEditorScreen} — the erosion sub-editor writes
 *       its result back to this builder via {@link EndPresetBuilder#erosionConfig}</li>
 *   <li>Done / Cancel buttons at the bottom</li>
 * </ul>
 *
 * <p><b>Vertical bounds.</b> Creation sessions expose section-aligned minimum
 * and maximum build Y controls and apply them to both the End dimension type
 * and noise settings. Existing-world sessions display their loaded bounds as
 * read-only values because a saved dimension cannot be resized in place.</p>
 *
 * <p><b>Why {@link CycleButton} for enums.</b> {@link SeaMode} and
 * {@link TopologyMode} are enums with 2-3 values each — a slider over a
 * discrete enum set would be overkill, and vanilla's {@link CycleButton}
 * already handles the enum-cycle UI idiom cleanly.</p>
 *
 * <p><b>Architecture.</b> The screen holds an {@link EndPresetBuilder}
 * (mutable editing state),
 * widgets mutate the builder, and Done builds the immutable
 * {@link EndPreset} via {@link EndPresetBuilder#build()}.</p>
 *
 * <p><b>Compile-only verification.</b> This screen cannot be unit-tested
 * in the sandbox (it requires a live {@code Minecraft} instance and a
 * render thread). The testable core lives in {@link EndPresetBuilder}
 * (round-trip, reset, setters) and the slider-scale policies (position↔value
 * math); this screen is a thin view over both.</p>
 */
public class EndPresetEditorScreen extends Screen {

    /** Slider scales — defined as constants so they can be referenced from tests if needed. */
    private static final int EDITOR_TOP = 38;
    private static final int EDITOR_TOP_WITH_RELOAD_NOTICE = 50;
    private static final int EDITOR_TITLE_Y = 20;
    private static final int EXISTING_WORLD_TITLE_Y = 12;
    private static final int RELOAD_NOTICE_Y = 26;
    private static final int LARGE_WORLD_WARNING_Y = 26;
    private static final int LARGE_WORLD_WARNING_WITH_RELOAD_Y = 38;
    private static final int PREVIEW_CONTROL_Y = 34;
    private static final int PREVIEW_CONTROL_Y_WITH_RELOAD_NOTICE = 46;
    private static final int RELOAD_NOTICE_COLOR = 0xFFE0A84A;
    private static final int LARGE_WORLD_WARNING_COLOR = 0xFFE0A84A;
    private static final int LARGE_WORLD_WARNING_THRESHOLD = 1024;
    private static final int EDITOR_BOTTOM_MARGIN = 18;
    private static final int WIDGET_WIDTH = 200;
    private static final int WIDGET_HEIGHT = 20;
    private static final int PREFERRED_ROW_STEP = 26;

    private static final IntSliderScale MIN_BUILD_Y_SCALE = new IntSliderScale(
            WorldVerticalBoundsEditorPolicy.MIN_BUILD_Y,
            WorldVerticalBoundsEditorPolicy.MAX_MIN_BUILD_Y,
            WorldVerticalBoundsEditorPolicy.ALIGNMENT);
    private static final IntSliderScale MAX_BUILD_Y_SCALE = new IntSliderScale(
            WorldVerticalBoundsEditorPolicy.MIN_MAX_BUILD_Y,
            WorldVerticalBoundsEditorPolicy.MAX_BUILD_Y,
            WorldVerticalBoundsEditorPolicy.ALIGNMENT);
    private static final IntSliderScale SEA_LEVEL_SCALE = new IntSliderScale(-128, 256, 8);
    private static final IntSliderScale ISLAND_BASELINE_SCALE = new IntSliderScale(-256, 512, 16);
    private static final IntSliderScale TERRAIN_SEED_OFFSET_SCALE = new IntSliderScale(-4096, 4096, 1);
    private static final IntSliderScale TERRAIN_REGION_SIZE_SCALE = new IntSliderScale(125, 5000, 25);
    private static final FloatSliderScale GLOBAL_VERTICAL_SCALE =
            new FloatSliderScale(0.01F, 1.0F, 0.01F, 2);
    private static final FloatSliderScale GLOBAL_HORIZONTAL_SCALE =
            new FloatSliderScale(0.01F, 5.0F, 0.01F, 2);
    private static final FloatSliderScale TERRAIN_BLEND_RANGE =
            new FloatSliderScale(0.0F, 1.0F, 0.01F, 2);

    private final EndPresetBuilder builder;
    private final Runnable onDone;
    private final EndPresetEditorContext context;
    private EndPreset lastValidPreset;
    private TerrainPreviewSettings previewSettings = TerrainPreviewSettings.DEFAULT;
    private EditorPage activePage = EditorPage.WORLD;
    private Component statusMessage;
    /**
     * The screen to return to when Done/Cancel is pressed. Stored as
     * {@link Screen} so this class compiles in common (no client-only deps
     * beyond what vanilla's {@code Screen} already pulls in).
     *
     * <p>Why a parent field rather than relying on {@link Minecraft#setScreen}
     * in {@code onDone}: the standard vanilla pattern is that a sub-screen's
     * Done/Cancel calls {@link #onClose()} which calls
     * {@code Minecraft.setScreen(parent)} — that way the parent screen's
     * {@code init()} re-runs and refreshes any state derived from the
     * edited preset. Without a parent, {@code Screen.onClose()} falls
     * through to {@code Minecraft.setScreen(null)} which dumps the user
     * back to the main menu (the v0.1.5 bug).</p>
     */
    private final Screen parent;

    /**
     * @param initial the preset to load for editing (or {@code defaults()})
     * @param parent the screen to return to on Done/Cancel — typically the
     *               {@link net.minecraft.client.gui.screens.worldselection.CreateWorldScreen}
     *               that opened this editor
     * @param onDone callback invoked with the built preset when the user
     *               presses Done — the caller is responsible for persisting
     *               the preset (writing the JSON file, applying to worldgen)
     */
    public EndPresetEditorScreen(EndPreset initial, Screen parent, Runnable onDone) {
        this(initial, parent, onDone, EndPresetEditorContext.createWorld());
    }

    /**
     * Creates an editor session with optional existing-world persistence.
     *
     * @param initial initial immutable editor snapshot
     * @param parent screen to restore after Done or Cancel
     * @param onDone callback after a successfully committed preset
     * @param context persistence context for this editor session
     */
    public EndPresetEditorScreen(
            EndPreset initial, Screen parent, Runnable onDone, EndPresetEditorContext context) {
        super(Component.translatable("endterraforged.gui.preset_editor.title"));
        this.builder = new EndPresetBuilder(initial);
        this.lastValidPreset = this.builder.build();
        this.parent = parent;
        this.onDone = onDone;
        this.context = Objects.requireNonNull(context, "context");
    }

    @Override
    public void onClose() {
        // Return to the parent screen (typically CreateWorldScreen) instead
        // of falling through to Screen.onClose() which calls
        // Minecraft.setScreen(null) — the v0.1.5 bug returned the user to
        // the main menu instead of the create-world screen.
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = Math.max(20, cx - 230);
        int editorTop = editorTop();
        int rowStep = EditorPageLayout.rowStepForHeight(
                this.height, editorTop, EDITOR_BOTTOM_MARGIN,
                WIDGET_HEIGHT, activePage.totalRows(context.hasWorldDir()), PREFERRED_ROW_STEP);
        EditorColumnLayout column = new EditorColumnLayout(
                left, editorTop, WIDGET_WIDTH, WIDGET_HEIGHT, rowStep);

        addPageTabs(column);
        addActivePage(column);
        addActionButtons(column);
        addPreview(left, previewControlY());
    }

    private void addPageTabs(EditorColumnLayout column) {
        EditorPage[] pages = EditorPage.values();
        ActionButtonLayout.Bounds[] tabs = column.nextActionRow(pages.length);
        for (int i = 0; i < pages.length; i++) {
            EditorPage page = pages[i];
            Button button = Button.builder(page.title(),
                            btn -> setActivePage(page))
                    .bounds(tabs[i].x(), tabs[i].y(), tabs[i].width(), tabs[i].height())
                    .build();
            button.active = page != activePage;
            addRenderableWidget(button);
        }
    }

    private void addActivePage(EditorColumnLayout column) {
        switch (activePage) {
            case WORLD -> addWorldPage(column);
            case TERRAIN -> addTerrainPage(column);
            case LAYERS -> addLayersPage(column);
            case ADVANCED -> addAdvancedPage(column);
        }
    }

    private void addWorldPage(EditorColumnLayout column) {
        WorldVerticalBounds bounds = displayedWorldBounds();
        ActionButtonLayout.Bounds row = column.nextRow();
        if (context.canEditWorldBounds()) {
            addRenderableWidget(new EndSlider(
                    row.x(), row.y(), row.width(), row.height(),
                    Component.translatable("endterraforged.gui.max_build_y"),
                    MAX_BUILD_Y_SCALE, bounds.maxYInclusive(),
                    v -> setMaximumBuildY((int) v)));
        } else {
            addRenderableWidget(readOnlyWorldBoundButton(
                    row, "endterraforged.gui.max_build_y", bounds.maxYInclusive()));
        }

        row = column.nextRow();
        if (context.canEditWorldBounds()) {
            addRenderableWidget(new EndSlider(
                    row.x(), row.y(), row.width(), row.height(),
                    Component.translatable("endterraforged.gui.min_build_y"),
                    MIN_BUILD_Y_SCALE, bounds.minY(),
                    v -> setMinimumBuildY((int) v)));
        } else {
            addRenderableWidget(readOnlyWorldBoundButton(
                    row, "endterraforged.gui.min_build_y", bounds.minY()));
        }

        row = column.nextRow();
        addRenderableWidget(new EndSlider(
                row.x(), row.y(), row.width(), row.height(),
                Component.translatable("endterraforged.gui.sea_level_y"),
                SEA_LEVEL_SCALE, builder.seaLevelY(),
                v -> builder.seaLevelY((int) v)));

        row = column.nextRow();
        addRenderableWidget(new EndSlider(
                row.x(), row.y(), row.width(), row.height(),
                Component.translatable("endterraforged.gui.island_baseline_y"),
                ISLAND_BASELINE_SCALE, builder.islandBaselineY(),
                v -> builder.islandBaselineY((int) v)));

        row = column.nextRow();
        addRenderableWidget(CycleButton.<SeaMode>builder(EndPresetEditorScreen::seaModeLabel)
                .withValues(SeaMode.values())
                .withInitialValue(builder.seaMode())
                .create(row.x(), row.y(), row.width(), row.height(),
                        Component.translatable("endterraforged.gui.sea_mode"),
                        (btn, mode) -> builder.seaMode(mode)));

        row = column.nextRow();
        addRenderableWidget(CycleButton.<TopologyMode>builder(EndPresetEditorScreen::topologyModeLabel)
                .withValues(TopologyMode.values())
                .withInitialValue(builder.topologyMode())
                .create(row.x(), row.y(), row.width(), row.height(),
                        Component.translatable("endterraforged.gui.topology_mode"),
                        (btn, mode) -> builder.topologyMode(mode)));

        row = column.nextRow();
        addRenderableWidget(CycleButton.booleanBuilder(
                        Component.translatable("endterraforged.gui.floating_islands.on"),
                        Component.translatable("endterraforged.gui.floating_islands.off"))
                .withInitialValue(builder.floatingIslandsEnabled())
                .create(row.x(), row.y(), row.width(), row.height(),
                        Component.translatable("endterraforged.gui.floating_islands"),
                        (btn, enabled) -> builder.floatingIslandsEnabled(enabled)));
    }

    private Button readOnlyWorldBoundButton(
            ActionButtonLayout.Bounds row, String translationKey, int value) {
        Button button = Button.builder(
                        Component.translatable(translationKey, value), ignored -> { })
                .bounds(row.x(), row.y(), row.width(), row.height())
                .tooltip(Tooltip.create(Component.translatable(
                        "endterraforged.gui.world_bounds_locked")))
                .build();
        button.active = false;
        return button;
    }

    private WorldVerticalBounds displayedWorldBounds() {
        if (context.canEditWorldBounds()) {
            return builder.worldBounds();
        }
        return context.worldBounds().orElseGet(builder::worldBounds);
    }

    private void setMinimumBuildY(int minY) {
        builder.worldBounds(WorldVerticalBoundsEditorPolicy.withMinimumY(
                builder.worldBounds(), minY));
    }

    private void setMaximumBuildY(int maxYInclusive) {
        builder.worldBounds(WorldVerticalBoundsEditorPolicy.withMaximumY(
                builder.worldBounds(), maxYInclusive));
    }

    private static Component seaModeLabel(SeaMode mode) {
        return Component.translatable("endterraforged.gui.sea_mode."
                + mode.name().toLowerCase(Locale.ROOT));
    }

    private static Component topologyModeLabel(TopologyMode mode) {
        return Component.translatable("endterraforged.gui.topology_mode."
                + mode.name().toLowerCase(Locale.ROOT));
    }

    private void addTerrainPage(EditorColumnLayout column) {
        ActionButtonLayout.Bounds row = column.nextRow();
        addRenderableWidget(new EndSlider(
                row.x(), row.y(), row.width(), row.height(),
                Component.translatable("endterraforged.gui.terrain.seed_offset"),
                TERRAIN_SEED_OFFSET_SCALE, builder.terrainConfig().terrainSeedOffset(),
                v -> setTerrainSeedOffset((int) v)));

        row = column.nextRow();
        addRenderableWidget(new EndSlider(
                row.x(), row.y(), row.width(), row.height(),
                Component.translatable("endterraforged.gui.terrain.region_size"),
                TERRAIN_REGION_SIZE_SCALE, builder.terrainConfig().terrainRegionSize(),
                v -> setTerrainRegionSize((int) v)));

        row = column.nextRow();
        addRenderableWidget(new EndSlider(
                row.x(), row.y(), row.width(), row.height(),
                Component.translatable("endterraforged.gui.terrain.global_vertical_scale"),
                GLOBAL_VERTICAL_SCALE, builder.terrainConfig().globalVerticalScale(),
                v -> setTerrainConfig((float) v, builder.terrainConfig().globalHorizontalScale())));

        row = column.nextRow();
        addRenderableWidget(new EndSlider(
                row.x(), row.y(), row.width(), row.height(),
                Component.translatable("endterraforged.gui.terrain.global_horizontal_scale"),
                GLOBAL_HORIZONTAL_SCALE, builder.terrainConfig().globalHorizontalScale(),
                v -> setTerrainConfig(builder.terrainConfig().globalVerticalScale(), (float) v)));

        row = column.nextRow();
        addRenderableWidget(new EndSlider(
                row.x(), row.y(), row.width(), row.height(),
                Component.translatable("endterraforged.gui.terrain.blend_range"),
                TERRAIN_BLEND_RANGE, builder.terrainConfig().terrainBlendRange(),
                v -> setTerrainBlendRange((float) v)));

        row = column.nextRow();
        addRenderableWidget(CycleButton.<TerrainShape>builder(EndPresetEditorScreen::terrainShapeName)
                .withValues(TerrainShape.values())
                .withInitialValue(builder.terrainConfig().terrainShape())
                .create(row.x(), row.y(), row.width(), row.height(),
                        Component.translatable("endterraforged.gui.terrain.shape"),
                        (btn, shape) -> setTerrainShape(shape)));
    }

    private void addLayersPage(EditorColumnLayout column) {
        TerrainConfig terrain = buildPreviewPreset().terrainConfig();
        addLayerButton(column, "endterraforged.gui.terrain.mountains.open",
                "endterraforged.gui.mountains_editor.title",
                terrain.mountains(),
                TerrainLayerConfig.DEFAULT,
                this::withMountainsLayer,
                this::setMountainLayer);
        addLayerButton(column, "endterraforged.gui.terrain.plains.open",
                "endterraforged.gui.plains_editor.title",
                terrain.plains(),
                TerrainLayerConfig.DISABLED,
                this::withPlainsLayer,
                this::setPlainsLayer);
        addLayerButton(column, "endterraforged.gui.terrain.hills.open",
                "endterraforged.gui.hills_editor.title",
                terrain.hills(),
                TerrainLayerConfig.DISABLED,
                this::withHillsLayer,
                this::setHillsLayer);
        addLayerButton(column, "endterraforged.gui.terrain.plateau.open",
                "endterraforged.gui.plateau_editor.title",
                terrain.plateau(),
                TerrainLayerConfig.DISABLED,
                this::withPlateauLayer,
                this::setPlateauLayer);
        addLayerButton(column, "endterraforged.gui.terrain.volcano.open",
                "endterraforged.gui.volcano_editor.title",
                terrain.volcano(),
                TerrainLayerConfig.DISABLED,
                this::withVolcanoLayer,
                this::setVolcanoLayer);
    }

    private void addAdvancedPage(EditorColumnLayout column) {
        ActionButtonLayout.Bounds row = column.nextRow();
        addRenderableWidget(Button.builder(
                        Component.translatable("endterraforged.gui.continent.open"),
                        btn -> Minecraft.getInstance().setScreen(
                                new ContinentConfigEditorScreen(
                                        buildPreviewPreset(), this,
                                        config -> applyContinentConfig(builder, config))))
                .bounds(row.x(), row.y(), row.width(), row.height())
                .build());

        row = column.nextRow();
        addRenderableWidget(Button.builder(
                        Component.translatable("endterraforged.gui.climate.open"),
                        btn -> Minecraft.getInstance().setScreen(
                                new ClimateConfigEditorScreen(
                                        buildPreviewPreset(), this,
                                        config -> builder.climateConfig(config))))
                .bounds(row.x(), row.y(), row.width(), row.height())
                .build());

        row = column.nextRow();
        addRenderableWidget(Button.builder(
                        Component.translatable("endterraforged.gui.erosion.open"),
                        btn -> Minecraft.getInstance().setScreen(
                                new ErosionConfigEditorScreen(
                                        buildPreviewPreset(),
                                        this,
                                        config -> builder.erosionConfig(config))))
                .bounds(row.x(), row.y(), row.width(), row.height())
                .build());

        row = column.nextRow();
        addRenderableWidget(Button.builder(
                        Component.translatable("endterraforged.gui.biome_layout.open"),
                        btn -> Minecraft.getInstance().setScreen(
                                new BiomeLayoutConfigEditorScreen(
                                        buildPreviewPreset(),
                                        this,
                                        config -> builder.biomeLayoutConfig(config))))
                .bounds(row.x(), row.y(), row.width(), row.height())
                .build());

        row = column.nextRow();
        addRenderableWidget(Button.builder(
                        Component.translatable("endterraforged.gui.subsurface.open"),
                        btn -> Minecraft.getInstance().setScreen(
                                new SubsurfaceConfigEditorScreen(
                                        buildPreviewPreset(),
                                        this,
                                        config -> builder.subsurfaceConfig(config))))
                .bounds(row.x(), row.y(), row.width(), row.height())
                .build());

        if (EndPresetEditorActionVisibilityPolicy.showsPresetLibrary(context)) {
            row = column.nextRow();
            Path worldDir = context.worldDir().orElseThrow();
            addRenderableWidget(Button.builder(
                            Component.translatable("endterraforged.gui.preset_library.open"),
                            btn -> Minecraft.getInstance().setScreen(
                                    new EndPresetLibraryScreen(
                                            worldDir,
                                            this,
                                            builder::build,
                                            this::loadPresetFromLibrary)))
                    .bounds(row.x(), row.y(), row.width(), row.height())
                    .build());
        }

    }

    private void addLayerButton(EditorColumnLayout column,
                                String openKey, String titleKey,
                                TerrainLayerConfig layer,
                                TerrainLayerConfig resetLayer,
                                java.util.function.BiFunction<TerrainConfig, TerrainLayerConfig, TerrainConfig> updater,
                                java.util.function.Consumer<TerrainLayerConfig> done) {
        ActionButtonLayout.Bounds row = column.nextRow();
        addRenderableWidget(Button.builder(
                        Component.translatable(openKey),
                        btn -> Minecraft.getInstance().setScreen(
                                newLayerEditor(titleKey, layer, resetLayer, updater, done)))
                .bounds(row.x(), row.y(), row.width(), row.height())
                .build());
    }

    private void addActionButtons(EditorColumnLayout column) {
        ActionButtonLayout.Bounds[] actions = column.nextActionRow(3);

        addRenderableWidget(Button.builder(
                        Component.translatable("controls.reset"),
                        btn -> resetToDefaults())
                .bounds(actions[0].x(), actions[0].y(), actions[0].width(), actions[0].height())
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("endterraforged.gui.done"),
                        btn -> finishEditing())
                .bounds(actions[1].x(), actions[1].y(), actions[1].width(), actions[1].height())
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.cancel"),
                        btn -> onClose())
                .bounds(actions[2].x(), actions[2].y(), actions[2].width(), actions[2].height())
                .build());
    }

    private void addPreview(int left, int controlY) {
        EditorScreenWidgets.addLivePreview(this::addRenderableWidget, left + WIDGET_WIDTH + 28,
                this.width, controlY, previewSettings,
                mode -> previewSettings = previewSettings.withMode(mode),
                scale -> previewSettings = previewSettings.withScale(scale),
                this::buildPreviewPreset, () -> previewSettings);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, titleY(), 0xFFFFFF);
        if (EndPresetEditorActionVisibilityPolicy.showsReloadRequiredNotice(context)) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("endterraforged.gui.preset_editor.reload_required"),
                    this.width / 2, RELOAD_NOTICE_Y, RELOAD_NOTICE_COLOR);
        }
        if (displayedWorldBounds().height() >= LARGE_WORLD_WARNING_THRESHOLD) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("endterraforged.gui.preset_editor.large_world_warning"),
                    this.width / 2, largeWorldWarningY(), LARGE_WORLD_WARNING_COLOR);
        }
        EditorScreenWidgets.renderStatus(graphics, this.font, statusMessage, this.width, this.height);
    }

    private int editorTop() {
        return EndPresetEditorActionVisibilityPolicy.showsReloadRequiredNotice(context)
                ? EDITOR_TOP_WITH_RELOAD_NOTICE
                : EDITOR_TOP;
    }

    private int previewControlY() {
        return EndPresetEditorActionVisibilityPolicy.showsReloadRequiredNotice(context)
                ? PREVIEW_CONTROL_Y_WITH_RELOAD_NOTICE
                : PREVIEW_CONTROL_Y;
    }

    private int largeWorldWarningY() {
        return EndPresetEditorActionVisibilityPolicy.showsReloadRequiredNotice(context)
                ? LARGE_WORLD_WARNING_WITH_RELOAD_Y
                : LARGE_WORLD_WARNING_Y;
    }

    private int titleY() {
        return EndPresetEditorActionVisibilityPolicy.showsReloadRequiredNotice(context)
                ? EXISTING_WORLD_TITLE_Y
                : EDITOR_TITLE_Y;
    }

    private void resetToDefaults() {
        builder.reset();
        lastValidPreset = builder.build();
        statusMessage = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(this);
        }
    }

    private EndPreset buildPreviewPreset() {
        try {
            lastValidPreset = builder.build();
            statusMessage = null;
        } catch (IllegalStateException e) {
            statusMessage = Component.literal(e.getMessage());
        }
        return lastValidPreset;
    }

    private void setActivePage(EditorPage page) {
        if (page == activePage) {
            return;
        }
        activePage = page;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(this);
        }
    }

    private void finishEditing() {
        EndPresetEditorFinishPolicy.FinishResult result = EndPresetEditorFinishPolicy.finish(
                builder::build, context, EndPresetAccess::set, onDone);
        if (result.status() == EndPresetEditorFinishPolicy.Status.FINISHED) {
            onClose();
            return;
        }
        statusMessage = result.status() == EndPresetEditorFinishPolicy.Status.SAVE_FAILED
                ? Component.translatable(result.message())
                : Component.literal(result.message());
    }

    static void applyContinentConfig(EndPresetBuilder builder, ContinentConfig config) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(config, "config");
        builder.continentConfig(config);
        if (config.continentBands().enabled()) {
            builder.upgradeToCurrentFormat();
        }
    }

    private void loadPresetFromLibrary(EndPreset preset, String statusKey) {
        builder.load(Objects.requireNonNull(preset, "preset"));
        lastValidPreset = preset;
        statusMessage = Component.translatable(Objects.requireNonNull(statusKey, "statusKey"));
    }

    private void setTerrainConfig(float globalVerticalScale, float globalHorizontalScale) {
        updateTerrainConfig(config -> config
                .globalVerticalScale(globalVerticalScale)
                .globalHorizontalScale(globalHorizontalScale));
    }

    private void setTerrainBlendRange(float terrainBlendRange) {
        updateTerrainConfig(config -> config.terrainBlendRange(terrainBlendRange));
    }

    private void setTerrainSeedOffset(int terrainSeedOffset) {
        updateTerrainConfig(config -> config.terrainSeedOffset(terrainSeedOffset));
    }

    private void setTerrainRegionSize(int terrainRegionSize) {
        updateTerrainConfig(config -> config.terrainRegionSize(terrainRegionSize));
    }

    private void setTerrainShape(TerrainShape terrainShape) {
        updateTerrainConfig(config -> config.terrainShape(terrainShape));
    }

    private void setPlainsLayer(TerrainLayerConfig plains) {
        updateTerrainConfig(config -> config.plains(plains));
    }

    private void setHillsLayer(TerrainLayerConfig hills) {
        updateTerrainConfig(config -> config.hills(hills));
    }

    private void setPlateauLayer(TerrainLayerConfig plateau) {
        updateTerrainConfig(config -> config.plateau(plateau));
    }

    private void setMountainLayer(TerrainLayerConfig mountains) {
        updateTerrainConfig(config -> config.mountains(mountains));
    }

    private void setVolcanoLayer(TerrainLayerConfig volcano) {
        updateTerrainConfig(config -> config.volcano(volcano));
    }

    private TerrainLayerConfigEditorScreen newLayerEditor(String titleKey,
                                                          TerrainLayerConfig layer,
                                                          TerrainLayerConfig resetLayer,
                                                          java.util.function.BiFunction<TerrainConfig, TerrainLayerConfig, TerrainConfig> updater,
                                                          java.util.function.Consumer<TerrainLayerConfig> done) {
        return new TerrainLayerConfigEditorScreen(Component.translatable(titleKey),
                buildPreviewPreset(), this, layer, resetLayer, updater, done);
    }

    private TerrainConfig withPlainsLayer(TerrainConfig current, TerrainLayerConfig plains) {
        return applyTerrainUpdate(current, config -> config.plains(plains));
    }

    private TerrainConfig withHillsLayer(TerrainConfig current, TerrainLayerConfig hills) {
        return applyTerrainUpdate(current, config -> config.hills(hills));
    }

    private TerrainConfig withPlateauLayer(TerrainConfig current, TerrainLayerConfig plateau) {
        return applyTerrainUpdate(current, config -> config.plateau(plateau));
    }

    private TerrainConfig withMountainsLayer(TerrainConfig current, TerrainLayerConfig mountains) {
        return applyTerrainUpdate(current, config -> config.mountains(mountains));
    }

    private TerrainConfig withVolcanoLayer(TerrainConfig current, TerrainLayerConfig volcano) {
        return applyTerrainUpdate(current, config -> config.volcano(volcano));
    }

    private void updateTerrainConfig(Consumer<TerrainConfigBuilder> update) {
        try {
            builder.terrainConfig(applyTerrainUpdate(builder.terrainConfig(), update));
        } catch (IllegalStateException e) {
            statusMessage = Component.literal(e.getMessage());
        }
    }

    private static TerrainConfig applyTerrainUpdate(
            TerrainConfig current, Consumer<TerrainConfigBuilder> update) {
        TerrainConfigBuilder builder = new TerrainConfigBuilder(current);
        update.accept(builder);
        return builder.build();
    }

    private static Component terrainShapeName(TerrainShape shape) {
        return Component.translatable("endterraforged.gui.terrain.shape."
                + shape.name().toLowerCase(Locale.ROOT));
    }

    private enum EditorPage {
        WORLD("endterraforged.gui.preset_editor.page.world", 7),
        TERRAIN("endterraforged.gui.preset_editor.page.terrain", 6),
        LAYERS("endterraforged.gui.preset_editor.page.layers", 5),
        ADVANCED("endterraforged.gui.preset_editor.page.advanced", 5);

        private final String titleKey;
        private final int controlRows;

        EditorPage(String titleKey, int controlRows) {
            this.titleKey = titleKey;
            this.controlRows = controlRows;
        }

        Component title() {
            return Component.translatable(this.titleKey);
        }

        int totalRows(boolean libraryAvailable) {
            return this.controlRows + (this == ADVANCED && libraryAvailable ? 1 : 0) + 2;
        }
    }
}
