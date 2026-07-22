package endterraforged.neoforge.client;

import java.nio.file.Path;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import endterraforged.EndTerraForged;
import endterraforged.client.gui.screen.EndPresetEditorBootstrap;
import endterraforged.client.gui.screen.EndPresetEditorContext;
import endterraforged.client.gui.screen.EndPresetEditorLaunchPolicy;
import endterraforged.client.gui.screen.EndPresetEditorScreen;
import endterraforged.world.config.EndPresetAccess;
import endterraforged.world.config.WorldVerticalBounds;

/**
 * NeoForge adapter for opening the preset editor from a local integrated
 * server's pause menu.
 */
final class ExistingWorldPresetEditorLauncher {

    private ExistingWorldPresetEditorLauncher() {
    }

    static boolean canOpenFromCurrentClient() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null
                && minecraft.getSingleplayerServer() != null;
    }

    static void open(Screen parent) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }

        Path worldDir = server.getWorldPath(LevelResource.ROOT).normalize();
        EndPresetEditorBootstrap.InitialPresetResult initialPreset =
                EndPresetEditorBootstrap.loadInitialPreset(
                        worldDir,
                        EndPresetAccess::getEditableOrDefault,
                        message -> EndTerraForged.LOGGER.warn(
                                "EndTerraForged preset file at {} could not be loaded for editing. {}",
                                worldDir,
                                message));
        EndPresetEditorLaunchPolicy.LaunchAction action =
                EndPresetEditorLaunchPolicy.afterInitialPreset(initialPreset);
        if (!action.openEditor()) {
            action.toast().ifPresent(toast -> showToast(
                    minecraft, SystemToast.SystemToastId.PACK_LOAD_FAILURE, toast));
            return;
        }

        ServerLevel endLevel = server.getLevel(Level.END);
        WorldVerticalBounds actualBounds = endLevel != null
                ? new WorldVerticalBounds(endLevel.getMinBuildHeight(), endLevel.getHeight())
                : initialPreset.preset().orElseThrow().worldBounds();
        minecraft.setScreen(new EndPresetEditorScreen(
                initialPreset.preset().orElseThrow(),
                parent,
                ExistingWorldPresetEditorLauncher::showSavedToast,
                EndPresetEditorContext.forWorld(worldDir, actualBounds)));
    }

    private static void showSavedToast() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            showToast(minecraft, SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    EndPresetEditorLaunchPolicy.savedToast());
        }
    }

    private static void showToast(
            Minecraft minecraft,
            SystemToast.SystemToastId id,
            EndPresetEditorLaunchPolicy.ToastMessage toast) {
        SystemToast.addOrUpdate(
                minecraft.getToasts(),
                id,
                Component.translatable(toast.titleKey()),
                Component.translatable(toast.descriptionKey()));
    }
}
