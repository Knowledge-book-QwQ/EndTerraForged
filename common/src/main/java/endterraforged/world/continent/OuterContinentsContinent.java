package endterraforged.world.continent;

import java.util.Objects;

import endterraforged.world.noise.Noise;

/**
 * Main-island-exterior macro landness: large deterministic continents
 * separated by End void.
 *
 * <p>The wrapped cell field owns continent centres, shape and domain warp.
 * This wrapper only gates it through {@link EndCentralRegionPolicy}, ensuring
 * ETF land cannot begin inside the protected vanilla region and ramps in
 * smoothly outside it. It contains no cache or mutable random state, so the
 * result is independent of chunk access order.</p>
 */
public record OuterContinentsContinent(Noise continents) implements Continent {

    public OuterContinentsContinent {
        Objects.requireNonNull(continents, "continents");
    }

    @Override
    public float compute(float x, float z, int seed) {
        float activation = EndCentralRegionPolicy.outerActivation(x, z);
        if (activation <= 0.0F) {
            return 0.0F;
        }
        return activation * continents.compute(x, z, seed);
    }

    @Override
    public void sampleSignals(float x, float z, int seed, ContinentSignalBuffer output) {
        float activation = EndCentralRegionPolicy.outerActivation(x, z);
        if (activation <= 0.0F) {
            output.set(0.0F, 0.0F, 0.0F);
            return;
        }
        if (this.continents instanceof Continent continent) {
            continent.sampleSignals(x, z, seed, output);
        } else {
            float landness = this.continents.compute(x, z, seed);
            output.set(landness, landness, 1.0F);
        }
        output.scale(activation);
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
        return visitor.apply(new OuterContinentsContinent(continents.mapAll(visitor)));
    }
}
