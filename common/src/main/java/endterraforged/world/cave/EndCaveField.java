package endterraforged.world.cave;

import java.util.Objects;

import endterraforged.world.config.CaveChamberConfig;
import endterraforged.world.config.CaveNetworkConfig;
import endterraforged.world.config.CaveSystemConfig;
import endterraforged.world.config.SubsurfaceConfig;
import endterraforged.world.noise.NoiseMath;

/**
 * Three-dimensional cave strength field for the spectacle-first cave system.
 *
 * <p>The field reuses the top-down cave footprint as its horizontal graph seed,
 * then applies a depth envelope from the cave system/chamber controls. This is
 * intentionally still lightweight: later graph/chamber nodes can replace the
 * internal math without changing {@code EndDensity}'s subsurface hook.</p>
 */
public final class EndCaveField {

    private static final float CARVE_THRESHOLD = 0.35F;
    private static final float MIN_HALF_HEIGHT_BLOCKS = 4.0F;

    public static final EndCaveField DISABLED =
            new EndCaveField(CaveSystemConfig.DISABLED, CaveNetworkConfig.DEFAULT,
                    CaveChamberConfig.DEFAULT, EndCavePreviewMask.DISABLED,
                    EndCaveGraph.DISABLED);

    private final CaveSystemConfig system;
    private final CaveNetworkConfig network;
    private final CaveChamberConfig chambers;
    private final EndCavePreviewMask footprint;
    private final EndCaveGraph graph;
    private final float minLandness;
    private final float centerAlpha;
    private final float maximumCarveDepth;

    private EndCaveField(CaveSystemConfig system,
                         CaveNetworkConfig network,
                         CaveChamberConfig chambers,
                         EndCavePreviewMask footprint,
                         EndCaveGraph graph) {
        this.system = system;
        this.network = network;
        this.chambers = chambers;
        this.footprint = footprint;
        this.graph = graph;
        this.minLandness = network.minLandness();
        this.centerAlpha = NoiseMath.clamp(0.35F
                + chambers.floorBias() * 0.3F
                + system.spectacleBias() * 0.1F,
                0.1F, 0.9F);
        float maximumChamberHalfHeight = Math.max(MIN_HALF_HEIGHT_BLOCKS,
                chambers.maxRadius() * chambers.verticalStretch() * 0.55F);
        this.maximumCarveDepth = system.depthEnd() + maximumChamberHalfHeight;
    }

    public static EndCaveField fromConfig(SubsurfaceConfig config, int seed) {
        Objects.requireNonNull(config, "config");
        CaveSystemConfig system = config.caveSystemConfig();
        if (!system.enabled()) {
            return DISABLED;
        }
        return new EndCaveField(system, config.caveNetworkConfig(),
                config.caveChamberConfig(), config.buildCavePreviewMask(seed),
                EndCaveGraph.fromConfig(config, seed));
    }

    public boolean enabled() {
        return system.enabled();
    }

    public boolean carves(float x, float z, float landness, float yNorm,
                          float terrainTopNorm, int worldHeight) {
        return strength(x, z, landness,
                yNorm, terrainTopNorm, worldHeight) >= CARVE_THRESHOLD;
    }

    public float strength(float x, float z, float landness, float yNorm,
                          float terrainTopNorm, int worldHeight) {
        if (!enabled() || worldHeight <= 0 || yNorm > terrainTopNorm
                || landness < this.minLandness) {
            return 0.0F;
        }

        float depthBlocks = (terrainTopNorm - yNorm) * worldHeight;
        if (depthBlocks > this.maximumCarveDepth) {
            return 0.0F;
        }

        float graphStrength = graph.strength(x, z, landness,
                yNorm, terrainTopNorm, worldHeight);
        float footprintStrength = footprint.strength(x, z, landness);
        if (footprintStrength <= 0.0F) {
            return graphStrength;
        }

        float start = effectiveDepthStart(footprintStrength);
        float end = Math.max(start + 1.0F, system.depthEnd());
        if (depthBlocks < start || depthBlocks > end) {
            return 0.0F;
        }

        float span = end - start;
        float radius = chamberRadius(footprintStrength);
        float center = start + span * this.centerAlpha;
        float maxHalfHeight = Math.max(MIN_HALF_HEIGHT_BLOCKS, span * 0.55F);
        float halfHeight = NoiseMath.clamp(radius * chambers.verticalStretch(),
                MIN_HALF_HEIGHT_BLOCKS, maxHalfHeight);

        float vertical = 1.0F - Math.abs(depthBlocks - center) / halfHeight;
        vertical = smoothstep(0.0F, 1.0F, vertical);

        float fade = Math.max(4.0F, radius * 0.25F);
        float topGate = smoothstep(start, start + fade, depthBlocks);
        float bottomGate = 1.0F - smoothstep(end - fade, end, depthBlocks);
        float footprintEnvelope = footprintStrength * vertical * topGate * bottomGate;
        return NoiseMath.clamp(Math.max(graphStrength, footprintEnvelope), 0.0F, 1.0F);
    }

    private float effectiveDepthStart(float footprintStrength) {
        float opening = system.surfaceOpeningChance() * footprintStrength;
        return Math.max(0.0F, system.depthStart() * (1.0F - opening));
    }

    private float chamberRadius(float footprintStrength) {
        float alpha = NoiseMath.clamp(footprintStrength + system.spectacleBias() * 0.25F,
                0.0F, 1.0F);
        return NoiseMath.lerp(chambers.minRadius(), chambers.maxRadius(), alpha);
    }


    private static float smoothstep(float edge0, float edge1, float value) {
        float width = Math.max(0.0001F, edge1 - edge0);
        float alpha = NoiseMath.clamp((value - edge0) / width, 0.0F, 1.0F);
        return alpha * alpha * (3.0F - 2.0F * alpha);
    }
}
