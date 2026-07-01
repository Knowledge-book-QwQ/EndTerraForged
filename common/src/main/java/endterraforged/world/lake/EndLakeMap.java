/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * EndTerraForged original design (LGPL-3.0-or-later). RTF (MIT) has no direct
 * equivalent — RTF lakes are a width-bulge of the river bed (a "wide zone1"
 * along a reach), coupled to the river network and sea-anchored hydrology.
 * The End has no sea in SeaMode.NONE, so lakes here are independent circular
 * basins placed per worley cell, with a water level anchored to the local
 * terrain at the lake centre (not sea level). Lakes generate regardless of
 * sea presence — "lake without sea" is the design centre.
 */
package endterraforged.world.lake;

import endterraforged.world.heightmap.EndHeightmap;
import endterraforged.world.heightmap.EndLevels;
import endterraforged.world.noise.NoiseMath;

/**
 * The End's lake network: a chained post-processor that carves circular
 * basins into the height field. Each worley cell may host one lake; the lake
 * is a 2D radial falloff centred on the cell, with water level anchored to
 * the local terrain (not sea level), so lakes generate in any sea mode.
 *
 * <p><b>Layout.</b> A worley cell grid (size {@link #cellSize}) defines
 * candidate lake centres. Each cell hosts a lake with probability
 * {@link #lakeChance}; the centre is the cell midpoint plus a small
 * hash-driven jitter. A 3×3 neighbourhood scan per sample finds the nearest
 * lake, so lakes from adjacent cells can overlap at borders — producing
 * compound shorelines rather than isolated puddles.</p>
 *
 * <p><b>Basin carving.</b> The perpendicular distance from the sample to the
 * lake centre is mapped through a hermite falloff: inside {@link #bedRadius}
 * the carver has full strength (open water), between {@code bedRadius} and
 * {@link #valleyRadius} it tapers to zero (shore → beach → undisturbed
 * terrain). The carver only <em>lowers</em> terrain.</p>
 *
 * <p><b>Water level — the End difference.</b> RTF anchors lake level to sea
 * (via {@code ContinentalHydrology}). The End has no sea in
 * {@link endterraforged.world.config.SeaMode#NONE}, so the lake level is
 * {@code centerHeight - depth × elevationRange}, where {@code centerHeight}
 * is the raw terrain height at the lake centre. This means a lake on a
 * high-altitude island sits high; a lake in a lowland sits low — each lake
 * is locally referenced, independent of any global sea.</p>
 *
 * <p><b>Chaining.</b> {@link #modifyHeight} takes an upstream
 * {@code inputHeight} (raw terrain or the output of a prior post-processor
 * such as {@link endterraforged.world.river.EndRiverMap}). The lake carves
 * relative to the upstream height, so a river running through a lake is
 * carved on top of the lake basin, not the raw terrain. The lake centre
 * height is sampled from the raw terrain field to avoid recursion.</p>
 *
 * <p><b>What this class does NOT do.</b> No water block placement (stage-4.7
 * surface system), no outlet carving (a lake with no outlet is a closed
 * basin — acceptable in the End where water can vanish into the void at the
 * island edge), no connectivity to rivers (stage 4.6 forks).</p>
 *
 * <p><b>Thread safety.</b> Immutable record; safe to query from parallel
 * chunk-gen threads.</p>
 *
 * @param cellSize     worley cell size in blocks; smaller = denser lake grid
 * @param lakeChance   fraction of cells that host a lake, in {@code [0,1]};
 *                     {@code 0} = no lakes, {@code 1} = every cell has one
 * @param bedRadius    open-water radius in blocks (full-strength carve zone)
 * @param valleyRadius total shore radius in blocks (carve tapers to 0 here)
 * @param depth        lake depth below the centre terrain height, as a
 *                     fraction of {@link EndLevels#elevationRange}
 */
