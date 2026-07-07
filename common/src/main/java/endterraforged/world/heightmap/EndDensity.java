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

import endterraforged.world.config.SeaMode;

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
 * <p><b>Column structure by {@link SeaMode}.</b> Given a terrain top at
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
 * the entire column is void regardless of sea mode — the continent gate is
 * the primary existence switch, this class only shapes the column below the
 * terrain top.</p>
 *
 * <p><b>Output.</b> Returns {@code 0.0} (void/air) or {@code 1.0} (solid).
 * Surface smoothing (interpolating density near the terrain top for natural
 * block edges) is deferred to the stage-3 vanilla {@code NoiseRouter}
 * interpolation; this class provides the discrete solid/void decision only.</p>
 *
 * <p><b>Thread safety.</b> All state is read from the immutable
 * {@link EndHeightmap}; safe to query from parallel chunk-gen threads.</p>
 */
public final class EndDensity {

    private final EndHeightmap heightmap;
    private final EndLevels levels;
    private final SeaMode seaMode;

    /**
     * Builds an EndDensity backed by the given heightmap.
     *
     * @param heightmap the source 2D height field; its {@link EndHeightmap#levels()}
     *                  and {@link EndHeightmap#seaMode()} drive the column shape
     */
    public EndDensity(EndHeightmap heightmap) {
        this.heightmap = heightmap;
        this.levels = heightmap.levels();
        this.seaMode = heightmap.seaMode();
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
        // Continent gate: no landmass here means the whole column is void,
        // regardless of sea mode. This is what carves the void between islands
        // (ISLANDS) and the rift channels (CONTINENTAL_SHATTERED).
        float landness = this.heightmap.getLandness(x, z, seed);
        if (landness <= 0.0F) {
            return 0.0F;
        }

        // Normalised terrain height in [surface, 1] and the block's normalised Y.
        // Comparing in normalised space avoids the int truncation of scale(float)
        // and keeps the surface / terrainTop comparisons on the same footing.
        float heightNorm = this.heightmap.getHeight(x, z, seed);
        float yNorm = this.levels.scale(worldY);

        // Above the terrain top: air.
        if (yNorm > heightNorm) {
            return 0.0F;
        }

        // Below the terrain top. WITH_FLOOR fills solid all the way down (seabed).
        // NONE / NO_FLOOR carve void below the reference surface so the terrain
        // is a floating shell rather than a column rooted to the world bottom.
        if (!this.seaMode.hasFloor() && yNorm <= this.levels.surface) {
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

    /** The backing heightmap (exposed for stage-3 bridge code and tests). */
    public EndHeightmap heightmap() {
        return this.heightmap;
    }
}
