/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). The End dimension's
 * preset editor screen — vanilla-{@link Screen}-backed, RTF-pattern (MIT)
 * but with the slider math extracted into SliderScale.
 */
package endterraforged.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import endterraforged.client.gui.widget.EndSlider;
import endterraforged.client.gui.widget.IntSliderScale;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetAccess;
import endterraforged.world.config.EndPresetBuilder;
import endterraforged.world.config.SeaMode;
import endterraforged.world.config.TopologyMode;

/**
 * The End dimension's preset editor screen.
 *
 * <p><b>Layout.</b> A vertical stack of widgets on the screen's left side,
 * each bound to one of {@link EndPresetBuilder}'s setters:</p>
 * <ul>
 *   <li>4 {@link EndSlider}s backed by {@link IntSliderScale} for the int
 *       fields ({@code worldHeight}, {@code minY}, {@code seaLevelY},
 *       {@code islandBaselineY}) — replaces the prior cycle-button approach
 *       with fine-grained, step-snapped sliders</li>
 *   <li>2 {@link CycleButton}s for the enum switches ({@link SeaMode},
 *       {@link TopologyMode})</li>
 *   <li>1 boolean {@link CycleButton} for {@code floatingIslandsEnabled}</li>
 *   <li>1 "Erosion..." {@link Button} that opens
 *       {@link ErosionConfigEditorScreen} — the erosion sub-editor writes
 *       its result back to this builder via {@link EndPresetBuilder#erosionConfig}</li>
 *   <li>Done / Cancel buttons at the bottom</li>
 * </ul>
 *
 * <p><b>Why EndSlider over CycleButton for ints.</b> The int fields are
 * range-bounded continuous values (e.g. {@code worldHeight ∈ [1024, 8192]}),
 * so a slider is the natural control. The cycle button only exposed 3
 * preset values per field, hiding most of the range from the user. The
 * {@link SliderScale} layer keeps the math unit-testable while the screen
 * layer stays a thin vanilla-{@link Screen} adapter.</p>
 *
 * <p><b>Why {@link CycleButton} for enums.</b> {@link SeaMode} and
 * {@link TopologyMode} are enums with 2-3 values each — a slider over a
 * discrete enum set would be overkill, and vanilla's {@link CycleButton}
 * already handles the enum-cycle UI idiom cleanly.</p>
 *
 * <p><b>Why no erosion sub-editor yet.</b> {@link endterraforged.world.filter.ErosionConfig}
 * has 6 tunable fields (2 int + 4 float), and exposing them all needs its
 * own sub-screen or a paginated layout — not a flat stack of 13 widgets.
 * The current screen edits the 7 dimension-shape fields directly and opens
 * the {@link ErosionConfigEditorScreen} sub-screen for the 6 erosion fields.</p>
 *
 * <p><b>Architecture.</b> Follows RTF's {@code PresetEditorPage} pattern:
 * the screen holds an {@link EndPresetBuilder} (mutable editing state),
 * widgets mutate the builder, and Done builds the immutable
 * {@link EndPreset} via {@link EndPresetBuilder#build()}.</p>
 *
 * <p><b>Compile-only verification.</b> This screen cannot be unit-tested
 * in the sandbox (it requires a live {@code Minecraft} instance and a
 * render thread). The testable core lives in {@link EndPresetBuilder}
 * (round-trip, reset, setters) and {@link SliderScale} (position↔value
 * math); this screen is a thin view over both.</p>
 */
public class EndPresetEditorScreen extends Screen {

    /** Slider scales — defined as constants so they can be referenced from tests if needed. */
    private static final IntSliderScale WORLD_HEIGHT_SCALE = new IntSliderScale(1024, 8192, 64);
    private static final IntSliderScale MIN_Y_SCALE = new IntSliderScale(-4080, 0, 16);
    private static final IntSliderScale SEA_LEVEL_SCALE = new IntSliderScale(-128, 256, 8);
    private static final IntSliderScale ISLAND_BASELINE_SCALE = new IntSliderScale(-256, 512, 16);

    private final EndPresetBuilder builder;
    private final Runnable onDone;
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
        super(Component.translatable("endterraforged.gui.preset_editor.title"));
        this.builder = new EndPresetBuilder(initial);
        this.parent = parent;
        this.onDone = onDone;
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
        // Start below the title (drawn at y=20 in render()).
        int y = 50;
        final int widgetWidth = 200;
        final int widgetHeight = 20;
        final int rowHeight = 26;

