package endterraforged.world.climate;

import endterraforged.world.noise.Noise;
import endterraforged.world.noise.NoiseMath;

/**
 * RTF-style unit-interval falloff curve for climate channels.
 */
record ClimateFalloff(Noise input, float falloff) implements Noise {

    @Override
    public float compute(float x, float z, int seed) {
        float value = NoiseMath.clamp(input.compute(x, z, seed), 0.0F, 1.0F);
        float centered = (value - 0.5F) * 2.0F;
        float curved = NoiseMath.pow(Math.abs(centered), falloff);
        return NoiseMath.clamp(0.5F + Math.copySign(curved, centered) * 0.5F, 0.0F, 1.0F);
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
        return visitor.apply(new ClimateFalloff(input.mapAll(visitor), falloff));
    }
}
