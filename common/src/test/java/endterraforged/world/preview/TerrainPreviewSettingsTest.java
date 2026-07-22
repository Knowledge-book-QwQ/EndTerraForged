package endterraforged.world.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TerrainPreviewSettingsTest {

    @Test
    void withersKeepSettingsImmutable() {
        TerrainPreviewSettings settings = TerrainPreviewSettings.DEFAULT;

        TerrainPreviewSettings layers = settings.withMode(TerrainPreviewMode.LAYERS);
        TerrainPreviewSettings wide = layers.withScale(TerrainPreviewScale.WIDE);

        assertEquals(TerrainPreviewMode.COMBINED, settings.mode());
        assertEquals(TerrainPreviewScale.NORMAL, settings.scale());
        assertEquals(TerrainPreviewMode.LAYERS, layers.mode());
        assertEquals(TerrainPreviewScale.NORMAL, layers.scale());
        assertEquals(TerrainPreviewMode.LAYERS, wide.mode());
        assertEquals(TerrainPreviewScale.WIDE, wide.scale());
    }

    @Test
    void rejectsNullSettingsMembers() {
        assertThrows(NullPointerException.class,
                () -> new TerrainPreviewSettings(null, TerrainPreviewScale.NORMAL));
        assertThrows(NullPointerException.class,
                () -> new TerrainPreviewSettings(TerrainPreviewMode.COMBINED, null));
    }
}
