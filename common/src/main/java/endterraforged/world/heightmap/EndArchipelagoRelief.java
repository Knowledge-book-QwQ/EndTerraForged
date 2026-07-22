package endterraforged.world.heightmap;

import java.util.Objects;

import endterraforged.world.noise.Noise;
import endterraforged.world.noise.Noises;

/** Low and broad relief used by attached archipelago land only. */
public final class EndArchipelagoRelief {

    private static final float MAX_RELIEF = 0.34F;

    public static final EndArchipelagoRelief DISABLED = new EndArchipelagoRelief();

    private final Noise base;
    private final Noise ridge;
    private final Noise detail;
    private final boolean enabled;

    public EndArchipelagoRelief(int seed, int continentScale) {
        int scale = Math.max(512, continentScale);
        this.base = Noises.simplex(seed + 7712, scale, 3);
        this.ridge = Noises.perlinRidge(seed + 4829, Math.max(384, scale / 2), 3, 2.1F, 0.82F);
        this.detail = Noises.simplex(seed + 5541, Math.max(128, scale / 8), 2);
        this.enabled = true;
    }

    private EndArchipelagoRelief() {
        this.base = Noises.zero();
        this.ridge = Noises.zero();
        this.detail = Noises.zero();
        this.enabled = false;
    }

    /** Returns the final normalized archipelago top for this column. */
    public float top(float x, float z, EndLevels levels, EndArchipelagoSignalBuffer signals) {
        Objects.requireNonNull(levels, "levels");
        Objects.requireNonNull(signals, "signals");
        if (!this.enabled || signals.landness() <= 0.0F) {
            return levels.surface;
        }
        float baseValue = this.base.compute(x, z, 0);
        float ridgeValue = this.ridge.compute(x, z, 0);
        float detailValue = this.detail.compute(x, z, 0);
        float relief = Math.clamp(
                0.42F * baseValue + 0.38F * ridgeValue + 0.20F * detailValue,
                0.0F, 1.0F);
        float elevation = signals.landness()
                * (0.018F + signals.reliefWeight() * (0.08F + 0.20F * relief));
        return levels.surface + levels.elevationRange * Math.clamp(elevation, 0.0F, MAX_RELIEF);
    }

    public boolean enabled() {
        return this.enabled;
    }
}
