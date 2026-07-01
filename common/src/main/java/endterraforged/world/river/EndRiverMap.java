/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). Informed by RTF's
 * rivermap / UpliftRiverCarver (MIT) — lineage TerraForged (dags) ->
 * ReTerraForged (raccoonman) -> EndTerraForged — but the water-level model
 * is rewritten: RTF anchors water level to sea level and routes rivers to
 * the ocean; End has no sea in SeaMode.NONE, so the riverbed descends from
 * a per-cell source height down to the dimension's reference surface (the
 * void threshold), letting water spill off the island edge into the void.
 */
package endterraforged.world.river;

import endterraforged.world.heightmap.EndHeightmap;
import endterraforged.world.heightmap.EndLevels;
import endterraforged.world.noise.NoiseMath;

/**
 * The End's river network: a post-processor that carves river valleys into an
 * {@link EndHeightmap}'s terrain field.
 *
 * <p><b>Network layout.</b> A worley cell grid defines candidate river reaches.
 * Each cell hosts a river with probability {@link #riverChance}; the river is a
 * 2D {@link River} segment running from the cell centre (the approximate
 * source / peak) outward in a hash-derived direction toward the cell edge (the
 * approximate island rim / void). A 3×3 neighbourhood scan per sample finds the
 * nearest reach, so rivers from adjacent cells can overlap at borders — that is
 * what makes rivers feel connected rather than confined to one cell.</p>
 *
 * <p><b>Zone carving.</b> Borrowed from RTF's {@code UpliftRiverCarver}: the
 * perpendicular distance to the reach segment is mapped through a hermite
 * falloff. Inside {@link #bedWidth} the carver has full strength (riverbed);
 * between {@code bedWidth} and {@link #valleyWidth} it tapers to zero (banks →
 * valley floor → undisturbed terrain). The carver only <em>lowers</em> terrain
 * ({@code lerp(terrain, bed, riverness)} with {@code bed < terrain}), never
 * raises it.</p>
 *
 * <p><b>Water level — the End difference.</b> RTF sets the riverbed level from
 * a sea-anchored {@code ContinentalHydrology} step function. The End has no
 * sea in {@link endterraforged.world.config.SeaMode#NONE}, so the bed level
 * interpolates along the reach: {@code lerp(sourceHeight, surface, t) - bedDepth},
 * where {@code sourceHeight} is the terrain height at the reach start and
 * {@code surface} is the dimension's reference surface (island baseline in
 * NONE, sea level otherwise). At {@code t=1} (island edge) the bed meets the
 * surface, so water naturally spills into the void — no sea required.</p>
 *
 * <p><b>What this class does NOT do.</b> It does not place water blocks (that
 * is a stage-4.7 surface-system concern requiring MC chunk access). It does
 * not generate forks, lakes, or wetlands (stage 4.5 / 4.6). It does not cache
 * river networks per tile (stage 4 perf optimisation). It is the minimal
 * valley-carving kernel that produces the "water runs off the island" shape.</p>
 *
 * <p><b>Thread safety.</b> All fields are immutable primitives set at
 * construction; the backing {@link EndHeightmap} is immutable. Safe to query
 * from parallel chunk-gen threads.</p>
 *
 * @param cellSize    worley cell size in blocks; smaller = denser river grid
 * @param riverChance fraction of cells that host a river, in {@code [0,1]};
 *                    {@code 0} = no rivers, {@code 1} = every cell has one
 * @param bedWidth    riverbed half-width in blocks (full-strength carve zone)
 * @param valleyWidth total valley half-width in blocks (carve tapers to 0 here)
 * @param bedDepth    riverbed depth below the along-reach water level, as a
 *                    fraction of {@link EndLevels#elevationRange}
 */
