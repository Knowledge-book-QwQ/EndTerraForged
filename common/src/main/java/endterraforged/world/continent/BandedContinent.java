package endterraforged.world.continent;

import java.util.Objects;

import endterraforged.world.config.ContinentBandsConfig;
import endterraforged.world.noise.Noise;

/**
 * Applies End-specific shelf and inland bands to an algorithm's raw continent
 * field without changing that algorithm's golden output.
 */
public final class BandedContinent implements Continent {

    private static final ThreadLocal<ContinentSignalBuffer> COMPUTE_SCRATCH =
            ThreadLocal.withInitial(ContinentSignalBuffer::new);

    private final Noise source;
    private final Continent signalSource;
    private final ContinentBandsConfig bands;

    public BandedContinent(Noise source, ContinentBandsConfig bands) {
        this.source = Objects.requireNonNull(source, "source");
        this.signalSource = source instanceof Continent continent ? continent : null;
        this.bands = Objects.requireNonNull(bands, "bands");
    }

    @Override
    public float compute(float x, float z, int seed) {
        if (this.signalSource == null) {
            return this.bands.landness(this.source.compute(x, z, seed));
        }
        ContinentSignalBuffer output = COMPUTE_SCRATCH.get();
        this.signalSource.sampleSignals(x, z, seed, output);
        return this.bands.landness(continuousBandSignal(output.edge(), output.landness()));
    }

    @Override
    public void sampleSignals(float x, float z, int seed, ContinentSignalBuffer output) {
        Objects.requireNonNull(output, "output");
        if (this.signalSource != null) {
            this.signalSource.sampleSignals(x, z, seed, output);
        } else {
            float rawLandness = this.source.compute(x, z, seed);
            output.set(rawLandness, rawLandness, 1.0F);
        }
        float bandSignal = continuousBandSignal(output.edge(), output.landness());
        output.setValues(
                output.edge(),
                this.bands.landness(bandSignal),
                this.bands.inlandness(bandSignal));
    }

    /**
     * Retains the source's shape modulation at the outer shelf, then converges
     * to the continuous cell-edge signal before inland relief begins.
     *
     * <p>RTF's raw landness deliberately stops applying its coastal shape at
     * the inland threshold. Feeding that switch directly into ETF's relief
     * bands can turn a sub-pixel edge change into a full-height terrain wall.
     * The shelf-to-rim blend keeps the coastline shape without leaking that
     * discontinuity into continent interiors.</p>
     */
    private float continuousBandSignal(float edge, float shapedLandness) {
        if (!this.bands.enabled()) {
            return shapedLandness;
        }
        float alpha = smoothstep(edge, this.bands.shelfThreshold(), this.bands.rimThreshold());
        return lerp(shapedLandness, edge, alpha);
    }

    private static float smoothstep(float value, float start, float end) {
        float alpha = Math.clamp((value - start) / (end - start), 0.0F, 1.0F);
        return alpha * alpha * (3.0F - 2.0F * alpha);
    }

    private static float lerp(float start, float end, float alpha) {
        return start + (end - start) * alpha;
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
        return visitor.apply(new BandedContinent(this.source.mapAll(visitor), this.bands));
    }
}
