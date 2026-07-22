package endterraforged.world.continent;

import endterraforged.world.noise.Noise;
import endterraforged.world.noise.NoiseMath;

/**
 * Full mainland continent: emits solid land everywhere.
 */
public record CompleteContinent(float landness) implements Continent {

    @Override
    public float compute(float x, float z, int seed) {
        return NoiseMath.clamp(landness, 0.0F, 1.0F);
    }

    @Override
    public float minValue() {
        return 0.0F;
    }

    @Override
    public float maxValue() {
        return 1.0F;
    }

    @Override
    public Noise mapAll(Visitor visitor) {
        return visitor.apply(this);
    }
}
