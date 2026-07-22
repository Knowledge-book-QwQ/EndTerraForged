package endterraforged.world.cave;

import java.util.Objects;

import endterraforged.world.config.CaveChamberConfig;
import endterraforged.world.config.CaveNetworkConfig;
import endterraforged.world.config.CaveSystemConfig;
import endterraforged.world.config.SubsurfaceConfig;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.NoiseMath;
import endterraforged.world.noise.Noises;

/**
 * Top-down footprint mask for the spectacle-first cave system.
 *
 * <p>This is intentionally a two-dimensional preview/runtime seed, not the
 * final cave carver. It gives the GUI and tests a deterministic cave layout
 * signal while the later 3D field can extend the same config groups with depth
 * and corridor graph semantics.</p>
 */
public final class EndCavePreviewMask {

    public static final EndCavePreviewMask DISABLED =
            new EndCavePreviewMask(CaveSystemConfig.DISABLED, CaveNetworkConfig.DEFAULT,
                    CaveChamberConfig.DEFAULT, Noises.zero(), Noises.zero(),
                    Noises.zero(), Noises.zero(), 0);

    private final CaveSystemConfig system;
    private final CaveNetworkConfig network;
    private final CaveChamberConfig chambers;
    private final Noise regionNoise;
    private final Noise chamberNoise;
    private final Noise networkNoise;
    private final Noise roughnessNoise;
    private final int seed;
    private final float minLandness;
    private final float regionThreshold;
    private final float chamberThreshold;
    private final float networkThreshold;
    private final float roughness;

    private EndCavePreviewMask(CaveSystemConfig system,
                               CaveNetworkConfig network,
                               CaveChamberConfig chambers,
                               Noise regionNoise,
                               Noise chamberNoise,
                               Noise networkNoise,
                               Noise roughnessNoise,
                               int seed) {
        this.system = system;
        this.network = network;
        this.chambers = chambers;
        this.regionNoise = regionNoise;
        this.chamberNoise = chamberNoise;
        this.networkNoise = networkNoise;
        this.roughnessNoise = roughnessNoise;
        this.seed = seed;
        this.minLandness = network.minLandness();
        this.regionThreshold = 1.0F - (0.25F + network.networkDensity() * 0.45F
                + system.spectacleBias() * 0.20F);
        float radiusBias = (chambers.minRadius() + chambers.maxRadius())
                / (float) Math.max(1, network.chamberSpacing() * 2);
        float chamberCoverage = NoiseMath.clamp(0.08F
                + chambers.chamberProbability() * 0.48F
                + system.spectacleBias() * 0.24F
                + radiusBias * 0.20F,
                0.03F, 0.92F);
        float networkCoverage = NoiseMath.clamp(0.06F
                + network.networkDensity() * 0.34F
                + system.connectivity() * 0.24F
                + (network.branchingFactor() / 8.0F) * 0.18F
                + network.loopChance() * 0.14F,
                0.03F, 0.9F);
        this.chamberThreshold = 1.0F - chamberCoverage;
        this.networkThreshold = 1.0F - networkCoverage;
        this.roughness = chambers.roughness();
    }

    public static EndCavePreviewMask fromConfig(SubsurfaceConfig config, int seed) {
        Objects.requireNonNull(config, "config");
        CaveSystemConfig system = config.caveSystemConfig();
        if (!system.enabled()) {
            return DISABLED;
        }

        CaveNetworkConfig network = config.caveNetworkConfig();
        CaveChamberConfig chambers = config.caveChamberConfig();
        int caveSeed = seed + system.seedOffset();
        int corridorScale = corridorScale(network);
        int roughnessScale = Math.max(24, chambers.minRadius());
        Noise regionNoise = Noises.map(
                Noises.simplex(caveSeed, network.regionSize(), 3, 2.0F, 0.5F),
                0.0F, 1.0F);
        Noise chamberNoise = Noises.map(
                Noises.simplex(caveSeed + 101, network.chamberSpacing(), 3, 2.0F, 0.5F),
                0.0F, 1.0F);
        Noise networkNoise = Noises.map(
                Noises.simplex(caveSeed + 211, corridorScale, 4, 2.0F, 0.55F),
                0.0F, 1.0F);
        Noise roughnessNoise = Noises.map(
                Noises.simplex(caveSeed + 353, roughnessScale, 2, 2.0F, 0.5F),
                0.0F, 1.0F);
        return new EndCavePreviewMask(system, network, chambers, regionNoise,
                chamberNoise, networkNoise, roughnessNoise, caveSeed);
    }

