/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). RTF (MIT) has no
 * equivalent: RTF's overworld always has a seabed, so "below sea level =
 * void" never happens upstream. This is the stage-2.6 post-process that
 * makes SeaMode.NO_FLOOR and SeaMode.NONE produce floating islands /
 * floorless seas.
 */
package endterraforged.world.heightmap;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Objects;

import endterraforged.world.config.SeaMode;
import endterraforged.world.noise.Interpolation;
import endterraforged.world.noise.Noise;
import endterraforged.world.noise.Simplex;

/**
 * The 3D solidity field of the End: decides, for a given world block
 * coordinate, whether terrain should be solid or void.
 *
 * <p>This is the bridge between the 2D {@link EndHeightmap} (which only knows
 * the above-surface terrain shape) and the full column structure that chunk
 * generation needs. The stage-3 {@code DensityFunction} bridge will delegate
 * to this class so that vanilla's {@code final_density} channel reflects the
 * End's sea-mode semantics without the algorithm layer branching on topology.</p>
 *
 * <p><b>Column structure by {@link SeaMode}.</b> Finite landmasses bound their
 * own solid body between the terrain top and configured underside. Sea mode
 * independently controls exterior columns: WITH_FLOOR adds a coherent seabed,
 * while NONE and NO_FLOOR leave exterior density empty.</p>
 *
 * <p>Given a terrain top at
 * {@code terrainTopY} and the dimension's reference surface at
 * {@code surfaceY}:</p>
 * <ul>
 *   <li><b>WITH_FLOOR</b> — solid from {@code terrainTopY} down to the world
 *       bottom (continuous seabed). This is the closest to RTF's overworld.</li>
 *   <li><b>NONE</b> — solid only between {@code surfaceY} and {@code terrainTopY};
 *       everything below {@code surfaceY} is void. Islands float over nothing.</li>
 *   <li><b>NO_FLOOR</b> — same as NONE: solid only above {@code surfaceY}, void
 *       below. The difference from NONE is semantic (surface = sea level, so
 *       there is water above the void) rather than structural.</li>
 * </ul>
 *
 * <p>Where the continent reports {@code landness == 0} (open space / rift),
 * NONE and NO_FLOOR remain empty. WITH_FLOOR becomes solid only at and below
 * its low-frequency seabed; the empty band above it is filled by the separate
 * exterior-ocean fluid picker.</p>
 *
 * <p><b>Output.</b> Returns {@code 0.0} (void/air) or {@code 1.0} (solid).
 * Surface smoothing (interpolating density near the terrain top for natural
 * block edges) is deferred to the stage-3 vanilla {@code NoiseRouter}
 * interpolation; this class provides the discrete solid/void decision only.</p>
 *
 * <p><b>Thread safety.</b> Runtime dependencies are immutable. Each worker
 * thread owns a bounded primitive column cache, so parallel chunk-gen
 * threads never share mutable sample state.</p>
 */
public final class EndDensity {

    private static final int COLUMN_CACHE_SIZE = 256;
    private static final int COLUMN_CACHE_MASK = COLUMN_CACHE_SIZE - 1;
    private static final ThreadLocal<ColumnCache> COLUMN_SAMPLES =
            ThreadLocal.withInitial(ColumnCache::new);
    private static final int OCEAN_FLOOR_SEED_OFFSET = 0x4F434541;
    private static final Noise OCEAN_FLOOR_NOISE =
            new Simplex(1.0F / 768.0F, 3, 2.0F, 0.5F, Interpolation.CURVE3);

    private final EndHeightmap heightmap;
    private final EndLevels levels;
    private final SeaMode seaMode;
    private final EndLandmassVolume landmassVolume;
    private final EndSubsurface subsurface;

    /**
     * Builds an EndDensity backed by the given heightmap.
     *
     * @param heightmap the source 2D height field; its {@link EndHeightmap#levels()}
     *                  and {@link EndHeightmap#seaMode()} drive the column shape
     */
    public EndDensity(EndHeightmap heightmap) {
        this(heightmap, EndSubsurface.DISABLED);
    }