public record EndRiverMap(float cellSize, float riverChance, float bedWidth,
                          float valleyWidth, float bedDepth) {

    /** Reasonable End-tuned defaults: sparse rivers, narrow beds, shallow valleys. */
    public static EndRiverMap defaults() {
        return new EndRiverMap(380.0F, 0.35F, 12.0F, 90.0F, 0.04F);
    }

    /**
     * Post-processes the terrain height at {@code (x, z)}: returns the
     * river-carved height, or the original terrain height if no river is near.
     *
     * <p>Void (landness 0) is returned untouched — rivers only carve land.</p>
     *
     * @param x         world X
     * @param z         world Z
     * @param seed      world seed (must match the heightmap's seed)
     * @param heightmap the source terrain field
     * @return normalised height in {@code [surface, 1]}, carved where a river
     *         passes through
     */
    public float modifyHeight(float x, float z, int seed, EndHeightmap heightmap) {
        // Rivers only exist on land — void stays void.
        float landness = heightmap.getLandness(x, z, seed);
        if (landness <= 0.0F) {
            return heightmap.getHeight(x, z, seed);
        }

        RiverSample sample = sampleNearestRiver(x, z, seed);
        if (sample == null) {
            return heightmap.getHeight(x, z, seed);
        }

        float riverness = riverness(sample.distance);
        if (riverness <= 0.0F) {
            return heightmap.getHeight(x, z, seed);
        }

        EndLevels levels = heightmap.levels();
        float terrainHeight = heightmap.getHeight(x, z, seed);
        // Source height: terrain at the reach start. This is the "peak" the
        // river descends from. Sampling the heightmap at the source point is
        // safe — it does not recurse through modifyHeight (getHeight is the
        // raw continent × mountains field, pre-river).
        float sourceHeight = heightmap.getHeight(sample.river.x1(), sample.river.z1(), seed);
        // Bed descends from sourceHeight (t=0, island interior) to surface
        // (t=1, island edge / void threshold), then drops by bedDepth so water
        // sits in a channel rather than flush with the surface.
        float bedLevel = NoiseMath.lerp(sourceHeight, levels.surface, sample.t)
                - bedDepth * levels.elevationRange;

        // Rivers only lower terrain, never raise it. Where the bed sits above
        // the existing terrain (e.g. a low spot near a high-altitude source),
        // the min() leaves the terrain untouched — faithful to RTF's
        // `if (finalHeight < cell.height) cell.height = finalHeight` guard.
        float carved = NoiseMath.lerp(terrainHeight, bedLevel, riverness);
        return Math.min(terrainHeight, carved);
    }

    /**
     * River intensity at a given perpendicular distance from the reach.
     * Returns {@code 1} inside the bed, tapers to {@code 0} at the valley edge
     * via a hermite curve for smooth banks.
     */
    float riverness(float distance) {
        if (distance >= valleyWidth) {
            return 0.0F;
        }
        if (distance <= bedWidth) {
            return 1.0F;
        }
        float t = (distance - bedWidth) / (valleyWidth - bedWidth);
        return 1.0F - NoiseMath.interpHermite(t);
    }

    /**
     * Scans the 3×3 worley neighbourhood of {@code (x, z)} for the nearest
     * river reach. Returns the reach and the sample's perpendicular distance /
     * along-reach projection, or {@code null} if no cell in the neighbourhood
     * hosts a river.
     *
     * <p>One coherent scan (like {@code IslandsContinent}'s single worley
     * pass) ensures the nearest reach is found without the desynchronisation
     * that separate per-module scans would introduce.</p>
     */
    private RiverSample sampleNearestRiver(float x, float z, int seed) {
        float invCell = 1.0F / cellSize;
        int cellX = NoiseMath.floor(x * invCell);
        int cellZ = NoiseMath.floor(z * invCell);

        River nearest = null;
        float nearestDist = Float.MAX_VALUE;
        float nearestT = 0.0F;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;
                if (!hasRiver(seed, cx, cz)) {
                    continue;
                }
                River river = buildRiver(seed, cx, cz);
                float dist = river.distanceTo(x, z);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = river;
                    nearestT = river.projection(x, z);
                }
            }
        }

        if (nearest == null) {
            return null;
        }
        return new RiverSample(nearest, nearestDist, nearestT);
    }

    /**
     * Whether the worley cell at {@code (cx, cz)} hosts a river, derived from
     * a per-cell hash. {@code riverChance=0} → never; {@code 1} → always.
     */
    private boolean hasRiver(int seed, int cx, int cz) {
        float h = NoiseMath.clamp((NoiseMath.valCoord2D(seed, cx, cz) + 1.0F) * 0.5F, 0.0F, 1.0F);
        return h < riverChance;
    }

    /**
     * Builds the reach segment for cell {@code (cx, cz)}: starts at the cell
     * centre (jittered), runs outward in a hash-derived direction for one cell
     * length. The jitter and direction are deterministic per cell so the same
     * river is reconstructed on every sample.
     */
    private River buildRiver(int seed, int cx, int cz) {
        // Cell centre in world space, with a small hash-driven jitter so
        // sources don't sit on a perfect grid.
        float jitterX = (NoiseMath.valCoord2D(seed + 0x9E3779B1, cx, cz)) * 0.15F * cellSize;
        float jitterZ = (NoiseMath.valCoord2D(seed + 0x85EBCA77, cx, cz)) * 0.15F * cellSize;
        float startX = (cx + 0.5F) * cellSize + jitterX;
        float startZ = (cz + 0.5F) * cellSize + jitterZ;

        // Hash-derived direction: a full 2π angle so rivers radiate in all
        // directions across the grid.
        float angle = NoiseMath.clamp(
                (NoiseMath.valCoord2D(seed + 0xC2B2AE3D, cx, cz) + 1.0F) * 0.5F, 0.0F, 1.0F)
                * (float) (Math.PI * 2.0);
        float endX = startX + (float) Math.cos(angle) * cellSize;
        float endZ = startZ + (float) Math.sin(angle) * cellSize;

        return River.of(startX, startZ, endX, endZ);
    }

    /** A sampled river reach: the segment, the perpendicular distance to it, and the along-reach projection. */
    private record RiverSample(River river, float distance, float t) {
    }
}