    public boolean enabled() {
        return this.system.enabled();
    }

    public float strength(float x, float z, float landness) {
        return combinedStrength(x, z, landness);
    }

    public float chamberStrength(float x, float z, float landness) {
        return componentStrength(x, z, landness, true);
    }

    public float networkStrength(float x, float z, float landness) {
        return componentStrength(x, z, landness, false);
    }

    private float combinedStrength(float x, float z, float landness) {
        if (!enabled() || landness < this.minLandness) {
            return 0.0F;
        }

        float landGate = smoothstep(this.minLandness, 1.0F, landness);
        float region = sample(this.regionNoise, x, z);
        float roughness = (sample(this.roughnessNoise, x, z) - 0.5F) * this.roughness;
        float regionGate = smoothstep(this.regionThreshold, 1.0F, region);

        float chamberValue = NoiseMath.clamp(sample(this.chamberNoise, x, z)
                + roughness * 0.22F, 0.0F, 1.0F);
        float chamberMask = smoothstep(this.chamberThreshold, 1.0F, chamberValue);
        float networkValue = sample(this.networkNoise, x, z);
        float corridorRidge = 1.0F - Math.abs(networkValue * 2.0F - 1.0F);
        corridorRidge = NoiseMath.clamp(corridorRidge + roughness * 0.14F, 0.0F, 1.0F);
        float networkMask = smoothstep(this.networkThreshold, 1.0F, corridorRidge);

        float gatedRegion = 0.45F + regionGate * 0.55F;
        float chamberStrength = chamberMask * gatedRegion * landGate;
        float networkStrength = networkMask * gatedRegion * landGate;
        return NoiseMath.clamp(Math.max(chamberStrength, networkStrength), 0.0F, 1.0F);
    }

    private float componentStrength(float x, float z, float landness, boolean chamber) {
        if (!enabled() || landness < this.minLandness) {
            return 0.0F;
        }

        float landGate = smoothstep(this.minLandness, 1.0F, landness);
        float region = sample(this.regionNoise, x, z);
        float roughness = (sample(this.roughnessNoise, x, z) - 0.5F) * this.roughness;
        float regionGate = smoothstep(this.regionThreshold, 1.0F, region);

        float mask;
        if (chamber) {
            float chamberValue = NoiseMath.clamp(sample(this.chamberNoise, x, z)
                    + roughness * 0.22F, 0.0F, 1.0F);
            mask = smoothstep(this.chamberThreshold, 1.0F, chamberValue);
        } else {
            float networkValue = sample(this.networkNoise, x, z);
            float corridorRidge = 1.0F - Math.abs(networkValue * 2.0F - 1.0F);
            corridorRidge = NoiseMath.clamp(corridorRidge + roughness * 0.14F, 0.0F, 1.0F);
            mask = smoothstep(this.networkThreshold, 1.0F, corridorRidge);
        }
        float gatedRegion = 0.45F + regionGate * 0.55F;
        return NoiseMath.clamp(mask * gatedRegion * landGate, 0.0F, 1.0F);
    }


    private float sample(Noise noise, float x, float z) {
        return NoiseMath.clamp(noise.compute(x, z, this.seed), 0.0F, 1.0F);
    }


    private static float smoothstep(float edge0, float edge1, float value) {
        float width = Math.max(0.0001F, edge1 - edge0);
        float alpha = NoiseMath.clamp((value - edge0) / width, 0.0F, 1.0F);
        return alpha * alpha * (3.0F - 2.0F * alpha);
    }

    private static int corridorScale(CaveNetworkConfig network) {
        float divisor = Math.max(1.0F, network.branchingFactor() + 1.0F);
        return Math.max(32, Math.round(network.chamberSpacing() / divisor));
    }

}
