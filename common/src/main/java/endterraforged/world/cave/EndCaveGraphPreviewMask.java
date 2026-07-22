package endterraforged.world.cave;

import java.util.Objects;

import endterraforged.world.config.SubsurfaceConfig;
import endterraforged.world.noise.NoiseMath;

/**
 * Top-down debug mask for graph-derived cave features.
 *
 * <p>This mask exposes runtime-only graph semantics such as rifts and flow
 * corridors without adding graph nodes to preset JSON. It is intended for GUI
 * preview and tests; 3D carving still goes through {@link EndCaveField}.</p>
 */
public final class EndCaveGraphPreviewMask {

    public static final EndCaveGraphPreviewMask DISABLED =
            new EndCaveGraphPreviewMask(EndCaveGraph.DISABLED);

    private final EndCaveGraph graph;

    private EndCaveGraphPreviewMask(EndCaveGraph graph) {
        this.graph = graph;
    }

    public static EndCaveGraphPreviewMask fromConfig(SubsurfaceConfig config, int seed) {
        Objects.requireNonNull(config, "config");
        if (!config.caveSystemConfig().enabled()) {
            return DISABLED;
        }
        return new EndCaveGraphPreviewMask(EndCaveGraph.fromConfig(config, seed));
    }

    public boolean enabled() {
        return graph.enabled();
    }

    public float strength(float x, float z, float landness) {
        return Math.max(riftStrength(x, z, landness), flowStrength(x, z, landness));
    }

    public float riftStrength(float x, float z, float landness) {
        return graph.riftPreviewStrength(x, z, landness);
    }

    public float flowStrength(float x, float z, float landness) {
        return graph.flowPreviewStrength(x, z, landness);
    }

    /**
     * Returns a preview-only water candidate mask. It does not place fluid blocks
     * and must not be serialized into preset JSON.
     */
    public float waterCandidateStrength(float x, float z, float landness) {
        float flow = flowStrength(x, z, landness);
        float rift = riftStrength(x, z, landness);
        float landGate = smoothstep(0.35F, 1.0F, landness);
        return NoiseMath.clamp(flow * landGate * (1.0F - rift * 0.45F), 0.0F, 1.0F);
    }

    /**
     * Returns a preview-only lava candidate mask. It does not place fluid blocks
     * and must not be serialized into preset JSON.
     */
    public float lavaCandidateStrength(float x, float z, float landness) {
        float flow = flowStrength(x, z, landness);
        float rift = riftStrength(x, z, landness);
        float riftGate = smoothstep(0.0F, 1.0F, rift);
        return NoiseMath.clamp(flow * (0.25F + riftGate * 0.75F) + rift * 0.25F, 0.0F, 1.0F);
    }

    /**
     * @deprecated Use {@link #waterCandidateStrength(float, float, float)} to
     * make the preview-only semantics explicit.
     */
    @Deprecated(forRemoval = false)
    public float waterStrength(float x, float z, float landness) {
        return waterCandidateStrength(x, z, landness);
    }

    /**
     * @deprecated Use {@link #lavaCandidateStrength(float, float, float)} to
     * make the preview-only semantics explicit.
     */
    @Deprecated(forRemoval = false)
    public float lavaStrength(float x, float z, float landness) {
        return lavaCandidateStrength(x, z, landness);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float width = Math.max(0.0001F, edge1 - edge0);
        float alpha = NoiseMath.clamp((value - edge0) / width, 0.0F, 1.0F);
        return alpha * alpha * (3.0F - 2.0F * alpha);
    }
}
