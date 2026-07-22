package endterraforged.client.gui.screen;

import java.util.Objects;

import endterraforged.world.preview.TerrainPreviewMode;

/**
 * Pure preview-mode rules for the subsurface editor.
 */
final class SubsurfacePreviewModePolicy {

    private static final TerrainPreviewMode[] ABYSS_MODES = {
            TerrainPreviewMode.ABYSS
    };
    private static final TerrainPreviewMode[] CAVE_MODES = {
            TerrainPreviewMode.CAVES,
            TerrainPreviewMode.CAVE_CHAMBERS,
            TerrainPreviewMode.CAVE_NETWORK,
            TerrainPreviewMode.CAVE_RIFTS,
            TerrainPreviewMode.CAVE_FLOWS,
            TerrainPreviewMode.CAVE_DEPTH,
            TerrainPreviewMode.CAVE_WATER,
            TerrainPreviewMode.CAVE_LAVA
    };

    private SubsurfacePreviewModePolicy() {
    }

    static TerrainPreviewMode[] modes(Section section) {
        return switch (Objects.requireNonNull(section, "section")) {
            case ABYSS -> ABYSS_MODES.clone();
            case CAVES -> CAVE_MODES.clone();
        };
    }

    static boolean allows(Section section, TerrainPreviewMode mode) {
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(mode, "mode");
        for (TerrainPreviewMode allowed : modes(section)) {
            if (allowed == mode) {
                return true;
            }
        }
        return false;
    }

    static TerrainPreviewMode fallbackMode(Section section) {
        return switch (Objects.requireNonNull(section, "section")) {
            case ABYSS -> TerrainPreviewMode.ABYSS;
            case CAVES -> TerrainPreviewMode.CAVES;
        };
    }

    static TerrainPreviewMode normalize(Section section, TerrainPreviewMode mode) {
        Objects.requireNonNull(mode, "mode");
        return allows(section, mode) ? mode : fallbackMode(section);
    }

    static boolean showsSlicePreview(Section section) {
        return Objects.requireNonNull(section, "section") == Section.CAVES;
    }

    enum Section {
        ABYSS,
        CAVES
    }

    record ModeMemory(TerrainPreviewMode abyssMode, TerrainPreviewMode caveMode) {
        static final ModeMemory DEFAULT = new ModeMemory(
                TerrainPreviewMode.ABYSS, TerrainPreviewMode.CAVES);

        ModeMemory {
            abyssMode = normalize(Section.ABYSS, Objects.requireNonNull(abyssMode, "abyssMode"));
            caveMode = normalize(Section.CAVES, Objects.requireNonNull(caveMode, "caveMode"));
        }

        TerrainPreviewMode modeFor(Section section) {
            return switch (Objects.requireNonNull(section, "section")) {
                case ABYSS -> abyssMode;
                case CAVES -> caveMode;
            };
        }

        ModeMemory remember(Section section, TerrainPreviewMode mode) {
            if (!allows(section, mode)) {
                return this;
            }
            return switch (section) {
                case ABYSS -> new ModeMemory(mode, caveMode);
                case CAVES -> new ModeMemory(abyssMode, mode);
            };
        }
    }
}