    /**
     * Builds an End density field with an explicit immutable subsurface runtime.
     *
     * @param heightmap the source height and continent fields
     * @param subsurface the cave and abyss carving runtime
     */
    public EndDensity(EndHeightmap heightmap, EndSubsurface subsurface) {
        this.heightmap = Objects.requireNonNull(heightmap, "heightmap");
        this.levels = heightmap.levels();
        this.seaMode = heightmap.seaMode();
        this.landmassVolume = heightmap.landmassVolume();
        this.subsurface = Objects.requireNonNull(subsurface, "subsurface");
    }

    /**
     * Terrain solidity at world block {@code (x, worldY, z)}.
     *
     * @param x      world X (block-aligned)
     * @param worldY world Y (block-aligned)
     * @param z      world Z (block-aligned)
     * @param seed   the world seed (must match the heightmap's seed for
     *               consistent continent / mountain sampling)
     * @return {@code 1.0} if the block should be solid terrain, {@code 0.0}
     *         if it should be void or air
     */
    public float density(float x, int worldY, float z, int seed) {
        float yNorm = this.levels.scale(worldY);
        // NONE / NO_FLOOR are guaranteed void below the reference surface.
        // Check this before touching the column cache: in a 512+ block world
        // the lower empty half is common, and no landness/terrain/cave sample
        // can change the result there.
        if (!this.landmassVolume.isFinite() && !this.seaMode.hasFloor() && yNorm <= this.levels.surface) {
            return 0.0F;
        }
        if (this.landmassVolume.isFinite() && !this.seaMode.hasFloor()
                && yNorm < this.landmassVolume.minimumUnderside()) {
            return 0.0F;
        }

        ColumnCache column = COLUMN_SAMPLES.get();
        column.refresh(this, x, z, seed);

        float landness = column.landness();
        boolean belowOceanFloor = this.seaMode.hasFloor() && worldY <= column.oceanFloorY();

        // The sea floor above is continuous across both open ocean and the
        // continent projection. Above that floor, a zero landness column has
        // no terrain body and is left to the fluid picker.
        if (landness <= 0.0F) {
            return belowOceanFloor ? 1.0F : 0.0F;
        }

        // Normalised terrain height in [surface, 1] and the block's normalised Y.
        // Comparing in normalised space avoids the int truncation of scale(float)
        // and keeps the surface / terrainTop comparisons on the same footing.
        float heightNorm = column.heightNorm();
        // Above the terrain top: air.
        if (yNorm > heightNorm) {
            return 0.0F;
        }

        if (this.landmassVolume.isFinite() && yNorm < column.undersideNorm()
                && !belowOceanFloor) {
            return 0.0F;
        }

        // The column bounds have already rejected top air and, for a finite
        // shelf, the lower void. Subsurface is the remaining local carve step.
        if (this.subsurface.carves(x, z, landness, yNorm, heightNorm, this.levels.worldHeight)) {
            return 0.0F;
        }

        return 1.0F;
    }

    /**
     * Convenience: whether the block at {@code (x, worldY, z)} is solid terrain.
     *
     * @return {@code true} iff {@link #density} returns {@code 1.0}
     */
    public boolean isSolid(float x, int worldY, float z, int seed) {
        return density(x, worldY, z, seed) >= 0.5F;
    }

    /**
     * Returns whether this void sample is connected to the configured sea.
     * Continental cave voids stay dry, while the open space below a finite
     * shelf is treated as ocean rather than as an underground cave.
     */
    public boolean hasOceanAt(float x, int worldY, float z, int seed) {
        if (!this.seaMode.hasSea() || worldY >= this.levels.surfaceY) {
            return false;
        }
        ColumnCache column = COLUMN_SAMPLES.get();
        column.refresh(this, x, z, seed);
        if (column.landness() <= 0.0F) {
            return true;
        }
        if (!this.landmassVolume.isFinite()
                || this.levels.scale(worldY) >= column.undersideNorm()) {
            return false;
        }
        return !this.seaMode.hasFloor() || worldY > column.oceanFloorY();
    }

    /** Package-private diagnostics hook for the WITH_FLOOR seabed contract. */
    int oceanFloorY(float x, float z, int seed) {
        ColumnCache column = COLUMN_SAMPLES.get();
        column.refresh(this, x, z, seed);
        return column.oceanFloorY();
    }

