package endterraforged.world.heightmap;

import java.util.Objects;

import endterraforged.world.config.ContinentAlgorithm;
import endterraforged.world.config.ContinentConfig;
import endterraforged.world.config.LandmassVolumeMode;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.Noises;

/**
 * Immutable vertical volume derived from a two-dimensional continent sample.
 *
 * <p>A floating shelf uses an independent low-frequency underside in its
 * interior and blends it back to the sampled terrain top at the void boundary.
 * This avoids both a perfectly flat base and a mirrored copy of every
 * high-frequency surface detail. The noise field is immutable and sampled once
 * per cached terrain column, so the calculation remains deterministic and safe
 * for parallel density sampling.</p>
 */
public final class EndLandmassVolume {

    private static final int UNDERSIDE_SEED_OFFSET = 0x51F15EED;
    private static final float UNDERSIDE_RELIEF_FACTOR = 0.18F;

    private final LandmassVolumeMode mode;
    private final float shelfThickness;
    private final float shelfEdgeThickness;
    private final float shelfThicknessBlocks;
    private final float shelfEdgeThicknessBlocks;
    private final float surface;
    private final float minimumUnderside;
    private final Noise undersideShape;

    public EndLandmassVolume(ContinentConfig config, EndLevels levels) {
        this(config, levels, 0);
    }

    public EndLandmassVolume(ContinentConfig config, EndLevels levels, int seed) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(levels, "levels");
        this.mode = config.landmassVolumeMode();
        this.shelfThicknessBlocks = config.shelfThickness();
        this.shelfEdgeThicknessBlocks = config.shelfEdgeThickness();
        this.shelfThickness = this.shelfThicknessBlocks / levels.worldHeight;
        this.shelfEdgeThickness = this.shelfEdgeThicknessBlocks / levels.worldHeight;
        this.surface = levels.surface;
        float maximumThickness = Math.max(this.shelfThickness, this.shelfEdgeThickness);
        this.minimumUnderside = Math.max(0.0F,
                this.surface - maximumThickness * (1.0F + UNDERSIDE_RELIEF_FACTOR));
        int macroScale = config.continentAlgorithm().usesRtfTectonicScale()
                ? config.continentScale()
                : config.outerContinentScale();
        this.undersideShape = Noises.simplex(
                seed + UNDERSIDE_SEED_OFFSET, Math.max(512, macroScale), 2);
    }

    public LandmassVolumeMode mode() {
        return this.mode;
    }

    public boolean isFinite() {
        return this.mode == LandmassVolumeMode.FLOATING_SHELF;
    }

    /**
     * Returns a proven lower bound for every finite shelf underside.
     *
     * <p>Terrain tops never fall below the dimension reference surface: raw
     * terrain begins there and the climate, river, and lake post-processors
     * preserve that bound. A shelf can therefore extend at most its thickest
     * configured depth below the reference surface. Density sampling may use
     * this bound to reject low Y values before querying a terrain column.</p>
     *
     * <p>Legacy columns have no finite underside, so callers must only use this
     * value when {@link #isFinite()} returns {@code true}.</p>
     */
    public float minimumUnderside() {
        return this.minimumUnderside;
    }

    /**
     * Returns the finite-shelf edge fade for a continent sample.
     *
     * <p>Finite landmasses use a smooth cubic fade so their terrain body and
     * auxiliary surface relief can converge at the same void boundary. Legacy
     * columns retain their historical full-strength terrain behaviour.</p>
     */
    public float edgeFade(float landness) {
        return isFinite() ? smoothstep(Math.clamp(landness, 0.0F, 1.0F)) : 1.0F;
    }

    /**
     * Returns the local finite-shelf support available below the reference
     * surface, measured in blocks.
     *
     * <p>This deliberately excludes terrain-top relief. A ridge must not prove
     * that it has enough vertical support by first raising the terrain top that
     * its own volume depends on.</p>
     */
    float availableShelfThicknessBlocks(float x, float z, float landness) {
        if (!isFinite()) {
            return Float.POSITIVE_INFINITY;
        }
        float edgeFade = edgeFade(landness);
        if (edgeFade <= 0.0F) {
            return 0.0F;
        }
        float thickness = this.shelfEdgeThicknessBlocks
                + (this.shelfThicknessBlocks - this.shelfEdgeThicknessBlocks) * edgeFade;
        float shape = this.undersideShape.compute(x, z, 0) * 2.0F - 1.0F;
        return Math.max(0.0F, edgeFade * thickness * (1.0F + shape * UNDERSIDE_RELIEF_FACTOR));
    }

    /**
     * Returns the normalised underside. Callers must only use this for finite
     * volumes; legacy columns have no meaningful underside.
     */
    public float underside(float x, float z, float landness, float terrainTop) {
        if (!isFinite()) {
            return 0.0F;
        }
        float edgeFade = edgeFade(landness);
        if (edgeFade <= 0.0F) {
            return terrainTop;
        }
        float thickness = this.shelfEdgeThickness
                + (this.shelfThickness - this.shelfEdgeThickness) * edgeFade;
        float shape = this.undersideShape.compute(x, z, 0) * 2.0F - 1.0F;
        float shapedThickness = thickness * (1.0F + shape * UNDERSIDE_RELIEF_FACTOR);
        float macroUnderside = Math.max(0.0F, this.surface - shapedThickness);
        return terrainTop + (macroUnderside - terrainTop) * edgeFade;
    }

    public float underside(float landness, float terrainTop) {
        return underside(0.0F, 0.0F, landness, terrainTop);
    }

    public boolean contains(float x, float z, float landness, float worldY, float terrainTop) {
        return !isFinite() || worldY >= underside(x, z, landness, terrainTop);
    }

    public boolean contains(float landness, float worldY, float terrainTop) {
        return contains(0.0F, 0.0F, landness, worldY, terrainTop);
    }

    private static float smoothstep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }
}
