/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Mixin on vanilla's
 * PresetEditor to register EndTerraForged's preset editor screen on the
 * "Customize" button of the World tab of CreateWorldScreen — pattern
 * borrowed from RTF's MixinPresetEditor (MIT).
 */
package endterraforged.mixin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import endterraforged.client.gui.screen.EndPresetEditorScreen;
import endterraforged.world.config.EndPreset;
import endterraforged.world.config.EndPresetAccess;

/**
 * Registers {@link EndPresetEditorScreen} as a {@link PresetEditor} on
 * vanilla's {@link PresetEditor#EDITORS} map for the
 * {@link WorldPresets#NORMAL} key, so the existing "Customize" button on
 * the World tab of {@link CreateWorldScreen} opens our editor instead of
 * only showing for Flat / Single-Biome world types.
 *
 * <p><b>The problem.</b> v0.1.5 removed the broken
 * {@code MixinCreateWorldScreen} that intercepted the create-world button
 * flow, but that left EndTerraForged with no UI entry point at all —
 * there was no way to reach {@link EndPresetEditorScreen} from the
 * create-world screen. The user asked for a button on the create-world
 * screen that opens the editor.</p>
 *
 * <p><b>The vanilla mechanism.</b> {@code CreateWorldScreen}'s World tab
 * has a "Customize" button ({@code customizeTypeButton}) that is only
 * enabled when the currently-selected world type has a
 * {@link PresetEditor} registered in {@link PresetEditor#EDITORS}.
 * {@code WorldTab.openPresetEditor()} looks up the editor for the
 * current world type and calls {@code createEditScreen(createWorldScreen,
 * worldCreationContext)} — vanilla returns a Flat-world editor or
 * Buffet-world editor.</p>
 *
 * <p><b>Why a Mixin and not an event.</b> The {@code EDITORS} map is
 * initialised in {@link PresetEditor}'s static {@code <clinit>} via
 * {@code Map.of(k1, v1, k2, v2)} — an immutable map that cannot be
 * modified after construction. The only way to add an entry is to
 * intercept the {@code Map.of} call itself. RTF uses the same pattern
 * ({@code MixinPresetEditor.@Redirect <clinit> Map.of(...)}) — it's the
 * canonical cross-loader pattern because {@code PresetEditor} is a
 * vanilla class present on both Fabric and NeoForge.</p>
 *
 * <p><b>Why {@code @At("INVOKE")} on {@code Map.of} and not
 * {@code @Inject} at {@code <clinit>} HEAD.</b> The {@code Map.of(k1, v1,
 * k2, v2)} call is the only site that constructs the immutable map. A
 * {@code @Redirect} on that call lets us return a mutable
 * {@link HashMap} containing the original two entries plus our third
 * entry. An {@code @Inject} at {@code <clinit>} HEAD or RETURN would
 * run before/after the {@code putstatic EDITORS} instruction but
 * couldn't easily replace the immutable map — we'd have to use
 * reflection to overwrite a {@code static final} field.</p>
 *
 * <p><b>Why {@code Optional.of(WorldPresets.NORMAL)} as the key.</b>
 * The {@code EDITORS} map is
 * {@code Map<Optional<ResourceKey<WorldPreset>>, PresetEditor>}. Vanilla
 * keys are {@code Optional.of(WorldPresets.FLAT)} and
 * {@code Optional.of(WorldPresets.SINGLE_BIOME_SURFACE)}. We use
 * {@code Optional.of(WorldPresets.NORMAL)} — the "Default" world type —
 * so the "Customize" button appears for the default world type, which
 * is what users select most of the time. (RTF uses the same key.)</p>
 *
 * <p><b>Why {@code remap = false}.</b> The {@code Map.of} call is a
 * JDK method on {@link Map}, not a vanilla method — its bytecode
 * reference does not need remapping between Mojang-mapped and
 * SRG-mapped Minecraft. Without {@code remap = false}, the Mixin
 * processor would try to remap a non-Minecraft method reference and
 * silently fail to find a target, dropping the redirect.</p>
 *
 * <p><b>Why this is in {@code common} not {@code fabric}/{@code neoforge}.</b>
 * {@link PresetEditor} is a vanilla class on both loaders; the same
 * Mixin works unchanged on both. Putting it in {@code common} avoids
 * duplicate code and keeps the {@code client} mixin array single-sourced
 * in {@code endterraforged-common.mixins.json}.</p>
 *
 * <p><b>Why it's in the {@code client} array of the mixin config.</b>
 * {@link PresetEditor} and {@link CreateWorldScreen} are client-only
 * classes. A Mixin on them would crash a dedicated server if loaded on
 * the server side. The mixin config's {@code client} array ensures
 * this Mixin is only applied when the client is present.</p>
 *
 * <p><b>What the editor does on Done.</b> {@link EndPresetEditorScreen}'s
 * Done button calls {@link EndPresetAccess#set} (publishes the edited
 * preset to the volatile holder read by {@code MixinRandomState}) and
 * then calls {@link EndPresetEditorScreen#onClose()} which returns to
 * the parent {@link CreateWorldScreen}. The user then clicks "Create"
 * on the create-world screen, which triggers {@code RandomState.create}
 * → our Mixin reads {@code EndPresetAccess.getOrDefault()} → builds the
 * End density field from the user's edited preset.</p>
 */
@Mixin(PresetEditor.class)
public interface MixinPresetEditor {

    /**
     * Replaces vanilla's immutable {@code Map.of(k1, v1, k2, v2)} with a
     * mutable {@link HashMap} that additionally contains an entry for the
     * EndTerraForged preset editor keyed on {@link WorldPresets#NORMAL}.
     *
     * <p>The two original entries (Flat + Single-Biome) are preserved
     * unchanged — vanilla's behaviour for those world types is
     * untouched. We only add a third entry that opens our editor when
     * the "Default" world type is selected and the user clicks
     * "Customize".</p>
     *
     * @param k1 the first key ({@code Optional.of(WorldPresets.FLAT)})
     * @param v1 the first value (Flat-world PresetEditor)
     * @param k2 the second key ({@code Optional.of(WorldPresets.SINGLE_BIOME_SURFACE)})
     * @param v2 the second value (Buffet-world PresetEditor)
     * @return a mutable map containing the two original entries plus the
     *         EndTerraForged entry for {@link WorldPresets#NORMAL}
     */
    @Redirect(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;",
                    remap = false
            ),
            remap = false
    )
    private static Map<Object, Object> endTerraForged$registerEndEditor(
            Object k1, Object v1, Object k2, Object v2) {
        // Start with a mutable HashMap so we can add our entry. The
        // originals are added by hand to preserve vanilla behaviour for
        // the Flat and Single-Biome world types.
        Map<Object, Object> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        // Register the EndTerraForged preset editor for the "Default"
        // (NORMAL) world type. When the user selects "Default" on the
        // create-world screen and clicks "Customize", vanilla calls
        // PresetEditor.createEditScreen(createWorldScreen, ctx) which
        // returns our EndPresetEditorScreen — vanilla then
        // Minecraft.setScreen(...) it.
        //
        // The lambda captures the createWorldScreen so we can return to
        // it on Done/Cancel. EndPresetAccess.getOrDefault() loads the
        // last-edited preset (or defaults() if none has been edited yet).
        map.put(
                Optional.of((ResourceKey<WorldPreset>) WorldPresets.NORMAL),
                (PresetEditor) (CreateWorldScreen screen, WorldCreationContext ctx) ->
                        new EndPresetEditorScreen(
                                EndPresetAccess.getOrDefault(),
                                screen,
                                () -> {}
                        )
        );
        return map;
    }
}
