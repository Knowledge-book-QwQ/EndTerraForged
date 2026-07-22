package endterraforged.world.heightmap;

import endterraforged.world.continent.ContinentSignalBuffer;

/**
 * Caller-owned combined column signal. Mainland ownership remains in the
 * embedded continent buffer; the attached archipelago only contributes
 * physical landmass support and diagnostic data.
 */
public final class EndLandmassSignalBuffer {

    private final ContinentSignalBuffer continentSignals = new ContinentSignalBuffer();
    private final EndArchipelagoSignalBuffer archipelagoSignals = new EndArchipelagoSignalBuffer();
    private float landness;

    /** Clears only the derived archipelago and combined values. */
    public void resetDerived() {
        this.archipelagoSignals.clear();
        this.landness = 0.0F;
    }

    /** Package-private destination used by EndHeightmap to sample the mainland. */
    ContinentSignalBuffer continentSignals() {
        return this.continentSignals;
    }

    /** Package-private destination used by EndHeightmap to sample the feature. */
    EndArchipelagoSignalBuffer archipelagoSignals() {
        return this.archipelagoSignals;
    }

    /** Package-private composition step shared by runtime and tests. */
    void combine() {
        this.landness = Math.max(this.continentSignals.landness(), this.archipelagoSignals.landness());
    }

    public float edge() {
        return this.continentSignals.edge();
    }

    public float mainlandLandness() {
        return this.continentSignals.landness();
    }

    public float inlandness() {
        return this.continentSignals.inlandness();
    }

    public boolean identified() {
        return this.continentSignals.identified();
    }

    public long continentId() {
        return this.continentSignals.continentId();
    }

    public int centerX() {
        return this.continentSignals.centerX();
    }

    public int centerZ() {
        return this.continentSignals.centerZ();
    }

    public float archipelagoMask() {
        return this.archipelagoSignals.mask();
    }

    public float archipelagoLandness() {
        return this.archipelagoSignals.landness();
    }

    public float archipelagoInlandness() {
        return this.archipelagoSignals.inlandness();
    }

    public float archipelagoReliefWeight() {
        return this.archipelagoSignals.reliefWeight();
    }

    public boolean archipelagoIdentified() {
        return this.archipelagoSignals.identified();
    }

    public long archipelagoChainId() {
        return this.archipelagoSignals.chainId();
    }

    public float landness() {
        return this.landness;
    }
}
