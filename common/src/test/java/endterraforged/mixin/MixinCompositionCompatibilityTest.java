package endterraforged.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class MixinCompositionCompatibilityTest {

    private static final Path NOISE_CHUNK_MIXIN = Path.of(
            "src/main/java/endterraforged/mixin/MixinNoiseChunk.java");
    private static final Path NOISE_GENERATOR_MIXIN = Path.of(
            "src/main/java/endterraforged/mixin/MixinNoiseBasedChunkGenerator.java");

    @Test
    void endDensityBindingDoesNotOwnTheSharedNoiseChunkMapAllCall() throws Exception {
        String source = Files.readString(NOISE_CHUNK_MIXIN);

        assertFalse(source.contains("@Redirect("),
                "NoiseChunk mapAll is a shared worldgen extension point. A Redirect prevents "
                        + "RTF's Overworld wrapper and other compatible wrappers from composing.");
        assertTrue(source.contains("@ModifyArg("),
                "ETF must wrap the NoiseChunk mapAll visitor instead of owning the outer invocation.");
        assertTrue(source.contains("priority = 1100"),
                "ETF's argument transformer must run before default-priority redirect mixins so "
                        + "the original mapAll invocation remains available for their redirect.");
        assertTrue(source.contains("EndDensityVisitor.withChunkVisitor("),
                "The wrapped visitor must bind ETF placeholders before vanilla's per-chunk visitor.");
    }

    @Test
    void endFluidPolicyComposesAtNoiseChunkArgumentsAndUsesTheBoundRuntime() throws Exception {
        String source = Files.readString(NOISE_GENERATOR_MIXIN);

        assertFalse(source.contains("@Redirect("),
                "Fluid integration must not own NoiseChunk.forChunk; other worldgen wrappers need "
                        + "the invocation to remain composable.");
        assertTrue(source.contains("@ModifyArgs("),
                "ETF needs both RandomState and the fluid argument without replacing the invocation.");
        assertTrue(source.contains("EndRandomStateAccess"),
                "The picker must come from the dimension-bound immutable runtime, not process globals.");
        assertTrue(source.contains("endTerraForged$isEnd()"),
                "Non-ETF RandomState instances must retain the original generator picker.");
        assertTrue(source.contains("EndVoidFluidPicker.picker()"),
                "A degraded ETF runtime must still block vanilla's Y=-54 lava fallback.");
    }
}