        // --- int sliders (EndSlider with IntSliderScale) --------------------

        addRenderableWidget(new EndSlider(
                cx - widgetWidth / 2, y, widgetWidth, widgetHeight,
                Component.translatable("endterraforged.gui.world_height"),
                WORLD_HEIGHT_SCALE, builder.worldHeight(),
                v -> builder.worldHeight((int) v)));
        y += rowHeight;

        addRenderableWidget(new EndSlider(
                cx - widgetWidth / 2, y, widgetWidth, widgetHeight,
                Component.translatable("endterraforged.gui.min_y"),
                MIN_Y_SCALE, builder.minY(),
                v -> builder.minY((int) v)));
        y += rowHeight;

        addRenderableWidget(new EndSlider(
                cx - widgetWidth / 2, y, widgetWidth, widgetHeight,
                Component.translatable("endterraforged.gui.sea_level_y"),
                SEA_LEVEL_SCALE, builder.seaLevelY(),
                v -> builder.seaLevelY((int) v)));
        y += rowHeight;

        addRenderableWidget(new EndSlider(
                cx - widgetWidth / 2, y, widgetWidth, widgetHeight,
                Component.translatable("endterraforged.gui.island_baseline_y"),
                ISLAND_BASELINE_SCALE, builder.islandBaselineY(),
                v -> builder.islandBaselineY((int) v)));
        y += rowHeight;

        // --- enum cycle buttons (SeaMode, TopologyMode) ---------------------

        addRenderableWidget(CycleButton.<SeaMode>builder(mode -> Component.literal(mode.name()))
                .withValues(SeaMode.values())
                .withInitialValue(builder.seaMode())
                .create(cx - widgetWidth / 2, y, widgetWidth, widgetHeight,
                        Component.translatable("endterraforged.gui.sea_mode"),
                        (btn, mode) -> builder.seaMode(mode)));
        y += rowHeight;

        addRenderableWidget(CycleButton.<TopologyMode>builder(mode -> Component.literal(mode.name()))
                .withValues(TopologyMode.values())
                .withInitialValue(builder.topologyMode())
                .create(cx - widgetWidth / 2, y, widgetWidth, widgetHeight,
                        Component.translatable("endterraforged.gui.topology_mode"),
                        (btn, mode) -> builder.topologyMode(mode)));
        y += rowHeight;

        // --- boolean toggle (floatingIslandsEnabled) -------------------------

        addRenderableWidget(CycleButton.booleanBuilder(
                        Component.translatable("endterraforged.gui.floating_islands.on"),
                        Component.translatable("endterraforged.gui.floating_islands.off"))
                .withInitialValue(builder.floatingIslandsEnabled())
                .create(cx - widgetWidth / 2, y, widgetWidth, widgetHeight,
                        Component.translatable("endterraforged.gui.floating_islands"),
                        (btn, enabled) -> builder.floatingIslandsEnabled(enabled)));
        y += rowHeight;

        // --- erosion sub-editor button --------------------------------------

        addRenderableWidget(Button.builder(
                        Component.translatable("endterraforged.gui.erosion.open"),
                        btn -> Minecraft.getInstance().setScreen(
                                new ErosionConfigEditorScreen(
                                        builder.erosionConfig(),
                                        config -> builder.erosionConfig(config))))
                .bounds(cx - widgetWidth / 2, y, widgetWidth, widgetHeight)
                .build());
        y += rowHeight;

        // --- Done / Cancel buttons ------------------------------------------

        addRenderableWidget(Button.builder(
                        Component.translatable("endterraforged.gui.done"),
                        btn -> {
                            // Snapshot the builder state into an immutable
                            // preset and publish it via EndPresetAccess so
                            // MixinRandomState (which runs later on the same
                            // thread during world creation) reads the user's
                            // edits instead of EndPreset.defaults().
                            EndPreset built = builder.build();
                            EndPresetAccess.set(built);
                            if (onDone != null) onDone.run();
                            onClose();
                        })
                .bounds(cx - widgetWidth / 2, y, widgetWidth / 2 - 2, widgetHeight)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.cancel"),
                        btn -> onClose())
                .bounds(cx + 2, y, widgetWidth / 2 - 2, widgetHeight)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }
}