public record EndLakeMap(float cellSize, float lakeChance, float bedRadius,
                         float valleyRadius, float depth) {

    /** End-tuned defaults: rarer than rivers, modest basins, shallow depth. */
    public static EndLakeMap defaults() {
        return new EndLakeMap(620.0F, 0.18F, 28.0F, 75.0F, 0.06F);
    }

    /**
     * Post-processes the height at {@code (x, z)}: returns the lake-carved
     * height, or {@code inputHeight} unchanged if no lake is near.
     *
     * <p>Void (landness 0) passes {@code inputHeight} through — lakes only
     * form on land.</p>
     *
     * @param x            world X
     * @param z            world Z
     * @param seed         world seed (must match the heightmap's seed)
     * @param heightmap    the source terrain field (for landness, levels, raw
     *                     centre sampling)
     * @param inputHeight  the upstream height at {@code (x, z)}
     * @return normalised height in {@code [surface, 1]}, carved where a lake
     *         sits; unchanged {@code inputHeight} otherwise
     */
    public float modifyHeight(float x, float z, int seed, EndHeightmap heightmap, float inputHeight) {
        // Lakes only form on land — void stays void.
        float landness = heightmap.getLandness(x, z, seed);
        if (landness <= 0.0F) {
            return inputHeight;
        }

        LakeSample sample = sampleNearestLake(x, z, seed);
        if (sample == null) {
            return inputHeight;
        }

        float lakerness = lakerness(sample.distance);
        if (lakerness <= 0.0F) {
            return inputHeight;
        }

        EndLevels levels = heightmap.levels();
        // Carve relative to the upstream height — chain with rivers / others.
        float terrainHeight = inputHeight;
        // Lake centre height: raw terrain at the lake centre. Sampled from the
        // raw field (not getHeight) to avoid recursion. The lake level is
        // locally referenced to the centre terrain, so a lake on a peak sits
        // high, a lake in a valley sits low — no sea required.
        float centerHeight = heightmap.getTerrainHeight(sample.cx, sample.cz, seed);
        float lakeLevel = centerHeight - depth * levels.elevationRange;

        // Lakes only lower terrain, never raise it. Where the lake level sits
        // above the upstream height (e.g. a low spot near a high-altitude
        // centre), min() leaves the terrain untouched.
        float carved = NoiseMath.lerp(terrainHeight, lakeLevel, lakerness);
        return Math.min(terrainHeight, carved);
    }

    /**
     * Lake intensity at a given distance from the centre. Returns {@code 1}
     * inside the bed (open water), tapers to {@code 0} at the shore radius via
     * a hermite curve for natural beaches.
     */
    float lakerness(float distance) {
        if (distance >= valleyRadius) {
            return 0.0F;
        }
        if (distance <= bedRadius) {
            return 1.0F;
        }
        float t = (distance - bedRadius) / (valleyRadius - bedRadius);
        return 1.0F - NoiseMath.interpHermite(t);
    }

    /**
     * Scans the 3×3 worley neighbourhood of {@code (x, z)} for the nearest
     * lake centre. Returns the centre coordinates and the sample's distance,
     * or {@code null} if no cell in the neighbourhood hosts a lake.
     */
    private LakeSample sampleNearestLake(float x, float z, int seed) {
        float invCell = 1.0F / cellSize;
        int cellX = NoiseMath.floor(x * invCell);
        int cellZ = NoiseMath.floor(z * invCell);

        float nearestCx = 0.0F;
        float nearestCz = 0.0F;
        float nearestDist = Float.MAX_VALUE;
        boolean found = false;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;
                if (!hasLake(seed, cx, cz)) {
                    continue;
                }
                float[] centre = lakeCentre(seed, cx, cz);
                float ddx = x - centre[0];
                float ddz = z - centre[1];
                float dist = (float) Math.sqrt(ddx * ddx + ddz * ddz);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearestCx = centre[0];
                    nearestCz = centre[1];
                    found = true;
                }
            }
        }

        if (!found) {
            return null;
        }
        return new LakeSample(nearestCx, nearestCz, nearestDist);
    }

    /**
     * Whether the worley cell at {@code (cx, cz)} hosts a lake, derived from a
     * per-cell hash. {@code lakeChance=0} → never; {@code 1} → always.
     */
    private boolean hasLake(int seed, int cx, int cz) {
        float h = NoiseMath.clamp((NoiseMath.valCoord2D(seed ^ 0x5117A3D2, cx, cz) + 1.0F) * 0.5F, 0.0F, 1.0F);
        return h < lakeChance;
    }

    /**
     * Builds the lake centre for cell {@code (cx, cz)}: the cell midpoint plus
     * a small hash-driven jitter so centres don't sit on a perfect grid.
     */
    private float[] lakeCentre(int seed, int cx, int cz) {
        float jitterX = NoiseMath.valCoord2D(seed + 0x9E3779B1, cx, cz) * 0.18F * cellSize;
        float jitterZ = NoiseMath.valCoord2D(seed + 0x85EBCA77, cx, cz) * 0.18F * cellSize;
        return new float[] {
                (cx + 0.5F) * cellSize + jitterX,
                (cz + 0.5F) * cellSize + jitterZ
        };
    }

    /** A sampled lake: the centre coordinates and the distance from the sample to it. */
    private record LakeSample(float cx, float cz, float distance) {
    }
}