    /** The backing heightmap (exposed for stage-3 bridge code and tests). */
    public EndHeightmap heightmap() {
        return this.heightmap;
    }

    /** The runtime underground modifier layer. */
    public EndSubsurface subsurface() {
        return this.subsurface;
    }

    /**
     * Per-worker direct-mapped cache for the two-dimensional terrain column
     * inputs. A weak runtime owner lets long-lived C2ME workers reuse one
     * bounded cache across world loads without retaining completed worlds.
     */
    private static final class ColumnCache {
        private final boolean[] initialized = new boolean[COLUMN_CACHE_SIZE];
        private final int[] xBits = new int[COLUMN_CACHE_SIZE];
        private final int[] zBits = new int[COLUMN_CACHE_SIZE];
        private final int[] seeds = new int[COLUMN_CACHE_SIZE];
        private final float[] landness = new float[COLUMN_CACHE_SIZE];
        private final float[] heightNorm = new float[COLUMN_CACHE_SIZE];
        private final float[] undersideNorm = new float[COLUMN_CACHE_SIZE];
        private final int[] oceanFloorY = new int[COLUMN_CACHE_SIZE];
        private final EndLandmassSignalBuffer signals = new EndLandmassSignalBuffer();
        private WeakReference<EndDensity> owner = new WeakReference<>(null);
        private int selectedIndex;

        private void refresh(EndDensity density, float x, float z, int seed) {
            if (this.owner.get() != density) {
                this.owner = new WeakReference<>(density);
                Arrays.fill(this.initialized, false);
            }

            EndHeightmap heightmap = density.heightmap;
            EndLandmassVolume landmassVolume = density.landmassVolume;
            int nextXBits = Float.floatToIntBits(x);
            int nextZBits = Float.floatToIntBits(z);
            int index = cacheIndex(nextXBits, nextZBits, seed);
            this.selectedIndex = index;
            if (this.initialized[index] && this.xBits[index] == nextXBits
                    && this.zBits[index] == nextZBits && this.seeds[index] == seed) {
                return;
            }

            this.xBits[index] = nextXBits;
            this.zBits[index] = nextZBits;
            this.seeds[index] = seed;
            heightmap.sampleLandmassSignals(x, z, seed, this.signals);
            this.landness[index] = this.signals.landness();
            this.heightNorm[index] = this.landness[index] > 0.0F
                    ? heightmap.getHeight(x, z, seed, this.signals)
                    : 0.0F;
            this.undersideNorm[index] = landmassVolume.isFinite() && this.landness[index] > 0.0F
                    ? landmassVolume.underside(x, z, this.landness[index], this.heightNorm[index])
                    : 0.0F;
            this.oceanFloorY[index] = density.seaMode.hasFloor()
                    ? oceanFloorY(density.levels, x, z, seed)
                    : density.levels.minY;
            this.initialized[index] = true;
        }

        private float landness() {
            return this.landness[this.selectedIndex];
        }

        private float heightNorm() {
            return this.heightNorm[this.selectedIndex];
        }

        private float undersideNorm() {
            return this.undersideNorm[this.selectedIndex];
        }

        private int oceanFloorY() {
            return this.oceanFloorY[this.selectedIndex];
        }

        private static int oceanFloorY(EndLevels levels, float x, float z, int seed) {
            int baseDepth = Math.clamp(levels.worldHeight / 8, 48, 128);
            float variation = 0.75F + 0.5F
                    * OCEAN_FLOOR_NOISE.compute(x, z, seed + OCEAN_FLOOR_SEED_OFFSET);
            int depth = Math.max(2, Math.round(baseDepth * variation));
            return Math.max(levels.minY, levels.surfaceFillY - depth);
        }

        private static int cacheIndex(int xBits, int zBits, int seed) {
            int hash = xBits;
            hash = 31 * hash + zBits;
            hash = 31 * hash + seed;
            hash ^= hash >>> 16;
            hash *= 0x7feb352d;
            hash ^= hash >>> 15;
            return hash & COLUMN_CACHE_MASK;
        }
    }
}
