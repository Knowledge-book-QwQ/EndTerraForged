package endterraforged.world.heightmap;

import java.util.Objects;

import endterraforged.world.cave.EndCaveField;
import endterraforged.world.config.AbyssPitConfig;
import endterraforged.world.config.SubsurfaceConfig;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.NoiseMath;
import endterraforged.world.noise.Noises;

/**
 * Runtime underground terrain modifiers.
 */
public final class EndSubsurface {

    public static final EndSubsurface DISABLED =
            new EndSubsurface(AbyssPitConfig.DISABLED, Noises.zero(),
                    EndCaveField.DISABLED, 0);

    private final AbyssPitConfig abyss;
    private final Noise abyssNoise;
    private final EndCaveField caves;
    private final int seed;

    private EndSubsurface(AbyssPitConfig abyss, Noise abyssNoise,
                          EndCaveField caves, int seed) {
        this.abyss = abyss;
        this.abyssNoise = abyssNoise;
        this.caves = caves;
        this.seed = seed;
    }

    public static EndSubsurface fromConfig(SubsurfaceConfig config, int seed) {
        Objects.requireNonNull(config, "config");
        AbyssPitConfig abyss = config.abyssPitConfig();
        EndCaveField caves = EndCaveField.fromConfig(config, seed);
        if (!abyss.enabled() && !caves.enabled()) {
            return DISABLED;
        }
        Noise noise = abyss.enabled()
                ? Noises.map(
                        Noises.simplex(seed + abyss.seedOffset(), abyss.pitScale(),
                                abyss.pitOctaves(), abyss.pitLacunarity(), abyss.pitGain()),
                        0.0F, 1.0F)
                : Noises.zero();
        return new EndSubsurface(abyss, noise, caves, seed);
    }

    public boolean enabled() {
        return abyss.enabled() || caves.enabled();
    }

    public boolean carves(float x, float z, float landness, float yNorm,
                          float terrainTopNorm, int worldHeight) {
        if (!enabled()) {
            return false;
        }
        if (caves.carves(x, z, landness, yNorm, terrainTopNorm, worldHeight)) {
            return true;
        }
        float strength = abyssStrength(x, z, landness);
        if (strength <= 0.0F) {
            return false;
        }
        float depthStrength = abyss.depthCurve() == 1.0F
                ? strength
                : (float) Math.pow(strength, abyss.depthCurve());
        float depthNorm = (abyss.depth() * depthStrength) / Math.max(1.0F, worldHeight);
        return yNorm <= terrainTopNorm && yNorm >= terrainTopNorm - depthNorm;
    }

    public float abyssStrength(float x, float z, float landness) {
        if (!abyss.enabled() || landness < abyss.minLandness()) {
            return 0.0F;
        }
        float value = NoiseMath.clamp(abyssNoise.compute(x, z, seed), 0.0F, 1.0F);
        if (value <= abyss.threshold()) {
            return 0.0F;
        }
        return NoiseMath.clamp((value - abyss.threshold()) / abyss.edgeFalloff(), 0.0F, 1.0F);
    }

    public float caveStrength(float x, float z, float landness, float yNorm,
                              float terrainTopNorm, int worldHeight) {
        return caves.strength(x, z, landness, yNorm, terrainTopNorm, worldHeight);
    }
}
